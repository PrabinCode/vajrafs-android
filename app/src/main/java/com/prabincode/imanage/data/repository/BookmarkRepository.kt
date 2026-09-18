/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.data.repository

import com.prabincode.imanage.data.local.dao.BookmarkDao
import com.prabincode.imanage.data.local.entity.BookmarkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class BookmarkRepository(private val bookmarkDao: BookmarkDao) {

    fun getAllBookmarks(): Flow<List<BookmarkEntity>> = bookmarkDao.getAllBookmarks()

    suspend fun addBookmark(path: String, title: String, isDirectory: Boolean = true) = withContext(Dispatchers.IO) {
        bookmarkDao.insertBookmark(
            BookmarkEntity(
                path = path,
                title = title,
                isDirectory = isDirectory,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeBookmark(path: String) = withContext(Dispatchers.IO) {
        bookmarkDao.deleteByPath(path)
    }

    suspend fun isBookmarked(path: String): Boolean = withContext(Dispatchers.IO) {
        bookmarkDao.isBookmarked(path)
    }
}
