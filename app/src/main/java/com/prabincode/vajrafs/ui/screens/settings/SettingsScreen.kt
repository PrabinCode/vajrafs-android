/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prabincode.vajrafs.data.crypto.PinCryptoHelper
import com.prabincode.vajrafs.util.AppLinks
import com.prabincode.vajrafs.util.formatFileSize
import com.prabincode.vajrafs.util.safeStatusBarInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, coil.annotation.ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPinSetup: () -> Unit,
    onNavigateToWifiShare: () -> Unit,
    onNavigateToOnboarding: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("vajrafs_prefs", Context.MODE_PRIVATE) }

    var flagSecureEnabled by remember {
        mutableStateOf(prefs.getBoolean("flag_secure", true))
    }
    var shredPasses by remember {
        mutableIntStateOf(prefs.getInt("shred_passes", 3))
    }
    var pinEnabled by remember {
        mutableStateOf(PinCryptoHelper.isPinEnabled(context))
    }
    var autoLockTimeout by remember {
        mutableIntStateOf(PinCryptoHelper.getAutoLockTimeoutSeconds(context))
    }
    var showHiddenFiles by remember {
        mutableStateOf(prefs.getBoolean("show_hidden_files", false))
    }
    var showNomediaFiles by remember {
        mutableStateOf(prefs.getBoolean("show_nomedia_files", false))
    }
    var showTimeoutMenu by remember { mutableStateOf(false) }
    var showDefaultAppDialog by remember { mutableStateOf(false) }

    var cacheBytes by remember { mutableLongStateOf(0L) }
    var vaultBytes by remember { mutableLongStateOf(0L) }
    var trashBytes by remember { mutableLongStateOf(0L) }
    var isCalculatingStorage by remember { mutableStateOf(true) }
    var isClearingCache by remember { mutableStateOf(false) }

    fun refreshStorageSizes() {
        scope.launch(Dispatchers.IO) {
            isCalculatingStorage = true
            val app = context.applicationContext as? com.prabincode.vajrafs.VajraApp
            val cacheSize = calculateCacheSize(context)
            val vSize = app?.vaultRepository?.getVaultStorageSize() ?: 0L
            val tSize = app?.trashRepository?.getTrashStorageSize() ?: 0L
            withContext(Dispatchers.Main) {
                cacheBytes = cacheSize
                vaultBytes = vSize
                trashBytes = tSize
                isCalculatingStorage = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshStorageSizes()
    }

    fun clearAppCache() {
        isClearingCache = true
        scope.launch(Dispatchers.IO) {
            val app = context.applicationContext as? com.prabincode.vajrafs.VajraApp
            var freedBytes = 0L

            // 1. External opened sessions
            freedBytes += com.prabincode.vajrafs.util.ExternalFileResolver.cleanExternalCache(context)

            // 2. Safe Vault decrypted previews
            app?.vaultRepository?.let {
                freedBytes += it.clearTempDecryptedFiles()
            }

            // 3. Coil disk & memory cache
            try {
                val imageLoader = coil.Coil.imageLoader(context)
                imageLoader.diskCache?.clear()
                imageLoader.memoryCache?.clear()
            } catch (_: Exception) {}

            // 4. Wipe internal and external cache directories
            try {
                context.cacheDir?.listFiles()?.forEach { file ->
                    freedBytes += getFolderSize(file)
                    file.deleteRecursively()
                }
                context.externalCacheDir?.listFiles()?.forEach { file ->
                    freedBytes += getFolderSize(file)
                    file.deleteRecursively()
                }
            } catch (_: Exception) {}

            val newCacheSize = calculateCacheSize(context)
            withContext(Dispatchers.Main) {
                cacheBytes = newCacheSize
                isClearingCache = false
                Toast.makeText(
                    context,
                    "Cache cleared! Freed ${formatFileSize(freedBytes)}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = safeStatusBarInsets(),
                title = { Text("Settings & Security", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Wireless PC Transfer
            item {
                Text(
                    text = "Connectivity & Sharing",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToWifiShare() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFF006874))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("PC / Wi-Fi Transfer Server", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Transfer files to/from your PC browser over local Wi-Fi without internet or cables.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Display & File Preferences
            item {
                Text(
                    text = "Display & File Preferences",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Show / Hide Hidden Files
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show Hidden Files & Folders", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Display files and folders starting with a dot (e.g. .thumbnails, .system).",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showHiddenFiles,
                            onCheckedChange = {
                                showHiddenFiles = it
                                prefs.edit().putBoolean("show_hidden_files", it).apply()
                            }
                        )
                    }
                }
            }

            // Show / Hide .nomedia Files
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.HideImage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show .nomedia Files & Folders", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "When off, hides .nomedia markers, folders containing them, and their media from Recent Files, categories and search.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showNomediaFiles,
                            onCheckedChange = {
                                showNomediaFiles = it
                                prefs.edit().putBoolean("show_nomedia_files", it).apply()
                            }
                        )
                    }
                }
            }

            // Default App & File Associations
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDefaultAppDialog = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.AppShortcut, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Default App & File Associations", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Set VajraFS as default viewer for PDFs, images, videos, audio, code & archives.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Security & Privacy
            item {
                Text(
                    text = "Security & Master Lock",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Master PIN Setup
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.Pin, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Master PIN / Password", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                if (pinEnabled) "Master PIN is active (PBKDF2 encrypted)" else "Set a 4-digit PIN for app & vault unlock fallback",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (pinEnabled) {
                            TextButton(onClick = {
                                PinCryptoHelper.disablePin(context)
                                pinEnabled = false
                                Toast.makeText(context, "PIN disabled", Toast.LENGTH_SHORT).show()
                            }) {
                                Text("Remove", color = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            Button(onClick = onNavigateToPinSetup) {
                                Text("Setup")
                            }
                        }
                    }
                }
            }

            // Auto-Lock Timeout
            if (pinEnabled) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Lock Inactivity Timeout", fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(2.dp))
                                val timeoutLabel = when (autoLockTimeout) {
                                    0 -> "Immediately on background"
                                    30 -> "30 seconds"
                                    60 -> "1 minute"
                                    300 -> "5 minutes"
                                    900 -> "15 minutes"
                                    else -> "$autoLockTimeout seconds"
                                }
                                Text("Lock after: $timeoutLabel", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box {
                                TextButton(onClick = { showTimeoutMenu = true }) {
                                    Text("Change")
                                }
                                DropdownMenu(
                                    expanded = showTimeoutMenu,
                                    onDismissRequest = { showTimeoutMenu = false }
                                ) {
                                    val options = listOf(
                                        0 to "Immediately",
                                        30 to "30 seconds",
                                        60 to "1 minute",
                                        300 to "5 minutes",
                                        900 to "15 minutes"
                                    )
                                    options.forEach { (secs, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                autoLockTimeout = secs
                                                PinCryptoHelper.setAutoLockTimeoutSeconds(context, secs)
                                                showTimeoutMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Anti-Snooping FLAG_SECURE
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Screen Obfuscation (FLAG_SECURE)", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Blocks screenshots, screen recordings, and obscures previews in the recent apps switcher.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = flagSecureEnabled,
                            onCheckedChange = {
                                flagSecureEnabled = it
                                prefs.edit().putBoolean("flag_secure", it).apply()
                                val activity = context as? Activity
                                if (it) {
                                    activity?.window?.setFlags(
                                        WindowManager.LayoutParams.FLAG_SECURE,
                                        WindowManager.LayoutParams.FLAG_SECURE
                                    )
                                } else {
                                    activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                                }
                            }
                        )
                    }
                }
            }

            // Secure Shredding Passes
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Shredder Overwrite Passes", fontWeight = FontWeight.SemiBold)
                            Text("$shredPasses Passes", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Number of multi-pass random byte overwrites before inode deletion.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = shredPasses.toFloat(),
                            onValueChange = {
                                shredPasses = it.toInt()
                                prefs.edit().putInt("shred_passes", it.toInt()).apply()
                            },
                            valueRange = 1f..7f,
                            steps = 5
                        )
                    }
                }
            }

            // Storage & App Cache
            item {
                Text(
                    text = "Storage & App Cache",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("App Temporary Cache", fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isCalculatingStorage) "Calculating..." else formatFileSize(cacheBytes),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (cacheBytes > 500 * 1024 * 1024L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                            if (isClearingCache) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Button(
                                    onClick = { clearAppCache() },
                                    enabled = cacheBytes > 0L && !isClearingCache,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("Clear Cache")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Storage Breakdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Safe Vault", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = if (isCalculatingStorage) "..." else formatFileSize(vaultBytes),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Column {
                                Text("Trash Bin", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = if (isCalculatingStorage) "..." else formatFileSize(trashBytes),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Column {
                                Text("Thumbnails Cap", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "150 MB max",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Clearing cache safely removes temporary file viewing sessions, decrypted vault previews, and thumbnail buffers without deleting your documents or vault contents.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // About & Help
            item {
                Text(
                    text = "About & Help",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Quick Tour / App Introduction
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToOnboarding() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Feature Tour & Introduction", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Revisit the first-time welcome slider and features.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Updates: flavor-specific (GitHub build checks for new APKs, Play build opens Google Play)
            item {
                UpdateSection()
            }

            // Share App
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { AppLinks.shareApp(context) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Share App", fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Recommend VajraFS to friends and family",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Developer & Brand Info (Compact)
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Code,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "VajraFS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "by PrabinCode",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "GitHub",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        AppLinks.openInBrowser(context, "https://github.com/PrabinCode/vajrafs-android")
                                    }
                                )
                                Text(
                                    text = "•",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Website",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        AppLinks.openInBrowser(context, "https://pcshrestha.com.np/vajrafs")
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "© 2026 Prabin Chandra Shrestha • GPL-3.0 Open-Source",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        if (showDefaultAppDialog) {
            AlertDialog(
                onDismissRequest = { showDefaultAppDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.AppShortcut,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = {
                    Text("Default App & File Types")
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "VajraFS by PrabinCode includes built-in offline viewers for:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text("• PDF documents (instant reader)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("• Photos & images (high-res pinch/zoom)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("• Audio & music (background playback)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("• Videos & movies (PiP & gesture controls)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("• Text, Markdown, JSON & source code", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("• ZIP, RAR, 7Z & TAR archive extraction", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "How to make VajraFS default:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "1. When opening any of these files from external apps (browser, WhatsApp, email), select VajraFS.\n2. Tap \"Always\" on Android's prompt.\n\nYou can also configure or clear default associations in Android system settings.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDefaultAppDialog = false
                            try {
                                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                } else {
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(fallback)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Unable to open system settings: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Text("Open System Settings")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDefaultAppDialog = false }) {
                        Text("Got it")
                    }
                }
            )
        }
    }
}

private fun calculateCacheSize(context: Context): Long {
    var total = 0L
    try {
        context.cacheDir?.let { total += getFolderSize(it) }
        context.externalCacheDir?.let { total += getFolderSize(it) }
        context.codeCacheDir?.let { total += getFolderSize(it) }
    } catch (_: Exception) {}
    return total
}

private fun getFolderSize(dir: File): Long {
    if (!dir.exists()) return 0L
    var size = 0L
    try {
        dir.walkTopDown().forEach { if (it.isFile) size += it.length() }
    } catch (_: Exception) {}
    return size
}

