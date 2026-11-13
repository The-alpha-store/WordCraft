package com.wordcraft.android

import androidx.compose.foundation.Canvas
import org.json.JSONObject
import org.json.JSONArray
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val SENTINEL = "\u200B"
private const val MAX_ZOOM = 2.5f
private const val MIN_ZOOM = 0.6f

/**
 * The document surface. Every page is rasterised by the WordCraft engine (layout + renderer);
 * Kotlin only displays the bitmaps, forwards touches/typing to the engine, and draws the caret,
 * selection, selection handles, find highlights and the floating mini toolbar on top.
 */
@Composable
fun DocumentCanvas(state: EditorState, onCommand: (String, String) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val hScroll = rememberScrollState()
    val listState = rememberLazyListState()
    val canvasScope = rememberCoroutineScope()
    val rulerOn = state.toggles["view.ruler"] == true
    var caretOn by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { state.refresh() }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { state.visiblePage = it }
    }
    // Blink the caret; it stays solid while the document is changing.
    LaunchedEffect(state.revision) {
        caretOn = true
        while (true) {
            delay(530)
            caretOn = !caretOn
        }
    }

    BoxWithConstraints(
        modifier
            .background(Color(0xFFD9DEE7))
            .pointerInput(Unit) {
                // Two-finger pinch only; single-finger drags still reach the list's scrolling.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.size >= 2) {
                            val change = event.calculateZoom()
                            if (change != 1f) {
                                // Zoom about the point between the fingers, so it stays under them.
                                val centroid = event.calculateCentroid()
                                val newZoom = (state.zoom * change).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                val k = newZoom / state.zoom
                                state.zoom = newZoom
                                if (k != 1f) {
                                    val firstItem = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                                    val itemHeight = (firstItem?.size ?: 0) + 12.dp.toPx()
                                    val absY = listState.firstVisibleItemIndex * itemHeight + listState.firstVisibleItemScrollOffset
                                    val dy = (absY + centroid.y) * (k - 1f)
                                    val dx = (hScroll.value + centroid.x) * (k - 1f)
                                    canvasScope.launch { listState.scrollBy(dy); hScroll.scrollBy(dx) }
                                }
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        val viewportPx = with(density) { maxWidth.toPx() }
        val marginPx = with(density) { 12.dp.toPx() }
        val firstWidth = state.pages.firstOrNull()?.w ?: 595f
        // Pixels per point: fit the first page to the screen at zoom 1, then apply zoom.
        val pxPerPoint = ((viewportPx - 2 * marginPx) / firstWidth) * state.zoom
        val widestPx = (state.pages.maxOfOrNull { it.w } ?: firstWidth) * pxPerPoint
        val contentWidthDp = with(density) { max(viewportPx, widestPx + 2 * marginPx).toDp() }

        // Scroll requests from the navigation pane, find and "go to".
        LaunchedEffect(state.scrollTarget) {
            state.scrollTarget?.let { t ->
                val index = t.page.coerceIn(0, (state.pages.size - 1).coerceAtLeast(0))
                listState.animateScrollToItem(index, (t.yPoints * pxPerPoint).toInt())
            }
        }
        // Keep the caret on screen while typing or moving a handle.
        LaunchedEffect(state.focus) {
            val f = state.focus ?: return@LaunchedEffect
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == f.page }
            if (item == null) {
                listState.animateScrollToItem(f.page, (f.top * pxPerPoint - 200f).toInt().coerceAtLeast(0))
                return@LaunchedEffect
            }
            val top = item.offset + f.top * pxPerPoint
            val bottom = top + f.height * pxPerPoint
            val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
            val margin = 90f
            when {
                top < margin -> listState.animateScrollBy(top - margin)
                bottom > viewport - margin -> listState.animateScrollBy(bottom - (viewport - margin))
            }
        }

        Box(Modifier.fillMaxSize().horizontalScroll(hScroll)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.width(contentWidthDp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = if (rulerOn) 12.dp + RulerHeight else 12.dp, bottom = 12.dp)
            ) {
                itemsIndexed(state.pages) { index, info ->
                    PageView(
                        state = state, index = index, info = info, pxPerPoint = pxPerPoint, caretOn = caretOn,
                        onCommand = onCommand,
                        onTouched = {
                            focusRequester.requestFocus()
                            keyboard?.show()
                        }
                    )
                }
            }
        }

        if (rulerOn) {
            val pg = state.pages.getOrNull(state.focus?.page ?: state.visiblePage) ?: state.pages.firstOrNull()
            if (pg != null) {
                val contentPx = with(density) { contentWidthDp.toPx() }
                val pageLeft = (contentPx - pg.w * pxPerPoint) / 2f - hScroll.value
                Ruler(state, pg, pageLeft, pxPerPoint, Modifier.align(Alignment.TopStart))
            }
        }

        ImeCapture(state, focusRequester, Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun PageView(
    state: EditorState,
    index: Int,
    info: PageInfo,
    pxPerPoint: Float,
    caretOn: Boolean,
    onCommand: (String, String) -> Unit,
    onTouched: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var liveStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var lastDoubleAt by remember { mutableStateOf(0L) }
    var lastDoublePos by remember { mutableStateOf(Offset.Zero) }
    val drawing = state.drawTool != null
    val tripleSlop = with(density) { 48.dp.toPx() }

    // Quantise the scale so pinch-zooming doesn't re-render on every tiny change.
    val renderScale = (pxPerPoint * 20f).roundToInt().coerceAtLeast(1) / 20f
    LaunchedEffect(index, state.revision, renderScale) {
        state.renderPage(index, renderScale)?.let { image = it.asImageBitmap() }
    }

    val widthDp = with(density) { (info.w * pxPerPoint).toDp() }
    val heightDp = with(density) { (info.h * pxPerPoint).toDp() }

    Box(
        Modifier
            .size(widthDp, heightDp)
            .shadow(3.dp, RoundedCornerShape(2.dp))
            .background(Color.White)
            .semantics { contentDescription = "Page ${index + 1}" }
            .pointerInput(index, pxPerPoint, drawing) {
                if (drawing) return@pointerInput
                detectTapGestures(
                    onTap = { offset ->
                        val px = offset.x / pxPerPoint
                        val py = offset.y / pxPerPoint
                        val now = System.currentTimeMillis()
                        val triple = now - lastDoubleAt < 900 && (offset - lastDoublePos).getDistance() < tripleSlop
                        if (triple) {
                            // Triple tap selects the whole paragraph.
                            lastDoubleAt = 0L
                            scope.launch {
                                state.hit(index, px, py, false)
                                state.command("select.paragraph", edits = false)
                            }
                        } else {
                            scope.launch {
                                // A picture or shape under the finger is selected; otherwise the caret moves.
                                state.tap(index, px, py)
                                // Tapping a squiggle offers its suggestions.
                                state.suggestion = state.proofMarks.firstOrNull {
                                    it.page == index && px >= it.x && px <= it.x + it.w && py >= it.top && py <= it.top + it.h
                                }
                            }
                        }
                        onTouched()
                    },
                    onDoubleTap = { offset ->
                        lastDoubleAt = System.currentTimeMillis()
                        lastDoublePos = offset
                        val px = offset.x / pxPerPoint
                        val py = offset.y / pxPerPoint
                        scope.launch {
                            when {
                                // Double tap in the top or bottom margin edits the header or footer in place.
                                py < info.by - 4f -> state.command("insert.editHeader", JSONObject().put("page", index).toString(), edits = false)
                                py > info.by + info.bh + 4f -> state.command("insert.editFooter", JSONObject().put("page", index).toString(), edits = false)
                                else -> {
                                    // Double tap selects the word, as in Word.
                                    state.hit(index, px, py, false)
                                    state.command("select.word", edits = false)
                                }
                            }
                        }
                        onTouched()
                    }
                )
            }
            .pointerInput(index, pxPerPoint, drawing) {
                if (drawing) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        scope.launch {
                            state.hit(index, offset.x / pxPerPoint, offset.y / pxPerPoint, false)
                            state.command("select.word", edits = false)
                        }
                        onTouched()
                    },
                    onDrag = { change, _ ->
                        val p = change.position
                        scope.launch { state.hit(index, p.x / pxPerPoint, p.y / pxPerPoint, true) }
                    }
                )
            }
            .pointerInput(drawing, pxPerPoint) {
                if (!drawing) return@pointerInput
                // Ink: collect one stroke per touch and hand it to the engine when the finger lifts.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (state.stylusOnly && down.type != PointerType.Stylus) return@awaitEachGesture
                    val pts = ArrayList<Offset>()
                    pts.add(down.position)
                    liveStroke = pts.toList()
                    down.consume()
                    val tool = state.drawTool ?: "pen"
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        pts.add(change.position)
                        change.consume()
                        liveStroke = pts.toList()
                        if (tool == "eraser" && pts.size % 4 == 0) {
                            val p = change.position
                            scope.launch {
                                state.command("draw.erase", JSONObject().put("page", index).put("x", (p.x / pxPerPoint).toDouble()).put("y", (p.y / pxPerPoint).toDouble()).toString())
                            }
                        }
                    }
                    val used = state.drawTool
                    if (used == "eraser") {
                        val p = pts.last()
                        scope.launch {
                            state.command("draw.erase", JSONObject().put("page", index).put("x", (p.x / pxPerPoint).toDouble()).put("y", (p.y / pxPerPoint).toDouble()).toString())
                        }
                    } else if (used != null && pts.size >= 2) {
                        val step = max(1, pts.size / 1500)
                        val arr = JSONArray()
                        pts.forEachIndexed { i, p ->
                            if (i % step == 0 || i == pts.size - 1) {
                                arr.put(JSONArray().put((p.x / pxPerPoint).toDouble()).put((p.y / pxPerPoint).toDouble()))
                            }
                        }
                        val params = JSONObject().put("page", index).put("points", arr).put("tool", used)
                            .put("color", state.drawColor).put("width", state.drawWidth.toDouble())
                        scope.launch { state.command("draw.stroke", params.toString()) }
                    }
                    liveStroke = emptyList()
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            image?.let { drawImage(it, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())) }
            drawProof(state, index, pxPerPoint)
            drawFindHits(state, index, pxPerPoint)
            if (state.selObject == null) drawSelection(state, index, info, pxPerPoint)
            if (state.collapsed && caretOn && !drawing) drawCaret(state, index, pxPerPoint)
            if (liveStroke.size > 1) drawLiveStroke(liveStroke, state.drawTool, state.drawColor, state.drawWidth * pxPerPoint)
        }

        // Comment markers in the right margin; tapping one opens the review panel.
        state.commentMarks.filter { it.page == index }.forEach { m ->
            val markerPx = with(density) { 22.dp.toPx() }
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            ((info.bx + info.bw) * pxPerPoint + 4f).coerceAtMost(info.w * pxPerPoint - markerPx).roundToInt(),
                            (m.top * pxPerPoint).roundToInt()
                        )
                    }
                    .size(22.dp)
                    .background(if (m.resolved) Color(0xFFB7C3D9) else Color(0xFFFFC107), RoundedCornerShape(6.dp))
                    .clickable { state.reviewRequest++ },
                contentAlignment = Alignment.Center
            ) { Text("C", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3B2F00)) }
        }

        if (!state.collapsed && state.selObject == null && !drawing) {
            val a = state.anchor
            val f = state.focus
            if (a != null && a.page == index) SelectionHandle(state, index, pxPerPoint, isAnchor = true)
            if (f != null && f.page == index) SelectionHandle(state, index, pxPerPoint, isAnchor = false)
            MiniToolbar(state, index, info, pxPerPoint, onCommand)
        }
        if (state.selObject?.page == index) ObjectOverlay(state, index, info, pxPerPoint, onCommand)
        SuggestionMenu(state, index, pxPerPoint)
    }
}

