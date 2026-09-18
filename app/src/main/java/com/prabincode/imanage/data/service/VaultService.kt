/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.data.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.prabincode.imanage.IManageApp
import com.prabincode.imanage.MainActivity
import com.prabincode.imanage.R
import com.prabincode.imanage.data.model.FileItem
import com.prabincode.imanage.data.repository.VaultRepository
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VaultService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var activeJob: Job? = null

    companion object {
        const val CHANNEL_ID = "safe_vault_progress_channel"
        const val CHANNEL_NAME = "Safe Vault Operations"
        const val ALERT_CHANNEL_ID = "safe_vault_alerts_channel"
        const val ALERT_CHANNEL_NAME = "Safe Vault Alerts"

        const val NOTIFICATION_ID = 4001
        const val COMPLETION_NOTIFICATION_ID = 4002

        const val ACTION_ENCRYPT = "com.prabincode.imanage.action.ENCRYPT"
        const val ACTION_RESTORE = "com.prabincode.imanage.action.RESTORE"
        const val ACTION_IMPORT = "com.prabincode.imanage.action.IMPORT"
        const val ACTION_CANCEL = "com.prabincode.imanage.action.CANCEL"

        const val EXTRA_FILE_PATHS = "extra_file_paths"
        const val EXTRA_VAULT_IDS = "extra_vault_ids"
        const val EXTRA_URIS = "extra_uris"
        const val EXTRA_SHRED_ORIGINAL = "extra_shred_original"
        const val EXTRA_TARGET_DIR = "extra_target_dir"

        private val _currentProgress = MutableStateFlow<VaultProgressState?>(null)
        val currentProgress: StateFlow<VaultProgressState?> = _currentProgress.asStateFlow()

        private val _lastCompletedTimestamp = MutableStateFlow(0L)
        val lastCompletedTimestamp: StateFlow<Long> = _lastCompletedTimestamp.asStateFlow()

        fun notifyOperationCompleted() {
            _lastCompletedTimestamp.value = System.currentTimeMillis()
        }

        fun startEncrypt(context: Context, paths: List<String>, shredOriginal: Boolean = true) {
            val intent = Intent(context, VaultService::class.java).apply {
                action = ACTION_ENCRYPT
                putStringArrayListExtra(EXTRA_FILE_PATHS, ArrayList(paths))
                putExtra(EXTRA_SHRED_ORIGINAL, shredOriginal)
            }
            startServiceCompat(context, intent)
        }

        fun startRestore(context: Context, ids: List<Long>, targetDir: String? = null) {
            val intent = Intent(context, VaultService::class.java).apply {
                action = ACTION_RESTORE
                putExtra(EXTRA_VAULT_IDS, ids.toLongArray())
                putExtra(EXTRA_TARGET_DIR, targetDir)
            }
            startServiceCompat(context, intent)
        }

        fun startImport(context: Context, uris: List<Uri>) {
            val intent = Intent(context, VaultService::class.java).apply {
                action = ACTION_IMPORT
                putStringArrayListExtra(EXTRA_URIS, ArrayList(uris.map { it.toString() }))
            }
            startServiceCompat(context, intent)
        }

        fun cancelActiveOperation(context: Context) {
            val intent = Intent(context, VaultService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }

        private fun startServiceCompat(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent.action == ACTION_CANCEL) {
            activeJob?.cancel()
            _currentProgress.value = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // Start foreground immediately to adhere to Android 8+ ANR prevention
        val initialNotif = buildProgressNotification(
            title = "Safe Vault",
            message = "Preparing task...",
            percent = 0,
            isIndeterminate = true
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, initialNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, initialNotif)
        }

        when (intent.action) {
            ACTION_ENCRYPT -> {
                val paths = intent.getStringArrayListExtra(EXTRA_FILE_PATHS) ?: emptyList()
                val shred = intent.getBooleanExtra(EXTRA_SHRED_ORIGINAL, true)
                handleEncrypt(paths, shred)
            }
            ACTION_RESTORE -> {
                val ids = intent.getLongArrayExtra(EXTRA_VAULT_IDS)?.toList() ?: emptyList()
                val targetDir = intent.getStringExtra(EXTRA_TARGET_DIR)
                handleRestore(ids, targetDir)
            }
            ACTION_IMPORT -> {
                val uriStrings = intent.getStringArrayListExtra(EXTRA_URIS) ?: emptyList()
                val uris = uriStrings.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
                handleImport(uris)
            }
            else -> {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun handleEncrypt(paths: List<String>, shredOriginal: Boolean) {
        activeJob?.cancel()
        activeJob = serviceScope.launch {
            val repo = (application as IManageApp).vaultRepository
            var successCount = 0
            val totalFiles = paths.size

            for ((index, path) in paths.withIndex()) {
                if (!isActive) break
                val file = File(path)
                if (!file.exists()) continue

                val fileName = file.name
                val totalBytes = VaultRepository.calculateTotalSize(file)

                updateProgress(
                    VaultProgressState(
                        operation = VaultOperationType.ENCRYPT,
                        fileName = fileName,
                        fileIndex = index + 1,
                        totalFiles = totalFiles,
                        bytesProcessed = 0L,
                        totalBytes = totalBytes,
                        percent = 0
                    )
                )

                val result = repo.moveToVault(
                    originalFile = file,
                    shredOriginal = shredOriginal,
                    onProgress = { processed, total ->
                        val percent = ((processed.toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 100)
                        updateProgress(
                            VaultProgressState(
                                operation = VaultOperationType.ENCRYPT,
                                fileName = fileName,
                                fileIndex = index + 1,
                                totalFiles = totalFiles,
                                bytesProcessed = processed,
                                totalBytes = total,
                                percent = percent
                            )
                        )
                    }
                )

                if (result.isSuccess) {
                    successCount++
                    _lastCompletedTimestamp.value = System.currentTimeMillis()
                }
            }

            finishOperation(
                title = "Safe Vault Encryption Complete",
                message = if (successCount > 0) "Successfully secured $successCount item(s)" else "No items were encrypted"
            )
        }
    }

    private fun handleRestore(ids: List<Long>, targetDirPath: String?) {
        activeJob?.cancel()
        activeJob = serviceScope.launch {
            val app = application as IManageApp
            val repo = app.vaultRepository
            val dao = app.database.vaultDao()
            var successCount = 0
            val total = ids.size
            val targetDir = targetDirPath?.let { File(it) }

            for ((index, id) in ids.withIndex()) {
                if (!isActive) break
                val item = dao.getById(id) ?: continue
                val fileName = item.originalFileName

                updateProgress(
                    VaultProgressState(
                        operation = VaultOperationType.RESTORE,
                        fileName = fileName,
                        fileIndex = index + 1,
                        totalFiles = total,
                        bytesProcessed = 0L,
                        totalBytes = item.fileSize,
                        percent = 0
                    )
                )

                val result = repo.restoreFromVault(
                    vaultEntity = item,
                    targetDirectory = targetDir,
                    onProgress = { processed, totalBytes ->
                        val percent = ((processed.toDouble() / totalBytes.toDouble()) * 100).toInt().coerceIn(0, 100)
                        updateProgress(
                            VaultProgressState(
                                operation = VaultOperationType.RESTORE,
                                fileName = fileName,
                                fileIndex = index + 1,
                                totalFiles = total,
                                bytesProcessed = processed,
                                totalBytes = totalBytes,
                                percent = percent
                            )
                        )
                    }
                )

                if (result.isSuccess) {
                    successCount++
                    _lastCompletedTimestamp.value = System.currentTimeMillis()
                }
            }

            finishOperation(
                title = "Safe Vault Restoration Complete",
                message = if (successCount > 0) "Restored $successCount item(s) to storage" else "Restoration failed"
            )
        }
    }

    private fun handleImport(uris: List<Uri>) {
        activeJob?.cancel()
        activeJob = serviceScope.launch {
            val repo = (application as IManageApp).vaultRepository
            var successCount = 0
            val total = uris.size

            for ((index, uri) in uris.withIndex()) {
                if (!isActive) break
                updateProgress(
                    VaultProgressState(
                        operation = VaultOperationType.IMPORT,
                        fileName = "Importing file ${index + 1} of $total",
                        fileIndex = index + 1,
                        totalFiles = total,
                        bytesProcessed = 0L,
                        totalBytes = 0L,
                        percent = 0,
                        isIndeterminate = true
                    )
                )

                val result = repo.importUriToVault(uri)
                if (result.isSuccess) {
                    successCount++
                    _lastCompletedTimestamp.value = System.currentTimeMillis()
                }
            }

            finishOperation(
                title = "Safe Vault Import Complete",
                message = if (successCount > 0) "Encrypted and imported $successCount item(s)" else "Import failed"
            )
        }
    }

    private fun updateProgress(state: VaultProgressState) {
        _currentProgress.value = state

        val title = when (state.operation) {
            VaultOperationType.ENCRYPT -> if (state.totalFiles > 1) "Securing (${state.fileIndex}/${state.totalFiles})" else "Securing in Safe Vault"
            VaultOperationType.RESTORE -> if (state.totalFiles > 1) "Restoring (${state.fileIndex}/${state.totalFiles})" else "Restoring from Safe Vault"
            VaultOperationType.IMPORT -> "Importing into Safe Vault"
        }

        val formattedSize = if (state.totalBytes > 0) {
            "${FileItem.formatBytes(state.bytesProcessed)} / ${FileItem.formatBytes(state.totalBytes)} (${state.percent}%)"
        } else {
            "${state.percent}%"
        }

        val body = "${state.fileName} • $formattedSize"
        val notif = buildProgressNotification(title, body, state.percent, state.isIndeterminate)

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notif)
    }

    @SuppressLint("LaunchActivityFromNotification")
    private fun buildProgressNotification(
        title: String,
        message: String,
        percent: Int,
        isIndeterminate: Boolean
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, VaultService::class.java).apply {
            action = ACTION_CANCEL
        }
        val pendingCancelIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_stat_vault)
            .setContentIntent(pendingOpenIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", pendingCancelIntent)

        if (isIndeterminate) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, percent, false)
        }

        return builder.build()
    }

    private fun finishOperation(title: String, message: String) {
        _currentProgress.value = null
        _lastCompletedTimestamp.value = System.currentTimeMillis()

        // Remove foreground notification
        stopForeground(STOP_FOREGROUND_REMOVE)

        // Post completion notification to alert channel
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completionNotif = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_stat_vault)
            .setContentIntent(pendingOpenIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(COMPLETION_NOTIFICATION_ID, completionNotif)

        stopSelf()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val progressChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active Safe Vault encryption and decryption progress"
                setShowBadge(false)
            }

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                ALERT_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when Safe Vault operations complete"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(progressChannel)
            manager?.createNotificationChannel(alertChannel)
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IManage:VaultServiceWakeLock")?.apply {
            acquire(10 * 60 * 1000L) // Safety 10-min max
        }
    }

    override fun onDestroy() {
        activeJob?.cancel()
        serviceScope.cancel()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        _currentProgress.value = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

enum class VaultOperationType {
    ENCRYPT, RESTORE, IMPORT
}

data class VaultProgressState(
    val operation: VaultOperationType,
    val fileName: String,
    val fileIndex: Int,
    val totalFiles: Int,
    val bytesProcessed: Long,
    val totalBytes: Long,
    val percent: Int,
    val isIndeterminate: Boolean = false
)
