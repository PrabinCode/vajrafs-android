# iManage Files — Privacy-First Native Android File Explorer

<p align="center">
  <b>A modern, ultra-secure, 100% private Android File Explorer built with Kotlin & Jetpack Compose (Material 3).</b>
  <br />
  <i>Developed with ❤️ by <b>Prabin Chandra Shrestha (PrabinCode)</b></i>
</p>

<p align="center">
  <a href="https://github.com/PrabinCode/imanage-android/releases/latest">
    <img src="https://img.shields.io/badge/Download-Latest%20APK-006874?style=for-the-badge&logo=android&logoColor=white" alt="Download APK" />
  </a>
  <a href="https://pcshrestha.com.np/imanage">
    <img src="https://img.shields.io/badge/Official-Website-0284c7?style=for-the-badge&logo=googlechrome&logoColor=white" alt="Official Website" />
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84?style=flat-square&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-green?style=flat-square" />
  <img src="https://img.shields.io/badge/Language-Kotlin%202.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20(Material%203)-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" />
  <img src="https://img.shields.io/badge/Architecture-Clean%20%2F%20Offline--First-orange?style=flat-square" />
  <img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square" />
</p>

---

## 🌟 Key Features

* **🛡️ 100% Private, Zero Tracking & Zero Telemetry:** Zero analytics SDKs, zero crash trackers, zero remote scraping. All file management remains strictly on-device.
* **🔒 Hardware-Backed Safe Vault:** AES-256 GCM envelope encryption via Android KeyStore with biometric and PIN authentication, automatic auto-lock, and instant purge of decrypted temporary streams.
* **🌐 High-Speed Wi-Fi PC Transfer:** Embedded HTTP server for wireless, cable-free file management directly from your computer browser with drag-and-drop upload and progress tracking.
* **🧹 Storage Analyzer & Duplicate Cleaner:** Real-time visual disk breakdown, large files finder (>100MB), empty folder cleaner, cache purging, and two-stage SHA-256 duplicate detection.
* **🗜️ Advanced Archive Suite:** Create password-protected encrypted ZIP archives and extract `.zip`, `.tar.gz`, `.tgz`, `.7z`, and `.rar` files.
* **📄 Built-in Sandboxed Media & Document Viewers:** Instant native PDF reader, zoomable image gallery, background music player, and gesture-controlled video player with Picture-in-Picture (PiP).
* **📝 In-App Code & Text Editor:** View and edit `.txt`, `.json`, `.xml`, `.md`, `.kt`, `.py`, `.sh`, `.log`, and config files directly with syntax-friendly styling.
* **🌪️ DoD 5220.22-M File Shredder:** Cryptographic multi-pass overwriting (`0x00`, `0xFF`, `SecureRandom`) before file unlinking to ensure irrecoverable data sanitization.
* **🗄️ Smart Recycle Bin:** Soft-delete safeguard with 1-tap instant file restoration or permanent shredding.
* **🧭 Root & Storage Explorer:** Browse Internal Storage, SD cards, and root partitions (`/`) with dotfiles/hidden files toggling.

---

## 🏗️ Project Architecture

```
app/src/main/java/com/prabincode/imanage/
├── data/
│   ├── archive/           # ZIP / TAR archive extraction and password-protected compression
│   ├── crypto/            # Android KeyStore, AES-256 envelope crypto, DoD shredder, checksums
│   ├── local/             # Room Database (Bookmarks, Trash, Vault index, Search history, Tags)
│   ├── media/             # Media player and background audio playback manager
│   ├── model/             # FileItem, FileType, StorageVolumeInfo, PrabinCodeBrand
│   ├── repository/        # FileSystemRepository, VaultRepository, StorageAnalyzerRepository, TrashRepository
│   ├── root/              # Root shell provider for deep storage exploration
│   ├── security/          # AutoLockManager and lifecycle security safeguards
│   ├── server/            # Local Wi-Fi HTTP socket server for PC transfer
│   ├── service/           # Foreground dataSync services (Wi-Fi server & Vault encryption)
│   └── update/            # In-app update manager and version checker
├── ui/
│   ├── components/        # BreadcrumbBar, FileListItem, AppDrawer, CategoryRow, StorageIndicator
│   ├── navigation/        # Jetpack Compose Navigation graph and destination routing
│   ├── screens/           # Home, Explorer, Vault, Analyzer, Trash, Search, Settings, Viewers
│   └── theme/             # PrabinCodeTheme & Material 3 dynamic color scheme
└── util/                  # File size formatters, MIME resolvers, external intent stream cleaners
```

---

## 🚀 Building from Source

### Prerequisites
* JDK 17
* Android SDK (API 35, Build Tools 35.0.0)

```bash
# Clone the repository
git clone https://github.com/PrabinCode/imanage-android.git
cd imanage-android

# Build Debug APK
./gradlew assembleDebug

# Build Release APK (Minified + ShrinkResources via R8)
./gradlew assembleRelease

# Build Google Play Store App Bundle (.aab)
./gradlew bundleRelease
```

### Build Artifacts
* **Debug APK:** `app/build/outputs/apk/debug/prabincode-imanage-v1.7.0-debug.apk`
* **Release APK:** `app/build/outputs/apk/release/prabincode-imanage-v1.7.0-release-unsigned.apk`
* **Play Store App Bundle:** `app/build/outputs/bundle/release/prabincode-imanage-v1.7.0-release.aab`

---

## 👨‍💻 Author & Maintainer

**Prabin Chandra Shrestha (PrabinCode)**
* **GitHub:** [@PrabinCode](https://github.com/PrabinCode)
* **Website:** [pcshrestha.com.np](https://pcshrestha.com.np)
* **Project Page:** [pcshrestha.com.np/imanage](https://pcshrestha.com.np/imanage)

---

## 📄 License & Open-Source Notice

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)** - see the [LICENSE](LICENSE) file for details.

> Anyone redistributing or modifying this software must retain the author copyright notices, provide full attribution to **PrabinCode**, and distribute any derived works under the same GPL-3.0 license.
