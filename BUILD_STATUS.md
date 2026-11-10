# Build and verification status

Date: 2026-11-10

## Verified (host x86_64 Linux, Rust 1.99.1)

- [x] JNI bridge (`rust/wordcraft_android_bridge`) compiles against the vendored WordCraft 0.16.0 engine (`cargo check`).
- [x] `cargo test --lib` passes: types text, applies bold, hit-tests a page point into a caret, reads caret
      geometry, renders a page with the engine rasteriser (checks glyph pixels exist), undo, inserts a table and
      a row, and inserts a base64 picture. This is the same engine call sequence the new JNI functions use.
- [x] Android-target dependency tree resolves with no fontconfig/GPU/windowing crates (`cargo tree --target aarch64-linux-android`).

## Fixed in this revision

- Bridge was missing upstream's `[patch.crates-io] krilla = ...` (craft-pdf needs that fork), so the bridge
  failed to compile. Patch, release profile and upstream `Cargo.lock` are now in the bridge crate.

## New in this revision (written, NOT yet compiled or run on Android)

- Rust: `nativeLayoutInfo`, `nativeRenderPage`, `nativeHitTest`, `nativeCaretInfo`.
- Kotlin: `EditorState.kt`, `DocumentCanvas.kt` (engine-rendered pages in a scrolling, pinch-zoomable canvas,
  tap to place caret, long-press-drag to select, caret/selection overlay, hidden-field keyboard input),
  new `MainActivity.kt` (formatting, clipboard, picture picker, table tools, open/save/PDF).
- The old whole-body `document.setText` text field is gone.

## Not yet verified

- [ ] `aarch64-linux-android` cross-compile (needs NDK; first CI run).
- [ ] Gradle/Kotlin/Compose compilation.
- [ ] APK installs and runs on a phone; touch/zoom/keyboard behaviour.

## Known limitations

- Keyboard input is committed-text only (no composing region / surrounding-text IME protocol, no predictive text).
- Selection highlight is approximate (built from caret geometry); no draggable selection handles yet.
- Table tools are menu buttons acting at the caret; no cell hit-testing/row-column handles on canvas yet.
- Pictures are inserted inline at 300 pt wide; no resize/move handles yet.
- Whole pages are re-rendered after each edit (fine for short documents; needs profiling on large ones).
