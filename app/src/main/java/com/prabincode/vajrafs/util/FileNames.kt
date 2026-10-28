/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.util

// Path separators plus the characters FAT/exFAT SD cards and Windows PCs (Wi-Fi transfer) reject
private val FORBIDDEN_NAME_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

// Linux filesystems cap a single name at 255 bytes, not characters
private const val MAX_NAME_BYTES = 255

/**
 * Returns a user-facing reason why [name] can't be used as a file or folder name,
 * or null if it is valid. Leading/trailing whitespace is ignored, matching how callers trim input.
 */
fun fileNameError(name: String): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> "Name can't be empty"
        trimmed == "." || trimmed == ".." -> "\"$trimmed\" is a reserved name"
        trimmed.any { it < ' ' || it == '\u007F' } -> "Name can't contain control characters"
        trimmed.any { it in FORBIDDEN_NAME_CHARS } -> "Name can't contain / \\ : * ? \" < > |"
        trimmed.toByteArray(Charsets.UTF_8).size > MAX_NAME_BYTES -> "Name is too long"
        else -> null
    }
}
