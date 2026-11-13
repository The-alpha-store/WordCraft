plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.wordcraft.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wordcraft.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-alpha.1"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    // Release: shrunk with R8. Signed with the keystore named in WORDCRAFT_KEYSTORE (and its
    // _PASSWORD, WORDCRAFT_KEY_ALIAS, WORDCRAFT_KEY_PASSWORD) when set, else with the debug key
    // so a test release still installs.
    signingConfigs {
        create("release") {
            val keystore = System.getenv("WORDCRAFT_KEYSTORE")
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = System.getenv("WORDCRAFT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("WORDCRAFT_KEY_ALIAS")
                keyPassword = System.getenv("WORDCRAFT_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (System.getenv("WORDCRAFT_KEYSTORE") != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        jniLibs { useLegacyPackaging = false }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}

// Compile the Rust JNI bridge (against the vendored WordCraft engine) before the app builds.
// cargo-ndk looks for Cargo.toml in its working directory, so run it inside the bridge crate.
val buildWordCraftRust by tasks.registering(Exec::class) {
    workingDir(rootProject.file("rust/wordcraft_android_bridge"))
    commandLine(
        "cargo", "ndk", "-t", "arm64-v8a",
        "-o", rootProject.file("app/src/main/jniLibs").absolutePath,
        "build", "--release"
    )
}

tasks.named("preBuild") { dependsOn(buildWordCraftRust) }
