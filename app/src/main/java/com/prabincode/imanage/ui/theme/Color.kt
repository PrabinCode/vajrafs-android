/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Primary Colors - Deep Teal & Slate Blue
val SlatePrimaryLight = Color(0xFF006874)
val SlateOnPrimaryLight = Color(0xFFFFFFFF)
val SlatePrimaryContainerLight = Color(0xFF97F0FF)
val SlateOnPrimaryContainerLight = Color(0xFF001F24)

val SlateSecondaryLight = Color(0xFF4A6267)
val SlateOnSecondaryLight = Color(0xFFFFFFFF)
val SlateSecondaryContainerLight = Color(0xFFCDE7ED)
val SlateOnSecondaryContainerLight = Color(0xFF051F23)

val SlateBackgroundLight = Color(0xFFFBFCFD)
val SlateOnBackgroundLight = Color(0xFF191C1D)
val SlateSurfaceLight = Color(0xFFFBFCFD)
val SlateOnSurfaceLight = Color(0xFF191C1D)

// Dark Palette (OLED friendly)
val SlatePrimaryDark = Color(0xFF4FD8EB)
val SlateOnPrimaryDark = Color(0xFF00363D)
val SlatePrimaryContainerDark = Color(0xFF004F58)
val SlateOnPrimaryContainerDark = Color(0xFF97F0FF)

val SlateSecondaryDark = Color(0xFFB1CBD0)
val SlateOnSecondaryDark = Color(0xFF1C3438)
val SlateSecondaryContainerDark = Color(0xFF334B4F)
val SlateOnSecondaryContainerDark = Color(0xFFCDE7ED)

val SlateBackgroundDark = Color(0xFF111415)
val SlateOnBackgroundDark = Color(0xFFE1E3E4)
val SlateSurfaceDark = Color(0xFF191C1D)
val SlateOnSurfaceDark = Color(0xFFE1E3E4)

// Category Specific Colors
val ColorFolder = Color(0xFFFFB74D)
val ColorImage = Color(0xFF42A5F5)
val ColorVideo = Color(0xFFEF5350)
val ColorAudio = Color(0xFFAB47BC)
val ColorDocument = Color(0xFF26A69A)
val ColorArchive = Color(0xFFFF7043)
val ColorApk = Color(0xFF66BB6A)
val ColorCode = Color(0xFF5C6BC0)
val ColorVault = Color(0xFFEC407A)
val ColorTrash = Color(0xFFE53935)

val LightColorScheme = lightColorScheme(
    primary = SlatePrimaryLight,
    onPrimary = SlateOnPrimaryLight,
    primaryContainer = SlatePrimaryContainerLight,
    onPrimaryContainer = SlateOnPrimaryContainerLight,
    secondary = SlateSecondaryLight,
    onSecondary = SlateOnSecondaryLight,
    secondaryContainer = SlateSecondaryContainerLight,
    onSecondaryContainer = SlateOnSecondaryContainerLight,
    background = SlateBackgroundLight,
    onBackground = SlateOnBackgroundLight,
    surface = SlateSurfaceLight,
    onSurface = SlateOnSurfaceLight
)

val DarkColorScheme = darkColorScheme(
    primary = SlatePrimaryDark,
    onPrimary = SlateOnPrimaryDark,
    primaryContainer = SlatePrimaryContainerDark,
    onPrimaryContainer = SlateOnPrimaryContainerDark,
    secondary = SlateSecondaryDark,
    onSecondary = SlateOnSecondaryDark,
    secondaryContainer = SlateSecondaryContainerDark,
    onSecondaryContainer = SlateOnSecondaryContainerDark,
    background = SlateBackgroundDark,
    onBackground = SlateOnBackgroundDark,
    surface = SlateSurfaceDark,
    onSurface = SlateOnSurfaceDark
)
