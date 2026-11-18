/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.util

/** GitHub (sideload) build: may open APKs in the system installer and links to the source repo. */
object Distribution {
    /** Whether tapping an APK hands it to Android's package installer. */
    const val CAN_INSTALL_APKS = true

    /** "About the project" link shown in Settings and on the Wi-Fi transfer pages. */
    const val PROJECT_URL = "https://github.com/PrabinCode/vajrafs-android"
    const val PROJECT_LINK_LABEL = "GitHub"
}
