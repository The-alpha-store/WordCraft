package com.wordcraft.android

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    action.contains("PDF", ignoreCase = true) -> Icons.Default.PictureAsPdf
    action.contains("Created", ignoreCase = true) -> Icons.Default.Add
    action.contains("Edited", ignoreCase = true) -> Icons.Default.Edit
    action.contains("Saved", ignoreCase = true) -> Icons.Default.Save
    else -> Icons.Default.FolderOpen
}

/**
 * The start screen: a greeting, quick ways to begin, then everything recently done: your
 * documents with a first-page preview, and a timeline of what you created, edited and exported.
 */
@Composable
fun HomeScreen(
    docs: List<RecentDoc>,
    events: List<ActivityEvent>,
    thumb: (String) -> Bitmap?,
    onNew: () -> Unit,
    onOpenFile: () -> Unit,
    onOpenRecent: (RecentDoc) -> Unit,
    onRemove: (RecentDoc) -> Unit
) {
    var tab by remember { mutableStateOf("Recent") }
    var query by remember { mutableStateOf("") }
    val shownDocs = docs.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }

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

        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionCard("Blank document", "Start writing", Icons.Default.Add, primary = true, modifier = Modifier.weight(1f), onClick = onNew)
            ActionCard("Open", "Browse your files", Icons.Default.FolderOpen, primary = false, modifier = Modifier.weight(1f), onClick = onOpenFile)
        }

        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Search recent documents") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
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
            if (shownDocs.isEmpty()) {
                EmptyState(if (docs.isEmpty()) "No documents yet" else "No matches", if (docs.isEmpty()) "Create a blank document or open a file. It will show up here." else "Try a different name.")
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                    var last = ""
                    shownDocs.forEach { doc ->
                        val b = bucket(doc.openedAt)
                        if (b != last) {
                            last = b
                            item(key = "h_$b") { SectionHeader(b) }
                        }
                        item(key = doc.uri) { RecentRow(doc, thumb(doc.uri), onOpenRecent, onRemove) }
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
private fun RecentRow(doc: RecentDoc, thumb: Bitmap?, onOpen: (RecentDoc) -> Unit, onRemove: (RecentDoc) -> Unit) {
    var menu by remember { mutableStateOf(false) }
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
            else Icon(Icons.Default.Description, null, tint = Color(0xFFB0B7C3))
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(doc.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${doc.action} ${timeText(doc.openedAt).lowercase().replaceFirstChar { it.uppercase() }}${sizeText(doc.sizeBytes)}",
                fontSize = 12.sp, color = Color(0xFF667085), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Open") }, onClick = { menu = false; onOpen(doc) })
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
