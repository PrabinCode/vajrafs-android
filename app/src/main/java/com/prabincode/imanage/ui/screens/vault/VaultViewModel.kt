/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.ui.screens.vault

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prabincode.imanage.data.local.entity.VaultEntity
import com.prabincode.imanage.data.repository.VaultRepository
import com.prabincode.imanage.data.service.VaultProgressState
import com.prabincode.imanage.data.service.VaultService
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VaultUiState(
    val isUnlocked: Boolean = false,
    val items: List<VaultEntity> = emptyList(),
    val activeTask: VaultProgressState? = null,
    val isLoading: Boolean = false,
    val previewFile: File? = null,
    val message: String? = null
)

class VaultViewModel(
    private val vaultRepository: VaultRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    init {
        observeVaultItems()
        observeActiveTask()
    }

    private fun observeVaultItems() {
        viewModelScope.launch {
            vaultRepository.vaultItems.collect { items ->
                _uiState.value = _uiState.value.copy(items = items)
            }
        }
    }

    private fun observeActiveTask() {
        viewModelScope.launch {
            VaultService.currentProgress.collect { progress ->
                _uiState.value = _uiState.value.copy(activeTask = progress)
            }
        }
    }

    fun setUnlocked(unlocked: Boolean) {
        _uiState.value = _uiState.value.copy(isUnlocked = unlocked)
    }

    fun importUris(context: Context, uris: List<Uri>) {
        VaultService.startImport(context, uris)
        _uiState.value = _uiState.value.copy(
            message = "Securing ${uris.size} item(s) into vault..."
        )
    }

    fun restoreItem(context: Context, item: VaultEntity) {
        VaultService.startRestore(context, listOf(item.id))
        _uiState.value = _uiState.value.copy(
            message = "Restoring ${item.originalFileName}..."
        )
    }

    fun previewItem(item: VaultEntity, onReady: (File) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = vaultRepository.decryptForPreview(item)
            _uiState.value = _uiState.value.copy(isLoading = false)
            if (result.isSuccess) {
                val file = result.getOrNull()
                if (file != null) {
                    onReady(file)
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    message = "Preview failed: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun deleteItem(item: VaultEntity) {
        viewModelScope.launch {
            vaultRepository.deleteVaultItem(item)
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun clearTempDecryptedFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            vaultRepository.clearTempDecryptedFiles()
        }
    }
}
