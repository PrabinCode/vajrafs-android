/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.archive

import com.prabincode.imanage.data.archive.ArchiveEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ArchiveEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testCreateAndExtractZip_unencrypted() = runBlocking {
        val file1 = tempFolder.newFile("file1.txt").apply { writeText("File 1 contents") }
        val file2 = tempFolder.newFile("file2.txt").apply { writeText("File 2 contents") }

        val destZip = tempFolder.root.resolve("output.zip")
        val createResult = ArchiveEngine.createZip(listOf(file1, file2), destZip)
        assertTrue(createResult.isSuccess)
        assertTrue(destZip.exists())
        assertTrue(destZip.length() > 0)

        val extractDir = tempFolder.newFolder("extracted")
        val extractResult = ArchiveEngine.extractArchive(destZip, extractDir)
        assertTrue(extractResult.isSuccess)

        val extracted1 = File(extractDir, "file1.txt")
        val extracted2 = File(extractDir, "file2.txt")
        assertTrue(extracted1.exists())
        assertTrue(extracted2.exists())
        assertEquals("File 1 contents", extracted1.readText())
        assertEquals("File 2 contents", extracted2.readText())
    }

    @Test
    fun testCreateAndExtractZip_encryptedAes256() = runBlocking {
        val secretDoc = tempFolder.newFile("secret.txt").apply { writeText("Confidential Data 12345") }
        val destZip = tempFolder.root.resolve("encrypted.zip")
        val password = "SuperSecretPassword123!"

        val createResult = ArchiveEngine.createZip(listOf(secretDoc), destZip, password = password)
        assertTrue(createResult.isSuccess)
        assertTrue(destZip.exists())

        // Extract with correct password
        val extractDir = tempFolder.newFolder("extracted_secret")
        val extractResult = ArchiveEngine.extractArchive(destZip, extractDir, password = password)
        assertTrue(extractResult.isSuccess)

        val extractedSecret = File(extractDir, "secret.txt")
        assertTrue(extractedSecret.exists())
        assertEquals("Confidential Data 12345", extractedSecret.readText())
    }
}
