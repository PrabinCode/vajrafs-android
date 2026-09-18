/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prabincode.imanage.data.model.FileType

data class QuickCategory(
    val title: String,
    val fileType: FileType,
    val icon: ImageVector,
    val color: Color
)

val defaultCategories = listOf(
    QuickCategory("Images", FileType.IMAGE, Icons.Default.Image, Color(0xFF42A5F5)),
    QuickCategory("Videos", FileType.VIDEO, Icons.Default.VideoFile, Color(0xFFEF5350)),
    QuickCategory("Audio", FileType.AUDIO, Icons.Default.AudioFile, Color(0xFFAB47BC)),
    QuickCategory("Documents", FileType.DOCUMENT, Icons.Default.Description, Color(0xFF26A69A)),
    QuickCategory("APKs", FileType.APK, Icons.Default.Android, Color(0xFF66BB6A)),
    QuickCategory("Archives", FileType.ARCHIVE, Icons.Default.FolderZip, Color(0xFFFF7043)),
    QuickCategory("Code", FileType.CODE, Icons.Default.Code, Color(0xFF5C6BC0)),
    QuickCategory("Encrypted", FileType.VAULT_ENCRYPTED, Icons.Default.Lock, Color(0xFFEC407A))
)

@Composable
fun CategoryGrid(
    onCategoryClick: (FileType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val rows = defaultCategories.chunked(4)
        rows.forEach { rowCategories ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                rowCategories.forEach { category ->
                    CategoryTile(
                        category = category,
                        onClick = { onCategoryClick(category.fileType) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(
    category: QuickCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(category.color.copy(alpha = 0.15f))
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = category.title,
                tint = category.color,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = category.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp
        )
    }
}
