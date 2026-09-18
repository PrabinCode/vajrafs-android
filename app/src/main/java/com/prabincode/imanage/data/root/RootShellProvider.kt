/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.data.root

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RootShellProvider {

    private var isRootAvailableCache: Boolean? = null

    /**
     * Checks if the device has a working root (su) binary.
     */
    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        if (isRootAvailableCache != null) return@withContext isRootAvailableCache!!

        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su"
        )

        for (path in paths) {
            if (File(path).exists()) {
                isRootAvailableCache = true
                return@withContext true
            }
        }

        try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            val available = !line.isNullOrEmpty()
            isRootAvailableCache = available
            available
        } catch (e: Exception) {
            isRootAvailableCache = false
            false
        }
    }

    /**
     * Executes a command with root privileges if available.
     */
    suspend fun executeRootCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", command).start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            if (process.exitValue() == 0) {
                Result.success(output.toString())
            } else {
                Result.failure(Exception("Root command exited with code ${process.exitValue()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
