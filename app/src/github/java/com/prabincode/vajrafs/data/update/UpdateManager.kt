/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.data.update

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.prabincode.vajrafs.util.AppLinks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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

sealed class UpdateDownloadState {
    data object Idle : UpdateDownloadState()
    data class Downloading(val versionName: String, val progressPercent: Int?) : UpdateDownloadState()
    data class ReadyToInstall(val versionName: String, val apkFile: File) : UpdateDownloadState()
    data class PermissionRequired(val versionName: String, val apkFile: File) : UpdateDownloadState()
    data class Failed(val versionName: String, val message: String, val fallbackUrl: String) : UpdateDownloadState()
}

/**
 * GitHub-distributed builds only: checks GitHub Releases (and version.json) and downloads/installs
 * newer APKs. Never compiled into the Play build.
 */
object UpdateManager {

    private const val GITHUB_OWNER = "PrabinCode"
    private const val GITHUB_REPO = "vajrafs-android"
    private const val GITHUB_LATEST_API_URL =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    private const val GITHUB_RELEASES_URL =
        "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    private const val VERSION_JSON_URL = "https://pcshrestha.com.np/vajrafs/version.json"
    private const val WEBSITE_URL = "https://pcshrestha.com.np/vajrafs"
    private const val USER_AGENT = "PrabinCode-VajraFS-Android-App"
    private const val MAX_REDIRECTS = 8
    private const val DOWNLOAD_MANAGER_PENDING_TIMEOUT_MS = 12_000L

    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    @Volatile
    private var activeDownloadJob: Job? = null

    @Volatile
    private var activeDownloadManagerId: Long? = null

    suspend fun checkForUpdates(context: Context): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        val (currentName, currentCode) = AppLinks.getCurrentVersion(context)
        val isDebugBuild = context.packageName.endsWith(".debug")

        val githubDeferred = async {
            fetchLatestFromGitHub(currentName, currentCode, isDebugBuild)
        }
        val siteDeferred = updateScope.async {
            fetchFromVersionJson(currentName, currentCode)
        }

        val githubResult = githubDeferred.await()
        val siteResult = if (githubResult.isSuccess) {
            withTimeoutOrNull(1500L) { siteDeferred.await() }
        } else {
            siteDeferred.await()
        }

        val ghInfo = githubResult.getOrNull()
        val siteInfo = siteResult?.getOrNull()

        if (ghInfo == null && siteInfo == null) {
            val error = githubResult.exceptionOrNull()
                ?: siteResult?.exceptionOrNull()
                ?: Exception("Could not reach update server")
            return@withContext Result.failure(error)
        }

