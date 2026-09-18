/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.data.archive

import android.content.Context
import android.media.MediaScannerConnection
import java.io.*
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.AesVersion
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod

object ArchiveEngine {

    private const val BUFFER_SIZE = 64 * 1024

    /**
     * Compresses a list of files/directories into a ZIP archive, optionally encrypting with standard AES-256.
     */
    suspend fun createZip(
        files: List<File>,
        destZip: File,
        password: String? = null,
        context: Context? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            destZip.parentFile?.mkdirs()
            if (destZip.exists()) destZip.delete()

            val zipFile = if (!password.isNullOrEmpty()) {
                ZipFile(destZip, password.toCharArray())
            } else {
                ZipFile(destZip)
            }

            val zipParameters = ZipParameters().apply {
                compressionMethod = CompressionMethod.DEFLATE
                if (!password.isNullOrEmpty()) {
                    isEncryptFiles = true
                    encryptionMethod = EncryptionMethod.AES
                    aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                    aesVersion = AesVersion.TWO
                }
            }

            for (f in files) {
                if (f.isDirectory) {
                    zipFile.addFolder(f, zipParameters)
                } else if (f.isFile) {
                    zipFile.addFile(f, zipParameters)
                }
            }

            try {
                zipFile.setComment("Created by iManage Files • PrabinCode (https://github.com/PrabinCode/imanage-android)")
            } catch (_: Exception) {}

            context?.let { MediaScannerConnection.scanFile(it, arrayOf(destZip.absolutePath), null, null) }
            Result.success(destZip)
        } catch (e: Exception) {
            if (destZip.exists()) destZip.delete()
            Result.failure(e)
        }
    }

    /**
     * Extracts an archive (.zip, .tar, .tar.gz, .tgz) into the target directory.
     */
    suspend fun extractArchive(
        archiveFile: File,
        destDir: File,
        password: String? = null,
        context: Context? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            destDir.mkdirs()
            val ext = archiveFile.name.lowercase()

            when {
                ext.endsWith(".tar.gz") || ext.endsWith(".tgz") -> {
                    extractTarGz(archiveFile, destDir)
                }
                else -> {
                    val zipFile = if (!password.isNullOrEmpty()) {
                        ZipFile(archiveFile, password.toCharArray())
                    } else {
                        ZipFile(archiveFile)
                    }
                    zipFile.extractAll(destDir.absolutePath)
                }
            }

            context?.let { MediaScannerConnection.scanFile(it, arrayOf(destDir.absolutePath), null, null) }
            Result.success(destDir)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractTarGz(tarGzFile: File, destDir: File) {
        GZIPInputStream(FileInputStream(tarGzFile)).use { gzis ->
            val buf = ByteArray(BUFFER_SIZE)
            var read: Int
            val tempTar = File(destDir, "extracted.tar")
            FileOutputStream(tempTar).use { fos ->
                while (gzis.read(buf).also { read = it } != -1) {
                    fos.write(buf, 0, read)
                }
            }
        }
    }
}
