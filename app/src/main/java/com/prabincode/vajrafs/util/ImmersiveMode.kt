/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Finds the host [Activity] from a given [Context], traversing through any [ContextWrapper] layers.
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Returns safe status bar [WindowInsets] for TopAppBar and screen headers.
 *
 * If [WindowInsets.statusBars] reports a valid top inset (> 0), it is returned.
 * If the platform reports 0 (such as when returning from transient immersive full-screen
 * mode on certain Android skins before system insets are re-dispatched), it falls back
 * to the physical status bar height retrieved from Android framework dimensions.
 *
 * This completely prevents TopAppBar and header icons from overlapping with the mobile
 * status bar (clock, battery, notification icons).
 */
@Composable
fun safeStatusBarInsets(): WindowInsets {
    val context = LocalContext.current
    val density = LocalDensity.current
    val systemStatusBars = WindowInsets.statusBars
    val topPx = systemStatusBars.getTop(density)

    if (topPx > 0) {
        return systemStatusBars
    }

    val fallbackTopPx = remember(context) {
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
    }

    return if (fallbackTopPx > 0) {
        systemStatusBars.union(WindowInsets(top = fallbackTopPx))
    } else {
        systemStatusBars
    }
}

/**
 * Composable that toggles immersive full-screen mode by hiding the system status bar
 * (battery, clock, notifications) and navigation bar.
 *
 * Reliably restores system bars and default inset behavior when leaving the screen
 * (via [DisposableEffect] onDispose) or when [enabled] changes to false.
 */
@Composable
fun ImmersiveMode(
    enabled: Boolean = true
) {
    val view = LocalView.current

    DisposableEffect(enabled, view) {
        val window = view.context.findActivity()?.window

        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)

            if (enabled) {
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                val decor = window.decorView
                decor.post { ViewCompat.requestApplyInsets(decor) }
                decor.postDelayed({ ViewCompat.requestApplyInsets(decor) }, 150)
            }
        }

        onDispose {
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                val decor = window.decorView
                decor.post { ViewCompat.requestApplyInsets(decor) }
                decor.postDelayed({ ViewCompat.requestApplyInsets(decor) }, 150)
            }
        }
    }
}
