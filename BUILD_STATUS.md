# Build and verification status

Date: 2026-11-09

## Completed in source tree

- [x] Extracted the user-provided WordCraft 0.16.0 archive.
- [x] Recorded its archive commit identifier and SHA-256.
- [x] Copied all 1,058 upstream files into `upstream/wordcraft/`.
- [x] Compared extracted upstream tree with vendored tree; no file differences were found.
- [x] Added Android Compose shell and JNI adapter.
- [x] Added command-id/JSON-parameter dispatch to the original engine `Session`.
- [x] Added filename-extension-based engine open/save calls.
- [x] Added a generic Save As path through the engine export dispatcher.
- [x] Prevented ordinary open/save from replacing an unchanged rich document with the plain-text editor buffer.
- [x] Added Gradle bootstrap, Android ARM64 configuration and GitHub Actions workflow.
- [x] Checked ZIP archive structure and project file inventory.

## Not yet verified

- [ ] Rust bridge compiles for `aarch64-linux-android`.
- [ ] Gradle Kotlin/Android compilation succeeds.
- [ ] Debug APK is produced.
- [ ] APK installs and launches on the user's physical phone.
- [ ] DOCX round-trip and PDF export pass real-device tests.
- [ ] Rich text, pagination rendering and native IME integration work end-to-end.

The current execution environment has Java but does not have `rustc`, `cargo`, `gradle`, Android SDK or Android NDK installed. Therefore no successful build or APK is claimed. The supplied GitHub Actions workflow is the reproducible build path, but it still needs its first run.
