package com.wordcraft.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

val RulerHeight = 30.dp

/**
 * Word's ruler: inches along the page, the margins shaded, and draggable markers for the
 * first-line indent, the left indent and the right indent of the paragraph at the caret.
 * Tap the ruler to add a tab stop; tap a tab stop to remove it.
 *
 * [pageLeftPx] is where the page's left edge is on screen, [ppp] the pixels per point.
 */
@Composable
fun Ruler(state: EditorState, page: PageInfo, pageLeftPx: Float, ppp: Float, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val f = state.fmt
    // Live values while a marker is being dragged (points, relative to the left margin).
    var liveLeft by remember { mutableStateOf<Float?>(null) }
    var liveFirst by remember { mutableStateOf<Float?>(null) }
    var liveRight by remember { mutableStateOf<Float?>(null) }
    var startLeft by remember { mutableStateOf(0f) }
    var startFirst by remember { mutableStateOf(0f) }
    var startRight by remember { mutableStateOf(0f) }
    val left = liveLeft ?: f.indentLeft
    val first = liveFirst ?: f.indentFirst
    val right = liveRight ?: f.indentRight
    val bodyLeftPx = pageLeftPx + page.bx * ppp
    val bodyRightPx = pageLeftPx + (page.bx + page.bw) * ppp
    val heightPx = with(density) { RulerHeight.toPx() }

    fun run(id: String, params: JSONObject) {
        scope.launch { state.command(id, params.toString()) }
    }

    Box(modifier.fillMaxWidth().height(RulerHeight).background(Color(0xFFE9ECF1))) {
        Canvas(
            Modifier.matchParentSize().pointerInput(page, pageLeftPx, ppp) {
                // Tap an empty spot to add a left tab stop there.
                detectTapGestures { o ->
                    val pos = (o.x - bodyLeftPx) / ppp
                    if (pos > 0f && pos < page.bw) {
                        run("para.tabs", JSONObject().put("set", JSONArray().put(JSONObject().put("pos", pos.toDouble()).put("align", "left"))))
                    }
                }
            }
        ) {
            // White writing area between the margins.
            drawRect(Color.White, Offset(bodyLeftPx, 4f), androidx.compose.ui.geometry.Size(bodyRightPx - bodyLeftPx, size.height - 8f))
            drawScale(bodyLeftPx, bodyRightPx, ppp, size.height)
            // Tab stops.
            f.tabs.forEach { t ->
                val x = bodyLeftPx + t * ppp
                drawLine(Color(0xFF444444), Offset(x, size.height - 14f), Offset(x, size.height - 4f), strokeWidth = 3f)
                drawLine(Color(0xFF444444), Offset(x, size.height - 4f), Offset(x + 8f, size.height - 4f), strokeWidth = 3f)
            }
            // Indent markers.
            val firstX = bodyLeftPx + (left + first) * ppp
            val leftX = bodyLeftPx + left * ppp
            val rightX = bodyRightPx - right * ppp
            triangle(firstX, 4f, down = true)
            triangle(leftX, size.height - 4f, down = false)
            triangle(rightX, size.height - 4f, down = false)
        }

        // Tab stop remove targets.
        f.tabs.forEach { t ->
            Box(
                Modifier
                    .offset { IntOffset((bodyLeftPx + t * ppp).roundToInt() - 18, 0) }
                    .size(36.dp, RulerHeight)
                    .pointerInput(t) {
                        detectTapGestures { run("para.tabs", JSONObject().put("clear", JSONArray().put(t.toDouble()))) }
                    }
            )
        }

        // Draggable markers: the value at the start of the drag plus how far the finger moved.
        Marker(bodyLeftPx + (left + first) * ppp, top = true,
            onStart = { startFirst = f.indentFirst; startLeft = f.indentLeft },
            onDrag = { dx -> liveFirst = (startFirst + dx / ppp).coerceIn(-startLeft, page.bw - startLeft) },
            onEnd = { liveFirst?.let { run("para.indents", JSONObject().put("firstLine", it.toDouble())) }; liveFirst = null })
        Marker(bodyLeftPx + left * ppp, top = false,
            onStart = { startLeft = f.indentLeft },
            onDrag = { dx -> liveLeft = (startLeft + dx / ppp).coerceIn(0f, page.bw - 18f) },
            onEnd = { liveLeft?.let { run("para.indents", JSONObject().put("left", it.toDouble())) }; liveLeft = null })
        Marker(bodyRightPx - right * ppp, top = false,
            onStart = { startRight = f.indentRight },
            onDrag = { dx -> liveRight = (startRight - dx / ppp).coerceIn(0f, page.bw - 18f) },
            onEnd = { liveRight?.let { run("para.indents", JSONObject().put("right", it.toDouble())) }; liveRight = null })
    }
}

/** A 40dp touch target centred on a marker; reports the horizontal drag. */
@Composable
private fun Marker(xPx: Float, top: Boolean, onStart: () -> Unit, onDrag: (Float) -> Unit, onEnd: () -> Unit) {
    val density = LocalDensity.current
    val half = with(density) { 20.dp.toPx() }
    val startNow by rememberUpdatedState(onStart)
    val dragNow by rememberUpdatedState(onDrag)
    val endNow by rememberUpdatedState(onEnd)
    var acc by remember { mutableStateOf(0f) }
    Box(
        Modifier
            .offset { IntOffset((xPx - half).roundToInt(), 0) }
            .size(40.dp, RulerHeight)
            .pointerInput(top) {
                detectDragGestures(
                    onDragStart = { acc = 0f; startNow() },
                    onDrag = { change, drag ->
                        change.consume()
                        acc += drag.x
                        dragNow(acc)
                    },
                    onDragEnd = { endNow() },
                    onDragCancel = { endNow() }
                )
            },
        contentAlignment = if (top) Alignment.TopCenter else Alignment.BottomCenter
    ) {}
}

private fun DrawScope.triangle(x: Float, y: Float, down: Boolean) {
    val path = Path()
    if (down) {
        path.moveTo(x - 7f, y); path.lineTo(x + 7f, y); path.lineTo(x, y + 11f)
    } else {
        path.moveTo(x - 7f, y); path.lineTo(x + 7f, y); path.lineTo(x, y - 11f)
    }
    path.close()
    drawPath(path, Color(0xFF185ABD))
}

/** Inch ticks across the writing area: long at each inch, medium at halves, short at eighths. */
private fun DrawScope.drawScale(bodyLeft: Float, bodyRight: Float, ppp: Float, h: Float) {
    val inch = 72f * ppp
    if (inch < 8f) return
    val mid = h / 2f
    var i = 0
    while (true) {
        val x = bodyLeft + i * inch / 8f
        if (x > bodyRight) break
        val len = when {
            i % 8 == 0 -> 9f
            i % 4 == 0 -> 6f
            else -> 3f
        }
        drawLine(Color(0xFF8A8F99), Offset(x, mid - len / 2f), Offset(x, mid + len / 2f), strokeWidth = 1.5f)
        i++
    }
}
