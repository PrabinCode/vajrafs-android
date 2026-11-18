/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.ui.screens.settings

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.prabincode.vajrafs.data.update.AppUpdateInfo
import com.prabincode.vajrafs.data.update.UpdateDownloadState
import com.prabincode.vajrafs.data.update.UpdateManager
import com.prabincode.vajrafs.util.AppLinks
import kotlinx.coroutines.launch

/** GitHub build: "Check for Updates" card, which checks GitHub Releases and downloads/installs newer APKs. */
@Composable
fun UpdateSection() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val versionInfo = remember { AppLinks.getCurrentVersion(context) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfoDialog by remember { mutableStateOf<AppUpdateInfo?>(null) }
    val downloadState by UpdateManager.downloadState.collectAsState()

    // Resume installation automatically once the user grants "Install unknown apps" in system settings.
    DisposableEffect(lifecycleOwner, downloadState) {
        val observer = LifecycleEventObserver { _, event ->
            val current = downloadState
            if (event == Lifecycle.Event.ON_RESUME &&
                current is UpdateDownloadState.PermissionRequired &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls())
            ) {
                UpdateManager.installApk(context, current.versionName, current.apkFile)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun triggerUpdateCheck() {
        if (isCheckingUpdate) return
        isCheckingUpdate = true
        scope.launch {
            val result = try {
                UpdateManager.checkForUpdates(context)
            } finally {
                isCheckingUpdate = false
            }
            result.fold(
                onSuccess = { info ->
                    if (info.isUpdateAvailable) {
                        updateInfoDialog = info
                    } else {
                        Toast.makeText(
                            context,
                            "You're using the latest version (v${versionInfo.first})",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onFailure = { error ->
                    Toast.makeText(
                        context,
                        "Could not check for updates: ${error.localizedMessage ?: "Offline"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    val cardClickable = when (downloadState) {
        is UpdateDownloadState.Idle -> !isCheckingUpdate
        is UpdateDownloadState.ReadyToInstall,
        is UpdateDownloadState.PermissionRequired -> true
        is UpdateDownloadState.Downloading,
        is UpdateDownloadState.Failed -> false
    }

    Card(
        onClick = {
            when (val state = downloadState) {
                is UpdateDownloadState.Idle -> triggerUpdateCheck()
                is UpdateDownloadState.ReadyToInstall ->
                    UpdateManager.installApk(context, state.versionName, state.apkFile)
                is UpdateDownloadState.PermissionRequired ->
                    UpdateManager.installApk(context, state.versionName, state.apkFile)
                is UpdateDownloadState.Downloading,
                is UpdateDownloadState.Failed -> Unit
            }
        },
        enabled = cardClickable,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        when (val state = downloadState) {
            is UpdateDownloadState.Idle -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Check for Updates", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isCheckingUpdate) {
                                "Checking GitHub Releases..."
                            } else {
                                "Installed: v${versionInfo.first} (Build ${versionInfo.second})"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            is UpdateDownloadState.Downloading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Downloading v${state.versionName}...", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = state.progressPercent?.let { "$it% downloaded" } ?: "Connecting...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { UpdateManager.cancelDownload(context) }) {
                            Text("Cancel")
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    val pct = state.progressPercent
                    if (pct != null) {
                        LinearProgressIndicator(
                            progress = { pct / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            is UpdateDownloadState.PermissionRequired -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.InstallMobile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Allow App Installs", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Grant \"Install unknown apps\" to finish updating to v${state.versionName}.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = {
                            UpdateManager.installApk(context, state.versionName, state.apkFile)
                        }
                    ) {
                        Text("Allow")
                    }
                }
            }

            is UpdateDownloadState.ReadyToInstall -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.InstallMobile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Update v${state.versionName} Ready", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Tap Install to launch the Android installer.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            UpdateManager.installApk(context, state.versionName, state.apkFile)
                        }
                    ) {
                        Text("Install")
                    }
                }
            }

            is UpdateDownloadState.Failed -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Update v${state.versionName} Failed", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                state.message,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { UpdateManager.resetDownloadState() }) {
                            Text("Dismiss")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                AppLinks.openInBrowser(context, state.fallbackUrl)
                            }
                        ) {
                            Text("Open in Browser")
                        }
                    }
                }
            }
        }
    }

    // In-App Update Dialog
    updateInfoDialog?.let { info ->
        AlertDialog(
            onDismissRequest = {
                if (!info.isMandatory) updateInfoDialog = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.NewReleases,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text("Update Available: v${info.versionName}")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (info.releaseDate.isNotBlank()) {
                        Text(
                            text = "Released: ${info.releaseDate}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (info.releaseNotes.isNotEmpty()) {
                        Text(
                            text = "What's New:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        info.releaseNotes.forEach { note ->
                            Row(modifier = Modifier.padding(start = 4.dp)) {
                                Text("• ", fontWeight = FontWeight.Bold)
                                Text(note, fontSize = 13.sp)
                            }
                        }
                    } else {
                        Text(
                            text = "A newer release (v${info.versionName}) is available on GitHub Releases.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        UpdateManager.startDownload(context, info)
                        updateInfoDialog = null
                    }
                ) {
                    Text("Update Now")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val fallbackTarget = info.fallbackUrl.ifBlank { "https://github.com/PrabinCode/vajrafs-android/releases/latest" }
                    TextButton(
                        onClick = {
                            AppLinks.openInBrowser(context, fallbackTarget)
                        }
                    ) {
                        Text(if (fallbackTarget.contains("github.com", ignoreCase = true)) "Release Page" else "Website")
                    }
                    if (!info.isMandatory) {
                        TextButton(onClick = { updateInfoDialog = null }) {
                            Text("Later")
                        }
                    }
                }
            }
        )
    }
}
