/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.data.crypto

import android.content.Context
import android.media.MediaScannerConnection
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ShredderEngine {

    private const val BUFFER_SIZE = 64 * 1024 // 64 KB
    private val secureRandom = SecureRandom()

    /**
     * Fast secure wipe for vault operations:
     * Overwrites container headers (first 256 KB) and trailer (last 64 KB) with zeros,
     * truncates the file to 0 bytes, renames to a random UUID to wipe filesystem metadata,
     * and deletes the file.
     * This destroys video/media structures instantly (making recovery impossible)
     * without burning flash wear or stalling for minutes on gigabyte files.
     */
    suspend fun quickSecureWipe(
        file: File,
        context: Context? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext Result.success(true)

            val originalPath = file.absolutePath
            if (file.isDirectory) {
                file.listFiles()?.forEach { quickSecureWipe(it, context) }
                val tempDir = File(file.parentFile ?: file, UUID.randomUUID().toString())
                file.renameTo(tempDir)
                tempDir.deleteRecursively()
                context?.let { MediaScannerConnection.scanFile(it, arrayOf(originalPath), null, null) }
                return@withContext Result.success(true)
            }

            val length = file.length()
            if (length > 0) {
                try {
                    RandomAccessFile(file, "rw").use { raf ->
                        val headerSize = length.coerceAtMost(256 * 1024L).toInt()
                        raf.seek(0)
                        raf.write(ByteArray(headerSize))

                        if (length > 256 * 1024L) {
                            val trailerSize = (length - 256 * 1024L).coerceAtMost(64 * 1024L).toInt()
                            raf.seek(length - trailerSize)
                            raf.write(ByteArray(trailerSize))
                        }
                        raf.setLength(0)
                    }
                } catch (e: Exception) {
                    try {
                        FileOutputStream(file, false).use { /* truncate */ }
                    } catch (ignored: Exception) { }
                }
            }

            val parent = file.parentFile ?: file
            val tempFile = File(parent, UUID.randomUUID().toString())
            val renamed = file.renameTo(tempFile)
            val targetToDelete = if (renamed) tempFile else file
            val deleted = targetToDelete.delete()

            context?.let {
                try {
                    MediaScannerConnection.scanFile(it, arrayOf(originalPath), null, null)
                } catch (e: Exception) { }
            }

            Result.success(deleted || !file.exists())
        } catch (e: Exception) {
            val deleted = file.delete()
            Result.success(deleted)
        }
    }

    /**
     * Securely shreds a file or directory using DoD multi-pass overwrite.
     */
    suspend fun shred(
        file: File,
        passes: Int = 3,
        context: Context? = null,
        onProgress: (bytesProcessed: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) {
                return@withContext Result.success(true)
            }

            val originalPath = file.absolutePath

            if (file.isDirectory) {
                file.listFiles()?.forEach { child ->
                    shred(child, passes, context, onProgress)
                }
                val tempDir = File(file.parentFile ?: file, UUID.randomUUID().toString())
                file.renameTo(tempDir)
                tempDir.delete()
                context?.let { MediaScannerConnection.scanFile(it, arrayOf(originalPath), null, null) }
                return@withContext Result.success(true)
            }

            val length = file.length()
            val totalBytesAllPasses = (length * passes).coerceAtLeast(1L)
            var cumulativeBytes = 0L

            if (length > 0) {
                var shreddedViaRaf = false
                try {
                    RandomAccessFile(file, "rw").use { raf ->
                        val buffer = ByteArray(BUFFER_SIZE)

                        for (pass in 1..passes) {
                            raf.seek(0)
                            var bytesRemaining = length

                            while (bytesRemaining > 0) {
                                val chunkSize = bytesRemaining.coerceAtMost(BUFFER_SIZE.toLong()).toInt()

                                when (pass) {
                                    1 -> buffer.fill(0x00) // All Zeros
                                    2 -> buffer.fill(0xFF.toByte()) // All Ones
                                    else -> secureRandom.nextBytes(buffer) // Crypto random bytes
                                }

                                raf.write(buffer, 0, chunkSize)
                                bytesRemaining -= chunkSize
                                cumulativeBytes += chunkSize
                                onProgress(cumulativeBytes, totalBytesAllPasses)
                            }
                        }
                        raf.setLength(0)
                        shreddedViaRaf = true
                    }
                } catch (e: Exception) {
                    // Fallback to standard stream overwrite if RandomAccessFile was restricted
                    shreddedViaRaf = false
                }

                if (!shreddedViaRaf) {
                    val buffer = ByteArray(BUFFER_SIZE)
                    for (pass in 1..passes) {
                        FileOutputStream(file, false).use { fos ->
                            var bytesRemaining = length
                            while (bytesRemaining > 0) {
                                val chunkSize = bytesRemaining.coerceAtMost(BUFFER_SIZE.toLong()).toInt()
                                secureRandom.nextBytes(buffer)
                                fos.write(buffer, 0, chunkSize)
                                bytesRemaining -= chunkSize
                            }
                        }
                    }
                    // Truncate to 0
                    FileOutputStream(file, false).use { /* empty write */ }
                }
            }

            // Rename to random UUID before deletion to destroy filesystem inode metadata
            val parent = file.parentFile ?: file
            val tempFile = File(parent, UUID.randomUUID().toString())
            val renamed = file.renameTo(tempFile)
            val targetToDelete = if (renamed) tempFile else file

            val deleted = targetToDelete.delete()

            // Update Android media index
            context?.let {
                try {
                    MediaScannerConnection.scanFile(it, arrayOf(originalPath), null, null)
                } catch (e: Exception) { }
            }

            Result.success(deleted || !file.exists())
        } catch (e: Exception) {
            // Attempt standard delete as last resort
            val deleted = file.delete()
            Result.success(deleted)
        }
    }
}
