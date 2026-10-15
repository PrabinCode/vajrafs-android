/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.security

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipSlipSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testZipSlip_detectsAndBlocksEscapeAttempt() {
        val destDir = tempFolder.newFolder("target_extract")
        val canonicalDestDir = destDir.canonicalPath

        // Create a zip with a traversal entry pointing outside destDir
        val zipFile = tempFolder.newFile("malicious.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val evilEntry = ZipEntry("../../escaped_file.txt")
            zos.putNextEntry(evilEntry)
            zos.write("malicious payload".toByteArray())
            zos.closeEntry()
        }

        var exceptionThrown = false
        try {
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val newFile = File(destDir, entry.name)
                    val canonicalTarget = newFile.canonicalPath
                    if (!canonicalTarget.startsWith(canonicalDestDir + File.separator) && canonicalTarget != canonicalDestDir) {
                        throw SecurityException("Zip entry is outside target directory: ${entry.name}")
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (e: SecurityException) {
            exceptionThrown = true
            assertTrue(e.message!!.contains("outside target directory"))
        }

        assertTrue("Expected SecurityException to be thrown for Zip Slip entry", exceptionThrown)
    }

    @Test
    fun testZipSlip_allowsLegitimateNestedFiles() {
        val destDir = tempFolder.newFolder("target_legit")
        val canonicalDestDir = destDir.canonicalPath

        val zipFile = tempFolder.newFile("benign.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val entry1 = ZipEntry("documents/hello.txt")
            zos.putNextEntry(entry1)
            zos.write("hello world".toByteArray())
            zos.closeEntry()

            val entry2 = ZipEntry("images/photo.jpg")
            zos.putNextEntry(entry2)
            zos.write("image data".toByteArray())
            zos.closeEntry()
        }

        var extractedCount = 0
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = File(destDir, entry.name)
                val canonicalTarget = newFile.canonicalPath
                if (!canonicalTarget.startsWith(canonicalDestDir + File.separator) && canonicalTarget != canonicalDestDir) {
                    throw SecurityException("Zip entry is outside target directory: ${entry.name}")
                }
                newFile.parentFile?.mkdirs()
                FileOutputStream(newFile).use { fos ->
                    zis.copyTo(fos)
                }
                extractedCount++
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        assertEquals(2, extractedCount)
        assertTrue(File(destDir, "documents/hello.txt").exists())
        assertTrue(File(destDir, "images/photo.jpg").exists())
    }
}
