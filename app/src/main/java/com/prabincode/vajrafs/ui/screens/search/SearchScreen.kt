/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.ui.screens.search

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.prabincode.vajrafs.data.model.FileItem
import com.prabincode.vajrafs.ui.components.*
import com.prabincode.vajrafs.util.safeStatusBarInsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onNavigateBack: () -> Unit,
    onOpenFile: (String) -> Unit,
    onNavigateToFolder: ((String, String, String?) -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var itemToRename by remember { mutableStateOf<FileItem?>(null) }
    var itemForInfo by remember { mutableStateOf<FileItem?>(null) }
    var itemToDelete by remember { mutableStateOf<FileItem?>(null) }

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = safeStatusBarInsets(),
                title = {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = { viewModel.onQueryChange(it) },
                        placeholder = { Text("Search files & folders...") },
                        singleLine = true,
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.isSearching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (state.query.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("Type at least 2 characters to search across storage", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (state.results.isEmpty() && !state.isSearching) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("No files matching '${state.query}'", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.results, key = { it.path }) { item ->
                        FileListItem(
                            item = item,
                            isSelectionMode = false,
                            isSelected = false,
                            onClick = { onOpenFile(item.path) },
                            onLongClick = { },
                            onRenameClick = { itemToRename = item },
                            onDeleteClick = { itemToDelete = item },
                            onShredClick = { itemToDelete = item },
                            onVaultClick = { viewModel.moveToVault(item) },
                            onZipClick = { viewModel.zipFile(item) },
                            onInfoClick = { itemForInfo = item },
                            onShareClick = {
                                try {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        item.file
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "*/*"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share ${item.name}"))
                                } catch (e: Exception) { }
                            },
                            onOpenFileLocation = {
                                val parent = item.file.parentFile
                                if (parent != null && parent.exists()) {
                                    onNavigateToFolder?.invoke(parent.absolutePath, parent.name.ifEmpty { "Storage" }, item.path)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    itemToRename?.let { item ->
        RenameDialog(
            currentName = item.name,
            onDismiss = { itemToRename = null },
            onConfirm = {
                viewModel.renameFile(item, it)
                itemToRename = null
            }
        )
    }

    itemForInfo?.let { item ->
        FileInfoDialog(
            item = item,
            onDismiss = { itemForInfo = null }
        )
    }

    itemToDelete?.let { item ->
        DeleteConfirmationDialog(
            itemCount = 1,
            itemName = item.name,
            onDismiss = { itemToDelete = null },
            onMoveToTrash = {
                viewModel.deleteFile(item, shred = false)
                itemToDelete = null
            },
            onSecureShred = {
                viewModel.deleteFile(item, shred = true)
                itemToDelete = null
            }
        )
    }
}
