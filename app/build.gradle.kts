import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.prabincode.vajrafs"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.prabincode.vajrafs"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "2.1.0"
        setProperty("archivesBaseName", "prabincode-vajrafs-v$versionName")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Release signing: keystore.properties in the repo root (local, git-ignored), or
    // VAJRAFS_KEYSTORE_* environment variables (CI). Without either, release builds stay unsigned.
    val keystoreProps = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }
    fun signingValue(prop: String, env: String): String? =
        keystoreProps.getProperty(prop) ?: System.getenv(env)

    val releaseStoreFile = signingValue("storeFile", "VAJRAFS_KEYSTORE_PATH")
    val hasReleaseSigning = releaseStoreFile != null && file(releaseStoreFile).exists()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = signingValue("storePassword", "VAJRAFS_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "VAJRAFS_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "VAJRAFS_KEY_PASSWORD")
            }
        }
    }

    // github: sideloaded APK with the in-app updater (src/github).
    // play:   Google Play AAB with no self-update code (src/play); Play delivers updates.
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
        }
        create("play") {
            dimension = "distribution"
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    
    // Biometric & Security
    implementation(libs.androidx.biometric)
    
    // DocumentFile & Storage
    implementation(libs.androidx.documentfile)
    
    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    
    // DataStore Preferences
    implementation(libs.androidx.datastore.preferences)
    
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    
    // Coil
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    
    // Archive Management (Universal Zip4j AES-256)
    implementation(libs.zip4j)

    // Media3 ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // EXIF Interface
    implementation(libs.androidx.exifinterface)
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.kotlinx.coroutines.core)

    debugImplementation(libs.androidx.ui.tooling)
}
