# Android port roadmap

## Phase 0 — source and build foundation

- [x] Use the user-provided original WordCraft 0.16.0 source only.
- [x] Preserve the original source tree unchanged.
- [x] Create a separate Kotlin/Compose Android shell and Rust JNI adapter.
- [ ] Produce the first reproducible ARM64 debug APK in CI.
- [ ] Install and launch it on a physical Android phone.

## Phase 1 — document lifecycle

- [x] New in-memory blank document.
- [x] Open formats through the original engine's extension dispatcher.
- [x] Export DOCX and PDF through original engine I/O.
- [ ] Add Save As for every supported export format.
- [ ] Add recent documents, autosave/recovery and safe background I/O.
- [ ] Add round-trip fixture tests for DOCX, RTF, ODT, TXT and PDF.

## Phase 2 — editor correctness

- [ ] Replace whole-body `document.setText` with engine-backed Android IME integration.
- [ ] Preserve caret, selection, composing region, surrounding text and grapheme boundaries.
- [ ] Implement clipboard and Android selection handles.
- [ ] Render rich text, inline objects, fields and paragraph properties.
- [ ] Add undo/redo state synchronization and dirty-state tracking.

## Phase 3 — paginated document canvas

- [ ] Render engine page display lists on Android Canvas.
- [ ] Implement page margins, page shadows, zoom and continuous/side-to-side navigation.
- [ ] Map touch coordinates to engine positions and hit-testing.
- [ ] Render tables, pictures, shapes, text boxes, comments and tracked changes.
- [ ] Add page setup and print/export previews.

## Phase 4 — WordCraft feature parity

- [ ] Build ribbon and command palette from the engine command catalog.
- [ ] Add parameter dialogs for commands that need them.
- [ ] Implement Insert, Layout, References, Mailings, Review, View and accessibility workflows.
- [ ] Add proofing, language packs, read aloud, comments, track changes and references.
- [ ] Implement mobile navigation, keyboard shortcuts and accessibility support.

## Phase 5 — quality and release

- [ ] Unit, integration, property and round-trip tests.
- [ ] Test on multiple Android screen sizes and Android versions.
- [ ] Test large documents, memory pressure, background/foreground transitions and interrupted saves.
- [ ] Benchmark layout and rendering on mid-range ARM64 phones.
- [ ] Sign release APK/AAB and publish reproducible build metadata.
