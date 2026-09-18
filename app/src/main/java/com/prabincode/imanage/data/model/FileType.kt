/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class FileType(
    val displayName: String,
    val extensions: Set<String>,
    val color: Color
) {
    FOLDER("Folder", emptySet(), Color(0xFFFFB74D)),
    IMAGE("Images", setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "svg", "heic", "raw"), Color(0xFF42A5F5)),
    VIDEO("Videos", setOf("mp4", "mkv", "avi", "mov", "flv", "webm", "3gp", "ts"), Color(0xFFEF5350)),
    AUDIO("Audio", setOf("mp3", "wav", "flac", "m4a", "aac", "ogg", "wma", "opus"), Color(0xFFAB47BC)),
    DOCUMENT("Documents", setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub"), Color(0xFF26A69A)),
    ARCHIVE("Archives", setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso"), Color(0xFFFF7043)),
    APK("APKs", setOf("apk", "apks", "xapk"), Color(0xFF66BB6A)),
    CODE("Code", setOf("kt", "java", "py", "js", "html", "css", "json", "xml", "cpp", "c", "h", "cs", "dart", "ts", "md", "sh", "sql", "yaml", "yml"), Color(0xFF5C6BC0)),
    VAULT_ENCRYPTED("Encrypted", setOf("imanage_enc", "enc"), Color(0xFFEC407A)),
    OTHER("Other", emptySet(), Color(0xFF78909C));

    companion object {
        fun fromExtension(ext: String, isDir: Boolean): FileType {
            if (isDir) return FOLDER
            val cleanExt = ext.lowercase().trim()
            return entries.find { it != FOLDER && it != OTHER && it.extensions.contains(cleanExt) } ?: OTHER
        }
    }
}
