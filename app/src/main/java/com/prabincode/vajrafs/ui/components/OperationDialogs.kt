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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prabincode.vajrafs.data.model.FileItem
import com.prabincode.vajrafs.data.model.OperationProgress
import com.prabincode.vajrafs.util.fileNameError

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (folderName: String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Folder", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    errorText = null
                },
                label = { Text("Folder Name") },
                singleLine = true,
                isError = errorText != null,
                supportingText = errorText?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    errorText = fileNameError(text)
                    if (errorText == null) onConfirm(text.trim())
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateFileDialog(
    onDismiss: () -> Unit,
    onConfirm: (fileName: String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val quickExtensions = listOf(".txt", ".nomedia", ".md", ".json", ".log")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New File", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        errorText = null
                    },
                    label = { Text("File Name (e.g. notes.txt)") },
                    singleLine = true,
                    isError = errorText != null,
                    supportingText = errorText?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Quick Presets:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Wraps to a second line on narrow screens or large font sizes
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    quickExtensions.forEach { ext ->
                        val isNomedia = ext == ".nomedia"
                        val isSelected = if (isNomedia) text == ".nomedia" else text.endsWith(ext)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                isNomedia -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (isNomedia) {
                                        text = ".nomedia"
                                    } else {
                                        val base = if (text.isBlank() || text == ".nomedia") "untitled" else text.substringBeforeLast(".")
                                        text = "$base$ext"
                                    }
                                    errorText = null
                                }
                        ) {
                            Text(
                                text = ext,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected || isNomedia) FontWeight.Bold else FontWeight.Medium,
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                    isNomedia -> MaterialTheme.colorScheme.onTertiaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                if (text == ".nomedia") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tip: .nomedia hides photos & videos in this folder from gallery apps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    errorText = fileNameError(text)
                    if (errorText == null) onConfirm(text.trim())
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateActionBottomSheet(
    onDismiss: () -> Unit,
    onCreateFolderClick: () -> Unit,
    onCreateFileClick: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Create in this Folder",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            ListItem(
                headlineContent = { Text("New Folder", fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text("Create a new subdirectory", fontSize = 12.sp) },
                leadingContent = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onDismiss()
                        onCreateFolderClick()
                    }
            )

            ListItem(
                headlineContent = { Text("New File", fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text("Create a blank file (.txt, .md, .json, etc.)", fontSize = 12.sp) },
                leadingContent = {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NoteAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onDismiss()
                        onCreateFileClick()
                    }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit
) {
    var text by remember { mutableStateOf(currentName) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    errorText = null
                },
                label = { Text("New Name") },
                singleLine = true,
                isError = errorText != null,
                supportingText = errorText?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.trim() == currentName) {
                        onDismiss()
                    } else {
                        errorText = fileNameError(text)
                        if (errorText == null) onConfirm(text.trim())
                    }
                }
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DeleteConfirmationDialog(
    itemCount: Int,
    itemName: String,
    onDismiss: () -> Unit,
    onMoveToTrash: () -> Unit,
    onSecureShred: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = if (itemCount > 1) "Delete $itemCount items?" else "Delete '$itemName'?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Choose how you would like to remove these files:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "• Move to Trash: Soft delete. Can be restored anytime within 30 days.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• Secure Shred: multi-pass overwrite before deleting. Cannot be undone.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onMoveToTrash,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Move to Trash")
                }
                Button(
                    onClick = onSecureShred,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Shred")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun FileInfoDialog(
    item: FileItem,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var checksums by remember { mutableStateOf<com.prabincode.vajrafs.data.crypto.FileChecksums?>(null) }
    var isCalculatingChecksums by remember { mutableStateOf(false) }
    var hashInput by remember { mutableStateOf("") }

    LaunchedEffect(item.path) {
        if (!item.isDirectory && item.file.exists() && item.file.canRead()) {
            isCalculatingChecksums = true
            val res = com.prabincode.vajrafs.data.crypto.ChecksumHelper.calculateChecksums(item.file)
            checksums = res.getOrNull()
            isCalculatingChecksums = false
        }
    }

    val matchResult = remember(hashInput, checksums) {
        val trimmed = hashInput.trim().lowercase()
        if (trimmed.isEmpty() || checksums == null) {
            null
        } else {
            val cs = checksums!!
            when {
                trimmed == cs.sha256.lowercase() -> "SHA-256 Match"
                trimmed == cs.md5.lowercase() -> "MD5 Match"
                trimmed == cs.sha1.lowercase() -> "SHA-1 Match"
                trimmed == cs.crc32.lowercase() -> "CRC-32 Match"
                else -> "Mismatch"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("File Details", fontWeight = FontWeight.Bold) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                item { InfoRow(label = "Name", value = item.name) }
                item { InfoRow(label = "Path", value = item.path) }
                item { InfoRow(label = "Type", value = item.fileType.displayName) }
                item { InfoRow(label = "Size", value = "${item.formattedSize} (${item.size} bytes)") }
                item { InfoRow(label = "Last Modified", value = item.formattedDate) }
                item { InfoRow(label = "Permissions", value = "Read: ${if (item.file.canRead()) "✓" else "✗"} | Write: ${if (item.file.canWrite()) "✓" else "✗"} | Exec: ${if (item.file.canExecute()) "✓" else "✗"}") }

                if (!item.isDirectory) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "Cryptographic Checksums",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (isCalculatingChecksums) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Calculating MD5, SHA-1, SHA-256...", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else if (checksums != null) {
                        val cs = checksums!!
                        item {
                            ChecksumRow(
                                algorithm = "SHA-256",
                                hash = cs.sha256,
                                onCopy = {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(cs.sha256))
                                    android.widget.Toast.makeText(context, "SHA-256 copied", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        item {
                            ChecksumRow(
                                algorithm = "MD5",
                                hash = cs.md5,
                                onCopy = {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(cs.md5))
                                    android.widget.Toast.makeText(context, "MD5 copied", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        item {
                            ChecksumRow(
                                algorithm = "SHA-1",
                                hash = cs.sha1,
                                onCopy = {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(cs.sha1))
                                    android.widget.Toast.makeText(context, "SHA-1 copied", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        item {
                            ChecksumRow(
                                algorithm = "CRC-32",
                                hash = cs.crc32,
                                onCopy = {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(cs.crc32))
                                    android.widget.Toast.makeText(context, "CRC-32 copied", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = hashInput,
                                onValueChange = { hashInput = it },
                                label = { Text("Verify / Compare Hash") },
                                placeholder = { Text("Paste expected hash here") },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (matchResult != null) {
                                val isMatch = matchResult != "Mismatch"
                                Text(
                                    text = if (isMatch) "✅ $matchResult" else "❌ Checksum Mismatch",
                                    color = if (isMatch) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun ChecksumRow(
    algorithm: String,
    hash: String,
    onCopy: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(algorithm, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = hash,
                fontSize = 11.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy $algorithm",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun OperationProgressDialog(
    progress: OperationProgress,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Prevent dismiss while running */ },
        title = { Text("Processing ${progress.type.name}...", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = progress.currentFileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${(progress.progress * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${FileItem.formatBytes(progress.bytesProcessed)} / ${FileItem.formatBytes(progress.totalBytes)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    )
}

@Composable
fun PasswordZipDialog(
    defaultZipName: String,
    onDismiss: () -> Unit,
    onConfirm: (zipName: String, password: String?) -> Unit
) {
    var zipName by remember { mutableStateOf(defaultZipName) }
    var enablePassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compress to ZIP", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = zipName,
                    onValueChange = { zipName = it },
                    label = { Text("Archive Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = enablePassword,
                        onCheckedChange = { enablePassword = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Password Protect (Encryption)", style = MaterialTheme.typography.bodyMedium)
                }

                if (enablePassword) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Enter Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = if (zipName.endsWith(".zip", ignoreCase = true)) zipName else "$zipName.zip"
                    val finalPass = if (enablePassword && password.isNotEmpty()) password else null
                    onConfirm(finalName, finalPass)
                }
            ) {
                Text("Compress")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ExtractArchiveDialog(
    archiveName: String,
    onDismiss: () -> Unit,
    onConfirm: (password: String?) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var hasPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Extract $archiveName", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Extract contents into current directory?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = hasPassword,
                        onCheckedChange = { hasPassword = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Archive is Password Protected", style = MaterialTheme.typography.bodyMedium)
                }

                if (hasPassword) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Archive Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalPass = if (hasPassword && password.isNotEmpty()) password else null
                    onConfirm(finalPass)
                }
            ) {
                Text("Extract Here")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AssignTagDialog(
    fileName: String,
    currentTagHex: String?,
    onDismiss: () -> Unit,
    onSelectTag: (colorHex: String, label: String) -> Unit,
    onRemoveTag: () -> Unit
) {
    val tags = listOf(
        Pair("#EF4444", "Important (Red)"),
        Pair("#10B981", "Personal (Green)"),
        Pair("#3B82F6", "Work (Blue)"),
        Pair("#F97316", "Finance (Orange)"),
        Pair("#8B5CF6", "Archive (Purple)")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Color Tag", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Tag: $fileName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                tags.forEach { (hex, label) ->
                    val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Red }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (currentTagHex == hex) color.copy(alpha = 0.2f) else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            TextButton(
                                onClick = { onSelectTag(hex, label.substringBefore(" (")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(label, fontWeight = if (currentTagHex == hex) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (currentTagHex != null) {
                TextButton(
                    onClick = onRemoveTag,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove Tag")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
