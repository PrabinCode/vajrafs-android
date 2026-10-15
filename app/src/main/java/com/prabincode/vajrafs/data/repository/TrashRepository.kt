/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.data.repository

import android.content.Context
import com.prabincode.vajrafs.data.crypto.ShredderEngine
import com.prabincode.vajrafs.data.local.dao.TrashDao
import com.prabincode.vajrafs.data.local.entity.TrashEntity
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TrashRepository(
    private val context: Context,
    private val trashDao: TrashDao
) {
    private val trashDir: File get() = File(context.filesDir, ".vajrafs_trash").apply { mkdirs() }

    val trashItems: Flow<List<TrashEntity>> = trashDao.getAllTrashItems()

    /**
     * Soft-deletes a file or directory by moving it to the trash folder and indexing it in SQLite.
     */
    suspend fun moveToTrash(file: File): Result<TrashEntity> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) {
                return@withContext Result.failure(IllegalArgumentException("File does not exist"))
            }

            val uniqueTrashName = "${UUID.randomUUID()}_${file.name}"
            val destTrashFile = File(trashDir, uniqueTrashName)

            val moved = file.renameTo(destTrashFile)
            if (!moved) {
                // Cross mount fallback
                file.copyRecursively(destTrashFile, overwrite = true)
                file.deleteRecursively()
            }

            val entity = TrashEntity(
                originalPath = file.absolutePath,
                originalName = file.name,
                trashedPath = destTrashFile.absolutePath,
                size = if (destTrashFile.isDirectory) getFolderSize(destTrashFile) else destTrashFile.length(),
                isDirectory = destTrashFile.isDirectory
            )

            val id = trashDao.insertTrashItem(entity)
            Result.success(entity.copy(id = id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Restores a trashed file back to its original location.
     */
    suspend fun restoreItem(trashEntity: TrashEntity): Result<File> = withContext(Dispatchers.IO) {
        try {
            val trashedFile = File(trashEntity.trashedPath)
            var targetFile = File(trashEntity.originalPath)

            targetFile.parentFile?.mkdirs()

            if (targetFile.exists()) {
                val name = targetFile.nameWithoutExtension
                val ext = targetFile.extension
                val dotExt = if (ext.isNotEmpty()) ".$ext" else ""
                targetFile = File(targetFile.parentFile, "${name}_restored$dotExt")
            }

            val restored = trashedFile.renameTo(targetFile)
            if (!restored) {
                trashedFile.copyRecursively(targetFile, overwrite = true)
                trashedFile.deleteRecursively()
            }

            trashDao.deleteById(trashEntity.id)
            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Permanently deletes a single item from the trash bin with shredding.
     */
    suspend fun permanentlyDelete(trashEntity: TrashEntity, shred: Boolean = true): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val trashedFile = File(trashEntity.trashedPath)
            if (shred) {
                ShredderEngine.shred(trashedFile, passes = 1)
            } else {
                trashedFile.deleteRecursively()
            }
            trashDao.deleteById(trashEntity.id)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Empties the entire trash bin.
     */
    suspend fun emptyTrash(shred: Boolean = true): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            trashDir.listFiles()?.forEach { file ->
                if (shred) {
                    ShredderEngine.shred(file, passes = 1)
                } else {
                    file.deleteRecursively()
                }
            }
            trashDao.clearTrash()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getTrashStorageSize(): Long = getFolderSize(trashDir)

    private fun getFolderSize(dir: File): Long {
        var size = 0L
        dir.walkTopDown().forEach { if (it.isFile) size += it.length() }
        return size
    }
}
