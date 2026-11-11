/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.ui.navigation

import androidx.lifecycle.Lifecycle
import com.prabincode.vajrafs.data.model.StorageVolumeInfo
import com.prabincode.vajrafs.ui.components.PathSegment
import com.prabincode.vajrafs.ui.components.parsePathSegments
import com.prabincode.vajrafs.ui.screens.explorer.resolveUpNavigationPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationGuardAndPathTest {

    private val volumes = listOf(
        StorageVolumeInfo("Internal Storage", "/storage/emulated/0", 1000L, 500L, isRemovable = false, isPrimary = true),
        StorageVolumeInfo("SD Card", "/storage/1234-ABCD", 2000L, 1000L, isRemovable = true, isPrimary = false)
    )

    @Test
    fun canPopBackStack_allowsPopOnlyForResumedTopEntryWithPreviousEntry() {
        assertTrue(
            canPopBackStack(
                currentEntryId = "explorer-1",
                callerEntryId = "explorer-1",
                callerLifecycleState = Lifecycle.State.RESUMED,
                hasPreviousEntry = true
            )
        )
    }

    @Test
    fun canPopBackStack_blocksSecondTapDuringExitTransition() {
        // After the first tap pops Explorer, currentEntryId becomes Home while the exiting
        // Explorer composable still finishes fading out.
        assertFalse(
            canPopBackStack(
                currentEntryId = "home-0",
                callerEntryId = "explorer-1",
                callerLifecycleState = Lifecycle.State.RESUMED,
                hasPreviousEntry = false
            )
        )
        assertFalse(
            canPopBackStack(
                currentEntryId = "explorer-1",
                callerEntryId = "explorer-1",
                callerLifecycleState = Lifecycle.State.STARTED,
                hasPreviousEntry = true
            )
        )
    }

    @Test
    fun canPopBackStack_neverPopsRootHomeDestination() {
        assertFalse(
            canPopBackStack(
                currentEntryId = "home-0",
                callerEntryId = "home-0",
                callerLifecycleState = Lifecycle.State.RESUMED,
                hasPreviousEntry = false
            )
        )
    }

    @Test
    fun canNavigateFrom_blocksDoubleNavigationWhenCallerNoLongerCurrentOrResumed() {
        assertTrue(canNavigateFrom("home-0", "home-0", Lifecycle.State.RESUMED))
        assertFalse(canNavigateFrom("explorer-1", "home-0", Lifecycle.State.RESUMED))
        assertFalse(canNavigateFrom("home-0", "home-0", Lifecycle.State.CREATED))
    }

    @Test
    fun parsePathSegments_anchorsInternalStorageWithoutRestrictedParents() {
        assertEquals(
            listOf(PathSegment("Internal Storage", "/storage/emulated/0")),
            parsePathSegments("/storage/emulated/0", volumes, "/storage/emulated/0")
        )
        assertEquals(
            listOf(
                PathSegment("Internal Storage", "/storage/emulated/0"),
                PathSegment("DCIM", "/storage/emulated/0/DCIM"),
                PathSegment("Camera", "/storage/emulated/0/DCIM/Camera")
            ),
            parsePathSegments("/storage/emulated/0/DCIM/Camera", volumes, "/storage/emulated/0")
        )
    }

    @Test
    fun parsePathSegments_anchorsRemovableVolumeAndRoot() {
        assertEquals(
            listOf(
                PathSegment("SD Card", "/storage/1234-ABCD"),
                PathSegment("Music", "/storage/1234-ABCD/Music")
            ),
            parsePathSegments("/storage/1234-ABCD/Music", volumes, "/storage/emulated/0")
        )
        assertEquals(
            listOf(PathSegment("System Root (/)", "/")),
            parsePathSegments("/", volumes, "/storage/emulated/0")
        )
    }

    @Test
    fun resolveUpNavigationPath_stopsAtVolumeRootsAndNavigatesInsideSubfolders() {
        val volumePaths = volumes.map { it.path }
        assertNull(resolveUpNavigationPath("/storage/emulated/0", volumePaths, "/storage/emulated/0") { true })
        assertNull(resolveUpNavigationPath("/storage/1234-ABCD", volumePaths, "/storage/emulated/0") { true })
        assertNull(resolveUpNavigationPath("/storage", volumePaths, "/storage/emulated/0") { true })

        assertEquals(
            "/storage/emulated/0",
            resolveUpNavigationPath("/storage/emulated/0/Download", volumePaths, "/storage/emulated/0") { true }
        )
        assertEquals(
            "/storage/emulated/0/DCIM",
            resolveUpNavigationPath("/storage/emulated/0/DCIM/Camera", volumePaths, "/storage/emulated/0") { true }
        )
        assertEquals(
            "/system",
            resolveUpNavigationPath("/system/app", volumePaths + "/", "/storage/emulated/0") { true }
        )
    }
}
