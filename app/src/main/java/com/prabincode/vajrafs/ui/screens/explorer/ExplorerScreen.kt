/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.ui.screens.explorer

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.prabincode.vajrafs.data.model.*
import com.prabincode.vajrafs.ui.components.*
import com.prabincode.vajrafs.util.safeStatusBarInsets
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    initialPath: String,
    title: String,
    categoryName: String? = null,
    highlightPath: String? = null,
    viewModel: ExplorerViewModel,
    onNavigateBack: () -> Unit,
    onOpenFile: (String) -> Unit,
    onNavigateToCategory: (FileType) -> Unit = {},
    onNavigateToVault: () -> Unit = {},
    onNavigateToAnalyzer: () -> Unit = {},
    onNavigateToTrash: () -> Unit = {},
    onNavigateToWifiShare: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToFolder: ((String, String, String?) -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showCreateActionMenu by remember { mutableStateOf(false) }
    var itemToRename by remember { mutableStateOf<FileItem?>(null) }
    var itemForInfo by remember { mutableStateOf<FileItem?>(null) }
    var itemForTag by remember { mutableStateOf<FileItem?>(null) }
    var itemsToZip by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var archiveToExtract by remember { mutableStateOf<FileItem?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showBatchRenameDialog by remember { mutableStateOf(false) }
    var itemsToDelete by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isPullRefreshing by remember { mutableStateOf(false) }
    var isInitialized by rememberSaveable(initialPath, categoryName) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var highlightedItemPath by rememberSaveable(highlightPath) { mutableStateOf(highlightPath) }

    LaunchedEffect(state.files, highlightPath) {
        if (!highlightPath.isNullOrEmpty() && state.files.isNotEmpty()) {
            val index = state.files.indexOfFirst { it.path == highlightPath }
            if (index >= 0) {
                listState.animateScrollToItem(index)
                delay(2500)
                highlightedItemPath = null
            }
        }
    }

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    LaunchedEffect(initialPath, categoryName) {
        viewModel.syncSettings()
        if (!isInitialized) {
            isInitialized = true
            if (!categoryName.isNullOrEmpty()) {
                val matchedType = FileType.entries.find { it.name.equals(categoryName, ignoreCase = true) || it.displayName.equals(categoryName, ignoreCase = true) } ?: FileType.DOCUMENT
                viewModel.navigateToCategory(matchedType)
            } else if (initialPath.isNotEmpty()) {
                viewModel.navigateTo(initialPath, title)
            } else {
                viewModel.loadCurrent()
            }
        } else {
            // Re-entering screen (e.g. Back from Video/Image viewer): refresh contents of current folder
            viewModel.loadCurrent()
        }
    }

    BackHandler(enabled = true) {
        if (state.isSelectionMode) {
            viewModel.clearSelection()
        } else if (state.isCategoryMode) {
            onNavigateBack()
        } else {
            val parent = File(state.currentPath).parentFile
            if (parent != null && parent.exists() && parent.canRead() && state.currentPath != "/" && state.currentPath != "/storage/emulated/0") {
                viewModel.navigateTo(parent.absolutePath, parent.name.ifEmpty { "Root" })
            } else {
                onNavigateBack()
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                storageVolumes = state.storageVolumes,
                bookmarks = state.bookmarks,
                onNavigateToPath = { path, heading ->
                    viewModel.navigateTo(path, heading)
                },
                onNavigateToCategory = onNavigateToCategory,
                onNavigateToVault = onNavigateToVault,
                onNavigateToAnalyzer = onNavigateToAnalyzer,
                onNavigateToTrash = onNavigateToTrash,
                onNavigateToWifiShare = onNavigateToWifiShare,
                onNavigateToSettings = onNavigateToSettings,
                onRemoveBookmark = { viewModel.removeBookmark(it) },
                onCloseDrawer = {
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    windowInsets = safeStatusBarInsets(),
                    title = {
                        Text(
                            text = if (state.isSelectionMode) "${state.selectedFiles.size} selected" else state.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (state.isSelectionMode) {
                                    viewModel.clearSelection()
                                } else if (state.isCategoryMode) {
                                    onNavigateBack()
                                } else {
                                    val parent = File(state.currentPath).parentFile
                                    if (parent != null && parent.exists() && state.currentPath != "/" && state.currentPath != "/storage/emulated/0") {
                                        viewModel.navigateTo(parent.absolutePath, parent.name.ifEmpty { "Root" })
                                    } else {
                                        onNavigateBack()
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (!state.isCategoryMode && state.currentPath.isNotEmpty()) {
                            IconButton(onClick = { viewModel.toggleBookmarkCurrentPath() }) {
                                Icon(
                                    imageVector = if (state.isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = if (state.isBookmarked) "Remove from favourites" else "Add to favourites",
                                    tint = if (state.isBookmarked) androidx.compose.ui.graphics.Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.toggleViewMode() }) {
                            Icon(
                                imageVector = if (state.viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                                contentDescription = "Toggle View"
                            )
                        }
                        IconButton(
                            onClick = {
                                if (!isPullRefreshing) {
                                    isPullRefreshing = true
                                    scope.launch {
                                        try {
                                            viewModel.refresh().join()
                                        } finally {
                                            delay(450)
                                            isPullRefreshing = false
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                        }
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Drawer")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Name (A to Z)") },
                                onClick = {
                                    showSortMenu = false
                                    viewModel.updateSortOption(state.sortOption.copy(sortBy = SortBy.NAME, order = SortOrder.ASCENDING))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Date (Newest First)") },
                                onClick = {
                                    showSortMenu = false
                                    viewModel.updateSortOption(state.sortOption.copy(sortBy = SortBy.DATE, order = SortOrder.DESCENDING))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Size (Largest First)") },
                                onClick = {
                                    showSortMenu = false
                                    viewModel.updateSortOption(state.sortOption.copy(sortBy = SortBy.SIZE, order = SortOrder.DESCENDING))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Type") },
                                onClick = {
                                    showSortMenu = false
                                    viewModel.updateSortOption(state.sortOption.copy(sortBy = SortBy.TYPE, order = SortOrder.ASCENDING))
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(if (state.sortOption.showHiddenFiles) "Hide Hidden Files" else "Show Hidden Files")
                                },
                                onClick = {
                                    showSortMenu = false
                                    viewModel.updateSortOption(state.sortOption.copy(showHiddenFiles = !state.sortOption.showHiddenFiles))
                                }
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (state.isSelectionMode) {
                    BatchActionBar(
                        selectedCount = state.selectedFiles.size,
                        onCopy = { viewModel.copySelected() },
                        onCut = { viewModel.cutSelected() },
                        onRename = { showBatchRenameDialog = true },
                        onDelete = {
                            itemsToDelete = state.files.filter { state.selectedFiles.contains(it.path) }
                            showDeleteConfirmDialog = true
                        },
                        onVault = {
                            val selected = state.files.filter { state.selectedFiles.contains(it.path) }
                            viewModel.moveItemsToVault(selected)
                        },
                        onZip = {
                            val selected = state.files.filter { state.selectedFiles.contains(it.path) }
                            viewModel.zipItems(selected)
                        },
                        onSelectAll = { viewModel.selectAll() },
                        onClose = { viewModel.clearSelection() }
                    )
                }
            },
            floatingActionButton = {
            if (state.clipboard != null) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.pasteClipboard() },
                    icon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
                    text = { Text("Paste (${state.clipboard?.items?.size})") },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            } else if (!state.isSelectionMode && !state.isCategoryMode) {
                FloatingActionButton(
                    onClick = { showCreateActionMenu = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add / Create")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Interactive Breadcrumbs for Directory Mode
            if (!state.isCategoryMode) {
                BreadcrumbBar(
                    currentPath = state.currentPath,
                    onNavigateToPath = { viewModel.navigateTo(it) }
                )
            }

            PullToRefreshBox(
                isRefreshing = isPullRefreshing,
                onRefresh = {
                    isPullRefreshing = true
                    scope.launch {
                        try {
                            viewModel.refresh().join()
                        } finally {
                            delay(450)
                            isPullRefreshing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                if (state.isLoading && state.files.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (state.files.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (state.isCategoryMode) "No ${state.title} files found" else "This folder is empty",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (state.viewMode == ViewMode.GRID) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.files, key = { it.path }) { item ->
                            val isSelected = state.selectedFiles.contains(item.path)
                            FileGridItem(
                                item = item,
                                isSelectionMode = state.isSelectionMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (state.isSelectionMode) {
                                        viewModel.toggleSelection(item.path)
                                    } else if (item.isDirectory) {
                                        viewModel.navigateTo(item.path, item.name)
                                    } else {
                                        onOpenFile(item.path)
                                    }
                                },
                                onLongClick = {
                                    viewModel.toggleSelection(item.path)
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.files, key = { it.path }) { item ->
                            val isSelected = state.selectedFiles.contains(item.path) || (item.path == highlightedItemPath)
                            val isArchive = item.extension.equals("zip", ignoreCase = true) || item.extension.equals("tgz", ignoreCase = true) || item.name.endsWith(".tar.gz", ignoreCase = true)
                            
                            val parentFolder = item.file.parentFile
                            val canOpenLocation = parentFolder != null && parentFolder.exists() && (state.isCategoryMode || item.file.parent != state.currentPath)

                            FileListItem(
                                item = item,
                                isSelectionMode = state.isSelectionMode,
                                isSelected = isSelected,
                                tagColorHex = state.tagsMap[item.path],
                                isBookmarked = state.bookmarks.any { it.path == item.path },
                                onBookmarkClick = { viewModel.toggleBookmark(item.path, item.name) },
                                onClick = {
                                    if (state.isSelectionMode) {
                                        viewModel.toggleFileSelection(item.path)
                                    } else if (item.isDirectory) {
                                        viewModel.navigateTo(item.path, item.name)
                                    } else if (isArchive) {
                                        archiveToExtract = item
                                    } else {
                                        onOpenFile(item.path)
                                    }
                                },
                                onLongClick = {
                                    viewModel.toggleFileSelection(item.path)
                                },
                                onRenameClick = { itemToRename = item },
                                onTagClick = { itemForTag = item },
                                onOpenFileLocation = if (canOpenLocation) {
                                    {
                                        val pPath = parentFolder!!.absolutePath
                                        val pTitle = parentFolder.name.ifEmpty { "Storage" }
                                        if (onNavigateToFolder != null) {
                                            onNavigateToFolder(pPath, pTitle, item.path)
                                        } else {
                                            viewModel.navigateTo(pPath, pTitle)
                                        }
                                    }
                                } else null,
                                onDeleteClick = {
                                    itemsToDelete = listOf(item)
                                    showDeleteConfirmDialog = true
                                },
                                onShredClick = {
                                    itemsToDelete = listOf(item)
                                    showDeleteConfirmDialog = true
                                },
                                onVaultClick = {
                                    viewModel.moveItemsToVault(listOf(item))
                                },
                                onZipClick = {
                                    itemsToZip = listOf(item)
                                },
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
                                onOpenWithClick = {
                                    try {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            item.file
                                        )
                                        val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(item.extension.lowercase()) ?: "*/*"
                                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, mimeType)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(viewIntent, "Open ${item.name} with"))
                                    } catch (e: Exception) { }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateActionMenu) {
        CreateActionBottomSheet(
            onDismiss = { showCreateActionMenu = false },
            onCreateFolderClick = { showCreateFolderDialog = true },
            onCreateFileClick = { showCreateFileDialog = true }
        )
    }

    if (showCreateFileDialog) {
        CreateFileDialog(
            onDismiss = { showCreateFileDialog = false },
            onConfirm = {
                viewModel.createFile(it)
                showCreateFileDialog = false
            }
        )
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = {
                viewModel.createFolder(it)
                showCreateFolderDialog = false
            }
        )
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

    itemForTag?.let { item ->
        AssignTagDialog(
            fileName = item.name,
            currentTagHex = state.tagsMap[item.path],
            onDismiss = { itemForTag = null },
            onSelectTag = { hex, label ->
                viewModel.assignTag(item.path, hex, label)
                itemForTag = null
            },
            onRemoveTag = {
                viewModel.removeTag(item.path)
                itemForTag = null
            }
        )
    }

    if (itemsToZip.isNotEmpty()) {
        val defaultName = "${itemsToZip.first().file.nameWithoutExtension}.zip"
        PasswordZipDialog(
            defaultZipName = defaultName,
            onDismiss = { itemsToZip = emptyList() },
            onConfirm = { zipName, password ->
                viewModel.zipItems(itemsToZip, zipName, password)
                itemsToZip = emptyList()
            }
        )
    }

    archiveToExtract?.let { archive ->
        ExtractArchiveDialog(
            archiveName = archive.name,
            onDismiss = { archiveToExtract = null },
            onConfirm = { password ->
                viewModel.extractArchive(archive, password)
                archiveToExtract = null
            }
        )
    }

    if (showDeleteConfirmDialog) {
        val count = itemsToDelete.size
        val name = itemsToDelete.firstOrNull()?.name ?: "selected items"
        DeleteConfirmationDialog(
            itemCount = count,
            itemName = name,
            onDismiss = {
                showDeleteConfirmDialog = false
                itemsToDelete = emptyList()
            },
            onMoveToTrash = {
                viewModel.deleteItems(itemsToDelete, shred = false)
                showDeleteConfirmDialog = false
                itemsToDelete = emptyList()
            },
            onSecureShred = {
                viewModel.deleteItems(itemsToDelete, shred = true)
                showDeleteConfirmDialog = false
                itemsToDelete = emptyList()
            }
        )
    }

    if (showBatchRenameDialog) {
        val selectedItems = state.files.filter { state.selectedFiles.contains(it.path) }
        BatchRenameDialog(
            items = selectedItems,
            onDismiss = { showBatchRenameDialog = false },
            onConfirm = { newNames ->
                viewModel.batchRename(selectedItems, newNames)
                showBatchRenameDialog = false
            }
        )
    }
    }
}
