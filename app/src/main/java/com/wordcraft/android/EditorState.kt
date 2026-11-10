package com.wordcraft.android

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One laid-out page, in points. (bx, by, bw, bh) is the body text area. */
data class PageInfo(val w: Float, val h: Float, val bx: Float, val by: Float, val bw: Float, val bh: Float)

/** Caret bar in page coordinates (points). */
data class CaretGeo(val page: Int, val x: Float, val top: Float, val height: Float)

/**
 * Holds what the UI needs to draw, and funnels every engine call through one lock so edits,
 * hit-tests and renders never interleave. The Rust engine remains the only document model.
 */
class EditorState {
    var pages by mutableStateOf<List<PageInfo>>(emptyList()); private set
    var focus by mutableStateOf<CaretGeo?>(null); private set
    var anchor by mutableStateOf<CaretGeo?>(null); private set
    var collapsed by mutableStateOf(true); private set

    /** Bumped after every engine change; page bitmaps are re-rendered when it changes. */
    var revision by mutableIntStateOf(0); private set
    var zoom by mutableFloatStateOf(1f)
    var status by mutableStateOf("")
    var dirty by mutableStateOf(false)

    private val gate = Mutex()

    /** Re-read page sizes and caret geometry from the engine. Caller must hold [gate]. */
    private suspend fun refreshLocked() = withContext(Dispatchers.IO) {
        val layout = JSONObject(NativeBridge.nativeLayoutInfo())
        val arr = layout.optJSONArray("pages")
        val list = ArrayList<PageInfo>()
        if (arr != null) for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list += PageInfo(
                o.getDouble("w").toFloat(), o.getDouble("h").toFloat(),
                o.optDouble("bx", 0.0).toFloat(), o.optDouble("by", 0.0).toFloat(),
                o.optDouble("bw", o.getDouble("w")).toFloat(), o.optDouble("bh", o.getDouble("h")).toFloat()
            )
        }
        val caret = JSONObject(NativeBridge.nativeCaretInfo())
        fun geo(key: String): CaretGeo? = caret.optJSONObject(key)?.let {
            CaretGeo(it.getInt("page"), it.getDouble("x").toFloat(), it.getDouble("top").toFloat(), it.getDouble("height").toFloat())
        }
        Triple(list, geo("focus") to geo("anchor"), caret.optBoolean("collapsed", true))
    }.let { (list, carets, isCollapsed) ->
        pages = list
        focus = carets.first
        anchor = carets.second
        collapsed = isCollapsed
        revision++
    }

    suspend fun refresh() = gate.withLock { refreshLocked() }

    /** Run any engine command. Returns true when the engine reported success. */
    suspend fun command(id: String, params: String = "{}", edits: Boolean = true): Boolean = gate.withLock {
        val raw = withContext(Dispatchers.IO) { NativeBridge.nativeRunCommand(id, params) }
        val ok = runCatching { JSONObject(raw).optBoolean("ok") }.getOrDefault(false)
        if (!ok) {
            status = runCatching { JSONObject(raw).optString("error", "Command failed") }.getOrDefault(raw)
        }
        if (ok && edits) dirty = true
        refreshLocked()
        ok
    }

    /** Place the caret (or extend the selection) at a page point given in points. */
    suspend fun hit(page: Int, x: Float, y: Float, extend: Boolean) = gate.withLock {
        withContext(Dispatchers.IO) { NativeBridge.nativeHitTest(page, x, y, extend) }
        refreshLocked()
    }

    /** Type text at the caret / over the selection. '\n' starts a new paragraph. */
    suspend fun typeText(text: String) = gate.withLock {
        withContext(Dispatchers.IO) {
            val parts = text.split('\n')
            parts.forEachIndexed { i, part ->
                if (i > 0) NativeBridge.nativeRunCommand("text.newParagraph", "{}")
                if (part.isNotEmpty()) {
                    NativeBridge.nativeRunCommand("text.insert", JSONObject().put("text", part).toString())
                }
            }
        }
        dirty = true
        refreshLocked()
    }

    suspend fun backspace() = gate.withLock {
        withContext(Dispatchers.IO) { NativeBridge.nativeRunCommand("text.backspace", "{}") }
        dirty = true
        refreshLocked()
    }

    suspend fun selectedText(): String = withContext(Dispatchers.IO) { NativeBridge.nativeGetSelection() }

    /** Render one page at [pxPerPoint] pixels per point. */
    suspend fun renderPage(index: Int, pxPerPoint: Float): Bitmap? = gate.withLock {
        withContext(Dispatchers.Default) {
            val bytes = NativeBridge.nativeRenderPage(index, pxPerPoint) ?: return@withContext null
            if (bytes.size < 8) return@withContext null
            val header = ByteBuffer.wrap(bytes, 0, 8).order(ByteOrder.LITTLE_ENDIAN)
            val w = header.int
            val h = header.int
            if (w <= 0 || h <= 0 || bytes.size - 8 < w * h * 4) return@withContext null
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(bytes, 8, bytes.size - 8))
            bitmap
        }
    }
}
