/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.ui.screens.viewer

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.prabincode.imanage.util.formatFileSize
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExifData(
    val make: String?,
    val model: String?,
    val exposureTime: String?,
    val fNumber: String?,
    val iso: String?,
    val focalLength: String?,
    val width: Int,
    val height: Int,
    val dateTime: String?,
    val flash: Boolean?,
    val latLong: Pair<Double, Double>?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    filePath: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val initialFile = remember(filePath) { File(filePath) }

    val imageExtensions = remember { setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif") }

    val imageFiles = remember(filePath) {
        val parent = initialFile.parentFile
        val list = parent?.listFiles()?.filter { f ->
            f.isFile && imageExtensions.contains(f.extension.lowercase())
        }?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }) ?: listOf(initialFile)
        if (list.isNotEmpty()) list else listOf(initialFile)
    }

    val initialIndex = remember(imageFiles, initialFile) {
        val idx = imageFiles.indexOfFirst { it.absolutePath == initialFile.absolutePath }
        if (idx >= 0) idx else 0
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { imageFiles.size }
    )

    val currentFile = imageFiles.getOrNull(pagerState.currentPage) ?: initialFile

    // UI state
    var showControls by remember { mutableStateOf(true) }
    var showExifSheet by remember { mutableStateOf(false) }

    // Rotation state per page index
    val rotationAngles = remember { mutableStateMapOf<Int, Float>() }
    val currentRotation = rotationAngles[pagerState.currentPage] ?: 0f

    // EXIF data extraction for current image
    val exifData = remember(currentFile) {
        try {
            val exif = ExifInterface(currentFile.absolutePath)
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)
            val exposure = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let {
                val sec = it.toDoubleOrNull()
                if (sec != null && sec > 0) {
                    if (sec < 1.0) "1/${(1.0 / sec).toInt()}s" else "${sec}s"
                } else it
            }
            val fNum = exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { "f/$it" }
            val iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                ?: exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
            val focal = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let {
                val f = it.toDoubleOrNull()
                if (f != null) "${f.toInt()}mm" else it
            }
            val w = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
            val h = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
            val dt = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            val flashVal = exif.getAttributeInt(ExifInterface.TAG_FLASH, -1)
            val flash = if (flashVal >= 0) (flashVal and 1) != 0 else null
            val latLongArr = exif.latLong
            val latLong = if (latLongArr != null && latLongArr.size >= 2) Pair(latLongArr[0], latLongArr[1]) else null

            ExifData(make, model, exposure, fNum, iso, focal, w, h, dt, flash, latLong)
        } catch (e: Exception) {
            null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Image Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val fileForPage = imageFiles[page]
            val rot = rotationAngles[page] ?: 0f

            ZoomableImage(
                file = fileForPage,
                rotation = rot,
                onToggleControls = { showControls = !showControls }
            )
        }

        // Top Bar Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(
                        text = currentFile.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1
                    )
                    Text(
                        text = "${pagerState.currentPage + 1} of ${imageFiles.size} · ${formatFileSize(currentFile.length())}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }

                // 90° Clockwise Rotation Button
                IconButton(onClick = {
                    val nextRot = (currentRotation + 90f) % 360f
                    rotationAngles[pagerState.currentPage] = nextRot
                }) {
                    Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90°", tint = Color.White)
                }

                // EXIF Info Button
                IconButton(onClick = { showExifSheet = true }) {
                    Icon(Icons.Default.Info, contentDescription = "EXIF Details", tint = Color.White)
                }

                // Share Button
                IconButton(onClick = {
                    try {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            currentFile
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
                    } catch (e: Exception) { }
                }) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White)
                }
            }
        }
    }

    // EXIF Metadata Modal Bottom Sheet
    if (showExifSheet) {
        ModalBottomSheet(
            onDismissRequest = { showExifSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Image & Camera Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // File attributes
                ExifRow("File Name", currentFile.name)
                ExifRow("File Size", formatFileSize(currentFile.length()))
                val lastModStr = remember(currentFile) {
                    SimpleDateFormat("MMM dd, yyyy · HH:mm:ss", Locale.getDefault()).format(Date(currentFile.lastModified()))
                }
                ExifRow("Modified Date", lastModStr)

                // EXIF details
                exifData?.let { data ->
                    if (data.width > 0 && data.height > 0) {
                        val mp = (data.width.toLong() * data.height.toLong()) / 1_000_000.0
                        ExifRow("Resolution", "${data.width} × ${data.height} px (${String.format(Locale.US, "%.1f", mp)} MP)")
                    }
                    if (!data.model.isNullOrBlank()) {
                        ExifRow("Device", "${data.make ?: ""} ${data.model}".trim())
                    }
                    if (!data.exposureTime.isNullOrBlank()) {
                        ExifRow("Shutter Speed", data.exposureTime)
                    }
                    if (!data.fNumber.isNullOrBlank()) {
                        ExifRow("Aperture", data.fNumber)
                    }
                    if (!data.iso.isNullOrBlank()) {
                        ExifRow("ISO Sensitivity", "ISO ${data.iso}")
                    }
                    if (!data.focalLength.isNullOrBlank()) {
                        ExifRow("Focal Length", data.focalLength)
                    }
                    if (data.flash != null) {
                        ExifRow("Flash", if (data.flash) "Fired" else "Did not fire")
                    }
                    if (!data.dateTime.isNullOrBlank()) {
                        ExifRow("Date Captured", data.dateTime)
                    }
                    if (data.latLong != null) {
                        ExifRow("GPS Coordinates", "${String.format(Locale.US, "%.4f", data.latLong.first)}, ${String.format(Locale.US, "%.4f", data.latLong.second)}")
                    }
                }

                ExifRow("Local Path", currentFile.absolutePath)
            }
        }
    }
}

@Composable
private fun ExifRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun ZoomableImage(
    file: File,
    rotation: Float,
    onToggleControls: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val animatedRotation by animateFloatAsState(
        targetValue = rotation,
        animationSpec = tween(durationMillis = 250),
        label = "rotation"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(file)
                .crossfade(true)
                .build(),
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                    rotationZ = animatedRotation
                )
        )
    }
}
