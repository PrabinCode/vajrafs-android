/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.data.model

import java.io.File

data class StorageVolumeInfo(
    val name: String,
    val path: String,
    val totalBytes: Long,
    val freeBytes: Long,
    val isRemovable: Boolean = false,
    val isPrimary: Boolean = true
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedPercentage: Float get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()) else 0f
    
    val formattedTotal: String get() = FileItem.formatBytes(totalBytes)
    val formattedUsed: String get() = FileItem.formatBytes(usedBytes)
    val formattedFree: String get() = FileItem.formatBytes(freeBytes)
}

enum class SortBy {
    NAME,
    DATE,
    SIZE,
    TYPE
}

enum class SortOrder {
    ASCENDING,
    DESCENDING
}

data class SortOption(
    val sortBy: SortBy = SortBy.NAME,
    val order: SortOrder = SortOrder.ASCENDING,
    val foldersFirst: Boolean = true,
    val showHiddenFiles: Boolean = false
)

enum class ViewMode {
    LIST,
    GRID,
    COMPACT
}

enum class OperationType {
    COPY,
    CUT,
    DELETE,
    TRASH,
    RESTORE,
    SHRED,
    ZIP,
    UNZIP,
    ENCRYPT,
    DECRYPT,
    RENAME
}

data class OperationProgress(
    val type: OperationType,
    val isRunning: Boolean = false,
    val progress: Float = 0f, // 0.0 to 1.0
    val currentFileName: String = "",
    val currentItemIndex: Int = 0,
    val totalItems: Int = 0,
    val bytesProcessed: Long = 0L,
    val totalBytes: Long = 0L,
    val speedMBps: Double = 0.0,
    val errorMessage: String? = null,
    val isCompleted: Boolean = false
)