/** The menu that opens when a squiggle is tapped: suggestions, Ignore all, Add to dictionary. */
@Composable
private fun SuggestionMenu(state: EditorState, index: Int, ppp: Float) {
    val m = state.suggestion ?: return
    if (m.page != index) return
    val scope = rememberCoroutineScope()
    Box(Modifier.offset { IntOffset((m.x * ppp).roundToInt(), ((m.top + m.h) * ppp).roundToInt()) }) {
        DropdownMenu(expanded = true, onDismissRequest = { state.suggestion = null }) {
            Text(m.message, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontSize = 12.sp, color = Color(0xFF667085))
            m.suggestions.forEach { s ->
                DropdownMenuItem(text = { Text(s, fontWeight = FontWeight.SemiBold) }, onClick = { scope.launch { state.fixIssue(m, s) } })
            }
            DropdownMenuItem(text = { Text("Ignore all") }, onClick = {
                state.suggestion = null
                scope.launch {
                    state.selectIssue(m)
                    state.command("review.ignoreAll", edits = false)
                    state.refreshProof()
                }
            })
            DropdownMenuItem(text = { Text("Add to dictionary") }, onClick = {
                state.suggestion = null
                scope.launch {
                    state.command("review.addToDictionary", JSONObject().put("word", m.text).toString(), edits = false)
                    state.refreshProof()
                }
            })
        }
    }
}

