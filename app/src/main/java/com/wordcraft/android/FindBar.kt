package com.wordcraft.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Docked Find and Replace, above the quick bar so the page stays visible. Matches are
 * highlighted on the pages as you type; up and down step through them.
 */
@Composable
fun FindBar(state: EditorState, startWithReplace: Boolean, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var wholeWord by remember { mutableStateOf(false) }
    var showReplace by remember { mutableStateOf(startWithReplace) }
    var count by remember { mutableStateOf<Int?>(null) }
    var index by remember { mutableStateOf(0) }
    val focus = remember { FocusRequester() }

    fun opts(withText: Boolean = true): JSONObject {
        val o = JSONObject().put("matchCase", matchCase).put("wholeWord", wholeWord)
        if (withText) o.put("text", query)
        return o
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
    DisposableEffect(Unit) { onDispose { state.findActive = false } }

    // Search as you type (after a short pause), highlight, and jump to the first match.
    LaunchedEffect(query, matchCase, wholeWord) {
        state.findActive = query.isNotEmpty()
        if (query.isEmpty()) {
            count = null
            state.refresh()
            return@LaunchedEffect
        }
        delay(180)
        val found = state.exec("edit.find", opts().put("highlight", true).toString(), edits = false)
        count = (found.result as? JSONObject)?.optInt("count") ?: 0
        if ((count ?: 0) > 0) {
            val next = state.exec("edit.findNext", opts().toString(), edits = false)
            index = ((next.result as? JSONObject)?.optInt("index") ?: 0) + 1
            state.scrollToCaret()
        }
    }

    fun step(forward: Boolean) {
        if (query.isEmpty()) return
        scope.launch {
            val r = state.exec(if (forward) "edit.findNext" else "edit.findPrevious", opts().toString(), edits = false)
            val o = r.result as? JSONObject
            if (o != null) {
                index = o.optInt("index") + 1
                count = o.optInt("count")
            }
            state.scrollToCaret()
        }
    }

    Column(Modifier.fillMaxWidth().background(Color.White)) {
        androidx.compose.material3.HorizontalDivider(color = Color(0xFFE1E1E1))
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(query, { query = it }, "Find in document", Modifier.weight(1f).focusRequester(focus))
            Text(
                when {
                    query.isEmpty() -> ""
                    count == null -> "…"
                    count == 0 -> "No results"
                    else -> "$index of $count"
                },
                Modifier.padding(horizontal = 8.dp), fontSize = 12.sp, color = Color(0xFF667085)
            )
            IconButton(onClick = { step(false) }, Modifier.size(40.dp)) { Icon(Icons.Default.KeyboardArrowUp, "Previous") }
            IconButton(onClick = { step(true) }, Modifier.size(40.dp)) { Icon(Icons.Default.KeyboardArrowDown, "Next") }
            IconButton(onClick = onClose, Modifier.size(40.dp)) { Icon(Icons.Default.Close, "Close find") }
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip("Match case", matchCase) { matchCase = !matchCase }
            Chip("Whole word", wholeWord) { wholeWord = !wholeWord }
            Chip("Replace", showReplace) { showReplace = !showReplace }
        }
        if (showReplace) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Field(replacement, { replacement = it }, "Replace with", Modifier.weight(1f))
                Chip("Replace", false) {
                    scope.launch {
                        state.exec("edit.replace", opts().put("with", replacement).toString())
                        step(true)
                    }
                }
                Chip("All", false) {
                    scope.launch {
                        val r = state.exec("edit.replaceAll", opts().put("with", replacement).toString())
                        val n = (r.result as? JSONObject)?.optInt("count", -1) ?: -1
                        if (r.ok) state.status = if (n >= 0) "Replaced $n" else "Replaced all"
                        count = 0
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier) {
    Box(
        modifier
            .height(40.dp)
            .background(Color(0xFFF4F5F7), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFFE1E4EA), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) Text(hint, fontSize = 14.sp, color = Color(0xFF98A2B3))
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp, color = Color(0xFF1B1B1B)),
            cursorBrush = SolidColor(WordBlue),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 6.dp)
            .background(if (on) Color(0xFFDCE8FA) else Color(0xFFF2F4F7), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(label, fontSize = 12.sp, color = if (on) WordBlue else Color(0xFF344054))
    }
}
