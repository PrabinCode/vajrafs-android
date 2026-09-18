/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.data.security

import android.content.Context
import com.prabincode.imanage.data.crypto.PinCryptoHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AutoLockManager {

    private var lastBackgroundTimestamp: Long = 0L
    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    fun onAppBackgrounded() {
        lastBackgroundTimestamp = System.currentTimeMillis()
    }

    fun onAppForegrounded(context: Context) {
        val pinEnabled = PinCryptoHelper.isPinEnabled(context)
        if (!pinEnabled) {
            _isLocked.value = false
            return
        }

        val timeoutSeconds = PinCryptoHelper.getAutoLockTimeoutSeconds(context)
        if (timeoutSeconds == 0) { // 0 = Immediately
            _isLocked.value = true
            purgeVaultDecryptedCache()
            return
        }

        if (lastBackgroundTimestamp > 0L) {
            val elapsedSeconds = (System.currentTimeMillis() - lastBackgroundTimestamp) / 1000
            if (elapsedSeconds >= timeoutSeconds) {
                _isLocked.value = true
                purgeVaultDecryptedCache()
            }
        }
    }

    fun unlock() {
        _isLocked.value = false
        lastBackgroundTimestamp = 0L
    }

    fun lock() {
        _isLocked.value = true
        purgeVaultDecryptedCache()
    }

    private fun purgeVaultDecryptedCache() {
        try {
            com.prabincode.imanage.IManageApp.instance.vaultRepository.clearTempDecryptedFiles()
        } catch (_: Exception) {}
    }
}
