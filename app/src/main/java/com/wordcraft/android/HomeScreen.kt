package com.wordcraft.android

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

private val PageBg = Color(0xFFF6F7F9)

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

private fun dayStart(millis: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = millis
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun bucket(millis: Long): String {
    val days = ((dayStart(System.currentTimeMillis()) - dayStart(millis)) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "Today"
        days == 1 -> "Yesterday"
        days < 7 -> "Earlier this week"
        days < 31 -> "Earlier this month"
        else -> "Older"
    }
}

private fun timeText(millis: Long): String {
    val c = Calendar.getInstance()
    c.timeInMillis = millis
    val hm = "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    return when (bucket(millis)) {
        "Today" -> "Today, $hm"
        "Yesterday" -> "Yesterday, $hm"
        else -> {
            val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            "${c.get(Calendar.DAY_OF_MONTH)} ${months[c.get(Calendar.MONTH)]}, $hm"
        }
    }
}

private fun sizeText(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 * 1024 -> " · ${maxOf(1, bytes / 1024)} KB"
    else -> " · %.1f MB".format(bytes / 1048576.0)
}

private fun actionIcon(action: String): ImageVector = when {
    action.contains("PDF", ignoreCase = true) || action.contains("Exported", ignoreCase = true) -> Icons.Default.PictureAsPdf
    action.contains("Created", ignoreCase = true) -> Icons.Default.Add
    action.contains("Edited", ignoreCase = true) -> Icons.Default.Edit
    action.contains("Saved", ignoreCase = true) -> Icons.Default.Save
    else -> Icons.Default.FolderOpen
}

private val TEMPLATES = listOf(
    "blank" to "Blank", "letter" to "Letter", "resume" to "Resume", "report" to "Report", "sample" to "Sample"
)

private fun extOf(name: String) = name.substringAfterLast('.', "").lowercase()

private fun badgeColor(ext: String): Color = when (ext) {
    "doc", "docx" -> Color(0xFF185ABD)
    "pdf" -> Color(0xFFC62828)
    "odt" -> Color(0xFFEF6C00)
    "html", "htm" -> Color(0xFF2E7D32)
    else -> Color(0xFF667085)
}

/**
 * The start screen: a greeting, a template gallery, then everything recently done: your
 * documents with first-page previews (pin the ones you keep coming back to), and a timeline of
 * what you created, edited and exported. Recovery and crash notices appear at the top.
 */
