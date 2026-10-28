/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.ui.screens.home

import android.database.ContentObserver
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prabincode.vajrafs.VajraApp
import com.prabincode.vajrafs.data.crypto.ShredderEngine
import com.prabincode.vajrafs.data.local.entity.BookmarkEntity
import com.prabincode.vajrafs.data.model.FileItem
import com.prabincode.vajrafs.data.model.FileType
import com.prabincode.vajrafs.data.model.StorageVolumeInfo
import com.prabincode.vajrafs.data.repository.BookmarkRepository
import com.prabincode.vajrafs.data.repository.FileSystemRepository
import com.prabincode.vajrafs.data.repository.TrashRepository
import com.prabincode.vajrafs.data.repository.VaultRepository
import com.prabincode.vajrafs.data.service.VaultService
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val storageVolumes: List<StorageVolumeInfo> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val recentFiles: List<FileItem> = emptyList(),
    val isLoading: Boolean = true,
    val permissionGranted: Boolean = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true
    },
    val toastMessage: String? = null
)

class HomeViewModel(
    private val fileSystemRepository: FileSystemRepository,
    private val trashRepository: TrashRepository,
    private val vaultRepository: VaultRepository,
    private val bookmarkRepository: BookmarkRepository = VajraApp.instance.bookmarkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    @Volatile
    private var hasInitialScanCompleted = false
    private var loadJob: Job? = null

    // Set whenever MediaStore reports a change (our own file ops, camera, downloads, Wi-Fi uploads...),
    // so returning to Home only rescans when something actually changed
    @Volatile
    private var mediaChangedSinceScan = false

    private val mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            mediaChangedSinceScan = true
        }
    }

    init {
        try {
            VajraApp.instance.contentResolver.registerContentObserver(
                MediaStore.Files.getContentUri("external"),
                true,
                mediaObserver
            )
        } catch (_: Exception) {}
        loadData()
        viewModelScope.launch {
            bookmarkRepository.getAllBookmarks().collectLatest { bms ->
                _uiState.value = _uiState.value.copy(bookmarks = bms)
            }
        }
        viewModelScope.launch {
            VaultService.lastCompletedTimestamp.collectLatest { ts ->
                if (ts > 0L) {
                    loadData()
                }
            }
        }
    }

    override fun onCleared() {
        VajraApp.instance.contentResolver.unregisterContentObserver(mediaObserver)
        super.onCleared()
    }

    /**
     * Called when Home resumes. Runs the full scan until one has succeeded; after that the cached
     * data stays on screen and a silent background rescan runs only if files changed meanwhile.
     */
    fun refreshIfNeeded() {
        // A scan is already running (e.g. the one started in init on cold start); don't restart it
        if (loadJob?.isActive == true) return
        val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        if (!hasInitialScanCompleted || (!_uiState.value.permissionGranted && hasPermission)) {
            loadData()
        } else if (mediaChangedSinceScan) {
            loadData(silent = true)
        }
    }

    /** Rescans storage and recent files. A [silent] scan keeps the current data visible and skips the loading state. */
    fun loadData(silent: Boolean = false): Job {
        loadJob?.cancel()
        // Cleared before scanning, so changes that land mid-scan still trigger the next refresh
        mediaChangedSinceScan = false
        val job = viewModelScope.launch {
            if (!silent) _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                withContext(Dispatchers.IO) {
                    val isStorageManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        Environment.isExternalStorageManager()
                    } else {
                        true
                    }

                    val volumes = fileSystemRepository.getStorageVolumes()

                    val recents = mutableListOf<FileItem>()
                    val visibility = fileSystemRepository.createVisibilityFilter()
                    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                    val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                    val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                    val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

                    fun collectRecent(dir: File) {
                        try {
                            if (dir.exists()) {
                                dir.walkTopDown().maxDepth(3).filter { it.isFile && visibility.isVisible(it) }.forEach {
                                    recents.add(FileItem.fromFile(it))
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    collectRecent(downloadDir)
                    collectRecent(dcimDir)
                    collectRecent(docsDir)
                    collectRecent(moviesDir)
                    collectRecent(picturesDir)

                    // Query Android MediaStore for recent videos and images across all apps
                    try {
                        val cr = VajraApp.instance.contentResolver
                        val projection = arrayOf(android.provider.MediaStore.MediaColumns.DATA, android.provider.MediaStore.MediaColumns.DATE_MODIFIED)
                        val queryUri = android.provider.MediaStore.Files.getContentUri("external")
                        val selection = "(${android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE} = ${android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO} OR ${android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE} = ${android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE})"
                        val sortOrder = "${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} DESC"

                        cr.query(queryUri, projection, selection, null, sortOrder)?.use { cursor ->
                            val dataIdx = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA)
                            var count = 0
                            while (cursor.moveToNext() && count < 30) {
                                val path = cursor.getString(dataIdx)
                                if (!path.isNullOrEmpty()) {
                                    val f = File(path)
                                    if (f.exists() && f.isFile && visibility.isVisible(f)) {
                                        recents.add(FileItem.fromFile(f))
                                        count++
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}

                    val sortedRecents = recents.distinctBy { it.path }.sortedByDescending { it.lastModified }.take(25)

                    // A newer scan replaced this one; drop these stale results
                    ensureActive()

                    _uiState.value = _uiState.value.copy(
                        storageVolumes = volumes,
                        recentFiles = sortedRecents,
                        permissionGranted = isStorageManager
                    )
                    if (isStorageManager) {
                        hasInitialScanCompleted = true
                    }
                }
            } catch (_: Exception) {
            } finally {
                // Only the latest scan may clear the spinner; a cancelled one finishing late must not
                if (loadJob === coroutineContext[Job]) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
        loadJob = job
        return job
    }

    fun deleteFile(item: FileItem, shred: Boolean) {
        viewModelScope.launch {
            if (shred) {
                ShredderEngine.shred(item.file, passes = ShredderEngine.configuredPasses(VajraApp.instance))
            } else {
                trashRepository.moveToTrash(item.file)
            }
            _uiState.value = _uiState.value.copy(
                toastMessage = if (shred) "Permanently shredded ${item.name}" else "Moved ${item.name} to Trash"
            )
            loadData()
        }
    }

    fun moveToVault(item: FileItem) {
        if (vaultRepository.isPathBusy(item.file.absolutePath)) {
            _uiState.value = _uiState.value.copy(toastMessage = "File is already being moved to Safe Vault")
            return
        }

        VaultService.startEncrypt(
            context = VajraApp.instance,
            paths = listOf(item.file.absolutePath),
            shredOriginal = true
        )
        _uiState.value = _uiState.value.copy(
            toastMessage = "Securing ${item.name} in Safe Vault... (Check notification)"
        )
        loadData()
    }

    fun renameFile(item: FileItem, newName: String) {
        viewModelScope.launch {
            val dest = File(item.file.parentFile, newName)
            fileSystemRepository.moveFile(item.file, dest)
            loadData()
        }
    }

    fun zipFile(item: FileItem) {
        viewModelScope.launch {
            val destZip = File(item.file.parentFile ?: Environment.getExternalStorageDirectory(), "${item.name}.zip")
            fileSystemRepository.zipFiles(listOf(item.file), destZip)
            loadData()
        }
    }

    fun removeBookmark(path: String) {
        viewModelScope.launch {
            bookmarkRepository.removeBookmark(path)
            _uiState.value = _uiState.value.copy(toastMessage = "Removed from favourites")
        }
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }
}
