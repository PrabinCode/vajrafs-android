/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.ui.components

import android.os.Environment
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prabincode.vajrafs.data.model.StorageVolumeInfo

@Composable
fun BreadcrumbBar(
    currentPath: String,
    onNavigateToPath: (String) -> Unit,
    modifier: Modifier = Modifier,
    storageVolumes: List<StorageVolumeInfo> = emptyList(),
    primaryStoragePath: String = Environment.getExternalStorageDirectory().absolutePath
) {
    val scrollState = rememberScrollState()
    val segments = parsePathSegments(
        path = currentPath,
        storageVolumes = storageVolumes,
        primaryStoragePath = primaryStoragePath
    )

    LaunchedEffect(currentPath) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            IconButton(
                onClick = {
                    segments.firstOrNull()?.let { onNavigateToPath(it.path) }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Home",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            segments.forEachIndexed { index, segment ->
                if (index > 0) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                val isLast = index == segments.size - 1
                TextButton(
                    onClick = { onNavigateToPath(segment.path) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = segment.name,
                        fontSize = 13.sp,
                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                        color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

data class PathSegment(val name: String, val path: String)

/**
 * Splits [path] into navigable segments anchored at the matching storage volume root so
 * restricted Android mount points (`/storage`, `/storage/emulated`) are not exposed.
 */
internal fun parsePathSegments(
    path: String,
    storageVolumes: List<StorageVolumeInfo> = emptyList(),
    primaryStoragePath: String = "/storage/emulated/0"
): List<PathSegment> {
    if (path.isEmpty() || path == "/") {
        return listOf(PathSegment("System Root (/)", "/"))
    }

    val normalizedPrimary = primaryStoragePath.trimEnd('/')
    val matchedVolume = storageVolumes
        .filter { vol ->
            val root = vol.path.trimEnd('/')
            root.isNotEmpty() && root != "/" && (path == root || path.startsWith("$root/"))
        }
        .maxByOrNull { it.path.length }

    val anchor = when {
        matchedVolume != null -> PathSegment(matchedVolume.name, matchedVolume.path.trimEnd('/'))
        normalizedPrimary.isNotEmpty() &&
            (path == normalizedPrimary || path.startsWith("$normalizedPrimary/")) ->
            PathSegment("Internal Storage", normalizedPrimary)
        else -> null
    }

    if (anchor != null) {
        val relative = path.removePrefix(anchor.path).trimStart('/')
        val segments = mutableListOf(anchor)
        if (relative.isNotEmpty()) {
            var cumulative = anchor.path
            relative.split('/').filter { it.isNotEmpty() }.forEach { part ->
                cumulative = "$cumulative/$part"
                segments.add(PathSegment(part, cumulative))
            }
        }
        return segments
    }

    val parts = path.split('/').filter { it.isNotEmpty() }
    val segments = mutableListOf(PathSegment("Root", "/"))
    var cumulative = ""
    parts.forEach { part ->
        cumulative += "/$part"
        segments.add(PathSegment(part, cumulative))
    }
    return segments
}
