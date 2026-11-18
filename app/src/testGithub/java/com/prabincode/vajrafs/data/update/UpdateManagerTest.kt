/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun isVersionNewer_detectsPatchAndMultiDigitUpdates() {
        assertTrue(isVersionNewer("2.2.1", "2.2.0"))
        assertTrue(isVersionNewer("v2.10.0", "2.2.1"))
        assertTrue(isVersionNewer("2.2.1", "2.2.1-beta.1"))
        assertFalse(isVersionNewer("2.2.1", "2.2.1"))
        assertFalse(isVersionNewer("2.0.0", "2.2.0"))
        assertFalse(isVersionNewer("2.2.1-rc1", "2.2.1"))
    }

    @Test
    fun isVersionNewer_usesVersionCodeWhenVersionNamesMatch() {
        assertTrue(isVersionNewer("2.2.1", "2.2.1", remoteCode = 14, currentCode = 13))
        assertFalse(isVersionNewer("2.2.1", "2.2.1", remoteCode = 13, currentCode = 13))
        assertFalse(isVersionNewer("2.2.1", "2.2.1", remoteCode = 0, currentCode = 13))
    }

    @Test
    fun selectPreferredUpdateInfo_prefersNewerGitHubReleaseOverStaleVersionJson() {
        val staleVersionJson = AppUpdateInfo(
            versionCode = 10,
            versionName = "2.0.0",
            minSupportedVersion = 1,
            releaseDate = "2026-10-15",
            downloadUrl = "https://github.com/PrabinCode/vajrafs-android/releases/download/v2.0.0/prabincode-vajrafs-v2.0.0-github-release.apk",
            fallbackUrl = "https://pcshrestha.com.np/vajrafs",
            releaseNotes = listOf("Old v2.0.0 notes"),
            isMandatory = false,
            isUpdateAvailable = false
        )
        val githubLatest = AppUpdateInfo(
            versionCode = 0,
            versionName = "2.2.1",
            minSupportedVersion = 1,
            releaseDate = "2026-11-11",
            downloadUrl = "https://github.com/PrabinCode/vajrafs-android/releases/download/v2.2.1/prabincode-vajrafs-v2.2.1-github-release.apk",
            fallbackUrl = "https://github.com/PrabinCode/vajrafs-android/releases/tag/v2.2.1",
            releaseNotes = emptyList(),
            isMandatory = false,
            isUpdateAvailable = true
        )

        val chosen = selectPreferredUpdateInfo(
            githubInfo = githubLatest,
            versionJsonInfo = staleVersionJson,
            currentName = "2.2.0",
            currentCode = 12
        )

        assertEquals("2.2.1", chosen.versionName)
        assertTrue(chosen.isUpdateAvailable)
        assertTrue(chosen.downloadUrl.contains("v2.2.1"))
    }

    @Test
    fun selectPreferredUpdateInfo_mergesMetadataWhenVersionsMatch() {
        val site = AppUpdateInfo(
            versionCode = 14,
            versionName = "2.3.0",
            minSupportedVersion = 12,
            releaseDate = "2026-12-01",
            downloadUrl = "https://pcshrestha.com.np/vajrafs",
            fallbackUrl = "https://pcshrestha.com.np/vajrafs",
            releaseNotes = listOf("Curated note 1", "Curated note 2"),
            isMandatory = true,
            isUpdateAvailable = true
        )
        val github = AppUpdateInfo(
            versionCode = 0,
            versionName = "2.3.0",
            minSupportedVersion = 1,
            releaseDate = "2026-12-01",
            downloadUrl = "https://github.com/PrabinCode/vajrafs-android/releases/download/v2.3.0/prabincode-vajrafs-v2.3.0-github-release.apk",
            fallbackUrl = "https://github.com/PrabinCode/vajrafs-android/releases/tag/v2.3.0",
            releaseNotes = listOf("GitHub raw note"),
            isMandatory = false,
            isUpdateAvailable = true
        )

        val merged = selectPreferredUpdateInfo(github, site, currentName = "2.2.1", currentCode = 13)
        assertEquals(14, merged.versionCode)
        assertEquals(12, merged.minSupportedVersion)
        assertTrue(merged.isMandatory)
        assertEquals(listOf("Curated note 1", "Curated note 2"), merged.releaseNotes)
        assertTrue(merged.downloadUrl.endsWith("-github-release.apk"))
    }

    @Test
    fun selectApkAssetUrl_matchesBuildTypeCorrectly() {
        val assets = listOf(
            "prabincode-vajrafs-v2.2.1-play-release.aab" to "https://example.com/app.aab",
            "prabincode-vajrafs-v2.2.1-github-debug.apk" to "https://example.com/debug.apk",
            "prabincode-vajrafs-v2.2.1-github-release.apk" to "https://example.com/release.apk"
        )

        assertEquals(
            "https://example.com/release.apk",
            selectApkAssetUrl(assets, isDebugBuild = false, fallbackUrl = "https://example.com/tag")
        )
        assertEquals(
            "https://example.com/debug.apk",
            selectApkAssetUrl(assets, isDebugBuild = true, fallbackUrl = "https://example.com/tag")
        )
        assertEquals(
            "https://example.com/tag",
            selectApkAssetUrl(emptyList(), isDebugBuild = false, fallbackUrl = "https://example.com/tag")
        )
    }

    @Test
    fun extractTagAndAssetsFromGitHubWebHtml_parsesLinks() {
        assertEquals(
            "v2.2.1",
            extractTagFromReleaseUrl("https://github.com/PrabinCode/vajrafs-android/releases/tag/v2.2.1")
        )

        val html = """
            <a href="/PrabinCode/vajrafs-android/releases/download/v2.2.1/prabincode-vajrafs-v2.2.1-github-debug.apk" rel="nofollow">debug</a>
            <a href="/PrabinCode/vajrafs-android/releases/download/v2.2.1/prabincode-vajrafs-v2.2.1-github-release.apk" rel="nofollow">release</a>
        """.trimIndent()

        val extracted = extractApkAssetsFromExpandedHtml(html)
        assertEquals(2, extracted.size)
        assertEquals(
            "https://github.com/PrabinCode/vajrafs-android/releases/download/v2.2.1/prabincode-vajrafs-v2.2.1-github-release.apk",
            selectApkAssetUrl(extracted, isDebugBuild = false, fallbackUrl = "")
        )
    }

    @Test
    fun parseReleaseNotes_stripsMarkdownAndStopsAtDownloads() {
        val body = """
            ## What's new in VajraFS 2.2.0

            ### Video player
            - **Gestures:** swipe up/down for brightness
            - Resume where you left off

            ## Downloads
            - `prabincode-vajrafs-v2.2.0-github-release.apk`

            **Full Changelog**: https://github.com/PrabinCode/vajrafs-android/compare/v2.1.0...v2.2.0
        """.trimIndent()

        val parsed = parseReleaseNotes(body)
        assertEquals(
            listOf(
                "Video player",
                "Gestures: swipe up/down for brightness",
                "Resume where you left off"
            ),
            parsed
        )

        val changelogOnly = "**Full Changelog**: https://github.com/PrabinCode/vajrafs-android/compare/v2.2.0...v2.2.1"
        assertTrue(parseReleaseNotes(changelogOnly).isEmpty())
    }

    @Test
    fun extractIsoDate_parsesTimestampPrefix() {
        assertEquals("2026-11-11", extractIsoDate("2026-11-11T18:33:11Z"))
        assertEquals("", extractIsoDate("invalid"))
    }
}
