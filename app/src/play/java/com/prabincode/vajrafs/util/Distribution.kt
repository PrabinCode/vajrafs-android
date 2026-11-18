/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.util

/**
 * Google Play build: no REQUEST_INSTALL_PACKAGES and no links to places that host APKs
 * (Device and Network Abuse policy). Updates come only from Google Play.
 */
object Distribution {
    /** Whether tapping an APK hands it to Android's package installer. */
    const val CAN_INSTALL_APKS = false

    /** "About the project" link shown in Settings and on the Wi-Fi transfer pages. */
    const val PROJECT_URL = "https://pcshrestha.com.np/vajrafs"
    const val PROJECT_LINK_LABEL = "Website"
}
