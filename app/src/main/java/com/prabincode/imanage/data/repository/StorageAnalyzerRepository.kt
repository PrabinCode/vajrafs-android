/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.data.repository

import android.os.Environment
import com.prabincode.imanage.data.model.FileItem
import com.prabincode.imanage.data.model.FileType
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CategoryUsage(
    val fileType: FileType,
    val totalBytes: Long,
    val fileCount: Int
)

data class StorageAnalysisResult(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val categoryBreakdown: List<CategoryUsage>,
    val largeFiles: List<FileItem>,
    val emptyFolders: List<FileItem>,
    val duplicateCandidates: List<List<FileItem>>
)

class StorageAnalyzerRepository {

    suspend fun analyzeStorage(
        rootPath: String = Environment.getExternalStorageDirectory().absolutePath,
        largeFileSizeThreshold: Long = 100 * 1024 * 1024L // 100 MB
    ): StorageAnalysisResult = withContext(Dispatchers.IO) {
        val root = File(rootPath)
        val totalSpace = root.totalSpace
        val freeSpace = root.usableSpace
        val usedSpace = (totalSpace - freeSpace).coerceAtLeast(0L)

        val typeBytesMap = mutableMapOf<FileType, Long>()
        val typeCountMap = mutableMapOf<FileType, Int>()
        val largeFiles = mutableListOf<FileItem>()
        val emptyFolders = mutableListOf<FileItem>()
        val sizeGroupMap = mutableMapOf<Long, MutableList<File>>()

        FileType.entries.forEach {
            typeBytesMap[it] = 0L
            typeCountMap[it] = 0
        }

        fun scan(dir: File) {
            val files = dir.listFiles()
            if (files == null || files.isEmpty()) {
                if (dir != root && !dir.name.startsWith(".")) {
                    emptyFolders.add(FileItem.fromFile(dir))
                }
                return
            }

            for (f in files) {
                if (f.name.startsWith(".") || f.name == "Android") continue

                if (f.isDirectory) {
                    scan(f)
                } else {
                    val len = f.length()
                    val item = FileItem.fromFile(f)
                    val type = item.fileType

                    typeBytesMap[type] = (typeBytesMap[type] ?: 0L) + len
                    typeCountMap[type] = (typeCountMap[type] ?: 0) + 1

                    if (len >= largeFileSizeThreshold) {
                        largeFiles.add(item)
                    }

                    // Group files by size for fast duplicate candidate detection (> 1MB)
                    if (len > 1024 * 1024L) {
                        sizeGroupMap.getOrPut(len) { mutableListOf() }.add(f)
                    }
                }
            }
        }

        scan(root)

        val categoryList = typeBytesMap.map { (type, bytes) ->
            CategoryUsage(
                fileType = type,
                totalBytes = bytes,
                fileCount = typeCountMap[type] ?: 0
            )
        }.filter { it.totalBytes > 0 || it.fileCount > 0 }
            .sortedByDescending { it.totalBytes }

        // Find actual duplicates among size matches using quick SHA-256 header hash
        val duplicateGroups = mutableListOf<List<FileItem>>()
        for ((_, candidateFiles) in sizeGroupMap) {
            if (candidateFiles.size > 1) {
                val hashGroups = candidateFiles.groupBy { file ->
                    quickHash(file)
                }
                for ((_, matchingFiles) in hashGroups) {
                    if (matchingFiles.size > 1) {
                        duplicateGroups.add(matchingFiles.map { FileItem.fromFile(it) })
                    }
                }
            }
        }

        StorageAnalysisResult(
            totalBytes = totalSpace,
            freeBytes = freeSpace,
            usedBytes = usedSpace,
            categoryBreakdown = categoryList,
            largeFiles = largeFiles.sortedByDescending { it.size },
            emptyFolders = emptyFolders,
            duplicateCandidates = duplicateGroups
        )
    }

    private fun quickHash(file: File): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(64 * 1024) // hash first 64KB for speed
                val read = fis.read(buffer)
                if (read > 0) {
                    md.update(buffer, 0, read)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) } + "_${file.length()}"
        } catch (e: Exception) {
            file.name + "_" + file.length()
        }
    }
}
