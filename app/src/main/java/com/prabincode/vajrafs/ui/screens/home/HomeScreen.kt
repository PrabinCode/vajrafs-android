/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.ui.screens.home

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.prabincode.vajrafs.util.findActivity
import com.prabincode.vajrafs.util.safeStatusBarInsets
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.prabincode.vajrafs.data.model.FileItem
import com.prabincode.vajrafs.data.model.FileType
import com.prabincode.vajrafs.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToExplorer: (path: String, title: String) -> Unit,
    onNavigateToCategory: (FileType) -> Unit,
    onNavigateToVault: () -> Unit,
    onNavigateToAnalyzer: () -> Unit,
    onNavigateToTrash: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToWifiShare: () -> Unit,
    onOpenFile: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current

    // Ensure status and navigation bars are always restored to normal insets on Home screen
    LaunchedEffect(Unit) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            val decor = window.decorView
            decor.post { androidx.core.view.ViewCompat.requestApplyInsets(decor) }
            decor.postDelayed({ androidx.core.view.ViewCompat.requestApplyInsets(decor) }, 150)
        }
    }

    var itemToRename by remember { mutableStateOf<FileItem?>(null) }
    var itemForInfo by remember { mutableStateOf<FileItem?>(null) }
    var itemToDelete by remember { mutableStateOf<FileItem?>(null) }
    var isPullRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshIfNeeded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                storageVolumes = state.storageVolumes,
                bookmarks = state.bookmarks,
                onNavigateToPath = onNavigateToExplorer,
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
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Navigation Drawer")
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "VajraFS",
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "OFFLINE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (!isPullRefreshing) {
                                    isPullRefreshing = true
                                    scope.launch {
                                        try {
                                            viewModel.loadData().join()
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
                        IconButton(onClick = onNavigateToSearch) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = isPullRefreshing,
                onRefresh = {
                    isPullRefreshing = true
                    scope.launch {
                        try {
                            viewModel.loadData().join()
                        } finally {
                            delay(450)
                            isPullRefreshing = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
            // Storage Permission Warning Banner
            if (!state.isLoading && !state.permissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "All Files Access Required",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Please grant 'All files access' so VajraFS can read and manage your storage.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                        context.startActivity(intent)
                                    }
                                }
                            ) {
                                Text("Grant Permission")
                            }
                        }
                    }
                }
            }

            // Storage Volumes (Internal storage, SD Card, System Root /)
            items(state.storageVolumes) { volume ->
                StorageIndicator(
                    volumeInfo = volume,
                    onManageClick = {
                        onNavigateToExplorer(volume.path, volume.name)
                    }
                )
            }

            // Quick Security & Tools (All 4 in same line, saves vertical space)
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    QuickToolTile(
                        title = "Safe Vault",
                        icon = Icons.Default.Lock,
                        iconColor = Color(0xFFEC407A),
                        onClick = onNavigateToVault,
                        modifier = Modifier.weight(1f)
                    )
                    QuickToolTile(
                        title = "Analyzer",
                        icon = Icons.Default.PieChart,
                        iconColor = Color(0xFF42A5F5),
                        onClick = onNavigateToAnalyzer,
                        modifier = Modifier.weight(1f)
                    )
                    QuickToolTile(
                        title = "Trash Bin",
                        icon = Icons.Outlined.Delete,
                        iconColor = Color(0xFFFFA726),
                        onClick = onNavigateToTrash,
                        modifier = Modifier.weight(1f)
                    )
                    QuickToolTile(
                        title = "PC Transfer",
                        icon = Icons.Default.Wifi,
                        iconColor = Color(0xFF00838F),
                        onClick = onNavigateToWifiShare,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Favourite Folders Section (Shown below tools container)
            if (state.bookmarks.isNotEmpty()) {
                item {
                    Text(
                        text = "Favourites",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(state.bookmarks, key = { it.path }) { bookmark ->
                            FavouriteFolderCard(
                                title = bookmark.title,
                                path = bookmark.path,
                                onOpen = { onNavigateToExplorer(bookmark.path, bookmark.title) },
                                onRemove = { viewModel.removeBookmark(bookmark.path) }
                            )
                        }
                    }
                }
            }

            // Categories Section
            item {
                Text(
                    text = "Categories",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                CategoryGrid(
                    onCategoryClick = { fileType ->
                        if (fileType == FileType.VAULT_ENCRYPTED) {
                            onNavigateToVault()
                        } else {
                            onNavigateToCategory(fileType)
                        }
                    }
                )
            }

            // Recent Files Section
            if (state.recentFiles.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent Files",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(state.recentFiles, key = { it.path }) { fileItem ->
                    FileListItem(
                        item = fileItem,
                        isSelectionMode = false,
                        isSelected = false,
                        onClick = { onOpenFile(fileItem.path) },
                        onLongClick = { },
                        onRenameClick = { itemToRename = fileItem },
                        onDeleteClick = { itemToDelete = fileItem },
                        onShredClick = { itemToDelete = fileItem },
                        onVaultClick = { viewModel.moveToVault(fileItem) },
                        onZipClick = { viewModel.zipFile(fileItem) },
                        onInfoClick = { itemForInfo = fileItem },
                        onShareClick = {
                            try {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    fileItem.file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share ${fileItem.name}"))
                            } catch (e: Exception) { }
                        },
                        onOpenFileLocation = {
                            val parent = fileItem.file.parentFile
                            if (parent != null && parent.exists()) {
                                onNavigateToExplorer(parent.absolutePath, parent.name.ifEmpty { "Storage" })
                            }
                        }
                    )
                }
            }
        }
    }
    }

    // Dialogs for Recent Files
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
}

