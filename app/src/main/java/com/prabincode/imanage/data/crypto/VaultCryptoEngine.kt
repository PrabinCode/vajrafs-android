/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.data.crypto

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VaultCryptoEngine {

    private const val TRANSFORMATION = "AES/CBC/PKCS7Padding"
    private const val IV_LENGTH = 16
    private const val BUFFER_SIZE = 256 * 1024 // 256 KB high-throughput buffer

    suspend fun encryptFile(
        sourceFile: File,
        destFile: File,
        context: Context? = null,
        secretKey: SecretKey = KeystoreHelper.getOrCreateDataKey(context),
        onProgress: (bytesProcessed: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!sourceFile.exists() || !sourceFile.isFile) {
                return@withContext Result.failure(IllegalArgumentException("Source is not a file: ${sourceFile.absolutePath}"))
            }

            destFile.parentFile?.mkdirs()

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv ?: return@withContext Result.failure(IllegalStateException("Cipher failed to generate IV"))

            val totalBytes = sourceFile.length().coerceAtLeast(1L)
            var bytesProcessed = 0L
            var lastProgressTime = 0L

            BufferedOutputStream(FileOutputStream(destFile), BUFFER_SIZE).use { fos ->
                // Write IV to file header first
                fos.write(iv)

                BufferedInputStream(FileInputStream(sourceFile), BUFFER_SIZE).use { fis ->
                    val inBuffer = ByteArray(BUFFER_SIZE)
                    var read: Int
                    while (fis.read(inBuffer).also { read = it } != -1) {
                        val outBytes = cipher.update(inBuffer, 0, read)
                        if (outBytes != null && outBytes.isNotEmpty()) {
                            fos.write(outBytes)
                        }
                        bytesProcessed += read
                        val now = System.currentTimeMillis()
                        if (now - lastProgressTime >= 200L || bytesProcessed >= totalBytes) {
                            lastProgressTime = now
                            onProgress(bytesProcessed, totalBytes)
                        }
                    }

                    val finalBytes = cipher.doFinal()
                    if (finalBytes != null && finalBytes.isNotEmpty()) {
                        fos.write(finalBytes)
                    }
                    fos.flush()
                }
            }

            // Ensure 100% progress emitted
            onProgress(totalBytes, totalBytes)
            Result.success(destFile)
        } catch (e: Exception) {
            if (destFile.exists()) {
                destFile.delete()
            }
            Result.failure(e)
        }
    }

    suspend fun decryptFile(
        encryptedFile: File,
        destFile: File,
        context: Context? = null,
        secretKey: SecretKey? = null,
        onProgress: (bytesProcessed: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        val targetKey = secretKey ?: KeystoreHelper.getOrCreateDataKey(context)
        val attemptResult = executeDecryption(encryptedFile, destFile, targetKey, onProgress)

        // If decryption fails with bad padding and no explicit key was specified,
        // retry with the legacy AndroidKeyStore master key for backward compatibility.
        if (attemptResult.isFailure && secretKey == null) {
            val error = attemptResult.exceptionOrNull()
            if (error is BadPaddingException || error is IllegalBlockSizeException || error is javax.crypto.AEADBadTagException) {
                val legacyKey = KeystoreHelper.getOrCreateMasterKey(context)
                if (legacyKey != targetKey) {
                    val retryResult = executeDecryption(encryptedFile, destFile, legacyKey, onProgress)
                    if (retryResult.isSuccess) {
                        return@withContext retryResult
                    }
                }
            }
        }

        attemptResult
    }

    private fun executeDecryption(
        encryptedFile: File,
        destFile: File,
        key: SecretKey,
        onProgress: (bytesProcessed: Long, totalBytes: Long) -> Unit
    ): Result<File> {
        try {
            if (!encryptedFile.exists() || encryptedFile.length() < IV_LENGTH) {
                return Result.failure(IllegalArgumentException("Invalid encrypted file: ${encryptedFile.absolutePath}"))
            }

            destFile.parentFile?.mkdirs()

            val totalBytes = (encryptedFile.length() - IV_LENGTH).coerceAtLeast(1L)
            var bytesProcessed = 0L
            var lastProgressTime = 0L

            BufferedInputStream(FileInputStream(encryptedFile), BUFFER_SIZE).use { fis ->
                val iv = ByteArray(IV_LENGTH)
                val readIv = fis.read(iv)
                if (readIv != IV_LENGTH) {
                    return Result.failure(IllegalStateException("Could not read IV header"))
                }

                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))

                BufferedOutputStream(FileOutputStream(destFile), BUFFER_SIZE).use { fos ->
                    val inBuffer = ByteArray(BUFFER_SIZE)
                    var read: Int
                    while (fis.read(inBuffer).also { read = it } != -1) {
                        val outBytes = cipher.update(inBuffer, 0, read)
                        if (outBytes != null && outBytes.isNotEmpty()) {
                            fos.write(outBytes)
                        }
                        bytesProcessed += read
                        val now = System.currentTimeMillis()
                        if (now - lastProgressTime >= 200L || bytesProcessed >= totalBytes) {
                            lastProgressTime = now
                            onProgress(bytesProcessed, totalBytes)
                        }
                    }

                    val finalBytes = cipher.doFinal()
                    if (finalBytes != null && finalBytes.isNotEmpty()) {
                        fos.write(finalBytes)
                    }
                    fos.flush()
                }
            }

            onProgress(totalBytes, totalBytes)
            return Result.success(destFile)
        } catch (e: Exception) {
            if (destFile.exists()) {
                destFile.delete()
            }
            return Result.failure(e)
        }
    }
}
