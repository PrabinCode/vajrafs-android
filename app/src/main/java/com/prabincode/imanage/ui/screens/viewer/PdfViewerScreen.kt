/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.ui.screens.viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

// Invert colors ColorMatrix: R' = 255 - R, G' = 255 - G, B' = 255 - B
private val INVERT_COLOR_MATRIX = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    filePath: String,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val file = remember(filePath) { File(filePath) }
    var pageCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Reading preferences
    var isNightMode by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // PdfRenderer & Thread synchronization
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    val renderMutex = remember { Mutex() }

    // LRU Cache for rendered bitmaps (up to 16 pages cached in memory)
    val pageCache = remember {
        object : LruCache<Int, Bitmap>(16) {
            override fun sizeOf(key: Int, value: Bitmap): Int = 1
        }
    }

    // Scroll state
    val listState = rememberLazyListState()

    // Initialize PdfRenderer
    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                if (!file.exists()) {
                    errorMessage = "File not found: ${file.name}"
                    isLoading = false
                    return@withContext
                }

                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                fileDescriptor = pfd
                pdfRenderer = renderer
                pageCount = renderer.pageCount
            } catch (e: Exception) {
                errorMessage = "Failed to load PDF: ${e.message}"
            }
            isLoading = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                pdfRenderer?.close()
                fileDescriptor?.close()
            } catch (e: Exception) { }
        }
    }

    // Function to render a single page safely
    suspend fun loadPageBitmap(index: Int): Bitmap? = withContext(Dispatchers.IO) {
        val cached = pageCache.get(index)
        if (cached != null && !cached.isRecycled) return@withContext cached

        val renderer = pdfRenderer ?: return@withContext null
        if (index < 0 || index >= pageCount) return@withContext null

        renderMutex.withLock {
            try {
                val page = renderer.openPage(index)
                val density = 2 // 2x density for crisp font rendering
                val maxDim = 2048
                val rawW = page.width * density
                val rawH = page.height * density
                val scaleFactor = if (rawW > maxDim || rawH > maxDim) {
                    val maxRaw = maxOf(rawW, rawH)
                    maxDim.toFloat() / maxRaw
                } else 1.0f
                val width = (rawW * scaleFactor).toInt().coerceAtLeast(1)
                val height = (rawH * scaleFactor).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                pageCache.put(index, bmp)
                bmp
            } catch (e: Exception) {
                null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.name, fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 16.sp)
                        if (pageCount > 0) {
                            Text(
                                text = "Page ${listState.firstVisibleItemIndex + 1} of $pageCount",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Night Mode Toggle
                    IconButton(onClick = { isNightMode = !isNightMode }) {
                        Icon(
                            imageVector = if (isNightMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (isNightMode) "Normal Mode" else "Night Mode",
                            tint = if (isNightMode) Color(0xFFFFD54F) else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Jump to Page Button
                    if (pageCount > 1) {
                        IconButton(onClick = { showJumpDialog = true }) {
                            Icon(Icons.Default.FindInPage, contentDescription = "Jump to Page")
                        }
                    }

                    // Zoom In
                    IconButton(onClick = { scale = (scale + 0.25f).coerceAtMost(3.0f) }) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
                    }

                    // Zoom Out
                    if (scale > 1f) {
                        IconButton(onClick = {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        }) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "Reset Zoom")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isNightMode) Color(0xFF121212) else MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (pageCount > 0) {
                Surface(
                    color = if (isNightMode) Color(0xFF1A1A1A) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showJumpDialog = true }
                            .padding(vertical = 10.dp, horizontal = 16.dp)
                    ) {
                        Text(
                            text = "Page ${listState.firstVisibleItemIndex + 1} of $pageCount (Tap to jump)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isNightMode) Color(0xFFE0E0E0) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        containerColor = if (isNightMode) Color(0xFF0D0D0D) else Color(0xFF262626)
    ) { padding ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 3.5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White)
            } else if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = Color.Red,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                // Continuous Vertical Page List
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(
                        count = pageCount,
                        key = { it }
                    ) { pageIndex ->
                        PdfPageItem(
                            pageIndex = pageIndex,
                            isNightMode = isNightMode,
                            onLoadBitmap = { loadPageBitmap(pageIndex) }
                        )
                    }
                }
            }
        }
    }

    // Jump to Page Dialog
    if (showJumpDialog) {
        var targetPageInput by remember { mutableStateOf("${listState.firstVisibleItemIndex + 1}") }
        var sliderValue by remember { mutableFloatStateOf((listState.firstVisibleItemIndex + 1).toFloat()) }

        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = {
                Text("Jump to Page", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Page ${sliderValue.toInt()} of $pageCount",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            sliderValue = it
                            targetPageInput = it.toInt().toString()
                        },
                        valueRange = 1f..pageCount.toFloat(),
                        steps = (pageCount - 2).coerceAtLeast(0)
                    )

                    OutlinedTextField(
                        value = targetPageInput,
                        onValueChange = { text ->
                            targetPageInput = text.filter { it.isDigit() }
                            targetPageInput.toIntOrNull()?.let {
                                if (it in 1..pageCount) {
                                    sliderValue = it.toFloat()
                                }
                            }
                        },
                        label = { Text("Page Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val targetPage = (targetPageInput.toIntOrNull() ?: sliderValue.toInt()).coerceIn(1, pageCount)
                    scope.launch {
                        listState.scrollToItem(targetPage - 1)
                    }
                    showJumpDialog = false
                }) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PdfPageItem(
    pageIndex: Int,
    isNightMode: Boolean,
    onLoadBitmap: suspend () -> Bitmap?
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPage by remember { mutableStateOf(true) }

    LaunchedEffect(pageIndex) {
        isLoadingPage = true
        bitmap = onLoadBitmap()
        isLoadingPage = false
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isNightMode) Color(0xFF1E1E1E) else Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                colorFilter = if (isNightMode) ColorFilter.colorMatrix(INVERT_COLOR_MATRIX) else null,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoadingPage) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Text("Page ${pageIndex + 1}", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}

