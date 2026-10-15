/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.prabincode.vajrafs.data.local.AppDatabase
import com.prabincode.vajrafs.data.repository.FileSystemRepository
import com.prabincode.vajrafs.data.repository.StorageAnalyzerRepository
import com.prabincode.vajrafs.data.repository.TrashRepository
import com.prabincode.vajrafs.data.repository.VaultRepository

import coil.disk.DiskCache
import coil.memory.MemoryCache

class VajraApp : Application(), ImageLoaderFactory {

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val fileSystemRepository: FileSystemRepository by lazy { FileSystemRepository(this) }
    val vaultRepository: VaultRepository by lazy { VaultRepository(this, database.vaultDao()) }
    val trashRepository: TrashRepository by lazy { TrashRepository(this, database.trashDao()) }
    val storageAnalyzerRepository: StorageAnalyzerRepository by lazy { StorageAnalyzerRepository() }
    val tagRepository: com.prabincode.vajrafs.data.repository.TagRepository by lazy { com.prabincode.vajrafs.data.repository.TagRepository(database.tagDao()) }
    val bookmarkRepository: com.prabincode.vajrafs.data.repository.BookmarkRepository by lazy { com.prabincode.vajrafs.data.repository.BookmarkRepository(database.bookmarkDao()) }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024) // Capped at 150 MB
                    .build()
            }
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Clear any orphaned temp decrypted preview files on startup
        vaultRepository.clearTempDecryptedFiles()
        // Clean any lingering external opened cache files on startup
        com.prabincode.vajrafs.util.ExternalFileResolver.cleanExternalCache(this)
    }

    companion object {
        lateinit var instance: VajraApp
            private set
    }
}
