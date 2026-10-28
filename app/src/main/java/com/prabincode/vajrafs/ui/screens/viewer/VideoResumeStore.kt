/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.ui.screens.viewer

import android.content.Context
import java.io.File

/**
 * Remembers where each video was left off, so reopening it continues from there.
 *
 * Entries are keyed by absolute path and tied to the file size, so a different file saved under
 * the same name starts from the beginning. Only the most recent [MAX_ENTRIES] videos are kept.
 */
internal class VideoResumeStore(context: Context) {

    private val prefs = context.getSharedPreferences("vajrafs_video_resume", Context.MODE_PRIVATE)

    /** Saved position in ms, or null if there is nothing worth resuming. */
    fun get(file: File): Long? {
        val parts = prefs.getString(file.absolutePath, null)?.split('|') ?: return null
        val position = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val size = parts.getOrNull(1)?.toLongOrNull() ?: return null
        return if (size == file.length()) position else null
    }

    fun save(file: File, positionMs: Long, durationMs: Long) {
        // Short clips aren't worth resuming; near the start or end means "start fresh" next time
        if (durationMs < MIN_DURATION_MS || positionMs < MIN_POSITION_MS || positionMs > durationMs - END_MARGIN_MS) {
            prefs.edit().remove(file.absolutePath).apply()
            return
        }
        prefs.edit().putString(file.absolutePath, "$positionMs|${file.length()}|${System.currentTimeMillis()}").apply()
        prune()
    }

    private fun prune() {
        val all = prefs.all
        if (all.size <= MAX_ENTRIES) return
        val oldestFirst = all.entries.sortedBy { (it.value as? String)?.split('|')?.getOrNull(2)?.toLongOrNull() ?: 0L }
        prefs.edit().apply {
            oldestFirst.take(all.size - MAX_ENTRIES).forEach { remove(it.key) }
        }.apply()
    }

    private companion object {
        const val MAX_ENTRIES = 200
        const val MIN_DURATION_MS = 2 * 60_000L
        const val MIN_POSITION_MS = 10_000L
        const val END_MARGIN_MS = 15_000L
    }
}
