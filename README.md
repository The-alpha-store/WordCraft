# WordCraft Android — original engine integration

This project is built from the **user-supplied `storytold/wordcraft` 0.16.0 source archive**. It does not use a third-party Android port. The complete upstream source is vendored at `upstream/wordcraft/` and was copied without modifying its files. Android-specific code is outside that directory.

## Source provenance

- Upstream: https://github.com/storytold/wordcraft
- Version: `0.16.0`
- Commit identifier recorded in the uploaded ZIP comment: `02a802cbafe45b5c8fecc00490c8f6b78c5ba251`
- Uploaded source archive SHA-256: `a522418c557aacd3d12479dc3b66df378e22cdeb23432223fe85eb7e7b3d63a1`
- Original upstream `LICENSE-MIT`, `LICENSE-APACHE`, `NOTICE`, and `ATTRIBUTION.md` remain in `upstream/wordcraft/`.

## What is implemented in this source snapshot

- Kotlin + Jetpack Compose Android application shell.
- Rust JNI adapter which directly depends on `upstream/wordcraft/crates/engine`.
- A shared original WordCraft `Session` with command dispatch by stable command id and JSON parameters.
- Plain-text body retrieval and replacement, formatting commands, undo/redo, and layout page count.
- Original engine document open/save dispatch by filename extension. The file picker is not limited to DOCX; the engine decides which formats it supports.
- Android Storage Access Framework open, DOCX save, generic Save As, and PDF export flows.
- Initial Home, Insert, Layout, Review and View ribbon groups.
- A Gradle bootstrap script and GitHub Actions ARM64 debug build workflow.

## Important limitations — read before using

This is an **early Android port**, not a finished WordCraft mobile release and not yet a verified APK.

1. The visible editing surface is currently a Compose plain-text field. The first adapter synchronizes the whole body with WordCraft's `document.setText` command after a short debounce. This is not a rich-text IME adapter and may discard formatting/inline objects when the user edits text after applying formatting.
2. Ribbon actions call real engine commands, but the Android UI does not yet implement every command's parameter dialog or UI request. Some advanced commands will report a useful error or require parameters not yet exposed in the UI.
3. The app does not yet render WordCraft's page display lists. Page count comes from the original engine layout API, while the body itself is still shown as editable text.
4. Tables, pictures, shapes, comments, tracked changes, headers/footers, notes, equations, touch selection, caret geometry, rich text styling, and full accessibility/IME behavior still need dedicated Android UI work.
5. The Rust ARM64 build, Gradle APK build, and physical-phone installation have **not** been run in this environment. Android-target compatibility of all upstream dependencies must be proven by CI before claiming a working APK.

## Build requirements

- JDK 17
- Android SDK platform 35 and Build Tools 35.0.0
- Android NDK 27.2.12479018
- Rust 1.95.0 (selected by `rust-toolchain.toml`)
- Android Rust target `aarch64-linux-android`
- `cargo-ndk` 4.1.2
- Internet access on the first build for Gradle/Maven and pinned Cargo Git dependencies

### Linux / cloud build

Install Android SDK components and accept licenses:

```bash
sdkmanager --install "platforms;android-35" "build-tools;35.0.0" "ndk;27.2.12479018"
sdkmanager --licenses
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.2.12479018"
```

Install Rust and cargo-ndk:

```bash
rustup toolchain install 1.95.0 --profile minimal --target aarch64-linux-android
rustup default 1.95.0
cargo install cargo-ndk --version 4.1.2 --locked
```

Build the ARM64 JNI library and APK:

```bash
./gradlew --no-daemon :app:assembleDebug
```

The first run downloads Gradle 8.9 through `gradlew`. The APK should be at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The Gradle `preBuild` task invokes `cargo ndk` and writes `libwordcraft_android_bridge.so` under `app/src/main/jniLibs/arm64-v8a/`.

### Build only the Rust bridge

```bash
cargo metadata --no-deps --format-version 1 --manifest-path rust/wordcraft_android_bridge/Cargo.toml
cargo ndk -t arm64-v8a -o app/src/main/jniLibs build --release \
  --manifest-path rust/wordcraft_android_bridge/Cargo.toml
```

## Install on a physical Android phone

1. Download the `app-debug.apk` build artifact from GitHub Actions.
2. Transfer it to the phone and open it.
3. Allow installation from that file manager/browser if Android asks.
4. Launch WordCraft, create a test document, type text, try a formatting command, save DOCX, reopen it, and export PDF.

For ADB over Wi-Fi, pair the phone in Developer Options → Wireless debugging, then run `adb pair`, `adb connect`, and `adb install -r app-debug.apk` from a computer that can reach the phone.

## Engineering roadmap

See `docs/ROADMAP.md` and `docs/ENGINE_INTEGRATION.md`. The next major task is replacing whole-document text synchronization with a selection-aware Android IME adapter and rendering the engine's actual paginated display lists.

## Licensing

The Android integration files in this directory are MIT licensed (see `LICENSE`). The vendored upstream WordCraft source remains separately dual-licensed MIT OR Apache-2.0. Keep all upstream license, notice and attribution files when redistributing. This project is not endorsed by the original WordCraft authors.
