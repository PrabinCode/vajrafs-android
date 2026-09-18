/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.ui.screens.search

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prabincode.imanage.IManageApp
import com.prabincode.imanage.data.crypto.ShredderEngine
import com.prabincode.imanage.data.model.FileItem
import com.prabincode.imanage.data.repository.FileSystemRepository
import com.prabincode.imanage.data.repository.TrashRepository
import com.prabincode.imanage.data.repository.VaultRepository
import com.prabincode.imanage.data.service.VaultService
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<FileItem> = emptyList(),
    val isSearching: Boolean = false,
    val toastMessage: String? = null
)

class SearchViewModel(
    private val fileSystemRepository: FileSystemRepository,
    private val trashRepository: TrashRepository,
    private val vaultRepository: VaultRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
        searchJob?.cancel()

        if (newQuery.trim().length < 2) {
            _uiState.value = _uiState.value.copy(results = emptyList(), isSearching = false)
            return
        }

        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.value = _uiState.value.copy(isSearching = true)
            fileSystemRepository.searchFiles(
                rootPath = Environment.getExternalStorageDirectory().absolutePath,
                query = newQuery
            ).collect { list ->
                _uiState.value = _uiState.value.copy(
                    results = list,
                    isSearching = false
                )
            }
        }
    }

    fun deleteFile(item: FileItem, shred: Boolean) {
        viewModelScope.launch {
            if (shred) {
                ShredderEngine.shred(item.file, passes = 3)
            } else {
                trashRepository.moveToTrash(item.file)
            }
            _uiState.value = _uiState.value.copy(
                toastMessage = if (shred) "Permanently shredded ${item.name}" else "Moved ${item.name} to Trash"
            )
            onQueryChange(_uiState.value.query)
        }
    }

    fun moveToVault(item: FileItem) {
        if (vaultRepository.isPathBusy(item.file.absolutePath)) {
            _uiState.value = _uiState.value.copy(toastMessage = "File is already being moved to Safe Vault")
            return
        }

        VaultService.startEncrypt(
            context = IManageApp.instance,
            paths = listOf(item.file.absolutePath),
            shredOriginal = true
        )
        _uiState.value = _uiState.value.copy(
            toastMessage = "Securing ${item.name} in Safe Vault... (Check notification)"
        )
        viewModelScope.launch {
            delay(1200)
            onQueryChange(_uiState.value.query)
        }
    }

    fun renameFile(item: FileItem, newName: String) {
        viewModelScope.launch {
            val dest = File(item.file.parentFile, newName)
            fileSystemRepository.moveFile(item.file, dest)
            onQueryChange(_uiState.value.query)
        }
    }

    fun zipFile(item: FileItem) {
        viewModelScope.launch {
            val destZip = File(item.file.parentFile ?: Environment.getExternalStorageDirectory(), "${item.name}.zip")
            fileSystemRepository.zipFiles(listOf(item.file), destZip)
            onQueryChange(_uiState.value.query)
        }
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }
}
