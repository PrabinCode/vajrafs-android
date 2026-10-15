/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.security

import com.prabincode.vajrafs.data.server.LocalWifiServer
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PathTraversalSanitizationTest {

    @Test
    fun testEscapeHtml_escapesDangerousCharacters() {
        val input = "<script>alert('xss & \"hack\"')</script>"
        val expected = "&lt;script&gt;alert(&#39;xss &amp; &quot;hack&quot;&#39;)&lt;/script&gt;"
        val actual = LocalWifiServer.escapeHtml(input)
        assertEquals(expected, actual)
    }

    @Test
    fun testEscapeHtml_preservesSafeCharacters() {
        val input = "Normal_File-Name.123.pdf"
        assertEquals(input, LocalWifiServer.escapeHtml(input))
    }

    @Test
    fun testFilenameSanitization_stripsDirectoryTraversal() {
        val malicious1 = "../../../../data/data/com.prabincode.vajrafs/databases/db"
        val clean1 = File(malicious1).name
        assertEquals("db", clean1)

        val malicious2 = "..\\..\\windows\\system32\\calc.exe"
        val clean2 = File(malicious2).name
        assertFalse(clean2.contains(".."))
    }

    @Test
    fun testIsAllowedPath_rejectsNullAndBlank() {
        assertFalse(LocalWifiServer.isAllowedPath(null, null))
        assertFalse(LocalWifiServer.isAllowedPath("", null))
        assertFalse(LocalWifiServer.isAllowedPath("   ", null))
    }
}