/** A draggable teardrop under one end of the selection. */
@Composable
private fun SelectionHandle(state: EditorState, index: Int, ppp: Float, isAnchor: Boolean) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val geo = (if (isAnchor) state.anchor else state.focus) ?: return
    val current by rememberUpdatedState(geo)
    var pos by remember { mutableStateOf(Offset.Zero) }
    val radiusPx = with(density) { 22.dp.toPx() }
    Box(
        Modifier
            .offset { IntOffset((geo.x * ppp - radiusPx).roundToInt(), ((geo.top + geo.height) * ppp).roundToInt()) }
            .size(44.dp)
            .pointerInput(isAnchor, ppp) {
                detectDragGestures(
                    onDragStart = { pos = Offset(current.x * ppp, (current.top + current.height / 2f) * ppp) },
                    onDrag = { change, drag ->
                        change.consume()
                        pos += drag
                        scope.launch {
                            if (isAnchor) state.moveAnchor(index, pos.x / ppp, pos.y / ppp)
                            else state.hit(index, pos.x / ppp, pos.y / ppp, true)
                        }
                    }
                )
            },
        contentAlignment = Alignment.TopCenter
    ) {
        Box(Modifier.size(18.dp).background(WordBlue, CircleShape))
    }
}

/** Word's mini toolbar: appears above the selection with the formatting you reach for first. */
@Composable
private fun MiniToolbar(state: EditorState, index: Int, info: PageInfo, ppp: Float, onCommand: (String, String) -> Unit) {
    val a = state.anchor ?: return
    val f = state.focus ?: return
    val first = if (a.page < f.page || (a.page == f.page && (a.top < f.top || (abs(a.top - f.top) < 1f && a.x <= f.x)))) a else f
    if (first.page != index) return
    val density = LocalDensity.current
    val widthPx = with(density) { 330.dp.toPx() }
    val heightPx = with(density) { 44.dp.toPx() }
    val pageW = info.w * ppp
    val x = (first.x * ppp - widthPx / 3f).coerceIn(0f, max(0f, pageW - widthPx))
    val above = first.top * ppp - heightPx - with(density) { 8.dp.toPx() }
    val y = if (above >= 0f) above else (first.top + first.height) * ppp + with(density) { 40.dp.toPx() }
    val fmt = state.fmt
    Row(
        Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .shadow(8.dp, RoundedCornerShape(12.dp))
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.widthIn(max = 326.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            MiniButton(commandIcon("edit.cut"), "Cut", false) { onCommand("edit.cut", "{}") }
            MiniButton(commandIcon("edit.copy"), "Copy", false) { onCommand("edit.copy", "{}") }
            MiniButton(commandIcon("edit.paste"), "Paste", false) { onCommand("edit.paste", "{}") }
            Box(Modifier.size(width = 1.dp, height = 24.dp).background(Color(0xFFDDDDDD)))
            MiniButton(Icons.Default.FormatBold, "Bold", fmt.bold) { onCommand("format.bold", "{}") }
            MiniButton(Icons.Default.FormatItalic, "Italic", fmt.italic) { onCommand("format.italic", "{}") }
            MiniButton(Icons.Default.FormatUnderlined, "Underline", fmt.underline) { onCommand("format.underline", "{}") }
            MiniButton(commandIcon("format.highlight"), "Highlight", fmt.highlight) { onCommand("format.highlight", "{\"color\":\"FFFF00\"}") }
            Box(Modifier.size(width = 1.dp, height = 24.dp).background(Color(0xFFDDDDDD)))
            MiniText("Select all") { onCommand("select.all", "{}") }
            MiniText("Look up") { onCommand("app.lookup", "{}") }
        }
    }
}

