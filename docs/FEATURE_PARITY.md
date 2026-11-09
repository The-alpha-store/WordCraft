# Feature parity tracker

Status applies to this Android source snapshot, not to the upstream desktop/web project.

| Area | Current Android status | Remaining work |
|---|---|---|
| Engine integration | JNI calls the original WordCraft `Session` and command registry | First ARM64 compile and API audit |
| New document | Engine `file.new` command exposed | Templates and multi-document UI |
| Open | Engine extension-based open dispatcher | SAF filter/metadata edge cases; password prompts |
| Save DOCX | Original engine serializer | Dirty-state/recovery and round-trip tests |
| Save As | Generic engine export call | Better extension/MIME mapping and validation |
| PDF export | Original engine serializer | Preview and print/share integration tests |
| Plain text editing | Compose text field + debounced `document.setText` | Replace with selection-aware IME; preserve formatting |
| Formatting | Bold/italic/underline/strike and paragraph alignment/list commands | Selection-aware rich text UI and parameter dialogs |
| Undo/redo | Original engine history commands | Enablement/state synchronization |
| Tables | 3×3 insertion command wired | Table grid picker, cell navigation and rendering |
| Page layout | Initial margins/orientation/A4/columns actions | Complete page setup dialogs and visual pagination |
| Review | Word count, spelling, comments, tracking commands exposed | Comment panes, revision UI and parameterized actions |
| View | Print/read/web layout and zoom commands exposed | Render pages, scrolling and zoom gestures |
| Pictures/shapes | Not implemented in Android UI | SAF import, media handling and canvas hit testing |
| Headers/footers/notes/equations | A few insertion commands exposed | Dedicated editing surfaces and UI |
| Proofing/languages | Engine dependency available through Rust | Android dictionaries, language packs and settings |
| Accessibility | Compose semantics on basic controls | Full document semantics and TalkBack testing |
| Device testing | Not run | Install/test on physical ARM64 phone |
