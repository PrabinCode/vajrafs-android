/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.ui.screens.analyzer

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prabincode.imanage.data.crypto.ShredderEngine
import com.prabincode.imanage.data.model.FileItem
import com.prabincode.imanage.data.repository.DuplicateDetector
import com.prabincode.imanage.data.repository.DuplicateGroup
import com.prabincode.imanage.data.repository.DuplicateScanResult
import com.prabincode.imanage.data.repository.TrashRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateCleanerScreen(
    trashRepository: TrashRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<DuplicateScanResult?>(null) }
    var currentScannedFile by remember { mutableStateOf("") }
    var scannedCount by remember { mutableIntStateOf(0) }
    var selectedForDeletion by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun startScan() {
        scope.launch {
            isScanning = true
            selectedForDeletion = emptySet()
            val result = DuplicateDetector.findDuplicates { count, file ->
                scannedCount = count
                currentScannedFile = file
            }
            scanResult = result
            isScanning = false

            // Auto-select all duplicates except the first one in each group
            val autoSelected = mutableSetOf<String>()
            result.duplicateGroups.forEach { group ->
                group.files.drop(1).forEach { autoSelected.add(it.path) }
            }
            selectedForDeletion = autoSelected
        }
    }

    LaunchedEffect(Unit) {
        startScan()
    }

    fun deleteSelected() {
        scope.launch {
            val toDeletePaths = selectedForDeletion.toList()
            for (path in toDeletePaths) {
                val file = java.io.File(path)
                if (file.exists()) {
                    trashRepository.moveToTrash(file)
                }
            }
            Toast.makeText(context, "Moved ${toDeletePaths.size} duplicate files to Trash", Toast.LENGTH_SHORT).show()
            startScan()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Duplicate Cleaner", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isScanning) {
                        IconButton(onClick = { startScan() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Rescan")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!isScanning && scanResult != null && scanResult!!.duplicateGroups.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column {
                            Text(
                                text = "${selectedForDeletion.size} files selected",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val selectedBytes = scanResult!!.duplicateGroups.flatMap { it.files }
                                .filter { selectedForDeletion.contains(it.path) }
                                .sumOf { it.size }
                            Text(
                                text = "Reclaim ${FileItem.formatBytes(selectedBytes)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Button(
                            onClick = { deleteSelected() },
                            enabled = selectedForDeletion.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clean Duplicates")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isScanning) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Scanning for Duplicate Files...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Scanned $scannedCount files",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentScannedFile,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else if (scanResult != null && scanResult!!.duplicateGroups.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Duplicates Found!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your storage is clean and optimized.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            } else if (scanResult != null) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Summary Header Card
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "${scanResult!!.duplicateGroups.size} Duplicate Clusters Found",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Wasting ~${FileItem.formatBytes(scanResult!!.totalWastedBytes)} of disk space",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.AutoFixHigh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }

                    // Duplicate Groups
                    items(scanResult!!.duplicateGroups) { group ->
                        DuplicateGroupCard(
                            group = group,
                            selectedPaths = selectedForDeletion,
                            onTogglePath = { path ->
                                selectedForDeletion = if (selectedForDeletion.contains(path)) {
                                    selectedForDeletion - path
                                } else {
                                    selectedForDeletion + path
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    selectedPaths: Set<String>,
    onTogglePath: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = group.files.firstOrNull()?.name ?: "File",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${FileItem.formatBytes(group.fileSize)} each (${group.files.size} copies)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            group.files.forEachIndexed { index, fileItem ->
                val isSelected = selectedPaths.contains(fileItem.path)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onTogglePath(fileItem.path) }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (index == 0) "Original: ${fileItem.path}" else "Duplicate: ${fileItem.path}",
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (index == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = fileItem.formattedDate,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}