@Composable
fun HomeScreen(
    docs: List<RecentDoc>,
    events: List<ActivityEvent>,
    thumb: (String) -> Bitmap?,
    recoveryTitle: String?,
    crashReport: String?,
    onRecover: () -> Unit,
    onDiscardRecovery: () -> Unit,
    onShareCrash: () -> Unit,
    onDismissCrash: () -> Unit,
    onNewTemplate: (String) -> Unit,
    onOpenFile: () -> Unit,
    onOpenRecent: (RecentDoc) -> Unit,
    onRemove: (RecentDoc) -> Unit,
    onTogglePin: (RecentDoc) -> Unit,
    onClearRecent: () -> Unit
) {
    var tab by remember { mutableStateOf("Recent") }
    var filter by remember { mutableStateOf("All") }
    var query by remember { mutableStateOf("") }
    val shownDocs = docs.filter { d ->
        (query.isBlank() || d.name.contains(query.trim(), ignoreCase = true)) && when (filter) {
            "Pinned" -> d.pinned
            "Opened" -> d.action == "Opened"
            "Saved" -> d.action == "Saved" || d.action == "Edited"
            "Exported" -> d.action == "Exported"
            else -> true
        }
    }

    Column(Modifier.fillMaxSize().background(PageBg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(WordBlue, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Text("W", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(greeting(), fontSize = 13.sp, color = Color(0xFF667085))
                Text("WordCraft", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1B))
            }
        }

        if (recoveryTitle != null) {
            Notice(
                "Recover “$recoveryTitle”?", "The app closed before this document was saved.",
                "Recover", onRecover, "Discard", onDiscardRecovery
            )
        }
        if (crashReport != null) {
            Notice(
                "WordCraft closed unexpectedly", "A report was saved. Sharing it helps fix the problem.",
                "Share report", onShareCrash, "Dismiss", onDismissCrash
            )
        }

        Text("Start a document", Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF344054))
        LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(TEMPLATES) { (kind, label) -> TemplateTile(kind, label) { onNewTemplate(kind) } }
            item {
                Column(
                    Modifier.width(96.dp).clickable(onClick = onOpenFile),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(width = 84.dp, height = 112.dp).background(Color.White, RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFFD5D9E0), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.FolderOpen, null, Modifier.size(34.dp), tint = WordBlue) }
                    Text("Open…", Modifier.padding(top = 6.dp), fontSize = 12.sp)
                }
            }
        }

        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Search recent documents") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp)
        )

        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            listOf("Recent", "Activity").forEach { name ->
                Box(
                    Modifier
                        .padding(end = 8.dp)
                        .background(if (tab == name) WordBlue else Color(0xFFE9ECF1), RoundedCornerShape(20.dp))
                        .clickable { tab = name }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (tab == name) Color.White else Color(0xFF344054))
                }
            }
        }

        if (tab == "Recent") {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("All", "Pinned", "Opened", "Saved", "Exported").forEach { f ->
                    Box(
                        Modifier
                            .padding(end = 8.dp, bottom = 6.dp)
                            .background(if (filter == f) Color(0xFFDCE8FA) else Color.White, RoundedCornerShape(16.dp))
                            .border(1.dp, if (filter == f) WordBlue else Color(0xFFD5D9E0), RoundedCornerShape(16.dp))
                            .clickable { filter = f }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) { Text(f, fontSize = 13.sp, color = if (filter == f) WordBlue else Color(0xFF344054)) }
                }
                TextButton(onClick = onClearRecent) { Text("Clear", fontSize = 13.sp) }
            }
            if (shownDocs.isEmpty()) {
                EmptyState(if (docs.isEmpty()) "No documents yet" else "Nothing here", if (docs.isEmpty()) "Pick a template above or open a file. It will show up here." else "Try another filter or search.")
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                    val pinned = if (filter == "All" || filter == "Pinned") shownDocs.filter { it.pinned } else emptyList()
                    if (pinned.isNotEmpty()) {
                        item(key = "h_pinned") { SectionHeader("Pinned") }
                        pinned.forEach { doc -> item(key = "p_" + doc.uri) { RecentRow(doc, thumb(doc.uri), onOpenRecent, onRemove, onTogglePin) } }
                    }
                    var last = ""
                    shownDocs.filter { it !in pinned }.forEach { doc ->
                        val b = bucket(doc.openedAt)
                        if (b != last) {
                            last = b
                            item(key = "h_$b") { SectionHeader(b) }
                        }
                        item(key = doc.uri) { RecentRow(doc, thumb(doc.uri), onOpenRecent, onRemove, onTogglePin) }
                    }
                }
            }
        } else {
            if (events.isEmpty()) {
                EmptyState("No activity yet", "What you create, edit and export will be listed here.")
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                    var last = ""
                    events.forEachIndexed { i, e ->
                        val b = bucket(e.time)
                        if (b != last) {
                            last = b
                            item(key = "eh_$b") { SectionHeader(b) }
                        }
                        item(key = "e_$i") {
                            Row(
                                Modifier.fillMaxWidth().clickable(enabled = e.uri.isNotBlank()) {
                                    docs.firstOrNull { it.uri == e.uri }?.let(onOpenRecent)
                                }.padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(38.dp).background(Color(0xFFDCE8FA), CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(actionIcon(e.action), null, Modifier.size(20.dp), tint = WordBlue)
                                }
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text("${e.action} ${e.name}", fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(timeText(e.time), fontSize = 12.sp, color = Color(0xFF667085))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A highlighted card with a message and two buttons (recovery, crash report). */
@Composable
private fun Notice(title: String, body: String, primary: String, onPrimary: () -> Unit, secondary: String, onSecondary: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
            .background(Color(0xFFFFF4D6), RoundedCornerShape(16.dp)).padding(14.dp)
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF5B4300))
        Text(body, Modifier.padding(top = 2.dp), fontSize = 12.sp, color = Color(0xFF7A5C00))
        Row(Modifier.padding(top = 6.dp)) {
            TextButton(onClick = onPrimary) { Text(primary, fontWeight = FontWeight.SemiBold) }
            TextButton(onClick = onSecondary) { Text(secondary) }
        }
    }
}

@Composable
private fun TemplateTile(kind: String, label: String, onClick: () -> Unit) {
    Column(Modifier.width(96.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(width = 84.dp, height = 112.dp).background(Color.White, RoundedCornerShape(6.dp))
                .border(1.dp, Color(0xFFD5D9E0), RoundedCornerShape(6.dp)).padding(8.dp)
        ) {
            Canvas(Modifier.fillMaxSize()) { paperLines(kind) }
        }
        Text(label, Modifier.padding(top = 6.dp), fontSize = 12.sp)
    }
}

/** A tiny sketch of each template's layout. */
private fun DrawScope.paperLines(kind: String) {
    val line = Color(0xFFD0D5DD)
    fun bar(y: Float, w: Float, x: Float = 0f, h: Float = 3.5f, c: Color = line) {
        drawRect(c, Offset(size.width * x, size.height * y), Size(size.width * w, h))
    }
    when (kind) {
        "letter" -> {
            bar(0.02f, 0.4f, x = 0.6f); bar(0.08f, 0.3f, x = 0.7f)
            bar(0.22f, 0.35f); bar(0.28f, 0.45f)
            bar(0.42f, 0.2f)
            for (i in 0 until 5) bar(0.52f + i * 0.07f, if (i == 4) 0.5f else 1f)
            bar(0.9f, 0.3f)
        }
        "resume" -> {
            bar(0.02f, 0.6f, h = 7f, c = WordBlue); bar(0.12f, 0.4f)
            for (s in 0 until 3) {
                bar(0.26f + s * 0.24f, 0.35f, c = WordBlue)
                bar(0.33f + s * 0.24f, 1f); bar(0.39f + s * 0.24f, 0.8f)
            }
        }
        "report" -> {
            bar(0.04f, 0.8f, h = 8f, c = Color(0xFF0F4761)); bar(0.16f, 0.5f)
            bar(0.32f, 0.4f, c = Color(0xFF0F4761))
            for (i in 0 until 3) bar(0.4f + i * 0.07f, if (i == 2) 0.6f else 1f)
            bar(0.66f, 0.4f, c = Color(0xFF0F4761))
            for (i in 0 until 3) bar(0.74f + i * 0.07f, if (i == 2) 0.5f else 1f)
        }
        "sample" -> {
            bar(0.04f, 0.7f, h = 7f, c = WordBlue)
            for (i in 0 until 3) bar(0.2f + i * 0.07f, 1f)
            drawRect(Color(0xFFDCE8FA), Offset(0f, size.height * 0.5f), Size(size.width, size.height * 0.22f))
            for (i in 0 until 3) bar(0.78f + i * 0.07f, if (i == 2) 0.4f else 1f)
        }
        else -> {}
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, icon: ImageVector, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(104.dp)
            .background(if (primary) WordBlue else Color.White, RoundedCornerShape(18.dp))
            .border(if (primary) 0.dp else 1.dp, Color(0xFFE1E4EA), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(icon, null, Modifier.size(28.dp), tint = if (primary) Color.White else WordBlue)
        Column {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (primary) Color.White else Color(0xFF1B1B1B))
            Text(subtitle, fontSize = 12.sp, color = if (primary) Color(0xCCFFFFFF) else Color(0xFF667085))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF667085))
}

@Composable
private fun RecentRow(
    doc: RecentDoc, thumb: Bitmap?, onOpen: (RecentDoc) -> Unit, onRemove: (RecentDoc) -> Unit, onTogglePin: (RecentDoc) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val ext = extOf(doc.name)
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(doc) }.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(width = 52.dp, height = 68.dp).background(Color.White, RoundedCornerShape(6.dp))
                .border(1.dp, Color(0xFFD5D9E0), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (thumb != null) Image(thumb.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxSize().background(badgeColor(ext).copy(alpha = 0.10f), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                Text(ext.uppercase().ifBlank { "FILE" }.take(4), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = badgeColor(ext))
            }
            Box(
                Modifier.align(Alignment.BottomStart).padding(3.dp)
                    .background(badgeColor(ext), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp)
            ) { Text(ext.uppercase().ifBlank { "FILE" }.take(4), fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold) }
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(doc.name, Modifier.weight(1f, fill = false), fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (doc.pinned) Icon(Icons.Default.PushPin, "Pinned", Modifier.padding(start = 6.dp).size(15.dp), tint = WordBlue)
            }
            Text("${doc.action} · ${timeText(doc.openedAt)}${sizeText(doc.sizeBytes)}",
                fontSize = 12.sp, color = Color(0xFF667085), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Open") }, onClick = { menu = false; onOpen(doc) })
                DropdownMenuItem(text = { Text(if (doc.pinned) "Unpin" else "Pin to top") }, onClick = { menu = false; onTogglePin(doc) })
                DropdownMenuItem(text = { Text("Remove from list") }, onClick = { menu = false; onRemove(doc) })
            }
        }
    }
    HorizontalDivider(Modifier.padding(start = 86.dp), color = Color(0xFFECEEF2))
}

@Composable
private fun EmptyState(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Description, null, Modifier.size(56.dp), tint = Color(0xFFC3C9D4))
        Text(title, Modifier.padding(top = 12.dp), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(body, Modifier.padding(top = 4.dp), fontSize = 13.sp, color = Color(0xFF667085), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
