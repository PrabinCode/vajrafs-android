/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.ui.screens.analyzer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prabincode.vajrafs.data.model.FileItem
import com.prabincode.vajrafs.data.repository.StorageAnalysisResult
import com.prabincode.vajrafs.data.repository.StorageAnalyzerRepository
import com.prabincode.vajrafs.data.crypto.ShredderEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AnalyzerUiState(
    val isLoading: Boolean = true,
    val result: StorageAnalysisResult? = null,
    val statusMessage: String? = null
)

class StorageAnalyzerViewModel(
    private val analyzerRepository: StorageAnalyzerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyzerUiState())
    val uiState: StateFlow<AnalyzerUiState> = _uiState.asStateFlow()

    fun runAnalysis() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = analyzerRepository.analyzeStorage()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                result = result
            )
        }
    }

    fun cleanEmptyFolders() {
        viewModelScope.launch {
            val emptyList = _uiState.value.result?.emptyFolders ?: emptyList()
            for (folder in emptyList) {
                folder.file.delete()
            }
            _uiState.value = _uiState.value.copy(
                statusMessage = "Cleaned ${emptyList.size} empty folders"
            )
            runAnalysis()
        }
    }

    fun deleteLargeFile(item: FileItem) {
        viewModelScope.launch {
            item.file.delete()
            runAnalysis()
        }
    }

    fun shredFile(item: FileItem) {
        viewModelScope.launch {
            ShredderEngine.shred(item.file, passes = 1)
            runAnalysis()
        }
    }
}
