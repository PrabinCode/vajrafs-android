/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.data.repository

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.prabincode.imanage.data.crypto.ShredderEngine
import com.prabincode.imanage.data.crypto.VaultCryptoEngine
import com.prabincode.imanage.data.local.dao.VaultDao
import com.prabincode.imanage.data.local.entity.VaultEntity
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class VaultRepository(
    private val context: Context,
    private val vaultDao: VaultDao
) {
    private val vaultDir: File get() = File(context.filesDir, ".imanage_vault").apply { mkdirs() }
    private val tempDecryptedDir: File get() = File(context.cacheDir, ".vault_temp").apply { mkdirs() }

    companion object {
        fun calculateTotalSize(file: File): Long {
            if (!file.exists()) return 0L
            if (file.isFile) return file.length().coerceAtLeast(1L)
            return try {
                file.walkTopDown()
                    .filter { it.isFile }
                    .sumOf { it.length() }
                    .coerceAtLeast(1L)
            } catch (e: Exception) {
                file.length().coerceAtLeast(1L)
            }
        }
    }

    private val inFlightPaths = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val inFlightVaultIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    val vaultItems: Flow<List<VaultEntity>> = vaultDao.getAllVaultItems()

    fun isPathBusy(path: String): Boolean = inFlightPaths.contains(path)

    suspend fun moveToVault(
        originalFile: File,
        shredOriginal: Boolean = true,
        onProgress: (bytesProcessed: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<VaultEntity> = withContext(Dispatchers.IO) {
        val originalPath = originalFile.absolutePath

        // Concurrency guard: prevent duplicate parallel encryption of the same file
        if (!inFlightPaths.add(originalPath)) {
            return@withContext Result.failure(IllegalStateException("File is already being moved to Safe Vault: ${originalFile.name}"))
        }

        try {
            if (!originalFile.exists()) {
                return@withContext Result.failure(IllegalArgumentException("File does not exist: $originalPath"))
            }

            // DB deduplication: prevent duplicate rows if already in Safe Vault
            if (vaultDao.isPathInVault(originalPath)) {
                return@withContext Result.failure(IllegalStateException("File is already in Safe Vault: ${originalFile.name}"))
            }

            val isDir = originalFile.isDirectory
            val originalName = originalFile.name
            val encFileName = "${UUID.randomUUID()}.enc"
            val destEncFile = File(vaultDir, encFileName)

            val totalSize = calculateTotalSize(originalFile)
            val tempZip = if (isDir) File(vaultDir, "temp_${UUID.randomUUID()}.zip") else null

            try {
                if (isDir && tempZip != null) {
                    val halfBytes = totalSize / 2
                    // Phase 1: High-throughput raw archiving (0% to 50%)
                    zipDirectory(
                        dir = originalFile,
                        destZip = tempZip,
                        totalBytes = totalSize,
                        onProgress = { zippedBytes, total ->
                            val p1 = (zippedBytes.toDouble() / total.toDouble() * halfBytes).toLong()
                            onProgress(p1, totalSize)
                        }
                    )

                    // Phase 2: Hardware AES-256 encryption (50% to 100%)
                    val encResult = VaultCryptoEngine.encryptFile(
                        sourceFile = tempZip,
                        destFile = destEncFile,
                        context = context,
                        onProgress = { encBytes, encTotal ->
                            val p2 = halfBytes + (encBytes.toDouble() / encTotal.toDouble() * (totalSize - halfBytes)).toLong()
                            onProgress(p2.coerceAtMost(totalSize), totalSize)
                        }
                    )

                    if (encResult.isFailure) {
                        return@withContext Result.failure(encResult.exceptionOrNull() ?: Exception("Encryption failed"))
                    }
                } else {
                    // Single file encryption (0% to 100%)
                    val encResult = VaultCryptoEngine.encryptFile(
                        sourceFile = originalFile,
                        destFile = destEncFile,
                        context = context,
                        onProgress = onProgress
                    )

                    if (encResult.isFailure) {
                        return@withContext Result.failure(encResult.exceptionOrNull() ?: Exception("Encryption failed"))
                    }
                }

                // Ensure 100% progress emitted
                onProgress(totalSize, totalSize)

                val ext = originalFile.extension.lowercase()
                val mime = if (isDir) "resource/folder" else (MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*")

                val entity = VaultEntity(
                    encryptedFileName = encFileName,
                    originalFileName = originalName,
                    originalPath = originalPath,
                    fileSize = totalSize,
                    mimeType = mime,
                    isDirectory = isDir
                )

                val id = vaultDao.insertVaultItem(entity)
                val savedEntity = entity.copy(id = id)

                if (shredOriginal) {
                    ShredderEngine.quickSecureWipe(originalFile, context = context)
                }

                try {
                    MediaScannerConnection.scanFile(context, arrayOf(originalPath), null, null)
                } catch (e: Exception) { }

                Result.success(savedEntity)
            } finally {
                if (tempZip != null && tempZip.exists()) {
                    tempZip.delete()
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            inFlightPaths.remove(originalPath)
        }
    }

    suspend fun importUriToVault(
        uri: Uri,
        onProgress: (bytesProcessed: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<VaultEntity> = withContext(Dispatchers.IO) {
        try {
            var fileName = "Imported_${System.currentTimeMillis()}"
            var fileSize = 0L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }

            val tempImportFile = File(context.cacheDir, fileName)
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempImportFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val result = moveToVault(tempImportFile, shredOriginal = true, onProgress = onProgress)
                result
            } finally {
                if (tempImportFile.exists()) {
                    tempImportFile.delete()
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreFromVault(
        vaultEntity: VaultEntity,
        targetDirectory: File? = null,
        onProgress: (bytesProcessed: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!inFlightVaultIds.add(vaultEntity.id)) {
            return@withContext Result.failure(IllegalStateException("Item is already being restored: ${vaultEntity.originalFileName}"))
        }

        try {
            val encFile = File(vaultDir, vaultEntity.encryptedFileName)
            val originalParent = File(vaultEntity.originalPath).parentFile
            val targetDir = targetDirectory ?: if (originalParent != null && originalParent.exists()) originalParent else File(android.os.Environment.getExternalStorageDirectory(), "Download")
            targetDir.mkdirs()

            if (vaultEntity.isDirectory) {
                val tempZip = File(vaultDir, "temp_restore_${UUID.randomUUID()}.zip")
                try {
                    val totalBytes = vaultEntity.fileSize.coerceAtLeast(1L)
                    val halfBytes = totalBytes / 2

                    // Phase 1: Hardware-accelerated AES-256 Decryption (0% to 50%)
                    val decResult = VaultCryptoEngine.decryptFile(
                        encryptedFile = encFile,
                        destFile = tempZip,
                        context = context,
                        onProgress = { decBytes, decTotal ->
                            val p1 = (decBytes.toDouble() / decTotal.toDouble() * halfBytes).toLong()
                            onProgress(p1, totalBytes)
                        }
                    )

                    if (decResult.isSuccess) {
                        val destFolder = File(targetDir, vaultEntity.originalFileName)
                        destFolder.mkdirs()

                        // Phase 2: High-throughput Archive Extraction (50% to 100%)
                        unzipToDirectory(
                            zipFile = tempZip,
                            destDir = destFolder,
                            totalBytes = totalBytes,
                            onProgress = { unzippedBytes, _ ->
                                val p2 = halfBytes + (unzippedBytes.toDouble() / totalBytes.toDouble() * (totalBytes - halfBytes)).toLong()
                                onProgress(p2.coerceAtMost(totalBytes), totalBytes)
                            }
                        )

                        encFile.delete()
                        vaultDao.deleteById(vaultEntity.id)
                        MediaScannerConnection.scanFile(context, arrayOf(destFolder.absolutePath), null, null)
                        onProgress(totalBytes, totalBytes)
                        Result.success(destFolder)
                    } else {
                        Result.failure(decResult.exceptionOrNull() ?: Exception("Decryption failed"))
                    }
                } finally {
                    if (tempZip.exists()) {
                        tempZip.delete()
                    }
                }
            } else {
                var targetFile = File(targetDir, vaultEntity.originalFileName)
                if (targetFile.exists()) {
                    val nameWithoutExt = targetFile.nameWithoutExtension
                    val ext = targetFile.extension
                    val dotExt = if (ext.isNotEmpty()) ".$ext" else ""
                    targetFile = File(targetDir, "${nameWithoutExt}_restored_${System.currentTimeMillis()}$dotExt")
                }

                val decResult = VaultCryptoEngine.decryptFile(encFile, targetFile, context = context, onProgress = onProgress)
                if (decResult.isSuccess) {
                    encFile.delete()
                    vaultDao.deleteById(vaultEntity.id)
                    MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), null, null)
                    Result.success(targetFile)
                } else {
                    Result.failure(decResult.exceptionOrNull() ?: Exception("Decryption failed"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            inFlightVaultIds.remove(vaultEntity.id)
        }
    }

    suspend fun decryptForPreview(vaultEntity: VaultEntity): Result<File> = withContext(Dispatchers.IO) {
        try {
            val encFile = File(vaultDir, vaultEntity.encryptedFileName)
            if (vaultEntity.isDirectory) {
                val tempZip = File(tempDecryptedDir, "${vaultEntity.originalFileName}.zip")
                val result = VaultCryptoEngine.decryptFile(encFile, tempZip, context = context)
                result
            } else {
                val tempFile = File(tempDecryptedDir, vaultEntity.originalFileName)
                val result = VaultCryptoEngine.decryptFile(encFile, tempFile, context = context)
                result
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteVaultItem(vaultEntity: VaultEntity): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val encFile = File(vaultDir, vaultEntity.encryptedFileName)
            ShredderEngine.quickSecureWipe(encFile, context = context)
            vaultDao.deleteById(vaultEntity.id)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun clearTempDecryptedFiles(): Long {
        val size = calculateTotalSize(tempDecryptedDir)
        tempDecryptedDir.deleteRecursively()
        tempDecryptedDir.mkdirs()
        return size
    }

    fun getTempDecryptedSize(): Long = calculateTotalSize(tempDecryptedDir)

    fun getVaultStorageSize(): Long = calculateTotalSize(vaultDir)

    private fun zipDirectory(
        dir: File,
        destZip: File,
        totalBytes: Long,
        onProgress: (bytesProcessed: Long, totalBytes: Long) -> Unit
    ) {
        val safeTotal = totalBytes.coerceAtLeast(1L)
        val buffer = ByteArray(128 * 1024)
        var bytesProcessed = 0L
        var lastProgressTime = 0L

        ZipOutputStream(BufferedOutputStream(FileOutputStream(destZip), 128 * 1024)).use { zos ->
            // Use NO_COMPRESSION (Level 0) so zipping is a fast stream-copy (~150-250 MB/s).
            // Safe Vault encrypts the archive with AES-256 hardware acceleration immediately after,
            // so compressing already compressed photos/videos/documents wastes battery and locks the CPU.
            zos.setLevel(Deflater.NO_COMPRESSION)

            fun addEntry(file: File, relativePath: String) {
                if (file.isDirectory) {
                    val dirEntryName = if (relativePath.endsWith("/")) relativePath else "$relativePath/"
                    zos.putNextEntry(ZipEntry(dirEntryName))
                    zos.closeEntry()
                    val children = file.listFiles() ?: return
                    for (child in children) {
                        addEntry(child, "$dirEntryName${child.name}")
                    }
                } else {
                    val entry = ZipEntry(relativePath)
                    zos.putNextEntry(entry)
                    BufferedInputStream(FileInputStream(file), 128 * 1024).use { bis ->
                        var read: Int
                        while (bis.read(buffer).also { read = it } != -1) {
                            zos.write(buffer, 0, read)
                            bytesProcessed += read
                            val now = System.currentTimeMillis()
                            if (now - lastProgressTime >= 150L || bytesProcessed >= safeTotal) {
                                lastProgressTime = now
                                onProgress(bytesProcessed.coerceAtMost(safeTotal), safeTotal)
                            }
                        }
                    }
                    zos.closeEntry()
                }
            }

            dir.listFiles()?.forEach { child ->
                addEntry(child, child.name)
            }
            zos.flush()
        }
        onProgress(safeTotal, safeTotal)
    }

    private fun unzipToDirectory(
        zipFile: File,
        destDir: File,
        totalBytes: Long,
        onProgress: (bytesProcessed: Long, totalBytes: Long) -> Unit
    ) {
        val safeTotal = totalBytes.coerceAtLeast(1L)
        val canonicalDestDir = destDir.canonicalPath
        var extractedBytes = 0L
        var lastProgressTime = 0L
        val buffer = ByteArray(128 * 1024)

        ZipInputStream(BufferedInputStream(FileInputStream(zipFile), 128 * 1024)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = File(destDir, entry.name)
                // Zip Slip security guard: ensure target file cannot escape destDir
                val canonicalTarget = newFile.canonicalPath
                if (!canonicalTarget.startsWith(canonicalDestDir + File.separator) && canonicalTarget != canonicalDestDir) {
                    throw SecurityException("Zip entry is outside target directory: ${entry.name}")
                }

                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(newFile), 128 * 1024).use { fos ->
                        var read: Int
                        while (zis.read(buffer).also { read = it } != -1) {
                            fos.write(buffer, 0, read)
                            extractedBytes += read
                            val now = System.currentTimeMillis()
                            if (now - lastProgressTime >= 150L || extractedBytes >= safeTotal) {
                                lastProgressTime = now
                                onProgress(extractedBytes.coerceAtMost(safeTotal), safeTotal)
                            }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        onProgress(safeTotal, safeTotal)
    }
}
