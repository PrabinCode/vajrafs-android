/*
 * Copyright (C) 2026 Prabin Chandra Shrestha (PrabinCode)
 * Project: iManage Files
 * GitHub: https://github.com/PrabinCode/imanage-android
 *
 * Licensed under the GNU General Public License v3.0 (GPL-3.0)
 */
package com.prabincode.imanage.data.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

object KeystoreHelper {

    private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val MASTER_VAULT_KEY_ALIAS = "IManage_Master_Vault_Key_v3"
    private const val FALLBACK_KEY_FILE = ".vault_master.key"
    private const val ENCRYPTED_DEK_FILE = ".vault_dek.enc"

    @Volatile
    private var cachedDataKey: SecretKey? = null

    /**
     * Fast Data Encryption Key (DEK).
     * This key is a 256-bit AES key used for bulk file encryption/decryption at native CPU speeds.
     * The key is protected at rest by being encrypted with the Android KeyStore master key (envelope encryption).
     */
    @Synchronized
    fun getOrCreateDataKey(context: Context?): SecretKey {
        cachedDataKey?.let { return it }

        if (context == null) {
            return getOrCreateMasterKey(null)
        }

        try {
            val dekFile = File(context.filesDir, ENCRYPTED_DEK_FILE)
            val masterKey = getOrCreateMasterKey(context)

            if (dekFile.exists() && dekFile.length() > 16) {
                // Decrypt stored DEK using master key
                FileInputStream(dekFile).use { fis ->
                    val iv = ByteArray(16)
                    val readIv = fis.read(iv)
                    if (readIv == 16) {
                        val encryptedBytes = fis.readBytes()
                        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS7Padding")
                        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, masterKey, javax.crypto.spec.IvParameterSpec(iv))
                        val rawKey = cipher.doFinal(encryptedBytes)
                        if (rawKey.size == 32) {
                            val key = SecretKeySpec(rawKey, "AES")
                            cachedDataKey = key
                            return key
                        }
                    }
                }
            }

            // Generate a fresh 256-bit AES data key and wrap it with master key
            val rawKey = ByteArray(32)
            SecureRandom().nextBytes(rawKey)

            val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS7Padding")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, masterKey)
            val iv = cipher.iv ?: ByteArray(16).also { SecureRandom().nextBytes(it) }
            val encryptedKey = cipher.doFinal(rawKey)

            FileOutputStream(dekFile).use { fos ->
                fos.write(iv)
                fos.write(encryptedKey)
            }

            val key = SecretKeySpec(rawKey, "AES")
            cachedDataKey = key
            return key
        } catch (e: Exception) {
            // Fallback: use master key or fallback key directly if envelope fails
            return getOrCreateMasterKey(context)
        }
    }

    /**
     * Retrieves or creates the AES-256 SecretKey in AndroidKeyStore.
     * Used as Key Encryption Key (KEK) for wrapping data keys, and for decrypting legacy vault files.
     */
    @Synchronized
    fun getOrCreateMasterKey(context: Context? = null): SecretKey {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }

            if (keyStore.containsAlias(MASTER_VAULT_KEY_ALIAS)) {
                val entry = keyStore.getEntry(MASTER_VAULT_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                if (entry != null) {
                    return entry.secretKey
                }
            }

            // Generate new KeyStore AES key
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE_PROVIDER
            )

            val builder = KeyGenParameterSpec.Builder(
                MASTER_VAULT_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)

            keyGenerator.init(builder.build())
            return keyGenerator.generateKey()
        } catch (e: Exception) {
            // Android KeyStore failure fallback: generate & store a 256-bit key in app private storage
            if (context != null) {
                return getOrCreateFallbackKey(context)
            }
            // In-memory fallback if no context
            val rawKey = ByteArray(32)
            SecureRandom().nextBytes(rawKey)
            return SecretKeySpec(rawKey, "AES")
        }
    }

    private fun getOrCreateFallbackKey(context: Context): SecretKey {
        val keyFile = File(context.filesDir, FALLBACK_KEY_FILE)
        if (keyFile.exists() && keyFile.length() == 32L) {
            val keyBytes = ByteArray(32)
            FileInputStream(keyFile).use { it.read(keyBytes) }
            return SecretKeySpec(keyBytes, "AES")
        }

        val keyBytes = ByteArray(32)
        SecureRandom().nextBytes(keyBytes)
        FileOutputStream(keyFile).use { it.write(keyBytes) }
        return SecretKeySpec(keyBytes, "AES")
    }

    fun hasMasterKey(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
            keyStore.containsAlias(MASTER_VAULT_KEY_ALIAS)
        } catch (e: Exception) {
            false
        }
    }

    fun deleteKey(context: Context? = null) {
        try {
            cachedDataKey = null
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(MASTER_VAULT_KEY_ALIAS)) {
                keyStore.deleteEntry(MASTER_VAULT_KEY_ALIAS)
            }
            if (context != null) {
                File(context.filesDir, FALLBACK_KEY_FILE).delete()
                File(context.filesDir, ENCRYPTED_DEK_FILE).delete()
            }
        } catch (e: Exception) { }
    }
}
