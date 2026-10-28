/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.PackageInfoCompat

/** Version info and browser links, shared by every distribution flavor. */
object AppLinks {

    /** Installed versionName and versionCode. */
    fun getCurrentVersion(context: Context): Pair<String, Int> {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = PackageInfoCompat.getLongVersionCode(pInfo).toInt()
            val name = pInfo.versionName ?: "1.0.0"
            Pair(name, code)
        } catch (e: Exception) {
            Pair("2.0.0", 10)
        }
    }

    fun openInBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    /** Open Google Play Store listing for updates, reviews, and ratings. */
    fun openPlayStore(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openInBrowser(context, "https://play.google.com/store/apps/details?id=${context.packageName}")
        }
    }

    /** Share VajraFS app recommendation with friends. */
    fun shareApp(context: Context) {
        try {
            val shareText = "VajraFS — Secure, private, 100% offline file explorer with encrypted vault for Android:\nhttps://play.google.com/store/apps/details?id=${context.packageName}"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "VajraFS - Secure File Explorer")
                putExtra(Intent.EXTRA_TEXT, shareText)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(intent, "Share VajraFS"))
        } catch (_: Exception) {}
    }
}
