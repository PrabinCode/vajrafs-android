/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.data.crypto

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FileChecksums(
    val md5: String = "",
    val sha1: String = "",
    val sha256: String = "",
    val crc32: String = ""
)

object ChecksumHelper {

    private const val BUFFER_SIZE = 64 * 1024

    suspend fun calculateChecksums(file: File): Result<FileChecksums> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || !file.isFile || !file.canRead()) {
                return@withContext Result.failure(IllegalArgumentException("File cannot be read"))
            }

            val md5Digest = MessageDigest.getInstance("MD5")
            val sha1Digest = MessageDigest.getInstance("SHA-1")
            val sha256Digest = MessageDigest.getInstance("SHA-256")
            val crc32 = CRC32()

            val buffer = ByteArray(BUFFER_SIZE)
            FileInputStream(file).use { fis ->
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    md5Digest.update(buffer, 0, bytesRead)
                    sha1Digest.update(buffer, 0, bytesRead)
                    sha256Digest.update(buffer, 0, bytesRead)
                    crc32.update(buffer, 0, bytesRead)
                }
            }

            val md5Hex = bytesToHex(md5Digest.digest())
            val sha1Hex = bytesToHex(sha1Digest.digest())
            val sha256Hex = bytesToHex(sha256Digest.digest())
            val crc32Hex = String.format("%08X", crc32.value)

            Result.success(
                FileChecksums(
                    md5 = md5Hex,
                    sha1 = sha1Hex,
                    sha256 = sha256Hex,
                    crc32 = crc32Hex
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        val hexArray = "0123456789abcdef".toCharArray()
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = hexArray[v ushr 4]
            hexChars[i * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }
}
