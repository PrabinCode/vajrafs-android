# Security Policy

## 🔒 Security & Privacy Design

**VajraFS** is built offline-first, with no analytics, crash reporting or advertising SDKs.

1. **Network use is limited and user-started:**
   - The app declares `android.permission.INTERNET` for two features only:
     - **Wi-Fi PC Transfer**: a local HTTP server (port 8080) reachable only on your LAN, protected by a fresh 4-digit PIN each session. Traffic is plain HTTP, so use it on trusted networks.
     - **Check for updates** (GitHub build only): fetches `version.json` from `pcshrestha.com.np` when you tap it. The Google Play build contains no update code.
   - No file content, metadata or usage data is ever sent to the developer.

2. **Safe Vault cryptography (envelope encryption):**
   - A key-encryption key is generated in the **Android Keystore** (hardware-backed where the device supports it) and never leaves it.
   - It wraps a random 256-bit AES data key, which encrypts vault files with **AES-256 (CBC, PKCS7 padding)** in app-private storage.
   - Opening the vault requires biometrics or the master PIN (stored only as a salted PBKDF2-HMAC-SHA256 hash), with configurable auto-lock.
   - The key is not backed up (`allowBackup="false"`): a factory reset or uninstall makes vault files unrecoverable.

3. **File shredder:**
   - Overwrites file contents in multiple passes (`0x00`, `0xFF`, `SecureRandom`) before deleting.
   - On flash storage, wear levelling can keep old copies the app can't reach, so this lowers but can't rule out recovery.

4. **Anti-snooping (`FLAG_SECURE`):**
   - Enabled by default, blocking screenshots and app-switcher previews. You can switch it off in Settings.

## 🛡️ Reporting a Vulnerability

Please report vulnerabilities privately through a [GitHub Security Advisory](https://github.com/PrabinCode/vajrafs-android/security/advisories/new) or by email to prabin@pcshrestha.com.np. Don't open a public issue for security problems.
