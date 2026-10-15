/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.data.repository

import com.prabincode.vajrafs.data.local.dao.TagDao
import com.prabincode.vajrafs.data.local.entity.FileTagEntity
import kotlinx.coroutines.flow.Flow

enum class TagColor(val hex: String, val displayName: String) {
    RED("#EF4444", "Important"),
    GREEN("#10B981", "Personal"),
    BLUE("#3B82F6", "Work"),
    ORANGE("#F97316", "Finance"),
    PURPLE("#8B5CF6", "Archive")
}

class TagRepository(private val tagDao: TagDao) {

    fun getAllTags(): Flow<List<FileTagEntity>> = tagDao.getAllTags()

    suspend fun getTagForPath(path: String): FileTagEntity? = tagDao.getTagForPath(path)

    suspend fun setTag(path: String, colorHex: String, tagName: String) {
        tagDao.insertTag(
            FileTagEntity(
                path = path,
                colorHex = colorHex,
                tagName = tagName
            )
        )
    }

    suspend fun removeTag(path: String) {
        tagDao.deleteTag(path)
    }
}
