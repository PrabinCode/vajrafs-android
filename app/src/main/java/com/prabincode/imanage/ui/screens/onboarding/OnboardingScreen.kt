/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.ui.screens.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class OnboardingPage(
    val badge: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color,
    val gradientColors: List<Color>,
    val highlights: List<String>
)

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit
) {
    val context = LocalContext.current

    fun completeOnboarding() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                context.startActivity(intent)
            }
        }
        onFinish()
    }

    val pages = listOf(
        OnboardingPage(
            badge = "100% PRIVATE • ZERO AI • ZERO ADS",
            title = "100% Yours. Zero AI. Zero Trackers.",
            subtitle = "Your files belong strictly to you. No AI models scraping your private documents, zero analytics SDKs, and zero internet tracking.",
            icon = Icons.Outlined.VerifiedUser,
            accentColor = Color(0xFF00897B),
            gradientColors = listOf(Color(0xFF00897B), Color(0xFF004D40)),
            highlights = listOf(
                "Zero AI data scraping or remote model training",
                "Zero 3rd-party trackers, analytics SDKs, or telemetry",
                "100% Offline & Open-Source — files never leave your hardware"
            )
        ),
        OnboardingPage(
            badge = "FAST & INTUITIVE",
            title = "Smart & Lightweight File Management",
            subtitle = "Browse, organize, and manage your device storage with lightning speed and zero background bloat.",
            icon = Icons.Outlined.FolderShared,
            accentColor = Color(0xFF0288D1),
            gradientColors = listOf(Color(0xFF0288D1), Color(0xFF01579B)),
            highlights = listOf(
                "Instant categorized navigation (Media, Docs, APKs, Code)",
                "Fast batch file operations, custom tags & bookmarks",
                "Optional elevated Root Shell explorer for power users"
            )
        ),
        OnboardingPage(
            badge = "MILITARY-GRADE SECURITY",
            title = "Encrypted Vault & Secure Shredder",
            subtitle = "Keep sensitive documents and private photos safe with hardware-backed AES-256-GCM cryptography.",
            icon = Icons.Outlined.EnhancedEncryption,
            accentColor = Color(0xFFE91E63),
            gradientColors = listOf(Color(0xFFE91E63), Color(0xFF880E4F)),
            highlights = listOf(
                "AES-256-GCM encrypted hidden private vault",
                "Biometric & PBKDF2 Master PIN auto-lock protection",
                "Multi-pass secure file shredder & anti-snooping FLAG_SECURE"
            )
        ),
        OnboardingPage(
            badge = "ON-DEVICE CLEANER",
            title = "Storage Analyzer & Cleanup",
            subtitle = "Gain deep insights into disk space usage and eliminate duplicate clutter safely without cloud dependencies.",
            icon = Icons.Outlined.PieChart,
            accentColor = Color(0xFF5E35B1),
            gradientColors = listOf(Color(0xFF5E35B1), Color(0xFF311B92)),
            highlights = listOf(
                "Visual storage breakdown by file types and large folders",
                "Intelligent duplicate file scanner & one-tap cleaner",
                "Safe Recycle Bin with instant one-tap restoration"
            )
        ),
        OnboardingPage(
            badge = "ZERO CABLES • ZERO INTERNET",
            title = "Local Wi-Fi Share & Built-in Viewers",
            subtitle = "Transfer files to your PC wirelessly and view all your media without installing extra third-party apps.",
            icon = Icons.Outlined.Devices,
            accentColor = Color(0xFF00ACC1),
            gradientColors = listOf(Color(0xFF00ACC1), Color(0xFF006064)),
            highlights = listOf(
                "High-speed local Wi-Fi PC transfer server (browser-based)",
                "Built-in video player with background audio playback",
                "Integrated PDF viewer, image gallery & full code editor"
            )
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.size - 1

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App brand indicator
                Text(
                    text = "IManage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Skip button (visible on non-last pages)
                AnimatedVisibility(
                    visible = !isLastPage,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    TextButton(
                        onClick = { completeOnboarding() },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Skip",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Animated Dots / Pill Indicator
                Row(
                    modifier = Modifier.padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (index in pages.indices) {
                        val isSelected = pagerState.currentPage == index
                        val width = if (isSelected) 24.dp else 8.dp
                        val color = if (isSelected) {
                            pages[pagerState.currentPage].accentColor
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                        }

                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(color)
                                .clickable {
                                    scope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                        )
                    }
                }

                // Action Button (Next or Get Started)
                if (isLastPage) {
                    Button(
                        onClick = { completeOnboarding() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = pages[pagerState.currentPage].accentColor
                        )
                    ) {
                        Text(
                            text = "Get Started",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "Next",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) { pageIndex ->
            val page = pages[pageIndex]
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Hero Icon with Gradient Glow Container
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(page.gradientColors)
                        )
                ) {
                    Icon(
                        imageVector = page.icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(52.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Badge Pill
                Surface(
                    shape = RoundedCornerShape(50),
                    color = page.accentColor.copy(alpha = 0.12f),
                    modifier = Modifier.border(
                        width = 1.dp,
                        color = page.accentColor.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(50)
                    )
                ) {
                    Text(
                        text = page.badge,
                        color = page.accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Subtitle
                Text(
                    text = page.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Feature Highlights Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (highlight in page.highlights) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = page.accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = highlight,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
