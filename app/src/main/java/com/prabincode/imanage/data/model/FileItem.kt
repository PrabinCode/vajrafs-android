/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.data.model

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileItem(
    val file: File,
    val name: String = file.name,
    val path: String = file.absolutePath,
    val size: Long = if (file.isDirectory) 0L else file.length(),
    val lastModified: Long = file.lastModified(),
    val isDirectory: Boolean = file.isDirectory,
    val isHidden: Boolean = file.isHidden || file.name.startsWith("."),
    val extension: String = file.extension,
    val fileType: FileType = FileType.fromExtension(file.extension, file.isDirectory),
    val childCount: Int = if (file.isDirectory) (file.list()?.size ?: 0) else 0,
    val isSelected: Boolean = false
) {
    val formattedSize: String
        get() = if (isDirectory) {
            "$childCount items"
        } else {
            formatBytes(size)
        }

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("MMM dd, yyyy  hh:mm a", Locale.getDefault())
            return sdf.format(Date(lastModified))
        }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
            val cleanGroup = digitGroups.coerceIn(0, units.size - 1)
            return String.format(
                Locale.US,
                "%.1f %s",
                bytes / Math.pow(1024.0, cleanGroup.toDouble()),
                units[cleanGroup]
            )
        }

        fun fromFile(file: File): FileItem {
            return FileItem(file = file)
        }
    }
}
