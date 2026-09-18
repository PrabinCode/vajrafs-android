/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.ui.components

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
import java.io.File

@Composable
fun BreadcrumbBar(
    currentPath: String,
    onNavigateToPath: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val segments = parsePathSegments(currentPath)

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

private fun parsePathSegments(path: String): List<PathSegment> {
    if (path.isEmpty() || path == "/") return listOf(PathSegment("Internal Storage", "/"))
    val parts = path.split("/").filter { it.isNotEmpty() }
    val segments = mutableListOf<PathSegment>()

    var cumulativePath = ""
    parts.forEachIndexed { index, part ->
        cumulativePath += "/$part"
        val displayName = if (index == 0 && part == "storage") {
            "Storage"
        } else if (part == "emulated") {
            "Internal"
        } else if (part == "0") {
            "Main Storage"
        } else {
            part
        }
        segments.add(PathSegment(displayName, cumulativePath))
    }
    return segments
}
