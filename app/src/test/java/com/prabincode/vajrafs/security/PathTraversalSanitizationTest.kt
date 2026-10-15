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

    // Uses LocalWifiServer.sanitizeFileName, not File(name).name: the latter only splits on the
    // host OS separator, so backslash paths behave differently on Windows and Linux/Android.
    @Test
    fun testFilenameSanitization_stripsForwardSlashTraversal() {
        val clean = LocalWifiServer.sanitizeFileName("../../../../data/data/com.prabincode.vajrafs/databases/db", "fallback")
        assertEquals("db", clean)
    }

    @Test
    fun testFilenameSanitization_stripsBackslashTraversal() {
        val clean = LocalWifiServer.sanitizeFileName("..\\..\\windows\\system32\\calc.exe", "fallback")
        assertEquals("calc.exe", clean)
        assertFalse(clean.contains(".."))
    }

    @Test
    fun testFilenameSanitization_rejectsDotSegmentsAndBlank() {
        assertEquals("fallback", LocalWifiServer.sanitizeFileName("..", "fallback"))
        assertEquals("fallback", LocalWifiServer.sanitizeFileName(".", "fallback"))
        assertEquals("fallback", LocalWifiServer.sanitizeFileName("photos/..", "fallback"))
        assertEquals("fallback", LocalWifiServer.sanitizeFileName("   ", "fallback"))
        assertEquals("fallback", LocalWifiServer.sanitizeFileName(null, "fallback"))
    }

    @Test
    fun testFilenameSanitization_keepsNormalNamesAndDropsControlChars() {
        assertEquals("Report 2026.pdf", LocalWifiServer.sanitizeFileName("Report 2026.pdf", "fallback"))
        assertEquals("evil.txt", LocalWifiServer.sanitizeFileName("ev\u0000il.txt\n", "fallback"))
    }

    @Test
    fun testIsAllowedPath_rejectsNullAndBlank() {
        assertFalse(LocalWifiServer.isAllowedPath(null, null))
        assertFalse(LocalWifiServer.isAllowedPath("", null))
        assertFalse(LocalWifiServer.isAllowedPath("   ", null))
    }
}
