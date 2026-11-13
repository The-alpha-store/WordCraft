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

/** Formatting in effect at the selection, resolved through styles (what Word's ribbon shows). */
data class FormatState(
    val font: String = "",
    val size: Float = 11f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val sub: Boolean = false,
    val sup: Boolean = false,
    val highlight: Boolean = false,
    val align: String = "left",
    val style: String = "",
    val list: Boolean = false,
    val indentLeft: Float = 0f,
    val indentRight: Float = 0f,
    val indentFirst: Float = 0f,
    val tabs: List<Float> = emptyList()
)

/** A spelling or grammar issue as a rectangle on a page, with the engine's suggestions. */
data class ProofMark(
    val page: Int, val x: Float, val top: Float, val w: Float, val h: Float,
    val kind: String, val message: String, val text: String,
    val suggestions: List<String>, val start: String, val end: String
)

/** A comment anchor: where to draw its marker in the margin. */
data class CommentMark(val id: Int, val page: Int, val top: Float, val h: Float, val resolved: Boolean)

data class DocStats(val words: Int = 0, val chars: Int = 0, val selWords: Int = 0, val selChars: Int = 0)

/** A find match rectangle in page coordinates (points). */
data class FindHit(val page: Int, val x: Float, val top: Float, val w: Float, val h: Float)

/** The selected picture or shape: its frame in page points. */
data class ObjInfo(val page: Int, val x: Float, val y: Float, val w: Float, val h: Float, val floating: Boolean, val picture: Boolean)

class CmdResult(val ok: Boolean, val result: Any?)

/**
 * Holds what the UI needs to draw, and funnels every engine call through one lock so edits,
 * hit-tests and renders never interleave. The Rust engine remains the only document model.
 */
class EditorState {
    var pages by mutableStateOf<List<PageInfo>>(emptyList()); private set
    var focus by mutableStateOf<CaretGeo?>(null); private set
    var anchor by mutableStateOf<CaretGeo?>(null); private set
    var collapsed by mutableStateOf(true); private set

    /** Commands the engine currently reports as disabled, with its reason. */
    var disabled by mutableStateOf<Map<String, String>>(emptyMap()); private set

    /** Live formatting at the selection and document counts, refreshed after every change. */
    var fmt by mutableStateOf(FormatState()); private set
    var stats by mutableStateOf(DocStats()); private set

    /** Pressed state of toggle commands such as Show/Hide marks and Track Changes. */
    var toggles by mutableStateOf<Map<String, Boolean>>(emptyMap()); private set

    /** The picture or shape selected on the page, if any. */
    var selObject by mutableStateOf<ObjInfo?>(null); private set

    /** True when the caret is in a table (the engine enables table commands only there). */
    val inTable: Boolean get() = disabled.isNotEmpty() && disabled["table.insertRowBelow"] == null

    /** Spelling and grammar squiggles, refreshed a moment after typing stops. */
    var proofing by mutableStateOf(true)
    var proofMarks by mutableStateOf<List<ProofMark>>(emptyList()); private set
    var commentMarks by mutableStateOf<List<CommentMark>>(emptyList()); private set

    /** The squiggle the user tapped, shown as a suggestions menu. */
    var suggestion by mutableStateOf<ProofMark?>(null)

    /** Counts document edits, so slow work (proofing) can wait for typing to pause. */
    var editCount by mutableIntStateOf(0); private set

    /** Bumped when a margin comment marker is tapped, so the review panel opens. */
    var reviewRequest by mutableIntStateOf(0)

    /** Ink: the active tool (pen, pencil, highlighter, eraser) or null when not drawing. */
    var drawTool by mutableStateOf<String?>(null)
    var drawColor by mutableStateOf("185ABD")
    var drawWidth by mutableStateOf(2.5f)
    var stylusOnly by mutableStateOf(false)

    /** While true, find matches are highlighted on the pages. */
    var findActive by mutableStateOf(false)
    var findHits by mutableStateOf<List<FindHit>>(emptyList()); private set

    /** Bumped after every engine change; page bitmaps are re-rendered when it changes. */
    var revision by mutableIntStateOf(0); private set
    var zoom by mutableFloatStateOf(1f)
    var status by mutableStateOf("")
    var dirty by mutableStateOf(false)

    /** First page currently visible in the scrolling canvas (0-based), for the status bar. */
    var visiblePage by mutableIntStateOf(0)

    /** Set to ask the canvas to scroll to a page position; [nonce] makes repeats distinct. */
    data class ScrollTarget(val page: Int, val yPoints: Float, val nonce: Long)
    var scrollTarget by mutableStateOf<ScrollTarget?>(null)
    private var scrollNonce = 0L

