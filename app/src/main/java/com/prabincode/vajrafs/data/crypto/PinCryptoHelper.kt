/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: VajraFS
 * GitHub: https://github.com/PrabinCode/vajrafs-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.vajrafs.data.crypto

import android.content.Context
import android.util.Base64
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinCryptoHelper {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 10000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16

    private const val PREFS_NAME = "vajrafs_security_prefs"
    private const val KEY_PIN_HASH = "master_pin_hash"
    private const val KEY_PIN_SALT = "master_pin_salt"
    private const val KEY_PIN_ENABLED = "master_pin_enabled"
    private const val KEY_AUTOLOCK_TIMEOUT = "autolock_timeout_seconds"

    /**
     * Checks if a Master PIN is configured and enabled.
     */
    fun isPinEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PIN_ENABLED, false) && prefs.getString(KEY_PIN_HASH, null) != null
    }

    /**
     * Sets or updates the Master PIN using PBKDF2 hashing with a random 16-byte salt.
     */
    fun setMasterPin(context: Context, pin: String): Boolean {
        return try {
            val salt = ByteArray(SALT_LENGTH)
            SecureRandom().nextBytes(salt)

            val hash = hashPin(pin.toCharArray(), salt)

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putBoolean(KEY_PIN_ENABLED, true)
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Verifies the entered PIN against the stored PBKDF2 hash.
     */
    fun verifyPin(context: Context, enteredPin: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedHashBase64 = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val storedSaltBase64 = prefs.getString(KEY_PIN_SALT, null) ?: return false

        return try {
            val storedHash = Base64.decode(storedHashBase64, Base64.NO_WRAP)
            val storedSalt = Base64.decode(storedSaltBase64, Base64.NO_WRAP)

            val calculatedHash = hashPin(enteredPin.toCharArray(), storedSalt)
            storedHash.contentEquals(calculatedHash)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Disables and removes the master PIN.
     */
    fun disablePin(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .putBoolean(KEY_PIN_ENABLED, false)
            .apply()
    }

    fun getAutoLockTimeoutSeconds(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_AUTOLOCK_TIMEOUT, 60) // Default 1 minute
    }

    fun setAutoLockTimeoutSeconds(context: Context, seconds: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_AUTOLOCK_TIMEOUT, seconds).apply()
    }

    @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    private fun hashPin(pin: CharArray, salt: ByteArray): ByteArray {
        val spec: KeySpec = PBEKeySpec(pin, salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        return factory.generateSecret(spec).encoded
    }
}
