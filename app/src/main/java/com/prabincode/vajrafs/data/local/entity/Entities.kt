/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey
    val path: String,
    val title: String,
    val isDirectory: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "trash_items",
    indices = [
        Index(value = ["trashedTime"])
    ]
)
data class TrashEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalPath: String,
    val originalName: String,
    val trashedPath: String,
    val trashedTime: Long = System.currentTimeMillis(),
    val size: Long,
    val isDirectory: Boolean = false
)

@Entity(
    tableName = "vault_items",
    indices = [
        Index(value = ["originalPath"]),
        Index(value = ["encryptedAt"])
    ]
)
data class VaultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val encryptedFileName: String,
    val originalFileName: String,
    val originalPath: String,
    val fileSize: Long,
    val encryptedAt: Long = System.currentTimeMillis(),
    val mimeType: String = "*/*",
    val isDirectory: Boolean = false
)

@Entity(
    tableName = "search_history",
    indices = [
        Index(value = ["timestamp"])
    ]
)
data class SearchHistoryEntity(
    @PrimaryKey
    val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "file_tags",
    indices = [
        Index(value = ["taggedAt"])
    ]
)
data class FileTagEntity(
    @PrimaryKey
    val path: String,
    val colorHex: String,
    val tagName: String,
    val taggedAt: Long = System.currentTimeMillis()
)
