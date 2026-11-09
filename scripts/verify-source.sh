#!/usr/bin/env sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
UPSTREAM="$ROOT/upstream/wordcraft"
BRIDGE="$ROOT/rust/wordcraft_android_bridge/Cargo.toml"
[ -f "$UPSTREAM/Cargo.toml" ] || { echo "Missing vendored upstream Cargo.toml" >&2; exit 1; }
[ -f "$UPSTREAM/LICENSE-MIT" ] || { echo "Missing upstream MIT license" >&2; exit 1; }
[ -f "$UPSTREAM/LICENSE-APACHE" ] || { echo "Missing upstream Apache license" >&2; exit 1; }
[ -f "$BRIDGE" ] || { echo "Missing JNI bridge manifest" >&2; exit 1; }
grep -q 'version = "0.16.0"' "$UPSTREAM/Cargo.toml"
grep -q 'path = "../../upstream/wordcraft/crates/engine"' "$BRIDGE"
grep -q 'nativeRunCommand' "$ROOT/rust/wordcraft_android_bridge/src/lib.rs"
grep -q 'nativeOpenDocument' "$ROOT/rust/wordcraft_android_bridge/src/lib.rs"
grep -q 'nativeSaveDocument' "$ROOT/rust/wordcraft_android_bridge/src/lib.rs"
[ -f "$ROOT/app/src/main/java/com/wordcraft/android/MainActivity.kt" ]
[ -f "$ROOT/.github/workflows/android-build.yml" ]
echo "Source structure checks passed. This does not replace a Rust/Gradle build or device test."
