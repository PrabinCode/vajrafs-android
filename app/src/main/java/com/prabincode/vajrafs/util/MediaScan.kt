/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.util

import android.content.Context
import android.media.MediaScannerConnection
import android.webkit.MimeTypeMap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Upper bound on files handed to the media scanner by a single .nomedia change
private const val MAX_RESCAN_FILES = 5000

/**
 * Re-indexes the media under [dir] after its `.nomedia` marker was added or removed, so gallery
 * apps hide or show those files right away instead of waiting for the next system-wide scan.
 * Older Android versions don't rescan a folder on their own when `.nomedia` changes.
 *
 * Runs on [Dispatchers.IO]; the scanner itself works asynchronously, so callers can fire and forget.
 */
suspend fun rescanFolderMedia(context: Context, dir: File) = withContext(Dispatchers.IO) {
    try {
        val mimeMap = MimeTypeMap.getSingleton()
        val paths = mutableListOf(dir.absolutePath)
        dir.walkTopDown().maxDepth(4)
            .filter { file ->
                file.isFile && mimeMap.getMimeTypeFromExtension(file.extension.lowercase())
                    ?.let { it.startsWith("image/") || it.startsWith("video/") || it.startsWith("audio/") } == true
            }
            .take(MAX_RESCAN_FILES)
            .forEach { paths.add(it.absolutePath) }
        MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
    } catch (e: Exception) {
        // Ignore scan notification errors
    }
}
