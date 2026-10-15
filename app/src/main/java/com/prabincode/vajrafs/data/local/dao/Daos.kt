/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.data.local.dao

import androidx.room.*
import com.prabincode.vajrafs.data.local.entity.BookmarkEntity
import com.prabincode.vajrafs.data.local.entity.SearchHistoryEntity
import com.prabincode.vajrafs.data.local.entity.TrashEntity
import com.prabincode.vajrafs.data.local.entity.VaultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE path = :path)")
    suspend fun isBookmarked(path: String): Boolean
}

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash_items ORDER BY trashedTime DESC")
    fun getAllTrashItems(): Flow<List<TrashEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrashItem(item: TrashEntity): Long

    @Delete
    suspend fun deleteTrashItem(item: TrashEntity)

    @Query("DELETE FROM trash_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM trash_items")
    suspend fun clearTrash()
}

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_items ORDER BY encryptedAt DESC")
    fun getAllVaultItems(): Flow<List<VaultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultItem(item: VaultEntity): Long

    @Delete
    suspend fun deleteVaultItem(item: VaultEntity)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM vault_items WHERE id = :id")
    suspend fun getById(id: Long): VaultEntity?

    @Query("SELECT * FROM vault_items WHERE originalPath = :path LIMIT 1")
    suspend fun getItemByOriginalPath(path: String): VaultEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM vault_items WHERE originalPath = :path)")
    suspend fun isPathInVault(path: String): Boolean
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 10")
    fun getRecentSearches(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(query: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun deleteSearch(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearHistory()
}

@Dao
interface TagDao {
    @Query("SELECT * FROM file_tags ORDER BY taggedAt DESC")
    fun getAllTags(): Flow<List<com.prabincode.vajrafs.data.local.entity.FileTagEntity>>

    @Query("SELECT * FROM file_tags WHERE path = :path LIMIT 1")
    suspend fun getTagForPath(path: String): com.prabincode.vajrafs.data.local.entity.FileTagEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: com.prabincode.vajrafs.data.local.entity.FileTagEntity)

    @Query("DELETE FROM file_tags WHERE path = :path")
    suspend fun deleteTag(path: String)
}
