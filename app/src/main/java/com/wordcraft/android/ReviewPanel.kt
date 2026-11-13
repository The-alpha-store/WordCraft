package com.wordcraft.android

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private class Change(val kind: String, val author: String, val text: String, val start: JSONObject, val end: JSONObject)
private class Comment(val id: Int, val parent: Int?, val author: String, val text: String, val resolved: Boolean)

/**
 * Review: tracked changes with Accept and Reject for each one, comments with Reply, Resolve and
 * Delete, and the spelling and grammar issues with their suggestions.
 */
@Composable
fun ReviewPanelContent(state: EditorState, startTab: String, onRun: (String, String) -> Unit) {
    var tab by remember(startTab) { mutableStateOf(startTab) }
    var tick by remember { mutableIntStateOf(0) }
    var changes by remember { mutableStateOf<List<Change>>(emptyList()) }
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // Reload when the document changes or after an action here (not on every refresh).
    LaunchedEffect(state.editCount, tab, tick) {
        if (tab == "Changes") {
            val arr = state.exec("review.changes", edits = false).result as? JSONArray
            changes = (0 until (arr?.length() ?: 0)).map { i ->
                val o = arr!!.getJSONObject(i)
                Change(o.optString("kind"), o.optString("author"), o.optString("text"), o.getJSONObject("start"), o.getJSONObject("end"))
            }
        } else if (tab == "Comments") {
            val arr = state.exec("review.comments", edits = false).result as? JSONArray
            comments = (0 until (arr?.length() ?: 0)).map { i ->
                val o = arr!!.getJSONObject(i)
                Comment(o.optInt("id"), if (o.isNull("parent")) null else o.optInt("parent"), o.optString("author"), o.optString("text"), o.optBoolean("resolved"))
            }
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Changes", "Comments", "Editor").forEach { name ->
            Box(
                Modifier
                    .background(if (tab == name) Color(0xFFDCE8FA) else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { tab = name }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) { Text(name, fontSize = 13.sp, color = if (tab == name) WordBlue else Color(0xFF242424)) }
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))

    when (tab) {
        "Changes" -> {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Pill(if (state.toggles["review.trackChanges"] == true) "Tracking on" else "Tracking off", state.toggles["review.trackChanges"] == true) {
                    onRun("review.trackChanges", "{}")
                    tick++
                }
                Pill("Accept all", false) { onRun("review.acceptAll", "{}"); tick++ }
                Pill("Reject all", false) { onRun("review.rejectAll", "{}"); tick++ }
            }
            if (changes.isEmpty()) Text("No tracked changes. Turn tracking on and edit to see them here.", fontSize = 12.sp, color = Color(0xFF667085))
            LazyColumn {
                items(changes) { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            Modifier.weight(1f).clickable {
                                scope.launch {
                                    state.command("select.range", JSONObject().put("anchor", c.start).put("focus", c.end).toString(), edits = false)
                                    state.scrollToCaret()
                                }
                            }
                        ) {
                            Text(
                                (if (c.kind == "insert") "Inserted" else if (c.kind == "delete") "Deleted" else c.kind.replaceFirstChar { it.uppercase() }) + " · " + c.author,
                                fontSize = 11.sp, color = Color(0xFF667085)
                            )
                            Text(c.text.ifBlank { "(formatting)" }.take(90), fontSize = 13.sp, maxLines = 2)
                        }
                        IconButton(onClick = {
                            scope.launch {
                                state.command("select.range", JSONObject().put("anchor", c.start).put("focus", c.end).toString(), edits = false)
                                state.command("review.accept", "{\"next\":false}")
                                tick++
                            }
                        }, Modifier.size(36.dp)) { Icon(Icons.Default.Check, "Accept", tint = Color(0xFF2E7D32)) }
                        IconButton(onClick = {
                            scope.launch {
                                state.command("select.range", JSONObject().put("anchor", c.start).put("focus", c.end).toString(), edits = false)
                                state.command("review.reject", "{\"next\":false}")
                                tick++
                            }
                        }, Modifier.size(36.dp)) { Icon(Icons.Default.Close, "Reject", tint = Color(0xFFB42318)) }
                    }
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                }
            }
        }
        "Comments" -> {
            var draft by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it }, singleLine = true,
                    placeholder = { Text("Comment on the selection") }, modifier = Modifier.weight(1f)
                )
                TextButton(enabled = draft.isNotBlank(), onClick = {
                    onRun("review.newComment", JSONObject().put("text", draft).toString())
                    draft = ""
                    tick++
                }) { Text("Add") }
            }
            LazyColumn {
                items(comments.filter { it.parent == null }) { c ->
                    var reply by remember { mutableStateOf("") }
                    var replying by remember { mutableStateOf(false) }
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(c.author + if (c.resolved) " · Resolved" else "", fontSize = 11.sp, color = Color(0xFF667085))
                        Text(c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        comments.filter { it.parent == c.id }.forEach { r ->
                            Text("${r.author}: ${r.text}", Modifier.padding(start = 12.dp, top = 4.dp), fontSize = 13.sp)
                        }
                        Row {
                            TextButton(onClick = { replying = !replying }) { Text("Reply", fontSize = 12.sp) }
                            TextButton(onClick = { onRun("review.resolveComment", JSONObject().put("id", c.id).toString()); tick++ }) {
                                Text(if (c.resolved) "Reopen" else "Resolve", fontSize = 12.sp)
                            }
                            TextButton(onClick = { onRun("review.deleteComment", JSONObject().put("id", c.id).toString()); tick++ }) {
                                Text("Delete", fontSize = 12.sp, color = Color(0xFFB42318))
                            }
                        }
                        if (replying) Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = reply, onValueChange = { reply = it }, singleLine = true, modifier = Modifier.weight(1f))
                            TextButton(enabled = reply.isNotBlank(), onClick = {
                                onRun("review.reply", JSONObject().put("id", c.id).put("text", reply).toString())
                                reply = ""; replying = false; tick++
                            }) { Text("Send") }
                        }
                    }
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                }
            }
            if (comments.isEmpty()) Text("No comments yet.", fontSize = 12.sp, color = Color(0xFF667085))
        }
        else -> {
            if (!state.proofing) Text("Spell check is off. Turn it on from the ⋮ menu.", fontSize = 12.sp, color = Color(0xFF667085))
            else if (state.proofMarks.isEmpty()) Text("No spelling or grammar issues found.", fontSize = 12.sp, color = Color(0xFF667085))
            LazyColumn {
                items(state.proofMarks) { m ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(
                            m.text, Modifier.clickable { scope.launch { if (state.selectIssue(m)) state.scrollToCaret() } },
                            fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            color = if (m.kind == "spelling") Color(0xFFC62828) else Color(0xFF1E5AA8)
                        )
                        Text(m.message, fontSize = 11.sp, color = Color(0xFF667085))
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp)) {
                            m.suggestions.forEach { s -> Pill(s, false) { scope.launch { state.fixIssue(m, s) } } }
                        }
                    }
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                }
            }
        }
    }
}
