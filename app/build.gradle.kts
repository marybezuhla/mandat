import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// The version people see (major.minor.patch), set by hand before each release in version.txt.
val appVersion = rootProject.file("version.txt").readText().trim()
// versionCode comes from the same file, so anyone can rebuild the same APK from source:
// 0.2.0 -> 200. Releases up to 0.1.191 used the CI run number as versionCode, and Android
// refuses an update with a lower one, so nothing below 200 is accepted here.
val appVersionCode = appVersion.split(".").map { it.toInt() }.let { parts ->
    require(parts.size == 3) { "version.txt: must be major.minor.patch" }
    val (a, b, c) = parts
    require(b < 100 && c < 100) { "version.txt: minor and patch must be below 100" }
    (a * 10000 + b * 100 + c).also { require(it >= 200) { "version.txt: must be 0.2.0 or above" } }
}

android {
    namespace = "ua.mandat"
    compileSdk = 36

    defaultConfig {
        applicationId = "ua.mandat"
        minSdk = 31
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
    }

    // No Google-encrypted dependency list in the APK (F-Droid and IzzyOnDroid reject it).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Left unsigned here; signed later in a separate step that runs no build code
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Screen checks run on the computer (Robolectric) and need the app's resources
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Unlock with the phone's own fingerprint or PIN
    implementation("androidx.biometric:biometric:1.1.0")

    // Encrypted database: Room on top of SQLCipher
    val room = "2.7.2"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")
    implementation("net.zetetic:sqlcipher-android:4.19.1@aar")
    implementation("androidx.sqlite:sqlite:2.5.2")

    // Argon2id for backup passwords (Bouncy Castle, a widely audited crypto library)
    implementation("org.bouncycastle:bcprov-jdk18on:1.79")

    // Backups written and read in parts: AES-256-GCM-HKDF streaming encryption (Tink, Google's audited crypto library)
    implementation("com.google.crypto.tink:tink-android:1.20.0")

    // Password-protected export: standard ZIP with AES-256, opened by 7-Zip and similar apps
    implementation("net.lingala.zip4j:zip4j:2.11.5")

    // Optional on-device AI: Gemma runtime by Google AI Edge (Apache 2.0); the model file is added by the owner
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.1")

    testImplementation("junit:junit:4.13.2")
    // Real org.json for unit tests (Android provides it on the phone)
    testImplementation("org.json:json:20240303")

    // Screen checks only (never in the app): draw each screen with neutral test data,
    // so a crash is caught before a release and the look can be reviewed
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