@Composable
private fun MiniText(label: String, onClick: () -> Unit) {
    Box(Modifier.padding(horizontal = 2.dp).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp)) {
        Text(label, fontSize = 13.sp, color = Color(0xFF242424), maxLines = 1)
    }
}

@Composable
private fun MiniButton(icon: androidx.compose.ui.graphics.vector.ImageVector?, label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .padding(2.dp)
            .background(if (active) Color(0xFFDCE8FA) else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) Icon(icon, label, Modifier.size(20.dp), tint = if (active) WordBlue else Color(0xFF242424))
        else Text(label.take(1))
    }
}

private fun DrawScope.drawCaret(state: EditorState, index: Int, ppp: Float) {
    val caret = state.focus ?: return
    if (caret.page != index) return
    drawRect(
        color = Color(0xFF1B1B1B),
        topLeft = Offset(caret.x * ppp, caret.top * ppp),
        size = Size(2f.coerceAtLeast(1.5f * ppp), caret.height * ppp)
    )
}

private fun DrawScope.drawFindHits(state: EditorState, index: Int, ppp: Float) {
    if (!state.findActive) return
    for (h in state.findHits) {
        if (h.page != index) continue
        drawRect(Color(0x99FFD600), Offset(h.x * ppp, h.top * ppp), Size(h.w * ppp, h.h * ppp))
    }
}