    fun scrollTo(page: Int, yPoints: Float = 0f) {
        scrollTarget = ScrollTarget(page, yPoints, ++scrollNonce)
    }

    /** Scroll so the caret is visible. */
    fun scrollToCaret() {
        focus?.let { scrollTo(it.page, (it.top - 60f).coerceAtLeast(0f)) }
    }

    /** Move the caret to a position described by the engine (a Pos as JSON) and scroll to it. */
    suspend fun goTo(posJson: String) {
        val pos = JSONObject(posJson)
        val ok = command("select.range", JSONObject().put("anchor", pos).put("focus", pos).toString(), edits = false)
        if (ok) scrollToCaret()
    }

    private val gate = Mutex()

    private class Snapshot(
        val pages: List<PageInfo>,
        val focus: CaretGeo?,
        val anchor: CaretGeo?,
        val collapsed: Boolean,
        val disabled: Map<String, String>,
        val fmt: FormatState,
        val stats: DocStats,
        val hits: List<FindHit>,
        val toggles: Map<String, Boolean>,
        val obj: ObjInfo?
    )

    /** Re-read everything the UI shows from the engine. Caller must hold [gate]. */
    private suspend fun refreshLocked() {
        val wantHits = findActive
        val snap = withContext(Dispatchers.IO) {
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
            val off = JSONObject(NativeBridge.nativeDisabledCommands())
            val offMap = HashMap<String, String>()
            off.keys().forEach { k -> offMap[k] = off.optString(k) }

            val f = JSONObject(NativeBridge.nativeFormatState())
            val format = FormatState(
                font = f.optString("font"),
                size = f.optDouble("size", 11.0).toFloat(),
                bold = f.optBoolean("bold"),
                italic = f.optBoolean("italic"),
                underline = f.optBoolean("underline"),
                strike = f.optBoolean("strike"),
                sub = f.optBoolean("sub"),
                sup = f.optBoolean("sup"),
                highlight = f.optBoolean("highlight"),
                align = f.optString("align", "left"),
                style = f.optString("style"),
                list = f.optBoolean("list"),
                indentLeft = f.optDouble("indentLeft", 0.0).toFloat(),
                indentRight = f.optDouble("indentRight", 0.0).toFloat(),
                indentFirst = f.optDouble("indentFirst", 0.0).toFloat(),
                tabs = f.optJSONArray("tabs")?.let { arr ->
                    (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.optDouble("pos")?.toFloat() }
                } ?: emptyList()
            )
            val st = JSONObject(NativeBridge.nativeStats())
            val counts = DocStats(st.optInt("words"), st.optInt("chars"), st.optInt("selWords"), st.optInt("selChars"))

            val hits = ArrayList<FindHit>()
            if (wantHits) {
                val ha = JSONObject(NativeBridge.nativeFindHits()).optJSONArray("hits")
                if (ha != null) for (i in 0 until ha.length()) {
                    val h = ha.getJSONObject(i)
                    hits += FindHit(h.getInt("page"), h.getDouble("x").toFloat(), h.getDouble("top").toFloat(),
                        h.getDouble("w").toFloat(), h.getDouble("h").toFloat())
                }
            }
            val tg = JSONObject(NativeBridge.nativeToggleStates())
            val toggleMap = HashMap<String, Boolean>()
            tg.keys().forEach { k -> toggleMap[k] = tg.optBoolean(k) }
            val ob = JSONObject(NativeBridge.nativeSelectedObject())
            val obj = if (ob.optBoolean("has")) ObjInfo(
                ob.getInt("page"), ob.getDouble("x").toFloat(), ob.getDouble("y").toFloat(),
                ob.getDouble("w").toFloat(), ob.getDouble("h").toFloat(), ob.optBoolean("floating"), ob.optBoolean("picture")
            ) else null
            Snapshot(list, geo("focus"), geo("anchor"), caret.optBoolean("collapsed", true), offMap, format, counts, hits, toggleMap, obj)
        }
        pages = snap.pages
        focus = snap.focus
        anchor = snap.anchor
        collapsed = snap.collapsed
        disabled = snap.disabled
        fmt = snap.fmt
        stats = snap.stats
        findHits = snap.hits
        toggles = snap.toggles
        selObject = snap.obj
        revision++
    }

    suspend fun refresh() {
        gate.withLock { refreshLocked() }
    }

