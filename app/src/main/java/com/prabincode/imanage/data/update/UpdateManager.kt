/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.data.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val minSupportedVersion: Int,
    val releaseDate: String,
    val downloadUrl: String,
    val fallbackUrl: String,
    val releaseNotes: List<String>,
    val isMandatory: Boolean,
    val isUpdateAvailable: Boolean
)

object UpdateManager {

    private const val UPDATE_URL = "https://pcshrestha.com.np/imanage/version.json"
    private const val GITHUB_RELEASES_URL = "https://github.com/PrabinCode/imanage-android/releases/latest"

    fun getCurrentVersion(context: Context): Pair<String, Int> {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = PackageInfoCompat.getLongVersionCode(pInfo).toInt()
            val name = pInfo.versionName ?: "1.0.0"
            Pair(name, code)
        } catch (e: Exception) {
            Pair("1.4.0", 5)
        }
    }

    suspend fun checkForUpdates(context: Context): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val (_, currentCode) = getCurrentVersion(context)
            val url = URL(UPDATE_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "PrabinCode-iManage-Android-App")
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("HTTP $responseCode from update server"))
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.readText()
            reader.close()
            connection.disconnect()

            val json = JSONObject(response)
            val remoteCode = json.optInt("versionCode", 0)
            val remoteName = json.optString("versionName", "Unknown")
            val minVersion = json.optInt("minSupportedVersion", 1)
            val releaseDate = json.optString("releaseDate", "")
            val downloadUrl = json.optString("downloadUrl", GITHUB_RELEASES_URL)
            val fallbackUrl = json.optString("fallbackUrl", "https://pcshrestha.com.np/imanage")
            val isMandatory = json.optBoolean("isMandatory", false)

            val releaseNotes = mutableListOf<String>()
            val notesArray = json.optJSONArray("releaseNotes")
            if (notesArray != null) {
                for (i in 0 until notesArray.length()) {
                    releaseNotes.add(notesArray.optString(i))
                }
            }

            val isAvailable = remoteCode > currentCode

            Result.success(
                AppUpdateInfo(
                    versionCode = remoteCode,
                    versionName = remoteName,
                    minSupportedVersion = minVersion,
                    releaseDate = releaseDate,
                    downloadUrl = downloadUrl,
                    fallbackUrl = fallbackUrl,
                    releaseNotes = releaseNotes,
                    isMandatory = isMandatory,
                    isUpdateAvailable = isAvailable
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun startDownload(context: Context, updateInfo: AppUpdateInfo) {
        try {
            if (updateInfo.downloadUrl.endsWith(".apk", ignoreCase = true)) {
                val request = DownloadManager.Request(Uri.parse(updateInfo.downloadUrl))
                    .setTitle("Downloading I Manage v${updateInfo.versionName}")
                    .setDescription("Downloading latest release APK...")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        "imanage-v${updateInfo.versionName}.apk"
                    )
                    .setMimeType("application/vnd.android.package-archive")

                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                manager?.enqueue(request)
            } else {
                openInBrowser(context, updateInfo.downloadUrl.ifBlank { updateInfo.fallbackUrl })
            }
        } catch (e: Exception) {
            // Fallback to opening in browser
            openInBrowser(context, updateInfo.fallbackUrl.ifBlank { GITHUB_RELEASES_URL })
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
}
