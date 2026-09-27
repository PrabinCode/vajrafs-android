/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.prabincode.imanage.data.local.dao.BookmarkDao
import com.prabincode.imanage.data.local.dao.SearchHistoryDao
import com.prabincode.imanage.data.local.dao.TrashDao
import com.prabincode.imanage.data.local.dao.VaultDao
import com.prabincode.imanage.data.local.entity.BookmarkEntity
import com.prabincode.imanage.data.local.entity.SearchHistoryEntity
import com.prabincode.imanage.data.local.entity.TrashEntity
import com.prabincode.imanage.data.local.entity.VaultEntity

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_trash_items_trashedTime` ON `trash_items` (`trashedTime`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_items_originalPath` ON `vault_items` (`originalPath`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_items_encryptedAt` ON `vault_items` (`encryptedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_search_history_timestamp` ON `search_history` (`timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_file_tags_taggedAt` ON `file_tags` (`taggedAt`)")
    }
}

@Database(
    entities = [
        BookmarkEntity::class,
        TrashEntity::class,
        VaultEntity::class,
        SearchHistoryEntity::class,
        com.prabincode.imanage.data.local.entity.FileTagEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookmarkDao(): BookmarkDao
    abstract fun trashDao(): TrashDao
    abstract fun vaultDao(): VaultDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun tagDao(): com.prabincode.imanage.data.local.dao.TagDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "imanage_database.db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
