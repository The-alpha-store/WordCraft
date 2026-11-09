package com.wordcraft.android

/** Android boundary. All document semantics stay in the original Rust engine. */
object NativeBridge {
    init {
        System.loadLibrary("wordcraft_android_bridge")
    }

    external fun engineStatus(): String
    external fun nativeGetText(): String
    external fun nativeGetSelection(): String
    external fun nativeSetText(text: String): String
    external fun nativeRunCommand(command: String, paramsJson: String = "{}"): String
    external fun nativeFormatAll(command: String): String
    external fun nativeUndo(): String
    external fun nativeRedo(): String
    external fun nativePageCount(): Int
    external fun nativeOpenDocument(name: String, bytes: ByteArray): String
    external fun nativeSaveDocument(name: String): ByteArray?
    external fun nativeGetLayoutSummary(): String
    external fun nativeCommandCatalog(): String
}
