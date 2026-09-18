/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.ui.screens.viewer

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prabincode.imanage.util.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(
    filePath: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = remember(filePath) { File(filePath) }

    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var isLoaded by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }

    // Editor settings
    var isEditMode by remember { mutableStateOf(false) } // Default to read-only for safe viewing
    var isWordWrap by remember { mutableStateOf(true) }
    var showLineNumbers by remember { mutableStateOf(true) }

    // Search state
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchMatches = remember(searchQuery, textFieldValue.text) {
        if (searchQuery.isBlank()) emptyList()
        else {
            val matches = mutableListOf<Int>()
            var index = textFieldValue.text.indexOf(searchQuery, ignoreCase = true)
            while (index >= 0) {
                matches.add(index)
                index = textFieldValue.text.indexOf(searchQuery, index + searchQuery.length, ignoreCase = true)
            }
            matches
        }
    }
    var currentMatchIndex by remember { mutableIntStateOf(0) }

    // Scroll states
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    // File loading
    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                if (file.exists()) {
                    val raw = file.readText()
                    withContext(Dispatchers.Main) {
                        textFieldValue = TextFieldValue(raw)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    textFieldValue = TextFieldValue("Could not open file: ${e.message}")
                }
            }
            isLoaded = true
        }
    }

    // Line count
    val lineCount = remember(textFieldValue.text) {
        textFieldValue.text.count { it == '\n' } + 1
    }

    // Gutter text (1\n2\n3...)
    val lineNumbersString = remember(lineCount) {
        (1..lineCount).joinToString("\n")
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(file.name, fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 16.sp)
                            Text(
                                text = "${file.extension.uppercase().ifBlank { "TEXT" }} · $lineCount lines · ${formatFileSize(file.length())}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Search Button
                        IconButton(onClick = {
                            isSearchOpen = !isSearchOpen
                            if (!isSearchOpen) searchQuery = ""
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Search in file")
                        }

                        // Word Wrap Toggle
                        IconButton(onClick = { isWordWrap = !isWordWrap }) {
                            Icon(
                                imageVector = if (isWordWrap) Icons.Default.WrapText else Icons.Default.FormatAlignLeft,
                                contentDescription = if (isWordWrap) "Word Wrap (ON)" else "Word Wrap (OFF)",
                                tint = if (isWordWrap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Edit Mode Switch
                        IconButton(onClick = { isEditMode = !isEditMode }) {
                            Icon(
                                imageVector = if (isEditMode) Icons.Default.Edit else Icons.Default.Visibility,
                                contentDescription = if (isEditMode) "Edit Mode (Editable)" else "Read Mode (Protected)",
                                tint = if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Save Button (Active in Edit mode)
                        if (isEditMode) {
                            IconButton(
                                enabled = hasUnsavedChanges && !isSaving,
                                onClick = {
                                    scope.launch {
                                        isSaving = true
                                        withContext(Dispatchers.IO) {
                                            try {
                                                file.writeText(textFieldValue.text)
                                                withContext(Dispatchers.Main) {
                                                    hasUnsavedChanges = false
                                                    Toast.makeText(context, "Saved successfully", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Save error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                        isSaving = false
                                    }
                                }
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        Icons.Default.Save,
                                        contentDescription = "Save",
                                        tint = if (hasUnsavedChanges) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                )

                // Search Overlay Bar
                AnimatedVisibility(
                    visible = isSearchOpen,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    currentMatchIndex = 0
                                    if (searchMatches.isNotEmpty()) {
                                        val matchPos = searchMatches[0]
                                        textFieldValue = textFieldValue.copy(
                                            selection = TextRange(matchPos, matchPos + searchQuery.length)
                                        )
                                    }
                                },
                                placeholder = { Text("Find...", fontSize = 13.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                            )

                            if (searchMatches.isNotEmpty()) {
                                Text(
                                    text = "${currentMatchIndex + 1}/${searchMatches.size}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                // Prev match
                                IconButton(
                                    onClick = {
                                        if (currentMatchIndex > 0) {
                                            currentMatchIndex--
                                        } else {
                                            currentMatchIndex = searchMatches.size - 1
                                        }
                                        val matchPos = searchMatches[currentMatchIndex]
                                        textFieldValue = textFieldValue.copy(
                                            selection = TextRange(matchPos, matchPos + searchQuery.length)
                                        )
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match")
                                }

                                // Next match
                                IconButton(
                                    onClick = {
                                        if (currentMatchIndex < searchMatches.size - 1) {
                                            currentMatchIndex++
                                        } else {
                                            currentMatchIndex = 0
                                        }
                                        val matchPos = searchMatches[currentMatchIndex]
                                        textFieldValue = textFieldValue.copy(
                                            selection = TextRange(matchPos, matchPos + searchQuery.length)
                                        )
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match")
                                }
                            } else if (searchQuery.isNotBlank()) {
                                Text(
                                    text = "0 matches",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            IconButton(
                                onClick = {
                                    isSearchOpen = false
                                    searchQuery = ""
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close search")
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditMode) "✏️ Edit Mode" else "🔒 Read-Only (Tap icon to edit)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "${textFieldValue.text.length} characters",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) { padding ->
        if (!isLoaded) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(padding))
        } else {
            val textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(verticalScroll)
            ) {
                // Left Line Numbers Gutter
                if (showLineNumbers) {
                    Text(
                        text = lineNumbersString,
                        style = textStyle.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                            .padding(horizontal = 10.dp, vertical = 12.dp)
                    )
                }

                // Text Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (!isWordWrap) Modifier.horizontalScroll(horizontalScroll)
                            else Modifier
                        )
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = {
                            if (isEditMode) {
                                textFieldValue = it
                                hasUnsavedChanges = true
                            }
                        },
                        readOnly = !isEditMode,
                        textStyle = textStyle,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