    /** Re-run the spell and grammar check (slow, so callers wait for typing to pause). */
    suspend fun refreshProof() {
        if (!proofing) {
            proofMarks = emptyList()
            return
        }
        val marks = gate.withLock {
            withContext(Dispatchers.IO) {
                val arr = JSONObject(NativeBridge.nativeProofMarks()).optJSONArray("marks")
                val list = ArrayList<ProofMark>()
                if (arr != null) for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val sug = o.optJSONArray("suggestions")
                    list += ProofMark(
                        o.getInt("page"), o.getDouble("x").toFloat(), o.getDouble("top").toFloat(),
                        o.getDouble("w").toFloat(), o.getDouble("h").toFloat(),
                        o.optString("kind"), o.optString("message"), o.optString("text"),
                        (0 until (sug?.length() ?: 0)).map { sug!!.getString(it) },
                        o.getJSONObject("start").toString(), o.getJSONObject("end").toString()
                    )
                }
                list
            }
        }
        proofMarks = marks
        val cm = gate.withLock {
            withContext(Dispatchers.IO) {
                val arr = JSONObject(NativeBridge.nativeCommentMarks()).optJSONArray("marks")
                (0 until (arr?.length() ?: 0)).map { i ->
                    val o = arr!!.getJSONObject(i)
                    CommentMark(o.getInt("id"), o.getInt("page"), o.getDouble("top").toFloat(), o.getDouble("h").toFloat(), o.optBoolean("resolved"))
                }
            }
        }
        commentMarks = cm
    }

    /** Select the issue's word. */
    suspend fun selectIssue(mark: ProofMark): Boolean =
        command("select.range", JSONObject().put("anchor", JSONObject(mark.start)).put("focus", JSONObject(mark.end)).toString(), edits = false)

    /** Replace a misspelled word with a suggestion. */
    suspend fun fixIssue(mark: ProofMark, replacement: String) {
        suggestion = null
        if (selectIssue(mark)) typeText(replacement)
    }

    /** Run an engine command and return its result object too. */
    suspend fun exec(id: String, params: String = "{}", edits: Boolean = true): CmdResult = gate.withLock {
        val raw = withContext(Dispatchers.IO) { NativeBridge.nativeRunCommand(id, params) }
        val json = runCatching { JSONObject(raw) }.getOrNull()
        val ok = json?.optBoolean("ok") ?: false
        var result: Any? = null
        if (!ok) {
            status = json?.optString("error", "Command failed") ?: raw
        } else {
            result = json?.opt("result")?.takeIf { it != JSONObject.NULL }
            // Some commands return information (word count, search hits...): show short ones.
            val shown = result?.toString().orEmpty()
            status = if (shown.length in 3..120 && shown != "{}" && shown != "[]" && result !is JSONObject) shown else ""
        }
        if (ok && edits) { dirty = true; editCount++ }
        refreshLocked()
        CmdResult(ok, result)
    }

    /** Run any engine command. Returns true when the engine reported success. */
    suspend fun command(id: String, params: String = "{}", edits: Boolean = true): Boolean = exec(id, params, edits).ok

    /** Place the caret (or extend the selection) at a page point given in points. */
    suspend fun hit(page: Int, x: Float, y: Float, extend: Boolean) {
        gate.withLock {
            withContext(Dispatchers.IO) { NativeBridge.nativeHitTest(page, x, y, extend) }
            refreshLocked()
        }
    }

    /**
     * A tap on the page: selects a picture or shape if one is under the finger, otherwise puts the
     * caret there. Returns true when an object was selected.
     */
    suspend fun tap(page: Int, x: Float, y: Float): Boolean = gate.withLock {
        val objectHit = withContext(Dispatchers.IO) {
            val r = JSONObject(NativeBridge.nativeSelectObjectAt(page, x, y))
            r.optJSONObject("result")?.optBoolean("hit") ?: false
        }
        if (!objectHit) withContext(Dispatchers.IO) { NativeBridge.nativeHitTest(page, x, y, false) }
        refreshLocked()
        objectHit
    }

    /** Drag the selection's start handle: only the anchor moves. */
    suspend fun moveAnchor(page: Int, x: Float, y: Float) {
        gate.withLock {
            withContext(Dispatchers.IO) { NativeBridge.nativeMoveAnchor(page, x, y) }
            refreshLocked()
        }
    }

    /** Type text at the caret / over the selection. '\n' starts a new paragraph. */
    suspend fun typeText(text: String) {
        gate.withLock {
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
            editCount++
            refreshLocked()
        }
    }

    suspend fun backspace() {
        gate.withLock {
            withContext(Dispatchers.IO) { NativeBridge.nativeRunCommand("text.backspace", "{}") }
            dirty = true
            editCount++
            refreshLocked()
        }
    }

    suspend fun selectedText(): String = withContext(Dispatchers.IO) { NativeBridge.nativeGetSelection() }

    /** The whole document saved as bytes (DOCX, or PDF by file name). */
    suspend fun save(fileName: String): ByteArray? = gate.withLock {
        withContext(Dispatchers.IO) { NativeBridge.nativeSaveDocument(fileName) }
    }

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
