plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

// Compile the Android JNI adapter against the exact vendored WordCraft engine.
val buildWordCraftRust by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine(
        "cargo", "ndk", "-t", "arm64-v8a",
        "-o", "app/src/main/jniLibs",
        "build", "--release",
        "--manifest-path", "rust/wordcraft_android_bridge/Cargo.toml"
    )
}

subprojects {
    plugins.withId("com.android.application") {
        tasks.named("preBuild").configure {
            dependsOn(rootProject.tasks.named("buildWordCraftRust"))
        }
    }
}
