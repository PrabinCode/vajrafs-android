/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.crypto

import com.prabincode.imanage.data.crypto.ChecksumHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChecksumHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testChecksumCalculation_knownVector() = runBlocking {
        val testFile = tempFolder.newFile("test_checksum.txt")
        // "Hello, World!" known hashes
        testFile.writeText("Hello, World!")

        val result = ChecksumHelper.calculateChecksums(testFile)
        assertTrue(result.isSuccess)

        val checksums = result.getOrNull()
        assertNotNull(checksums)
        // MD5 of "Hello, World!" is 65a8e27d8879283831b664bd8b7f0ad4
        assertEquals("65a8e27d8879283831b664bd8b7f0ad4", checksums!!.md5)
        // SHA-1 of "Hello, World!" is 0a0a9f2a6772942557ab5355d76af442f8f65e01
        assertEquals("0a0a9f2a6772942557ab5355d76af442f8f65e01", checksums.sha1)
        // SHA-256 of "Hello, World!" is dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f
        assertEquals("dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f", checksums.sha256)
        // CRC32 of "Hello, World!" is EC4AC3D0
        assertEquals("EC4AC3D0", checksums.crc32.uppercase())
    }

    @Test
    fun testChecksumCalculation_nonExistentFile_fails() = runBlocking {
        val nonExistent = tempFolder.root.resolve("does_not_exist.bin")
        val result = ChecksumHelper.calculateChecksums(nonExistent)
        assertTrue(result.isFailure)
    }
}