// Tap opens; long-press offers removal, so a stray tap can't delete a favourite
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavouriteFolderCard(
    title: String,
    path: String,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val exists = remember(path) { File(path).isDirectory }
    val location = remember(path) { favouriteLocationLabel(path) }
    val accent = Color(0xFFF59E0B)

    Box {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = { if (exists) onOpen() else menuExpanded = true },
                    onLongClick = { menuExpanded = true }
                )
                .alpha(if (exists) 1f else 0.55f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 10.dp, bottom = 10.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.18f))
                ) {
                    Icon(
                        imageVector = if (exists) Icons.Default.FolderSpecial else Icons.Default.FolderOff,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.widthIn(min = 64.dp, max = 140.dp)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (exists) location else "Folder not found",
                        fontSize = 11.sp,
                        color = if (exists) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            if (exists) {
                DropdownMenuItem(
                    text = { Text("Open") },
                    leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                    onClick = { menuExpanded = false; onOpen() }
                )
            }
            DropdownMenuItem(
                text = { Text("Remove from favourites") },
                leadingIcon = { Icon(Icons.Default.StarBorder, contentDescription = null) },
                onClick = { menuExpanded = false; onRemove() }
            )
        }
    }
}

// Where the folder lives, e.g. "Internal › DCIM" or "SD card › Movies", so the subtitle doesn't repeat the title
private fun favouriteLocationLabel(path: String): String {
    val internalRoot = Environment.getExternalStorageDirectory().absolutePath
    val (rootLabel, relative) = when {
        path == internalRoot || path.startsWith("$internalRoot/") -> "Internal" to path.removePrefix(internalRoot)
        path.startsWith("/storage/") -> {
            // /storage/XXXX-XXXX/... is a removable volume
            val volumeRoot = path.split('/').take(3).joinToString("/")
            "SD card" to path.removePrefix(volumeRoot)
        }
        else -> "Device" to path
    }
    val parent = relative.trim('/').substringBeforeLast('/', missingDelimiterValue = "")
    return when {
        relative.trim('/').isEmpty() -> "$rootLabel storage"
        parent.isEmpty() -> rootLabel
        else -> "$rootLabel › ${parent.substringAfterLast('/')}"
    }
}

@Composable
private fun QuickToolTile(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconColor.copy(alpha = 0.15f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
