/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    
    object Explorer : Screen("explorer?path={path}&title={title}&category={category}&highlight={highlight}") {
        fun createRoute(
            path: String? = null,
            title: String? = null,
            category: String? = null,
            highlight: String? = null
        ): String {
            val encodedPath = path?.let { Uri.encode(it) } ?: ""
            val encodedTitle = title?.let { Uri.encode(it) } ?: ""
            val encodedCategory = category?.let { Uri.encode(it) } ?: ""
            val encodedHighlight = highlight?.let { Uri.encode(it) } ?: ""
            return "explorer?path=$encodedPath&title=$encodedTitle&category=$encodedCategory&highlight=$encodedHighlight"
        }
    }

    object Vault : Screen("vault")
    object Analyzer : Screen("analyzer")
    object Trash : Screen("trash")
    object Search : Screen("search")
    object Settings : Screen("settings")
    object WifiShare : Screen("wifi_share")
    object PinSetup : Screen("pin_setup")

    object TextEditor : Screen("text_editor?path={path}") {
        fun createRoute(path: String): String = "text_editor?path=${Uri.encode(path)}"
    }

    object PdfViewer : Screen("pdf_viewer?path={path}") {
        fun createRoute(path: String): String = "pdf_viewer?path=${Uri.encode(path)}"
    }

    object ImageViewer : Screen("image_viewer?path={path}") {
        fun createRoute(path: String): String = "image_viewer?path=${Uri.encode(path)}"
    }

    object VideoPlayer : Screen("video_player?path={path}") {
        fun createRoute(path: String): String = "video_player?path=${Uri.encode(path)}"
    }

    object DuplicateCleaner : Screen("duplicate_cleaner")
}
