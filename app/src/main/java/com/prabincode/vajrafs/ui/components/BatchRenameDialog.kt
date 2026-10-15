/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prabincode.vajrafs.data.model.FileItem

enum class BatchRenameMode(val label: String) {
    NUMBERING("Numbering"),
    FIND_REPLACE("Find & Replace"),
    PREFIX_SUFFIX("Prefix / Suffix"),
    CASE("Change Case")
}

@Composable
fun BatchRenameDialog(
    items: List<FileItem>,
    onDismiss: () -> Unit,
    onConfirm: (newNames: List<String>) -> Unit
) {
    var selectedMode by remember { mutableStateOf(BatchRenameMode.NUMBERING) }

    // Mode 1: Numbering
    var prefix by remember { mutableStateOf("Item_") }
    var startNumber by remember { mutableIntStateOf(1) }
    var digitPadding by remember { mutableIntStateOf(3) }

    // Mode 2: Find & Replace
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var ignoreCase by remember { mutableStateOf(true) }

    // Mode 3: Prefix/Suffix
    var prependText by remember { mutableStateOf("") }
    var appendText by remember { mutableStateOf("") }

    // Mode 4: Case
    var selectedCase by remember { mutableIntStateOf(0) } // 0: lower, 1: UPPER, 2: Title

    val previewNames = remember(
        selectedMode, prefix, startNumber, digitPadding,
        findText, replaceText, ignoreCase,
        prependText, appendText, selectedCase, items
    ) {
        items.mapIndexed { index, item ->
            val ext = if (item.extension.isNotEmpty()) ".${item.extension}" else ""
            val nameWithoutExt = item.file.nameWithoutExtension

            when (selectedMode) {
                BatchRenameMode.NUMBERING -> {
                    val num = startNumber + index
                    val formattedNum = num.toString().padStart(digitPadding, '0')
                    "$prefix$formattedNum$ext"
                }
                BatchRenameMode.FIND_REPLACE -> {
                    if (findText.isNotEmpty()) {
                        val newBase = if (ignoreCase) {
                            nameWithoutExt.replace(Regex(Regex.escape(findText), RegexOption.IGNORE_CASE), replaceText)
                        } else {
                            nameWithoutExt.replace(findText, replaceText)
                        }
                        "$newBase$ext"
                    } else {
                        item.name
                    }
                }
                BatchRenameMode.PREFIX_SUFFIX -> {
                    "$prependText$nameWithoutExt$appendText$ext"
                }
                BatchRenameMode.CASE -> {
                    val newBase = when (selectedCase) {
                        0 -> nameWithoutExt.lowercase()
                        1 -> nameWithoutExt.uppercase()
                        else -> nameWithoutExt.split(" ", "_", "-").joinToString(" ") { word ->
                            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                        }
                    }
                    val newExt = if (selectedCase == 1) ext.uppercase() else ext.lowercase()
                    "$newBase$newExt"
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Batch Rename (${items.size} items)", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                // Tab Mode Selector
                ScrollableTabRow(
                    selectedTabIndex = selectedMode.ordinal,
                    edgePadding = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BatchRenameMode.entries.forEach { mode ->
                        Tab(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            text = { Text(mode.label, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Options by Mode
                when (selectedMode) {
                    BatchRenameMode.NUMBERING -> {
                        OutlinedTextField(
                            value = prefix,
                            onValueChange = { prefix = it },
                            label = { Text("Prefix (e.g. Photo_)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = startNumber.toString(),
                                onValueChange = { startNumber = it.toIntOrNull() ?: 1 },
                                label = { Text("Start #") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = digitPadding.toString(),
                                onValueChange = { digitPadding = (it.toIntOrNull() ?: 1).coerceIn(1, 6) },
                                label = { Text("Digits (3 = 001)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    BatchRenameMode.FIND_REPLACE -> {
                        OutlinedTextField(
                            value = findText,
                            onValueChange = { findText = it },
                            label = { Text("Find Text") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = replaceText,
                            onValueChange = { replaceText = it },
                            label = { Text("Replace With") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(checked = ignoreCase, onCheckedChange = { ignoreCase = it })
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ignore Case", fontSize = 12.sp)
                        }
                    }
                    BatchRenameMode.PREFIX_SUFFIX -> {
                        OutlinedTextField(
                            value = prependText,
                            onValueChange = { prependText = it },
                            label = { Text("Prepend (Prefix)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = appendText,
                            onValueChange = { appendText = it },
                            label = { Text("Append (Suffix before extension)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    BatchRenameMode.CASE -> {
                        val cases = listOf("lowercase", "UPPERCASE", "Title Case")
                        Column {
                            cases.forEachIndexed { idx, label ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    RadioButton(
                                        selected = selectedCase == idx,
                                        onClick = { selectedCase = idx }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(label, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(6.dp))

                // Live Preview
                Text(
                    text = "Live Preview:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 130.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    val previewCount = items.size.coerceAtMost(5)
                    items((0 until previewCount).toList()) { idx ->
                        val oldName = items[idx].name
                        val newName = previewNames.getOrNull(idx) ?: oldName
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = oldName,
                                fontSize = 10.sp,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = newName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(previewNames) }
            ) {
                Text("Rename All")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