/**
 * Approximate selection highlight built from the engine's caret geometry (first line from the
 * anchor to the right margin, whole lines in between, last line up to the focus).
 */
private fun DrawScope.drawSelection(state: EditorState, index: Int, info: PageInfo, ppp: Float) {
    val a = state.anchor ?: return
    val f = state.focus ?: return
    if (state.collapsed) return
    val (first, last) = if (a.page < f.page || (a.page == f.page && (a.top < f.top || (abs(a.top - f.top) < 1f && a.x <= f.x)))) a to f else f to a
    if (index < first.page || index > last.page) return
    val color = Color(0x553D7BFF)
    val left = info.bx
    val right = info.bx + info.bw
    val startsHere = index == first.page
    val endsHere = index == last.page
    fun fill(x: Float, y: Float, w: Float, h: Float) {
        if (w > 0f && h > 0f) drawRect(color, Offset(x * ppp, y * ppp), Size(w * ppp, h * ppp))
    }
    if (startsHere && endsHere && abs(first.top - last.top) < 1f) {
        fill(first.x, first.top, last.x - first.x, first.height)
        return
    }
    val middleTop = if (startsHere) {
        fill(first.x, first.top, right - first.x, first.height)
        first.top + first.height
    } else info.by
    val middleBottom = if (endsHere) {
        fill(left, last.top, last.x - left, last.height)
        last.top
    } else info.by + info.bh
    fill(left, middleTop, right - left, middleBottom - middleTop)
}

/**
 * Invisible text field that only exists to bring up the Android keyboard. Every change is
 * diffed against a one-character sentinel and forwarded to the engine as text.insert /
 * text.backspace, then the field is reset. The field uses a password-type input so keyboards
 * send committed characters rather than composing regions.
 * Limitation: no composing text, predictive suggestions or surrounding-text IME protocol yet.
 */
