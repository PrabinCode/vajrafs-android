/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prabincode.vajrafs.data.update.AppUpdateInfo
import com.prabincode.vajrafs.data.update.UpdateManager
import com.prabincode.vajrafs.util.AppLinks
import kotlinx.coroutines.launch

/** GitHub build: "Check for Updates" card, which downloads a newer APK from GitHub Releases. */
@Composable
fun UpdateSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val versionInfo = remember { AppLinks.getCurrentVersion(context) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfoDialog by remember { mutableStateOf<AppUpdateInfo?>(null) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isCheckingUpdate) {
                isCheckingUpdate = true
                scope.launch {
                    val result = UpdateManager.checkForUpdates(context)
                    isCheckingUpdate = false
                    result.fold(
                        onSuccess = { info ->
                            if (info.isUpdateAvailable) {
                                updateInfoDialog = info
                            } else {
                                Toast.makeText(
                                    context,
                                    "You're using the latest version (v${versionInfo.first})",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onFailure = { error ->
                            Toast.makeText(
                                context,
                                "Could not check for updates: ${error.localizedMessage ?: "Offline"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Check for Updates", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Installed: v${versionInfo.first} (Build ${versionInfo.second})",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isCheckingUpdate) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    // In-App Update Dialog
    updateInfoDialog?.let { info ->
        AlertDialog(
            onDismissRequest = {
                if (!info.isMandatory) updateInfoDialog = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.NewReleases,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text("Update Available: v${info.versionName}")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (info.releaseDate.isNotBlank()) {
                        Text(
                            text = "Released: ${info.releaseDate}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (info.releaseNotes.isNotEmpty()) {
                        Text(
                            text = "What's New:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        info.releaseNotes.forEach { note ->
                            Row(modifier = Modifier.padding(start = 4.dp)) {
                                Text("• ", fontWeight = FontWeight.Bold)
                                Text(note, fontSize = 13.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        UpdateManager.startDownload(context, info)
                        updateInfoDialog = null
                        Toast.makeText(context, "Initiating update...", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Update Now")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            AppLinks.openInBrowser(
                                context,
                                info.fallbackUrl.ifBlank { "https://pcshrestha.com.np/vajrafs" }
                            )
                        }
                    ) {
                        Text("Website")
                    }
                    if (!info.isMandatory) {
                        TextButton(onClick = { updateInfoDialog = null }) {
                            Text("Later")
                        }
                    }
                }
            }
        )
    }
}
