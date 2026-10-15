/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.data.repository

import android.os.Environment
import com.prabincode.vajrafs.data.model.FileItem
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DuplicateGroup(
    val hash: String,
    val fileSize: Long,
    val files: List<FileItem>
)

data class DuplicateScanResult(
    val duplicateGroups: List<DuplicateGroup>,
    val totalWastedBytes: Long,
    val scannedFilesCount: Int
)

object DuplicateDetector {

    /**
     * Scans storage directory to identify exact duplicate files by byte size and SHA-256 hash.
     */
    suspend fun findDuplicates(
        rootDirectory: File = Environment.getExternalStorageDirectory(),
        onProgress: (scannedCount: Int, currentFile: String) -> Unit = { _, _ -> }
    ): DuplicateScanResult = withContext(Dispatchers.IO) {
        val sizeMap = mutableMapOf<Long, MutableList<File>>()
        var scannedCount = 0

        // Stage 1: Fast grouping by file size
        rootDirectory.walkTopDown()
            .onEnter { !it.name.startsWith(".") && it.name != "Android" }
            .filter { it.isFile && !it.name.startsWith(".") && it.length() > 1024L } // Filter files > 1KB
            .forEach { file ->
                scannedCount++
                if (scannedCount % 50 == 0) {
                    onProgress(scannedCount, file.name)
                }
                val list = sizeMap.getOrPut(file.length()) { mutableListOf() }
                list.add(file)
            }

        // Filter out unique file sizes
        val candidateGroups = sizeMap.filter { it.value.size > 1 }

        // Stage 2: SHA-256 content hashing of size matches
        val hashMap = mutableMapOf<String, MutableList<File>>()

        for ((_, fileList) in candidateGroups) {
            for (file in fileList) {
                try {
                    val hash = calculateSha256(file)
                    val group = hashMap.getOrPut(hash) { mutableListOf() }
                    group.add(file)
                } catch (e: Exception) { }
            }
        }

        val duplicateGroups = hashMap
            .filter { it.value.size > 1 }
            .map { (hash, files) ->
                DuplicateGroup(
                    hash = hash,
                    fileSize = files.first().length(),
                    files = files.map { FileItem.fromFile(it) }
                )
            }
            .sortedByDescending { it.fileSize * (it.files.size - 1) }

        val totalWastedBytes = duplicateGroups.sumOf { it.fileSize * (it.files.size - 1) }

        DuplicateScanResult(
            duplicateGroups = duplicateGroups,
            totalWastedBytes = totalWastedBytes,
            scannedFilesCount = scannedCount
        )
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(64 * 1024)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
