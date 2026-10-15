/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.data.server

import android.content.Context
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.storage.StorageManager
import com.prabincode.vajrafs.data.model.FileItem
import java.io.*
import java.net.*
import java.util.Collections
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class ServerState(
    val isRunning: Boolean = false,
    val ipAddress: String = "",
    val allIpAddresses: List<String> = emptyList(),
    val port: Int = 8080,
    val sessionPin: String = "",
    val activeConnections: Int = 0
)

object LocalWifiServer {

    private const val DEFAULT_PORT = 8080
    private var serverSocket: ServerSocket? = null
    private val threadPool = ThreadPoolExecutor(
        4,
        32,
        60L,
        TimeUnit.SECONDS,
        LinkedBlockingQueue(128),
        ThreadPoolExecutor.DiscardOldestPolicy()
    )
    private var isServerActive = false
    private var currentPin = ""
    private var appContext: Context? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    // Rate limiting & lockout state for PIN authentication
    private var failedPinAttempts = 0
    private var lockoutUntilMs = 0L

    private val _serverState = MutableStateFlow(ServerState())
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    fun acquireLocks(context: Context) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (wakeLock == null) {
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "VajraFS:WifiShareWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                    acquire(12 * 60 * 60 * 1000L)
                }
            }

            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiLock == null) {
                @Suppress("DEPRECATION")
                wifiLock = wifiManager?.createWifiLock(
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                    } else {
                        WifiManager.WIFI_MODE_FULL_HIGH_PERF
                    },
                    "VajraFS:WifiShareWifiLock"
                )?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (_: Exception) {}
    }

    fun releaseLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null

            wifiLock?.let {
                if (it.isHeld) it.release()
            }
            wifiLock = null
        } catch (_: Exception) {}
    }

    fun setPreventSleep(context: Context, enabled: Boolean) {
        if (enabled && isServerActive) {
            acquireLocks(context)
        } else {
            releaseLocks()
        }
    }

    suspend fun startServer(context: Context, port: Int = DEFAULT_PORT): Result<String> = withContext(Dispatchers.IO) {
        if (isServerActive) {
            return@withContext Result.success(_serverState.value.ipAddress)
        }

        appContext = context.applicationContext

        try {
            val (primaryIp, allIps) = getAllLocalIpAddresses(context)
            val displayIp = if (primaryIp.isNotEmpty() && primaryIp != "127.0.0.1" && primaryIp != "0.0.0.0") {
                primaryIp
            } else {
                "192.168.1.x"
            }

            currentPin = (1000 + Random.nextInt(9000)).toString()

            val socket = ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))
            serverSocket = socket
            isServerActive = true

            _serverState.value = ServerState(
                isRunning = true,
                ipAddress = displayIp,
                allIpAddresses = allIps,
                port = port,
                sessionPin = currentPin
            )

            val prefs = context.getSharedPreferences("vajrafs_prefs", Context.MODE_PRIVATE)
            val preventSleep = prefs.getBoolean("wifi_share_prevent_sleep", true)
            if (preventSleep) {
                acquireLocks(context)
            }

            // Start persistent foreground service so Android does not suspend network socket in background or when screen is locked
            com.prabincode.vajrafs.data.service.WifiServerService.start(context)

            threadPool.execute {
                while (isServerActive && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        threadPool.execute { handleClient(client) }
                    } catch (e: Exception) {
                        break
                    }
                }
            }

            Result.success(displayIp)
        } catch (e: Exception) {
            isServerActive = false
            releaseLocks()
            appContext?.let { com.prabincode.vajrafs.data.service.WifiServerService.stop(it) }
            Result.failure(e)
        }
    }

    suspend fun stopServer() = withContext(Dispatchers.IO) {
        isServerActive = false
        releaseLocks()
        appContext?.let { com.prabincode.vajrafs.data.service.WifiServerService.stop(it) }
        try {
            serverSocket?.close()
        } catch (e: Exception) { }
        serverSocket = null
        _serverState.value = ServerState(isRunning = false)
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.use { client ->
                client.tcpNoDelay = true
                client.soTimeout = 45000
                try {
                    client.receiveBufferSize = 256 * 1024
                    client.sendBufferSize = 256 * 1024
                } catch (_: Exception) {}

                val rawInput = client.getInputStream()
                val bufferedIn = BufferedInputStream(rawInput, 256 * 1024)
                val rawOutput = client.getOutputStream()
                val output = BufferedOutputStream(rawOutput, 64 * 1024)

                val requestLine = readLine(bufferedIn) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0].uppercase()
                val fullUri = parts[1]
                val uriParts = fullUri.split("?")
                val path = URLDecoder.decode(uriParts[0], "UTF-8")
                val queryParams = if (uriParts.size > 1) parseQuery(uriParts[1]) else emptyMap()

                // Read Headers
                val headers = mutableMapOf<String, String>()
                var headerLine = readLine(bufferedIn)
                while (!headerLine.isNullOrEmpty()) {
                    val idx = headerLine.indexOf(":")
                    if (idx != -1) {
                        headers[headerLine.substring(0, idx).trim().lowercase()] = headerLine.substring(idx + 1).trim()
                    }
                    headerLine = readLine(bufferedIn)
                }

                val cookieHeader = headers["cookie"] ?: ""
                val authCookie = extractCookie(cookieHeader, "vajrafs_auth")
                val isAuthed = currentPin.isNotEmpty() && (authCookie == currentPin || queryParams["pin"] == currentPin)

                if (path == "/auth") {
                    if (isPinLockedOut()) {
                        val remainingSec = ((lockoutUntilMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(1L)
                        sendHtml(output, getLoginPage("Too many failed attempts! Please wait $remainingSec seconds."))
                        return
                    }

                    val enteredPin = queryParams["pin"] ?: ""
                    if (currentPin.isNotEmpty() && enteredPin == currentPin) {
                        onPinSucceeded()
                        val response = "HTTP/1.1 302 Found\r\nSet-Cookie: vajrafs_auth=$currentPin; Path=/; HttpOnly; SameSite=Strict\r\nLocation: /\r\n\r\n"
                        output.write(response.toByteArray())
                        output.flush()
                    } else {
                        onPinFailed()
                        try { Thread.sleep(1000) } catch (_: Exception) {}
                        sendHtml(output, getLoginPage("Invalid PIN! Try again."))
                    }
                    return
                }

                if (!isAuthed) {
                    sendHtml(output, getLoginPage(null))
                    return
                }

                // 1. Download file (with containment validation)
                if (path == "/download") {
                    val filePath = queryParams["path"]
                    if (filePath != null && isAllowedPath(filePath, appContext)) {
                        val file = File(filePath)
                        if (file.exists() && file.isFile && file.canRead()) {
                            sendFileDownload(output, file)
                            return
                        }
                    }
                    sendForbidden(output)
                    return
                }

                // 2. High-speed Direct Binary Upload from PC to Phone (strictly sanitized)
                if (path == "/upload" && method == "POST") {
                    val rawTargetDir = queryParams["dir"] ?: Environment.getExternalStorageDirectory().absolutePath
                    val rawFilename = queryParams["filename"] ?: "Uploaded_${System.currentTimeMillis()}"
                    // Sanitize filename: strip any path traversal sequences
                    val sanitizedFilename = File(rawFilename).name.ifBlank { "Uploaded_${System.currentTimeMillis()}" }

                    if (!isAllowedPath(rawTargetDir, appContext)) {
                        sendForbidden(output)
                        return
                    }

                    val targetDir = File(rawTargetDir)
                    if (!targetDir.exists()) {
                        targetDir.mkdirs()
                    }

                    val outputFile = File(targetDir, sanitizedFilename)
                    if (!isAllowedPath(outputFile.canonicalPath, appContext)) {
                        sendForbidden(output)
                        return
                    }

                    val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L
                    if (contentLength > 0L) {
                        val tempFile = File(targetDir, "${sanitizedFilename}.part_${System.currentTimeMillis()}")
                        var success = false

                        try {
                            BufferedOutputStream(FileOutputStream(tempFile), 256 * 1024).use { fos ->
                                val buf = ByteArray(128 * 1024)
                                var remaining = contentLength
                                while (remaining > 0L) {
                                    val toRead = remaining.coerceAtMost(buf.size.toLong()).toInt()
                                    val read = bufferedIn.read(buf, 0, toRead)
                                    if (read == -1) break
                                    fos.write(buf, 0, read)
                                    remaining -= read
                                }
                                fos.flush()
                                success = (remaining == 0L)
                            }
                            if (success) {
                                if (outputFile.exists()) {
                                    outputFile.delete()
                                }
                                tempFile.renameTo(outputFile)
                                appContext?.let { MediaScannerConnection.scanFile(it, arrayOf(outputFile.absolutePath), null, null) }
                            } else {
                                tempFile.delete()
                            }
                        } catch (e: Exception) {
                            tempFile.delete()
                        }
                    }

                    val jsonClean = sanitizedFilename.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
                    val json = "{\"status\":\"ok\",\"file\":\"$jsonClean\"}"
                    val jsonBytes = json.toByteArray(Charsets.UTF_8)
                    val header = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${jsonBytes.size}\r\n" +
                            "Connection: close\r\n\r\n"
                    output.write(header.toByteArray())
                    output.write(jsonBytes)
                    output.flush()
                    return
                }

                // 3. Create folder from PC (strictly sanitized)
                if (path == "/mkdir") {
                    val rawTargetDir = queryParams["dir"] ?: Environment.getExternalStorageDirectory().absolutePath
                    val rawName = queryParams["name"] ?: "New Folder"
                    val cleanFolderName = File(rawName).name.ifBlank { "New Folder" }

                    if (!isAllowedPath(rawTargetDir, appContext)) {
                        sendForbidden(output)
                        return
                    }

                    val newFolder = File(rawTargetDir, cleanFolderName)
                    if (isAllowedPath(newFolder.canonicalPath, appContext)) {
                        newFolder.mkdirs()
                    }

                    val encodedDir = URLEncoder.encode(rawTargetDir, "UTF-8")
                    val response = "HTTP/1.1 302 Found\r\nLocation: /?dir=$encodedDir\r\n\r\n"
                    output.write(response.toByteArray())
                    output.flush()
                    return
                }

                // 4. Delete file/folder from PC (strictly contained)
                if (path == "/delete") {
                    val filePath = queryParams["path"]
                    val returnDir = queryParams["dir"] ?: Environment.getExternalStorageDirectory().absolutePath
                    val primaryRoot = Environment.getExternalStorageDirectory().canonicalPath

                    if (filePath != null && isAllowedPath(filePath, appContext)) {
                        val file = File(filePath).canonicalFile
                        // Never allow deleting the primary storage root itself
                        if (file.canonicalPath != primaryRoot && file.exists()) {
                            file.deleteRecursively()
                            appContext?.let { MediaScannerConnection.scanFile(it, arrayOf(file.absolutePath), null, null) }
                        }
                    }

                    val encodedDir = URLEncoder.encode(returnDir, "UTF-8")
                    val response = "HTTP/1.1 302 Found\r\nLocation: /?dir=$encodedDir\r\n\r\n"
                    output.write(response.toByteArray())
                    output.flush()
                    return
                }

                // 5. Serve Web Explorer UI (contained)
                val currentDirPath = queryParams["dir"] ?: Environment.getExternalStorageDirectory().absolutePath
                val targetDir = if (isAllowedPath(currentDirPath, appContext)) {
                    val dir = File(currentDirPath)
                    if (dir.exists() && dir.isDirectory) dir else Environment.getExternalStorageDirectory()
                } else {
                    Environment.getExternalStorageDirectory()
                }

                sendHtml(output, getExplorerPage(targetDir))
            }
        } catch (e: Exception) { }
    }

    private fun readLine(input: InputStream, maxLen: Int = 8192): String? {
        val bout = ByteArrayOutputStream(128)
        var b: Int
        var prev = -1
        while (input.read().also { b = it } != -1) {
            if (prev == '\r'.code && b == '\n'.code) {
                val bytes = bout.toByteArray()
                return String(bytes, 0, bytes.size - 1, Charsets.ISO_8859_1)
            }
            bout.write(b)
            prev = b
            if (bout.size() > maxLen) {
                return null // Line length exceeded limit; abort to prevent OOM
            }
        }
        if (bout.size() == 0) return null
        return bout.toString("ISO-8859-1")
    }

    private fun sendHtml(output: OutputStream, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray())
        output.write(bytes)
        output.flush()
    }

    private fun sendFileDownload(output: OutputStream, file: File) {
        val encodedName = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/octet-stream\r\n" +
                "Content-Disposition: attachment; filename=\"$encodedName\"\r\n" +
                "Content-Length: ${file.length()}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray())
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(64 * 1024)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
            }
        }
        output.flush()
    }

    private fun getLoginPage(error: String?): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>VajraFS — Wireless PC Transfer</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0f172a; color: #f8fafc; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
                    .card { background: #1e293b; padding: 2.5rem; border-radius: 16px; box-shadow: 0 10px 25px rgba(0,0,0,0.5); width: 100%; max-width: 380px; text-align: center; }
                    h2 { margin-top: 0; color: #38bdf8; }
                    input { width: 100%; padding: 12px; font-size: 1.2rem; text-align: center; letter-spacing: 4px; border: 2px solid #334155; border-radius: 8px; background: #0f172a; color: #fff; margin: 16px 0; box-sizing: border-box; }
                    input:focus { outline: none; border-color: #38bdf8; }
                    button { width: 100%; padding: 12px; background: #0284c7; border: none; border-radius: 8px; color: #fff; font-size: 1rem; font-weight: bold; cursor: pointer; }
                    button:hover { background: #0369a1; }
                    .error { color: #f87171; font-size: 0.9rem; margin-bottom: 12px; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h2>🛡️ VajraFS</h2>
                    <p style="color: #94a3b8; font-size: 0.9rem; margin-bottom: 4px;">Wireless PC File Transfer</p>
                    <p style="color: #64748b; font-size: 0.8rem; margin-top: 0;">Enter the 4-digit PIN shown on your phone screen.</p>
                    ${if (error != null) "<div class='error'>$error</div>" else ""}
                    <form action="/auth" method="GET">
                        <input type="password" name="pin" maxlength="4" placeholder="••••" autofocus required />
                        <button type="submit">Unlock Explorer</button>
                    </form>
                    <p style="color: #64748b; font-size: 0.75rem; margin-top: 24px; margin-bottom: 0;">
                        Open-source project by <a href="https://github.com/PrabinCode/vajrafs-android" target="_blank" style="color: #38bdf8; text-decoration: none;">PrabinCode</a>
                    </p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun getExplorerPage(currentDir: File): String {
        val files = currentDir.listFiles() ?: emptyArray()
        val sorted = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

        val parentPath = currentDir.parentFile?.absolutePath
        val encodedCurrent = URLEncoder.encode(currentDir.absolutePath, "UTF-8")

        val rows = StringBuilder()

        if (parentPath != null && currentDir.absolutePath != "/storage/emulated/0" && currentDir.absolutePath != "/") {
            val encodedParent = URLEncoder.encode(parentPath, "UTF-8")
            rows.append("""
                <tr>
                    <td colspan="4"><a href="/?dir=$encodedParent" class="folder-link">📁 .. (Go Up)</a></td>
                </tr>
            """.trimIndent())
        }

        for (f in sorted) {
            val name = f.name
            val escapedName = escapeHtml(name)
            val encodedPath = URLEncoder.encode(f.absolutePath, "UTF-8")
            if (f.isDirectory) {
                val count = f.list()?.size ?: 0
                rows.append("""
                    <tr>
                        <td>📁 <a href="/?dir=$encodedPath" class="folder-link">$escapedName</a></td>
                        <td>$count items</td>
                        <td>Folder</td>
                        <td>
                            <a href="/delete?path=$encodedPath&dir=$encodedCurrent" onclick="return confirm('Delete folder?')" class="del-btn">🗑️ Delete</a>
                        </td>
                    </tr>
                """.trimIndent())
            } else {
                val size = FileItem.formatBytes(f.length())
                rows.append("""
                    <tr>
                        <td>📄 $escapedName</td>
                        <td>$size</td>
                        <td>${f.extension.uppercase()}</td>
                        <td>
                            <a href="/download?path=$encodedPath" class="download-btn">⬇️ Download</a>
                            <a href="/delete?path=$encodedPath&dir=$encodedCurrent" onclick="return confirm('Delete file?')" class="del-btn">🗑️</a>
                        </td>
                    </tr>
                """.trimIndent())
            }
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>VajraFS — Wireless PC Explorer</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0f172a; color: #f8fafc; margin: 0; padding: 2rem; }
                    .header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #334155; padding-bottom: 1rem; margin-bottom: 1.5rem; }
                    .title { color: #38bdf8; font-size: 1.5rem; font-weight: bold; }
                    .actions-bar { display: flex; gap: 12px; align-items: center; margin-bottom: 1rem; background: #1e293b; padding: 12px 16px; border-radius: 12px; }
                    .path-bar { flex: 1; font-family: monospace; color: #94a3b8; word-break: break-all; }
                    .drop-zone { border: 2px dashed #0284c7; background: rgba(2, 132, 199, 0.05); border-radius: 12px; padding: 24px; text-align: center; cursor: pointer; margin-bottom: 1.25rem; transition: all 0.2s ease; }
                    .drop-zone:hover, .drop-zone.dragover { border-color: #38bdf8; background: rgba(56, 189, 248, 0.12); transform: translateY(-2px); }
                    .drop-icon { font-size: 2.2rem; margin-bottom: 6px; }
                    .drop-title { font-size: 1.1rem; font-weight: 600; color: #38bdf8; margin-bottom: 4px; }
                    .drop-subtitle { font-size: 0.85rem; color: #94a3b8; }
                    .drag-overlay { position: fixed; inset: 0; background: rgba(15, 23, 42, 0.85); backdrop-filter: blur(4px); display: none; align-items: center; justify-content: center; z-index: 9999; pointer-events: none; }
                    .drag-overlay.active { display: flex; }
                    .drag-overlay-box { border: 3px dashed #38bdf8; background: #1e293b; border-radius: 20px; padding: 2.5rem 3.5rem; text-align: center; box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.7); animation: popIn 0.2s ease; }
                    @keyframes popIn { from { opacity: 0; transform: scale(0.95); } to { opacity: 1; transform: scale(1); } }
                    .drag-overlay-title { font-size: 1.5rem; font-weight: bold; color: #38bdf8; margin: 12px 0 6px; }
                    .drag-overlay-dest { font-size: 0.9rem; color: #94a3b8; font-family: monospace; }
                    table { width: 100%; border-collapse: collapse; background: #1e293b; border-radius: 12px; overflow: hidden; margin-top: 1rem; }
                    th, td { padding: 12px 16px; text-align: left; border-bottom: 1px solid #334155; }
                    th { background: #0f172a; color: #94a3b8; font-size: 0.85rem; text-transform: uppercase; }
                    tr:hover { background: #334155; }
                    a { color: #38bdf8; text-decoration: none; font-weight: 500; }
                    a:hover { text-decoration: underline; }
                    .btn { background: #0284c7; color: #fff; padding: 8px 16px; border: none; border-radius: 8px; font-weight: bold; cursor: pointer; font-size: 0.9rem; text-decoration: none; display: inline-flex; align-items: center; gap: 6px; }
                    .btn:hover { background: #0369a1; }
                    .download-btn { background: #0284c7; color: #fff; padding: 4px 10px; border-radius: 6px; font-size: 0.85rem; display: inline-block; margin-right: 6px; }
                    .download-btn:hover { background: #0369a1; text-decoration: none; }
                    .del-btn { background: #ef4444; color: #fff; padding: 4px 8px; border-radius: 6px; font-size: 0.85rem; display: inline-block; }
                    .del-btn:hover { background: #dc2626; text-decoration: none; }
                    #progress-container { display: none; margin-bottom: 1rem; background: #1e293b; padding: 12px 16px; border-radius: 12px; border: 1px solid #334155; }
                    #progress-bar { width: 0%; height: 8px; background: #10b981; border-radius: 4px; transition: width 0.2s; }
                    #progress-text { font-size: 0.85rem; color: #38bdf8; margin-top: 6px; }
                    input[type=file] { display: none; }
                </style>
            </head>
            <body>
                <div id="drag-overlay" class="drag-overlay">
                    <div class="drag-overlay-box">
                        <div style="font-size: 3.5rem;">📥</div>
                        <div class="drag-overlay-title">Drop files to upload to phone</div>
                        <div class="drag-overlay-dest">Folder: ${currentDir.name.ifEmpty { "Storage" }}</div>
                    </div>
                </div>

                <div class="header">
                    <div class="title">📱 VajraFS Wireless Transfer</div>
                    <div style="color: #4ade80; font-size: 0.9rem;">● Local Wi-Fi (Offline) • by PrabinCode</div>
                </div>
                
                <div class="actions-bar">
                    <div class="path-bar">📂 ${currentDir.absolutePath}</div>
                    
                    <label class="btn" style="background: #10b981;">
                        📤 Upload Files to Phone
                        <input type="file" id="fileInput" multiple onchange="handleFileInput(this)" />
                    </label>

                    <button class="btn" onclick="let n = prompt('Enter new folder name:'); if(n) window.location.href='/mkdir?dir=$encodedCurrent&name=' + encodeURIComponent(n);">
                        📁 New Folder
                    </button>
                </div>

                <div id="drop-zone" class="drop-zone" onclick="document.getElementById('fileInput').click()">
                    <div class="drop-icon">📥</div>
                    <div class="drop-title">Drag &amp; Drop files here to upload to this folder</div>
                    <div class="drop-subtitle">or click here to browse files from your computer</div>
                </div>

                <div id="progress-container">
                    <div style="background: #334155; border-radius: 6px; overflow: hidden; height: 10px;">
                        <div id="progress-bar" style="width: 0%; height: 100%; background: #10b981; border-radius: 6px; transition: width 0.15s ease-out;"></div>
                    </div>
                    <div id="progress-text" style="font-size: 0.85rem; color: #38bdf8; margin-top: 8px; font-family: monospace;">Uploading...</div>
                </div>

                <table>
                    <thead>
                        <tr>
                            <th>Name</th>
                            <th>Size</th>
                            <th>Type</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        $rows
                    </tbody>
                </table>
                <div style="text-align: center; margin-top: 2.5rem; margin-bottom: 2rem; color: #64748b; font-size: 0.85rem; border-top: 1px solid #334155; padding-top: 1.5rem;">
                    <span>⚡ <strong>VajraFS</strong> — Open-source file manager developed by <a href="https://github.com/PrabinCode/vajrafs-android" target="_blank" style="color: #38bdf8; text-decoration: none; font-weight: 600;">PrabinCode</a></span>
                    <span style="margin: 0 8px;">•</span>
                    <span>100% Offline &amp; GPL-3.0</span>
                </div>

                <script>
                    const overlay = document.getElementById('drag-overlay');
                    const dropZone = document.getElementById('drop-zone');
                    let dragCounter = 0;
                    let isUploading = false;

                    // Unified drag and drop handling without duplicate drop events
                    window.addEventListener('dragenter', e => {
                        e.preventDefault();
                        dragCounter++;
                        if (e.dataTransfer && e.dataTransfer.types && Array.from(e.dataTransfer.types).includes('Files')) {
                            overlay.classList.add('active');
                            dropZone.classList.add('dragover');
                        }
                    });

                    window.addEventListener('dragover', e => {
                        e.preventDefault();
                    });

                    window.addEventListener('dragleave', e => {
                        e.preventDefault();
                        dragCounter--;
                        if (dragCounter <= 0) {
                            dragCounter = 0;
                            overlay.classList.remove('active');
                            dropZone.classList.remove('dragover');
                        }
                    });

                    window.addEventListener('drop', e => {
                        e.preventDefault();
                        e.stopPropagation();
                        dragCounter = 0;
                        overlay.classList.remove('active');
                        dropZone.classList.remove('dragover');

                        if (e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files.length > 0) {
                            uploadFiles(e.dataTransfer.files);
                        }
                    });

                    function handleFileInput(input) {
                        if (input.files && input.files.length > 0) {
                            uploadFiles(input.files);
                        }
                        input.value = '';
                    }

                    function formatBytes(bytes) {
                        if (bytes === 0) return '0 B';
                        const k = 1024;
                        const sizes = ['B', 'KB', 'MB', 'GB'];
                        const i = Math.floor(Math.log(bytes) / Math.log(k));
                        return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
                    }

                    async function uploadFiles(files) {
                        if (!files || files.length === 0) return;
                        if (isUploading) {
                            alert('An upload is already in progress. Please wait for it to complete.');
                            return;
                        }

                        isUploading = true;
                        const progContainer = document.getElementById('progress-container');
                        const progBar = document.getElementById('progress-bar');
                        const progText = document.getElementById('progress-text');

                        progContainer.style.display = 'block';

                        let totalBatchBytes = 0;
                        for (let i = 0; i < files.length; i++) {
                            totalBatchBytes += files[i].size;
                        }

                        let cumulativeUploadedBefore = 0;
                        const startTime = Date.now();

                        try {
                            for (let i = 0; i < files.length; i++) {
                                const file = files[i];

                                await new Promise((resolve) => {
                                    const xhr = new XMLHttpRequest();
                                    const url = '/upload?dir=' + encodeURIComponent('${currentDir.absolutePath}') + '&filename=' + encodeURIComponent(file.name);

                                    xhr.open('POST', url, true);

                                    xhr.upload.onprogress = function(e) {
                                        if (e.lengthComputable) {
                                            const currentOverallLoaded = cumulativeUploadedBefore + e.loaded;
                                            const totalPct = totalBatchBytes > 0 ? Math.min(100, Math.round((currentOverallLoaded / totalBatchBytes) * 100)) : 0;
                                            progBar.style.width = totalPct + '%';

                                            const elapsedSec = Math.max(0.1, (Date.now() - startTime) / 1000);
                                            const speedBytesPerSec = currentOverallLoaded / elapsedSec;
                                            const speedStr = formatBytes(speedBytesPerSec) + '/s';

                                            const uploadedStr = formatBytes(currentOverallLoaded);
                                            const totalStr = formatBytes(totalBatchBytes);

                                            progText.innerText = 'Uploading (' + (i + 1) + '/' + files.length + ') ' + file.name + ' • ' + totalPct + '% (' + uploadedStr + ' / ' + totalStr + ' at ' + speedStr + ')';
                                        }
                                    };

                                    xhr.onload = function() {
                                        cumulativeUploadedBefore += file.size;
                                        if (xhr.status !== 200) {
                                            alert('Upload warning: Server responded with status ' + xhr.status + ' for ' + file.name);
                                        }
                                        resolve();
                                    };

                                    xhr.onerror = function() {
                                        cumulativeUploadedBefore += file.size;
                                        alert('Network error while uploading: ' + file.name);
                                        resolve();
                                    };

                                    xhr.send(file);
                                });
                            }

                            progBar.style.width = '100%';
                            progText.innerText = '✅ All ' + files.length + ' file(s) transferred successfully! Refreshing...';
                            setTimeout(() => { window.location.reload(); }, 600);
                        } catch (err) {
                            alert('Upload error: ' + err.message);
                        } finally {
                            isUploading = false;
                        }
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun parseQuery(query: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx != -1) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                map[key] = value
            }
        }
        return map
    }

    fun getAllLocalIpAddresses(context: Context): Pair<String, List<String>> {
        val wifiIps = mutableListOf<String>()
        val hotspotIps = mutableListOf<String>()
        val otherLanIps = mutableListOf<String>()

        try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                for (network in cm.allNetworks) {
                    val caps = cm.getNetworkCapabilities(network)
                    val isWifi = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    val linkProps = cm.getLinkProperties(network)
                    if (linkProps != null) {
                        for (la in linkProps.linkAddresses) {
                            val addr = la.address
                            if (addr is Inet4Address && !addr.isLoopbackAddress) {
                                val host = addr.hostAddress
                                if (host != null && host != "127.0.0.1" && !host.startsWith("169.254")) {
                                    if (isWifi) {
                                        wifiIps.add(host)
                                    } else if (!host.startsWith("10.203") && !host.startsWith("10.64")) {
                                        otherLanIps.add(host)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) { }

        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (nif in interfaces) {
                val name = nif.name.lowercase()
                val isCellularOrVpn = name.contains("rmnet") || name.contains("ccmni") || name.contains("pdp") || name.contains("tun") || name.contains("dummy")
                val isWifiInterface = name.contains("wlan") || name.contains("eth")
                val isHotspotInterface = name.contains("ap") || name.contains("softap") || name.contains("rndis")

                for (addr in Collections.list(nif.inetAddresses)) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        if (host != "127.0.0.1" && !host.startsWith("169.254")) {
                            if (isWifiInterface) {
                                wifiIps.add(host)
                            } else if (isHotspotInterface) {
                                hotspotIps.add(host)
                            } else if (!isCellularOrVpn) {
                                otherLanIps.add(host)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) { }

        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                val ip = String.format(
                    java.util.Locale.US,
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
                if (ip != "0.0.0.0" && ip != "127.0.0.1") {
                    wifiIps.add(ip)
                }
            }
        } catch (e: Exception) { }

        val allUnique = (wifiIps + hotspotIps + otherLanIps).distinct()
        val primary = wifiIps.firstOrNull() ?: hotspotIps.firstOrNull() ?: otherLanIps.firstOrNull() ?: "192.168.1.x"

        return Pair(primary, allUnique)
    }

    private fun sendForbidden(output: OutputStream) {
        val forbidden = "HTTP/1.1 403 Forbidden\r\nContent-Type: text/plain\r\nContent-Length: 17\r\nConnection: close\r\n\r\nAccess Forbidden."
        output.write(forbidden.toByteArray())
        output.flush()
    }

    fun escapeHtml(text: String): String {
        val out = StringBuilder(text.length)
        for (i in 0 until text.length) {
            when (val c = text[i]) {
                '&' -> out.append("&amp;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                '"' -> out.append("&quot;")
                '\'' -> out.append("&#39;")
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    fun isAllowedPath(path: String?, context: Context?): Boolean {
        if (path.isNullOrBlank() || context == null) return false
        return try {
            val targetFile = File(path).canonicalFile
            val targetCanonical = targetFile.canonicalPath

            // 1. Strictly forbid app private internal data directories
            val privateDataDir = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    context.dataDir.canonicalFile.canonicalPath
                } else {
                    context.filesDir.parentFile?.canonicalFile?.canonicalPath ?: ""
                }
            } catch (_: Exception) {
                ""
            }
            if (privateDataDir.isNotEmpty() && (targetCanonical == privateDataDir || targetCanonical.startsWith(privateDataDir + File.separator))) {
                return false
            }

            // 2. Allow primary external storage
            val primaryStorage = Environment.getExternalStorageDirectory().canonicalFile.canonicalPath
            if (targetCanonical == primaryStorage || targetCanonical.startsWith(primaryStorage + File.separator)) {
                return true
            }

            // 3. Allow removable storage volumes (SD cards, USB OTG)
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            if (storageManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val matchedVolume = storageManager.storageVolumes.any { vol ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        vol.directory?.let { dir ->
                            val dirCanonical = dir.canonicalPath
                            targetCanonical == dirCanonical || targetCanonical.startsWith(dirCanonical + File.separator)
                        } ?: false
                    } else {
                        false
                    }
                }
                if (matchedVolume) return true
            }

            false
        } catch (_: Exception) {
            false
        }
    }

    private fun extractCookie(cookieHeader: String, name: String): String? {
        if (cookieHeader.isEmpty()) return null
        for (piece in cookieHeader.split(";")) {
            val trimmed = piece.trim()
            val eq = trimmed.indexOf('=')
            if (eq > 0) {
                val cookieName = trimmed.substring(0, eq).trim()
                if (cookieName.equals(name, ignoreCase = true)) {
                    return trimmed.substring(eq + 1).trim()
                }
            }
        }
        return null
    }

    @Synchronized
    private fun isPinLockedOut(): Boolean {
        return System.currentTimeMillis() < lockoutUntilMs
    }

    @Synchronized
    private fun onPinFailed() {
        failedPinAttempts++
        if (failedPinAttempts >= 5) {
            lockoutUntilMs = System.currentTimeMillis() + 15_000L
        }
    }

    @Synchronized
    private fun onPinSucceeded() {
        failedPinAttempts = 0
        lockoutUntilMs = 0L
    }
}
