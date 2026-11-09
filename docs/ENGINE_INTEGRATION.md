# Original WordCraft engine integration

## Source boundary

`upstream/wordcraft/` is the user's original 0.16.0 source archive, copied without edits. The Android application is a separate Gradle project. The JNI crate at `rust/wordcraft_android_bridge/` depends on `upstream/wordcraft/crates/engine` as a path dependency.

## Adapter surface

The native layer currently exposes:

- `engineStatus()` — reports whether the shared engine session can be locked.
- `nativeGetText()` / `nativeGetSelection()` — read body and selected text.
- `nativeRunCommand(id, paramsJson)` — invoke the original command registry through `Session::run`.
- `nativeSetText(text)` — invoke `document.setText` (whole-body replacement; transitional only).
- `nativeFormatAll(id)` — select all and run a formatting command.
- `nativeUndo()` / `nativeRedo()` — original engine history.
- `nativePageCount()` / `nativeGetLayoutSummary()` — query original layout.
- `nativeOpenDocument(filename, bytes)` — use `wordcraft_engine::io::open_bytes`.
- `nativeSaveDocument(filename)` — use `wordcraft_engine::io::save_bytes`.
- `nativeCommandCatalog()` — expose the original feature catalog for future dynamic ribbon generation.

All commands and serialization remain engine-owned. The JNI layer is not a second document model.

## First build audit

```bash
cargo metadata --no-deps --format-version 1 --manifest-path rust/wordcraft_android_bridge/Cargo.toml
cargo ndk -t arm64-v8a -o app/src/main/jniLibs build --release \
  --manifest-path rust/wordcraft_android_bridge/Cargo.toml
./gradlew --no-daemon :app:assembleDebug
```

The JNI crate is an isolated Cargo workspace so it does not accidentally become a member of the upstream WordCraft workspace. Its path dependency still resolves the upstream engine's own workspace dependencies from `upstream/wordcraft/Cargo.toml`.

The first Android-target compile is a required compatibility audit. If a dependency does not compile for Android, isolate the issue and keep any Android-specific workaround outside the vendored upstream source unless an upstream change is strictly required.

## Correct rich-text integration path

Do not build a second document model in Kotlin. Replace `document.setText` synchronization with an Android text-input adapter over the engine's selection and IME receipt APIs. The Android editor must:

1. Translate Android `InputConnection` edits into engine IME operations, preserving composition, selection and surrounding-text semantics.
2. Read engine selection/caret state back into Android selection and composing-region state.
3. Use engine commands for formatting, paragraph structure, tables, pictures, comments and revisions.
4. Render the engine's page/display-list output in a custom Android Canvas renderer.
5. Implement touch hit-testing, caret placement, selection handles, scroll/page navigation, and accessibility semantics.

Until those tasks are complete, the text field is intentionally labelled as a first-pass plain-text surface.