@Composable
private fun ImeCapture(state: EditorState, focusRequester: FocusRequester, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf(TextFieldValue(SENTINEL, TextRange(SENTINEL.length))) }
    BasicTextField(
        value = value,
        onValueChange = { next ->
            val text = next.text
            when {
                text.length < SENTINEL.length -> scope.launch { state.backspace() }
                text.length > SENTINEL.length -> {
                    val added = text.removePrefix(SENTINEL).ifEmpty { text.drop(SENTINEL.length) }
                    if (added.isNotEmpty()) scope.launch { state.typeText(added) }
                }
            }
            value = TextFieldValue(SENTINEL, TextRange(SENTINEL.length))
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = modifier.size(1.dp).alpha(0f).focusRequester(focusRequester).padding(0.dp)
    )
}

private data class Frame(val x: Float, val y: Float, val w: Float, val h: Float)

/**
 * Frame, eight resize handles and a toolbar for the selected picture or shape. Corner handles
 * keep the proportions; side handles stretch one way. A floating (wrapped) picture can also be
 * dragged to a new place. The change is committed to the engine when the finger lifts.
 */
@Composable
private fun ObjectOverlay(state: EditorState, index: Int, info: PageInfo, ppp: Float, onCommand: (String, String) -> Unit) {
    val obj = state.selObject ?: return
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var live by remember { mutableStateOf<Frame?>(null) }
    val liveNow by rememberUpdatedState(live)
    val objNow by rememberUpdatedState(obj)
    val frame = live ?: Frame(obj.x, obj.y, obj.w, obj.h)
    val handleBox = with(density) { 36.dp.toPx() }

    fun commit(f: Frame, moved: Boolean) {
        live = null
        val o = objNow
        scope.launch {
            val params = org.json.JSONObject().put("width", f.w.toDouble()).put("height", f.h.toDouble())
            if (o.floating) {
                params.put("page", index).put("x", f.x.toDouble()).put("y", f.y.toDouble())
                state.command("arrange.bounds", params.toString())
            } else if (!moved) {
                params.put("lockAspect", false)
                state.command("picture.size", params.toString())
            }
        }
    }

    // The frame outline.
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            WordBlue, Offset(frame.x * ppp, frame.y * ppp), Size(frame.w * ppp, frame.h * ppp),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
        )
    }

    // Drag the picture itself (floating pictures only).
    if (obj.floating) {
        var acc by remember { mutableStateOf(Offset.Zero) }
        var start by remember { mutableStateOf<Frame?>(null) }
        Box(
            Modifier
                .offset { IntOffset((frame.x * ppp).roundToInt(), (frame.y * ppp).roundToInt()) }
                .size(with(density) { (frame.w * ppp).toDp() }, with(density) { (frame.h * ppp).toDp() })
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { acc = Offset.Zero; start = Frame(objNow.x, objNow.y, objNow.w, objNow.h) },
                        onDrag = { change, drag ->
                            change.consume()
                            acc += drag
                            val s0 = start ?: return@detectDragGestures
                            live = Frame(s0.x + acc.x / ppp, s0.y + acc.y / ppp, s0.w, s0.h)
                        },
                        onDragEnd = { liveNow?.let { commit(it, moved = true) } },
                        onDragCancel = { live = null }
                    )
                }
        )
    }

    // Eight resize handles.
    val spots = listOf(0f to 0f, 0.5f to 0f, 1f to 0f, 0f to 0.5f, 1f to 0.5f, 0f to 1f, 0.5f to 1f, 1f to 1f)
    spots.forEach { (fx, fy) ->
        var acc by remember { mutableStateOf(Offset.Zero) }
        var start by remember { mutableStateOf<Frame?>(null) }
        Box(
            Modifier
                .offset {
                    IntOffset(
                        ((frame.x + fx * frame.w) * ppp - handleBox / 2f).roundToInt(),
                        ((frame.y + fy * frame.h) * ppp - handleBox / 2f).roundToInt()
                    )
                }
                .size(36.dp)
                .pointerInput(fx, fy) {
                    detectDragGestures(
                        onDragStart = { acc = Offset.Zero; start = Frame(objNow.x, objNow.y, objNow.w, objNow.h) },
                        onDrag = { change, drag ->
                            change.consume()
                            acc += drag
                            val s0 = start ?: return@detectDragGestures
                            val dx = acc.x / ppp
                            val dy = acc.y / ppp
                            var x = s0.x; var y = s0.y; var w = s0.w; var h = s0.h
                            if (fx == 0f) { x += dx; w -= dx } else if (fx == 1f) w += dx
                            if (fy == 0f) { y += dy; h -= dy } else if (fy == 1f) h += dy
                            val corner = fx != 0.5f && fy != 0.5f
                            if (corner) {
                                val k = max(w / s0.w, h / s0.h).coerceAtLeast(12f / s0.w)
                                w = s0.w * k; h = s0.h * k
                                if (fx == 0f) x = s0.x + s0.w - w
                                if (fy == 0f) y = s0.y + s0.h - h
                            }
                            w = w.coerceAtLeast(12f); h = h.coerceAtLeast(12f)
                            live = Frame(x, y, w, h)
                        },
                        onDragEnd = { liveNow?.let { commit(it, moved = false) } },
                        onDragCancel = { live = null }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(14.dp).background(Color.White, CircleShape).border(2.dp, WordBlue, CircleShape))
        }
    }

    // Toolbar above (or below) the frame.
    val toolbarWidthPx = with(density) { 320.dp.toPx() }
    val pageW = info.w * ppp
    val tx = (frame.x * ppp).coerceIn(0f, max(0f, pageW - toolbarWidthPx))
    val above = frame.y * ppp - with(density) { 52.dp.toPx() }
    val ty = if (above >= 0f) above else (frame.y + frame.h) * ppp + with(density) { 22.dp.toPx() }
    Row(
        Modifier
            .offset { IntOffset(tx.roundToInt(), ty.roundToInt()) }
            .shadow(8.dp, RoundedCornerShape(12.dp))
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val items = if (obj.picture) listOf(
            "Wrap" to "arrange.wrap", "Rotate" to "arrange.rotate", "Size" to "picture.size", "Border" to "picture.border",
            "Alt text" to "picture.altText", "Reset" to "picture.reset", "Delete" to "text.backspace"
        ) else listOf("Wrap" to "arrange.wrap", "Rotate" to "arrange.rotate", "Delete" to "text.backspace")
        Row(Modifier.widthIn(max = 320.dp).horizontalScroll(rememberScrollState())) {
            items.forEach { (label, id) ->
                Box(
                    Modifier.padding(horizontal = 2.dp)
                        .background(if (id == "text.backspace") Color(0xFFFDECEA) else Color(0xFFF2F4F7), RoundedCornerShape(10.dp))
                        .clickable { onCommand(id, "{}") }
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) { Text(label, fontSize = 13.sp, color = if (id == "text.backspace") Color(0xFFB42318) else Color(0xFF242424)) }
            }
        }
    }
}

