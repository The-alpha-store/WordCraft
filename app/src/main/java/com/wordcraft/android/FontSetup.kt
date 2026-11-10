package com.wordcraft.android

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Registers the fonts shipped in assets/fonts with the engine. Android has no Windows fonts, so
 * the app bundles Liberation Serif / Sans / Mono (SIL Open Font License, see
 * assets/fonts/LIBERATION-LICENSE.txt): they have the same character widths as Times New Roman,
 * Arial and Courier New, and the engine draws those names with them, so line and page breaks
 * match Word. The fonts keep their own names; the files are never renamed.
 */
object FontSetup {
    @Volatile
    private var done = false

    suspend fun registerBundled(context: Context) = withContext(Dispatchers.IO) {
        if (done) return@withContext
        val names = context.assets.list("fonts")?.filter { it.endsWith(".ttf", ignoreCase = true) }.orEmpty()
        for (name in names) {
            val bytes = context.assets.open("fonts/$name").use { it.readBytes() }
            val params = JSONObject()
                .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                .put("name", name)
                .toString()
            NativeBridge.nativeRunCommand("font.add", params)
        }
        done = true
    }

    /** Every font family the engine can draw with right now. */
    fun families(): List<String> = runCatching {
        val result = JSONObject(NativeBridge.nativeRunCommand("font.list", "{\"all\":true}")).optJSONObject("result")
        val arr = result?.optJSONArray("families")
        (0 until (arr?.length() ?: 0)).map { arr!!.getString(it) }
    }.getOrDefault(emptyList())
}