        Result.success(selectPreferredUpdateInfo(ghInfo, siteInfo, currentName, currentCode))
    }

    fun startDownload(context: Context, updateInfo: AppUpdateInfo) {
        val url = updateInfo.downloadUrl.trim()
        if (!url.endsWith(".apk", ignoreCase = true)) {
            AppLinks.openInBrowser(context, url.ifBlank { updateInfo.fallbackUrl.ifBlank { GITHUB_RELEASES_URL } })
            return
        }

        val current = _downloadState.value
        if (current is UpdateDownloadState.Downloading && current.versionName == updateInfo.versionName) {
            return
        }

        val appContext = context.applicationContext
        cancelInternal(appContext)

        val safeVersion = updateInfo.versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val fileName = "vajrafs-v$safeVersion.apk"
        _downloadState.value = UpdateDownloadState.Downloading(updateInfo.versionName, null)

        lateinit var launchedJob: Job
        launchedJob = updateScope.launch {
            try {
                cleanOldUpdateApks(appContext)

                val downloadedViaDm = tryDownloadWithDownloadManager(
                    appContext = appContext,
                    url = url,
                    versionName = updateInfo.versionName,
                    fileName = fileName
                )
                val apkFile = downloadedViaDm ?: downloadDirect(
                    appContext = appContext,
                    url = url,
                    versionName = updateInfo.versionName,
                    fileName = fileName
                )

                if (!isValidApkArchive(apkFile)) {
                    apkFile.delete()
                    throw Exception("Downloaded file is not a valid APK package")
                }

                withContext(Dispatchers.Main) {
                    installApk(appContext, updateInfo.versionName, apkFile)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val fallback = updateInfo.downloadUrl.ifBlank {
                    updateInfo.fallbackUrl.ifBlank { GITHUB_RELEASES_URL }
                }
                _downloadState.value = UpdateDownloadState.Failed(
                    versionName = updateInfo.versionName,
                    message = e.localizedMessage ?: "Download failed",
                    fallbackUrl = fallback
                )
            } finally {
                if (activeDownloadJob === launchedJob) {
                    activeDownloadJob = null
                }
            }
        }
        activeDownloadJob = launchedJob
    }

    fun installApk(context: Context, versionName: String, apkFile: File): Boolean {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            _downloadState.value = UpdateDownloadState.Failed(
                versionName = versionName,
                message = "Downloaded APK file is missing",
                fallbackUrl = GITHUB_RELEASES_URL
            )
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            _downloadState.value = UpdateDownloadState.PermissionRequired(versionName, apkFile)
            try {
                val manageIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(manageIntent)
            } catch (e: Exception) {
                _downloadState.value = UpdateDownloadState.Failed(
                    versionName = versionName,
                    message = "Enable \"Install unknown apps\" in system settings to install v$versionName",
                    fallbackUrl = GITHUB_RELEASES_URL
                )
            }
            return false
        }

        return try {
            _downloadState.value = UpdateDownloadState.ReadyToInstall(versionName, apkFile)
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            true
        } catch (e: Exception) {
            _downloadState.value = UpdateDownloadState.Failed(
                versionName = versionName,
                message = "Cannot launch installer: ${e.localizedMessage ?: "Unknown error"}",
                fallbackUrl = GITHUB_RELEASES_URL
            )
            false
        }
    }

    fun cancelDownload(context: Context) {
        cancelInternal(context.applicationContext)
        _downloadState.value = UpdateDownloadState.Idle
    }

    fun resetDownloadState() {
        if (_downloadState.value !is UpdateDownloadState.Downloading) {
            _downloadState.value = UpdateDownloadState.Idle
        }
    }

    private fun cancelInternal(appContext: Context) {
        activeDownloadJob?.cancel()
        activeDownloadJob = null
        activeDownloadManagerId?.let { id ->
            runCatching {
                val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                manager?.remove(id)
            }
        }
        activeDownloadManagerId = null
    }

    private suspend fun tryDownloadWithDownloadManager(
        appContext: Context,
        url: String,
        versionName: String,
        fileName: String
    ): File? = withContext(Dispatchers.IO) {
        val extDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return@withContext null
        val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return@withContext null

        val targetFile = File(extDir, fileName)
        if (targetFile.exists()) targetFile.delete()

        val downloadId = try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Downloading VajraFS v$versionName")
                .setDescription("Downloading latest release APK...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, fileName)
                .setMimeType("application/vnd.android.package-archive")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .addRequestHeader("User-Agent", USER_AGENT)
            manager.enqueue(request)
        } catch (_: Exception) {
            return@withContext null
        }

        activeDownloadManagerId = downloadId
        val startedAtMs = System.currentTimeMillis()
        var sawProgress = false

        try {
            while (isActive) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = manager.query(query)
                if (cursor == null) {
                    return@withContext null
                }
                cursor.use { c ->
                    if (!c.moveToFirst()) {
                        return@withContext null
                    }
                    val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val downloaded = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            _downloadState.value = UpdateDownloadState.Downloading(versionName, 100)
                            val localUri = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                            val resolved = localUri?.let { Uri.parse(it).path }?.let { File(it) } ?: targetFile
                            activeDownloadManagerId = null
                            return@withContext if (resolved.exists() && resolved.length() > 0L) resolved else null
                        }
                        DownloadManager.STATUS_FAILED -> {
                            manager.remove(downloadId)
                            activeDownloadManagerId = null
                            return@withContext null
                        }
                        DownloadManager.STATUS_RUNNING -> {
                            sawProgress = true
                            val percent = if (total > 0L) {
                                ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                            } else null
                            _downloadState.value = UpdateDownloadState.Downloading(versionName, percent)
                        }
                        DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED -> {
                            if (!sawProgress && System.currentTimeMillis() - startedAtMs > DOWNLOAD_MANAGER_PENDING_TIMEOUT_MS) {
                                manager.remove(downloadId)
                                activeDownloadManagerId = null
                                return@withContext null
                            }
                        }
                    }
                }
                delay(400L)
            }
        } catch (e: CancellationException) {
            runCatching { manager.remove(downloadId) }
            activeDownloadManagerId = null
            throw e
        }
        null
    }

    private suspend fun downloadDirect(
        appContext: Context,
        url: String,
        versionName: String,
        fileName: String
    ): File = withContext(Dispatchers.IO) {
        val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(appContext.cacheDir, "updates").apply { mkdirs() }
        if (!dir.exists()) dir.mkdirs()

        val targetFile = File(dir, fileName)
        val tempFile = File(dir, "$fileName.part")
        if (tempFile.exists()) tempFile.delete()
        if (targetFile.exists()) targetFile.delete()

        var connection: HttpURLConnection? = null
        try {
            connection = openFollowingRedirects(
                urlString = url,
                headers = mapOf("User-Agent" to USER_AGENT),
                connectTimeoutMs = 10_000,
                readTimeoutMs = 30_000
            )
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP $code downloading update")
            }

            val totalBytes = connection.contentLengthLong
            val buffer = ByteArray(16 * 1024)
            var downloaded = 0L
            var lastReportedPct = -1

            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(tempFile).use { output ->
                    while (isActive) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0L) {
                            val pct = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            if (pct != lastReportedPct) {
                                lastReportedPct = pct
                                _downloadState.value = UpdateDownloadState.Downloading(versionName, pct)
                            }
                        }
                    }
                }
            }

            if (!isActive) {
                tempFile.delete()
                throw CancellationException("Download cancelled")
            }

            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            targetFile
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        } finally {
            connection?.disconnect()
        }
    }

    private fun cleanOldUpdateApks(appContext: Context) {
        val dirs = listOfNotNull(
            appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            File(appContext.cacheDir, "updates")
        )
        dirs.forEach { dir ->
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.startsWith("vajrafs-") &&
                    (file.name.endsWith(".apk") || file.name.endsWith(".apk.part"))
                ) {
                    file.delete()
                }
            }
        }
    }

    private fun isValidApkArchive(file: File): Boolean {
        if (!file.exists() || file.length() < 4L) return false
        return try {
            FileInputStream(file).use { input ->
                val header = ByteArray(2)
                input.read(header) == 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun fetchLatestFromGitHub(
        currentName: String,
        currentCode: Int,
        isDebugBuild: Boolean
    ): Result<AppUpdateInfo> {
        val apiFailure = try {
            return Result.success(fetchLatestFromGitHubApi(currentName, currentCode, isDebugBuild))
        } catch (e: Exception) {
            e
        }

        return try {
            Result.success(fetchLatestFromGitHubWeb(currentName, currentCode, isDebugBuild))
        } catch (webError: Exception) {
            Result.failure(Exception(webError.message ?: apiFailure.message ?: "GitHub update check failed"))
        }
    }

    private fun fetchLatestFromGitHubApi(
        currentName: String,
        currentCode: Int,
        isDebugBuild: Boolean
    ): AppUpdateInfo {
        val (statusCode, _, body) = httpGetText(
            urlString = GITHUB_LATEST_API_URL,
            headers = mapOf(
                "Accept" to "application/vnd.github+json",
                "X-GitHub-Api-Version" to "2022-11-28",
                "User-Agent" to USER_AGENT
            ),
            connectTimeoutMs = 7000,
            readTimeoutMs = 7000
        )
        if (statusCode != HttpURLConnection.HTTP_OK) {
            throw Exception("GitHub API HTTP $statusCode")
        }

        val json = JSONObject(body)
        if (json.optBoolean("draft", false)) {
            throw Exception("Latest GitHub release is a draft")
        }

        val tagName = json.optString("tag_name", "").trim()
        val releaseTitle = json.optString("name", "").trim()
        val remoteVersion = normalizeVersionTag(tagName).ifBlank { normalizeVersionTag(releaseTitle) }
        if (remoteVersion.isBlank()) {
            throw Exception("Missing release version in GitHub API response")
        }

        val htmlUrl = json.optString("html_url", GITHUB_RELEASES_URL).ifBlank { GITHUB_RELEASES_URL }
        val publishedAt = json.optString("published_at", "")
        val releaseDate = extractIsoDate(publishedAt)
        val releaseNotes = parseReleaseNotes(json.optString("body", ""))

        val assetsList = mutableListOf<Pair<String, String>>()
        val assetsArray = json.optJSONArray("assets")
        if (assetsArray != null) {
            for (i in 0 until assetsArray.length()) {
                val assetObj = assetsArray.optJSONObject(i) ?: continue
                val assetName = assetObj.optString("name", "")
                val downloadUrl = assetObj.optString("browser_download_url", "")
                if (assetName.isNotBlank() && downloadUrl.isNotBlank()) {
                    assetsList.add(assetName to downloadUrl)
                }
            }
        }

        val downloadUrl = selectApkAssetUrl(assetsList, isDebugBuild, htmlUrl)
        val isAvailable = isVersionNewer(remoteVersion, currentName, 0, currentCode)

        return AppUpdateInfo(
            versionCode = 0,
            versionName = remoteVersion,
            minSupportedVersion = 1,
            releaseDate = releaseDate,
            downloadUrl = downloadUrl,
            fallbackUrl = htmlUrl,
            releaseNotes = releaseNotes,
            isMandatory = false,
            isUpdateAvailable = isAvailable
        )
    }

    private fun fetchLatestFromGitHubWeb(
        currentName: String,
        currentCode: Int,
        isDebugBuild: Boolean
    ): AppUpdateInfo {
        val (statusCode, finalUrl, body) = httpGetText(
            urlString = GITHUB_RELEASES_URL,
            headers = mapOf(
                "Accept" to "application/json",
                "User-Agent" to USER_AGENT
            ),
            connectTimeoutMs = 7000,
            readTimeoutMs = 7000
        )
        if (statusCode != HttpURLConnection.HTTP_OK) {
            throw Exception("GitHub HTTP $statusCode")
        }

        val tagFromJson = runCatching { JSONObject(body).optString("tag_name", "").trim() }.getOrDefault("")
        val tagName = tagFromJson.ifBlank { extractTagFromReleaseUrl(finalUrl) }
        val remoteVersion = normalizeVersionTag(tagName)
        if (remoteVersion.isBlank()) {
            throw Exception("Could not determine latest GitHub release tag")
        }

        val releasePageUrl = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/tag/$tagName"
        val expandedAssetsUrl = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/expanded_assets/$tagName"
        val assets = runCatching {
            val (assetsCode, _, assetsHtml) = httpGetText(
                urlString = expandedAssetsUrl,
                headers = mapOf("User-Agent" to USER_AGENT),
                connectTimeoutMs = 4500,
                readTimeoutMs = 4500
            )
            if (assetsCode == HttpURLConnection.HTTP_OK) {
                extractApkAssetsFromExpandedHtml(assetsHtml)
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())

        val suffix = if (isDebugBuild) "github-debug.apk" else "github-release.apk"
        val predictedApkUrl =
            "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/download/$tagName/prabincode-vajrafs-v$remoteVersion-$suffix"
        val downloadUrl = selectApkAssetUrl(assets, isDebugBuild, predictedApkUrl)
        val isAvailable = isVersionNewer(remoteVersion, currentName, 0, currentCode)

        return AppUpdateInfo(
            versionCode = 0,
            versionName = remoteVersion,
            minSupportedVersion = 1,
            releaseDate = "",
            downloadUrl = downloadUrl,
            fallbackUrl = releasePageUrl,
            releaseNotes = emptyList(),
            isMandatory = false,
            isUpdateAvailable = isAvailable
        )
    }

    private fun fetchFromVersionJson(currentName: String, currentCode: Int): Result<AppUpdateInfo> {
        return try {
            val (statusCode, _, response) = httpGetText(
                urlString = VERSION_JSON_URL,
                headers = mapOf(
                    "Accept" to "application/json",
                    "User-Agent" to USER_AGENT
                ),
                connectTimeoutMs = 5000,
                readTimeoutMs = 5000
            )
            if (statusCode != HttpURLConnection.HTTP_OK) {
                return Result.failure(Exception("HTTP $statusCode from update server"))
            }

            val json = JSONObject(response)
            val remoteCode = json.optInt("versionCode", 0)
            val remoteName = normalizeVersionTag(json.optString("versionName", ""))
                .ifBlank { json.optString("versionName", "Unknown") }
            val minVersion = json.optInt("minSupportedVersion", 1)
            val releaseDate = json.optString("releaseDate", "")
            val downloadUrl = json.optString("downloadUrl", GITHUB_RELEASES_URL)
            val fallbackUrl = json.optString("fallbackUrl", WEBSITE_URL)
            val isMandatory = json.optBoolean("isMandatory", false)

            val releaseNotes = mutableListOf<String>()
            val notesArray = json.optJSONArray("releaseNotes")
            if (notesArray != null) {
                for (i in 0 until notesArray.length()) {
                    val note = notesArray.optString(i).trim()
                    if (note.isNotEmpty()) releaseNotes.add(note)
                }
            }

            val isAvailable = isVersionNewer(remoteName, currentName, remoteCode, currentCode)

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

    private data class HttpTextResponse(
        val statusCode: Int,
        val finalUrl: String,
        val body: String
    )

    private fun httpGetText(
        urlString: String,
        headers: Map<String, String>,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): HttpTextResponse {
        var connection: HttpURLConnection? = null
        return try {
            connection = openFollowingRedirects(
                urlString = urlString,
                headers = headers,
                connectTimeoutMs = connectTimeoutMs,
                readTimeoutMs = readTimeoutMs
            )
            val statusCode = connection.responseCode
            val finalUrl = connection.url.toString()
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            HttpTextResponse(statusCode, finalUrl, body)
        } finally {
            connection?.disconnect()
        }
    }

    private fun openFollowingRedirects(
        urlString: String,
        headers: Map<String, String>,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): HttpURLConnection {
        var currentUrl = urlString
        for (hop in 0..MAX_REDIRECTS) {
            val parsedUrl = URL(currentUrl)
            require(parsedUrl.protocol.equals("https", ignoreCase = true)) {
                "Refusing non-HTTPS update URL"
            }
            val conn = (parsedUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                requestMethod = "GET"
                instanceFollowRedirects = false
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            val code = conn.responseCode
            if (code in listOf(
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_SEE_OTHER,
                    307,
                    308
                )
            ) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    throw Exception("Redirect without Location header")
                }
                currentUrl = URL(parsedUrl, location).toString()
                continue
            }
            return conn
        }
        throw Exception("Too many redirects while checking for updates")
    }
}

internal fun selectPreferredUpdateInfo(
    githubInfo: AppUpdateInfo?,
    versionJsonInfo: AppUpdateInfo?,
    currentName: String,
    currentCode: Int
): AppUpdateInfo {
    if (githubInfo == null) return requireNotNull(versionJsonInfo)
    if (versionJsonInfo == null) return githubInfo

    val cmp = compareVersions(githubInfo.versionName, versionJsonInfo.versionName)
    return when {
        cmp > 0 -> githubInfo
        cmp < 0 -> versionJsonInfo
        else -> {
            val mergedCode = maxOf(githubInfo.versionCode, versionJsonInfo.versionCode)
            val mergedName = githubInfo.versionName.ifBlank { versionJsonInfo.versionName }
            val mergedNotes = versionJsonInfo.releaseNotes.ifEmpty { githubInfo.releaseNotes }
            val mergedDownload = githubInfo.downloadUrl.takeIf { it.endsWith(".apk", ignoreCase = true) }
                ?: versionJsonInfo.downloadUrl
            val mergedFallback = githubInfo.fallbackUrl.ifBlank { versionJsonInfo.fallbackUrl }
            val mergedDate = githubInfo.releaseDate.ifBlank { versionJsonInfo.releaseDate }
            AppUpdateInfo(
                versionCode = mergedCode,
                versionName = mergedName,
                minSupportedVersion = maxOf(githubInfo.minSupportedVersion, versionJsonInfo.minSupportedVersion),
                releaseDate = mergedDate,
                downloadUrl = mergedDownload,
                fallbackUrl = mergedFallback,
                releaseNotes = mergedNotes,
                isMandatory = githubInfo.isMandatory || versionJsonInfo.isMandatory,
                isUpdateAvailable = isVersionNewer(mergedName, currentName, mergedCode, currentCode)
            )
        }
    }
}

internal fun normalizeVersionTag(raw: String): String {
    val trimmed = raw.trim()
    val match = Regex("""v?(\d+(?:\.\d+)+(?:-[A-Za-z0-9.]+)?)""").find(trimmed)
    return match?.groupValues?.getOrNull(1) ?: trimmed.removePrefix("v").removePrefix("V")
}

internal fun isVersionNewer(
    remoteVersion: String,
    currentVersion: String,
    remoteCode: Int = 0,
    currentCode: Int = 0
): Boolean {
    val hasNumericVersion = remoteVersion.any { it.isDigit() }
    if (!hasNumericVersion) {
        return remoteCode > 0 && currentCode > 0 && remoteCode > currentCode
    }
    val cmp = compareVersions(remoteVersion, currentVersion)
    if (cmp != 0) return cmp > 0
    return remoteCode > 0 && currentCode > 0 && remoteCode > currentCode
}

internal fun compareVersions(a: String, b: String): Int {
    val parsedA = parseSemver(a)
    val parsedB = parseSemver(b)
    val maxLen = maxOf(parsedA.core.size, parsedB.core.size)
    for (i in 0 until maxLen) {
        val partA = parsedA.core.getOrElse(i) { 0 }
        val partB = parsedB.core.getOrElse(i) { 0 }
        if (partA != partB) return partA.compareTo(partB)
    }
    val preA = parsedA.preRelease
    val preB = parsedB.preRelease
    if (preA.isEmpty() && preB.isEmpty()) return 0
    if (preA.isEmpty()) return 1
    if (preB.isEmpty()) return -1

    val preLen = maxOf(preA.size, preB.size)
    for (i in 0 until preLen) {
        val segA = preA.getOrNull(i) ?: return -1
        val segB = preB.getOrNull(i) ?: return 1
        val numA = segA.toIntOrNull()
        val numB = segB.toIntOrNull()
        val cmp = when {
            numA != null && numB != null -> numA.compareTo(numB)
            numA != null -> -1
            numB != null -> 1
            else -> segA.compareTo(segB, ignoreCase = true)
        }
        if (cmp != 0) return cmp
    }
    return 0
}

private data class ParsedSemver(val core: List<Int>, val preRelease: List<String>)

private fun parseSemver(raw: String): ParsedSemver {
    val cleaned = normalizeVersionTag(raw).substringBefore('+')
    val corePart = cleaned.substringBefore('-')
    val prePart = if ('-' in cleaned) cleaned.substringAfter('-') else ""
    val coreNums = corePart.split('.').map { seg ->
        Regex("""^\d+""").find(seg.trim())?.value?.toIntOrNull() ?: 0
    }
    val preSegments = if (prePart.isBlank()) emptyList() else prePart.split('.').filter { it.isNotBlank() }
    return ParsedSemver(coreNums, preSegments)
}

internal fun selectApkAssetUrl(
    assets: List<Pair<String, String>>,
    isDebugBuild: Boolean,
    fallbackUrl: String
): String {
    val apks = assets.filter { (name, url) ->
        name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()
    }
    if (apks.isEmpty()) return fallbackUrl

    val preferred = if (isDebugBuild) {
        apks.firstOrNull { it.first.contains("github-debug", ignoreCase = true) }
            ?: apks.firstOrNull { it.first.contains("debug", ignoreCase = true) }
            ?: apks.firstOrNull { it.first.contains("github-release", ignoreCase = true) }
            ?: apks.first()
    } else {
        apks.firstOrNull { it.first.contains("github-release", ignoreCase = true) }
            ?: apks.firstOrNull { it.first.contains("release", ignoreCase = true) }
            ?: apks.firstOrNull { !it.first.contains("debug", ignoreCase = true) }
            ?: apks.first()
    }
    return preferred.second
}

internal fun extractTagFromReleaseUrl(url: String): String {
    val match = Regex("""/releases/tag/([^/?#]+)""").find(url)
    return match?.groupValues?.getOrNull(1)?.trim().orEmpty()
}

internal fun extractApkAssetsFromExpandedHtml(html: String): List<Pair<String, String>> {
    val regex = Regex("""href=["']([^"']*?/releases/download/[^"']+/([^/"']+\.apk))["']""", RegexOption.IGNORE_CASE)
    return regex.findAll(html).map { match ->
        val rawHref = match.groupValues[1]
        val fileName = match.groupValues[2]
        val fullUrl = if (rawHref.startsWith("http", ignoreCase = true)) {
            rawHref
        } else {
            "https://github.com$rawHref"
        }
        fileName to fullUrl
    }.distinctBy { it.second }.toList()
}

internal fun extractIsoDate(publishedAt: String): String {
    val match = Regex("""^(\d{4}-\d{2}-\d{2})""").find(publishedAt.trim())
    return match?.groupValues?.getOrNull(1).orEmpty()
}

internal fun parseReleaseNotes(body: String): List<String> {
    if (body.isBlank()) return emptyList()
    val notes = mutableListOf<String>()
    for (rawLine in body.lines()) {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) continue
        if (trimmed.startsWith("## Downloads", ignoreCase = true) ||
            trimmed.startsWith("### Downloads", ignoreCase = true)
        ) {
            break
        }
        if (trimmed.contains("Full Changelog", ignoreCase = true) && trimmed.contains("https://")) {
            continue
        }
        if (trimmed.startsWith("## What's new", ignoreCase = true)) {
            continue
        }
        val stripped = trimmed
            .replace(Regex("""^#{1,6}\s*"""), "")
            .replace(Regex("""^[-*•]\s+"""), "")
            .replace("**", "")
            .replace("`", "")
            .trim()
        if (stripped.isNotEmpty()) {
            notes.add(stripped)
        }
        if (notes.size >= 15) break
    }
    return notes
}