/** Wavy underlines: red for spelling, blue for grammar and style. */
private fun DrawScope.drawProof(state: EditorState, index: Int, ppp: Float) {
    if (!state.proofing) return
    for (m in state.proofMarks) {
        if (m.page != index) continue
        val color = when (m.kind) {
            "spelling" -> Color(0xFFD32F2F)
            "grammar" -> Color(0xFF1E5AA8)
            else -> Color(0xFF7B1FA2)
        }
        val y = (m.top + m.h) * ppp - 1f
        var x = m.x * ppp
        val end = (m.x + m.w) * ppp
        val path = Path()
        path.moveTo(x, y)
        var up = true
        while (x < end) {
            x = minOf(x + 3f, end)
            path.lineTo(x, if (up) y - 2.4f else y)
            up = !up
        }
        drawPath(path, color, style = Stroke(width = 1.7f))
    }
}

/** The stroke being drawn right now, before the engine has it. */
private fun DrawScope.drawLiveStroke(points: List<Offset>, tool: String?, colorHex: String, widthPx: Float) {
    if (tool == "eraser") {
        drawCircle(Color(0x55000000), radius = 14f, center = points.last())
        return
    }
    val path = Path()
    path.moveTo(points.first().x, points.first().y)
    for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
    val base = Color(("FF$colorHex").toLong(16))
    val color = if (tool == "highlighter") base.copy(alpha = 0.4f) else base
    drawPath(path, color, style = Stroke(width = maxOf(widthPx, 2f), cap = androidx.compose.ui.graphics.StrokeCap.Round))
}
