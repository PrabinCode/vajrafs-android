# VajraFS — Secure File Explorer for Android

<p align="center">
  <b>A private, open-source Android file manager built with Kotlin & Jetpack Compose (Material 3).</b>
  <br />
  <i>Developed by <b>Prabin Chandra Shrestha (PrabinCode)</b></i>
</p>

<p align="center">
  <a href="https://github.com/PrabinCode/vajrafs-android/releases/latest">
    <img src="https://img.shields.io/badge/Download-Latest%20APK-006874?style=for-the-badge&logo=android&logoColor=white" alt="Download APK" />
  </a>
  <a href="https://pcshrestha.com.np/vajrafs">
    <img src="https://img.shields.io/badge/Official-Website-0284c7?style=for-the-badge&logo=googlechrome&logoColor=white" alt="Official Website" />
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84?style=flat-square&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Target%20SDK-36%20(Android%2016)-green?style=flat-square" />
  <img src="https://img.shields.io/badge/Language-Kotlin%202.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20(Material%203)-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" />
  <img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square" />
</p>

> **Upgrading from an earlier build?** VajraFS uses the package `com.prabincode.vajrafs`, so it installs as a new app. Move your files out of Safe Vault in the old app before uninstalling it; vault keys can't be transferred.

---

## 🌟 Features

* **🛡️ No tracking:** no analytics, crash-reporting or ad SDKs, and no account. Files stay on the device.
* **🔒 Safe Vault:** AES-256 envelope encryption. The data key is wrapped by an Android Keystore key, unlocked with biometrics or a master PIN, with auto-lock.
* **🌐 Wi-Fi PC Transfer:** a local HTTP server you open from any browser on the same Wi-Fi, protected by a session PIN. Upload, download, create folders and delete, with drag and drop.
* **🧹 Storage tools:** category breakdown, large-file finder, and a duplicate finder that groups files by size and then confirms with SHA-256.
* **🗜️ Archives:** create ZIP files, optionally password-protected with AES; extract `.zip`, `.tar.gz` and `.tgz`. Other archive types open with another app.
* **📄 Built-in viewers:** PDF (continuous scroll, night mode), images (zoom, rotate, EXIF), audio (background playback), and video (Media3 gestures, speed, picture-in-picture).
* **📝 Text & code editor:** line numbers, find in file and word wrap for `.txt`, `.json`, `.xml`, `.md`, `.kt`, `.py`, `.sh`, `.log` and more.
* **🌪️ File shredder:** multi-pass overwrite (`0x00`, `0xFF`, `SecureRandom`) before deleting. On flash storage this lowers, but can't rule out, recovery.
* **🗄️ Recycle bin:** restore deleted files with one tap.
* **🧭 Root browsing:** optional, on rooted devices.

---

## 🏗️ Project Architecture

```
app/src/
├── main/java/com/prabincode/vajrafs/
│   ├── data/
│   │   ├── archive/       # ZIP / TAR.GZ extraction and password-protected compression
│   │   ├── crypto/        # Android Keystore, AES-256 envelope crypto, shredder, checksums
│   │   ├── local/         # Room database (bookmarks, trash, vault index, search history, tags)
│   │   ├── media/         # Background audio playback
│   │   ├── model/         # FileItem, FileType, StorageVolumeInfo, PrabinCodeBrand
│   │   ├── repository/    # FileSystem, Vault, StorageAnalyzer, Trash repositories
│   │   ├── root/          # Root shell provider
│   │   ├── security/      # AutoLockManager
│   │   ├── server/        # Local Wi-Fi HTTP server for PC transfer
│   │   └── service/       # Foreground dataSync services (Wi-Fi server, vault)
│   ├── ui/                # Compose components, navigation, screens, theme
│   └── util/              # Formatters, MIME resolvers, AppLinks
├── github/java/...        # GitHub flavor: in-app updater (UpdateManager, UpdateSection)
└── play/java/...          # Play flavor: no self-update code; Play delivers updates
```

---

## 🚀 Building from Source

### Prerequisites
* JDK 17
* Android SDK with platform 36

### Distribution flavors
| Flavor | Output | Updates |
|---|---|---|
| `github` | APK for GitHub Releases | In-app "Check for updates" (downloads the new APK) |
| `play` | App Bundle for Google Play | Through Google Play; contains no update code |

```bash
git clone https://github.com/PrabinCode/vajrafs-android.git
cd vajrafs-android

./gradlew assembleGithubDebug        # Debug APK
./gradlew testGithubDebugUnitTest    # Unit tests
./gradlew assembleGithubRelease      # Release APK (R8 minified)
./gradlew bundlePlayRelease          # Google Play App Bundle
```

### Release signing
Release builds are signed only when a key is configured. Otherwise they build unsigned.
* **Locally:** create a git-ignored `keystore.properties` in the repo root:
  ```properties
  storeFile=/absolute/path/vajrafs-upload.jks
  storePassword=...
  keyAlias=vajrafs
  keyPassword=...
  ```
* **CI:** set the repository secrets `VAJRAFS_KEYSTORE_BASE64`, `VAJRAFS_KEYSTORE_PASSWORD`, `VAJRAFS_KEY_ALIAS` and `VAJRAFS_KEY_PASSWORD`. Tagged releases fail if signing is missing.

Google Play re-signs Play installs with Play App Signing, so a GitHub APK can't update a Play install (or the other way round). Uninstall one to switch.

### Build artifacts
* **GitHub APK:** `app/build/outputs/apk/github/release/prabincode-vajrafs-v2.0.0-github-release.apk`
* **Play App Bundle:** `app/build/outputs/bundle/playRelease/prabincode-vajrafs-v2.0.0-play-release.aab`

---

## 👨‍💻 Author & Maintainer

**Prabin Chandra Shrestha (PrabinCode)**
* **GitHub:** [@PrabinCode](https://github.com/PrabinCode)
* **Website:** [pcshrestha.com.np](https://pcshrestha.com.np)
* **Project Page:** [pcshrestha.com.np/vajrafs](https://pcshrestha.com.np/vajrafs)

---

## 📄 License & Open-Source Notice

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See the [LICENSE](LICENSE) file for details.

> Anyone redistributing or modifying this software must keep the author copyright notices, credit **PrabinCode**, and release derived works under the same GPL-3.0 license.
