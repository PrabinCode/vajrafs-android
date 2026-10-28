/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.ui.screens.vault

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.prabincode.vajrafs.util.safeStatusBarInsets
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.prabincode.vajrafs.data.local.entity.VaultEntity
import com.prabincode.vajrafs.data.model.FileItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    onNavigateBack: () -> Unit,
    onOpenFile: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importUris(context, uris)
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    fun showBiometricPrompt() {
        if (activity == null) {
            viewModel.setUnlocked(true)
            return
        }

        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    viewModel.setUnlocked(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Encrypted Vault")
            .setSubtitle("Authenticate using biometrics or screen lock")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(promptInfo)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearTempDecryptedFiles()
        }
    }

    LaunchedEffect(Unit) {
        if (!state.isUnlocked) {
            showBiometricPrompt()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = safeStatusBarInsets(),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Encrypted Safe Vault", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFFEC407A),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isUnlocked) {
                        IconButton(onClick = {
                            viewModel.setUnlocked(false)
                            viewModel.clearTempDecryptedFiles()
                        }) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock Vault")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (state.isUnlocked) {
                ExtendedFloatingActionButton(
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    icon = { Icon(Icons.Default.AddModerator, contentDescription = null) },
                    text = { Text("Add Files to Vault") },
                    containerColor = Color(0xFFEC407A),
                    contentColor = Color.White
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Live background task progress card
            state.activeTask?.let { task ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFEC407A).copy(alpha = 0.12f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFFEC407A)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            val title = when (task.operation) {
                                com.prabincode.vajrafs.data.service.VaultOperationType.ENCRYPT -> "Securing ${task.fileName}"
                                com.prabincode.vajrafs.data.service.VaultOperationType.RESTORE -> "Restoring ${task.fileName}"
                                com.prabincode.vajrafs.data.service.VaultOperationType.IMPORT -> "Importing ${task.fileName}"
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${task.percent}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEC407A)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (task.isIndeterminate) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFFEC407A)
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { task.percent / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFFEC407A)
                            )
                        }
                        if (task.totalBytes > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${FileItem.formatBytes(task.bytesProcessed)} of ${FileItem.formatBytes(task.totalBytes)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (state.isLoading && state.activeTask == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (!state.isUnlocked) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEC407A).copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = "Locked",
                                tint = Color(0xFFEC407A),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Vault is Locked",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Files inside this vault are encrypted with hardware-wrapped AES-256 keys.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showBiometricPrompt() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC407A))
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Unlock Vault")
                        }
                    }
                } else if (state.items.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EnhancedEncryption,
                            contentDescription = null,
                            tint = Color(0xFFEC407A).copy(alpha = 0.5f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Your Vault is Empty",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap '+ Add Files to Vault' below, or select files in the file explorer and choose 'Move to Encrypted Vault'.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.items, key = { it.id }) { item ->
                            VaultItemCard(
                                item = item,
                                onPreview = {
                                    viewModel.previewItem(item) { decryptedTempFile ->
                                        onOpenFile(decryptedTempFile.absolutePath)
                                    }
                                },
                                onRestore = { viewModel.restoreItem(context, item) },
                                onDelete = { viewModel.deleteItem(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultItemCard(
    item: VaultEntity,
    onPreview: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFEC407A).copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFFEC407A),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.originalFileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.isDirectory) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFEC407A).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "FOLDER",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEC407A),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(item.encryptedAt))
                Text(
                    text = "${FileItem.formatBytes(item.fileSize)} • $dateStr",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row {
                IconButton(onClick = onPreview) {
                    Icon(Icons.Outlined.Visibility, contentDescription = "Preview", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onRestore) {
                    Icon(Icons.Outlined.Restore, contentDescription = "Restore", tint = Color(0xFF26A69A))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteForever, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
