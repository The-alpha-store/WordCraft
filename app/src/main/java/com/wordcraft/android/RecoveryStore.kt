package com.wordcraft.android

import android.content.Context
import java.io.File

/**
 * Keeps a copy of the open document while it has unsaved changes, so a crash or the system
 * closing the app never loses typing. The copy is offered on the home screen at the next launch.
 */
class RecoveryStore(context: Context) {
    private val dir = File(context.filesDir, "recovery").apply { mkdirs() }
    private val data = File(dir, "unsaved.docx")
    private val meta = File(dir, "unsaved.txt")

    fun write(title: String, uri: String, bytes: ByteArray) {
        runCatching {
            data.writeBytes(bytes)
            meta.writeText("$title\n$uri")
        }
    }

    /** The title, original uri (may be blank) and bytes of an unsaved document, if one is kept. */
    fun read(): Triple<String, String, ByteArray>? = runCatching {
        if (!data.exists() || !meta.exists()) return null
        val lines = meta.readText().split('\n')
        Triple(lines.getOrElse(0) { "Recovered document" }, lines.getOrElse(1) { "" }, data.readBytes())
    }.getOrNull()

    fun clear() {
        runCatching { data.delete(); meta.delete() }
    }
}
