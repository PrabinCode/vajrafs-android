/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prabincode.vajrafs.data.local.entity.BookmarkEntity
import com.prabincode.vajrafs.data.model.FileType
import com.prabincode.vajrafs.data.model.StorageVolumeInfo

@Composable
fun AppDrawerContent(
    storageVolumes: List<StorageVolumeInfo>,
    bookmarks: List<BookmarkEntity>,
    onNavigateToPath: (path: String, title: String) -> Unit,
    onNavigateToCategory: (FileType) -> Unit,
    onNavigateToVault: () -> Unit,
    onNavigateToAnalyzer: () -> Unit,
    onNavigateToTrash: () -> Unit,
    onNavigateToWifiShare: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onCloseDrawer: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(310.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            // Header
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "VajraFS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "by PrabinCode",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "OFFLINE",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Storage Section
            item {
                DrawerSectionHeader(title = "Storage Disks")
            }

            items(storageVolumes) { volume ->
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            imageVector = if (volume.isPrimary) Icons.Default.Smartphone else if (volume.isRemovable) Icons.Default.SdCard else Icons.Default.Computer,
                            contentDescription = volume.name
                        )
                    },
                    label = {
                        Column {
                            Text(volume.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                "${volume.formattedFree} free of ${volume.formattedTotal}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    selected = false,
                    onClick = {
                        onCloseDrawer()
                        onNavigateToPath(volume.path, volume.name)
                    },
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            // Bookmarks / Favorites Section
            if (bookmarks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    DrawerSectionHeader(title = "Favourites")
                }

                items(bookmarks, key = { it.path }) { bookmark ->
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B)
                            )
                        },
                        label = {
                            Text(bookmark.title, fontWeight = FontWeight.Medium, maxLines = 1)
                        },
                        badge = {
                            IconButton(
                                onClick = { onRemoveBookmark(bookmark.path) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove from favourites",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        selected = false,
                        onClick = {
                            onCloseDrawer()
                            onNavigateToPath(bookmark.path, bookmark.title)
                        },
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }

            // Security & Tools Hub
            item {
                Spacer(modifier = Modifier.height(6.dp))
                DrawerSectionHeader(title = "Security & Tools")
            }

            item {
                DrawerToolItem(
                    title = "Safe Vault (AES-256)",
                    icon = Icons.Default.Lock,
                    iconColor = Color(0xFFEC407A),
                    onClick = {
                        onCloseDrawer()
                        onNavigateToVault()
                    }
                )
                DrawerToolItem(
                    title = "Storage Analyzer",
                    icon = Icons.Default.PieChart,
                    iconColor = Color(0xFF42A5F5),
                    onClick = {
                        onCloseDrawer()
                        onNavigateToAnalyzer()
                    }
                )
                DrawerToolItem(
                    title = "Recycle Trash Bin",
                    icon = Icons.Outlined.Delete,
                    iconColor = Color(0xFFFFA726),
                    onClick = {
                        onCloseDrawer()
                        onNavigateToTrash()
                    }
                )
                DrawerToolItem(
                    title = "PC Wi-Fi Transfer",
                    icon = Icons.Default.Wifi,
                    iconColor = Color(0xFF006874),
                    onClick = {
                        onCloseDrawer()
                        onNavigateToWifiShare()
                    }
                )
            }

            // Categories Section
            item {
                Spacer(modifier = Modifier.height(6.dp))
                DrawerSectionHeader(title = "Categories")
            }

            items(defaultCategories) { cat ->
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            imageVector = cat.icon,
                            contentDescription = cat.title,
                            tint = cat.color
                        )
                    },
                    label = { Text(cat.title) },
                    selected = false,
                    onClick = {
                        onCloseDrawer()
                        if (cat.fileType == FileType.VAULT_ENCRYPTED) {
                            onNavigateToVault()
                        } else {
                            onNavigateToCategory(cat.fileType)
                        }
                    },
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }

            // Settings Section
            item {
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings & Master Lock") },
                    selected = false,
                    onClick = {
                        onCloseDrawer()
                        onNavigateToSettings()
                    },
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun DrawerSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun DrawerToolItem(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = title, tint = iconColor) },
        label = { Text(title, fontWeight = FontWeight.Medium) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}
