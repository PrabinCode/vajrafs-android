/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prabincode.vajrafs.data.model.FileItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    item: FileItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    tagColorHex: String? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShredClick: () -> Unit,
    onVaultClick: () -> Unit,
    onZipClick: () -> Unit,
    onInfoClick: () -> Unit,
    onShareClick: () -> Unit,
    onOpenWithClick: () -> Unit = {},
    onTagClick: () -> Unit = {},
    onOpenFileLocation: (() -> Unit)? = null,
    isBookmarked: Boolean = false,
    onBookmarkClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            if (isSelectionMode) {
                IconButton(
                    onClick = onClick,
                    modifier = Modifier
                        .size(36.dp)
                        .padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = "Select",
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FileIconView(item = item, size = 42.dp)

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (tagColorHex != null) {
                        val color = try { Color(android.graphics.Color.parseColor(tagColorHex)) } catch (e: Exception) { Color.Red }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.formattedSize,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = " • ",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = item.formattedDate,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isSelectionMode) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = { menuExpanded = false; onRenameClick() }
                        )
                        DropdownMenuItem(
                            text = { Text("Assign Color Tag") },
                            onClick = { menuExpanded = false; onTagClick() }
                        )
                        if (item.isDirectory && onBookmarkClick != null) {
                            DropdownMenuItem(
                                text = { Text(if (isBookmarked) "Remove from Favourites" else "Add to Favourites") },
                                onClick = { menuExpanded = false; onBookmarkClick() }
                            )
                        }
                        onOpenFileLocation?.let { openLocation ->
                            DropdownMenuItem(
                                text = { Text("Open file location") },
                                onClick = { menuExpanded = false; openLocation() }
                            )
                        }
                        if (!item.isDirectory) {
                            DropdownMenuItem(
                                text = { Text("Open with...") },
                                onClick = { menuExpanded = false; onOpenWithClick() }
                            )
                            DropdownMenuItem(
                                text = { Text("Share") },
                                onClick = { menuExpanded = false; onShareClick() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Move to Encrypted Vault") },
                            onClick = { menuExpanded = false; onVaultClick() }
                        )
                        DropdownMenuItem(
                            text = { Text("Compress (ZIP)") },
                            onClick = { menuExpanded = false; onZipClick() }
                        )
                        DropdownMenuItem(
                            text = { Text("Details & Info") },
                            onClick = { menuExpanded = false; onInfoClick() }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Move to Trash") },
                            onClick = { menuExpanded = false; onDeleteClick() }
                        )
                        DropdownMenuItem(
                            text = { Text("Secure Shred (Permanent)", color = MaterialTheme.colorScheme.error) },
                            onClick = { menuExpanded = false; onShredClick() }
                        )
                    }
                }
            }
        }
    }
}
