package com.wordcraft.android

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WordCraftApp() }
    }
}

private enum class RibbonTab { HOME, INSERT, TABLE, LAYOUT, REVIEW, VIEW }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordCraftApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val state = remember { EditorState() }
    var title by remember { mutableStateOf("Untitled document") }
    var selectedTab by remember { mutableStateOf(RibbonTab.HOME) }

    fun cmd(id: String, params: String = "{}", edits: Boolean = true) {
        scope.launch { state.command(id, params, edits) }
    }

    suspend fun readBytes(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not read the selected file")
    }

    suspend fun saveTo(uri: Uri, fileName: String) {
        val bytes = withContext(Dispatchers.IO) { NativeBridge.nativeSaveDocument(fileName) }
            ?: error("WordCraft could not export $fileName")
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Could not open the destination")
        }
        state.dirty = false
        state.status = "Saved $fileName"
    }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching {
                val bytes = readBytes(uri)
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: (uri.lastPathSegment ?: "document.docx")
                state.status = withContext(Dispatchers.IO) { NativeBridge.nativeOpenDocument(name, bytes) }
                title = name
                state.dirty = false
                state.refresh()
            }.onFailure { state.status = "Open failed: ${it.message}" }
        }
    }

    val saveDocx = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching { saveTo(uri, "${title.substringBeforeLast('.', title)}.docx") }
                .onFailure { state.status = "DOCX save failed: ${it.message}" }
        }
    }

    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching { saveTo(uri, "${title.substringBeforeLast('.', title)}.pdf") }
                .onFailure { state.status = "PDF export failed: ${it.message}" }
        }
    }

    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching {
                val data = Base64.encodeToString(readBytes(uri), Base64.NO_WRAP)
                // Width in points; the engine scales the height and keeps the picture inline.
                val params = JSONObject().put("data", data).put("width", 300).toString()
                state.command("insert.picture", params)
            }.onFailure { state.status = "Picture insert failed: ${it.message}" }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("WordCraft", fontWeight = FontWeight.Bold)
                        Text(if (state.dirty) "$title • edited" else title, fontSize = 11.sp, maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { cmd("edit.undo") }) { Icon(Icons.Default.Undo, "Undo") }
                    IconButton(onClick = { cmd("edit.redo") }) { Icon(Icons.Default.Redo, "Redo") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().background(Color.White).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RibbonTab.values().forEach { tab ->
                    TextButton(onClick = { selectedTab = tab }) {
                        Text(
                            tab.name.lowercase().replaceFirstChar { it.uppercase() },
                            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().background(Color(0xFFF9FAFB)).horizontalScroll(rememberScrollState()).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (selectedTab) {
                    RibbonTab.HOME -> {
                        IconButton(onClick = { cmd("format.bold") }) { Icon(Icons.Default.FormatBold, "Bold") }
                        IconButton(onClick = { cmd("format.italic") }) { Icon(Icons.Default.FormatItalic, "Italic") }
                        IconButton(onClick = { cmd("format.underline") }) { Icon(Icons.Default.FormatUnderlined, "Underline") }
                        Btn("A+") { cmd("format.growFont") }
                        Btn("A−") { cmd("format.shrinkFont") }
                        Btn("Left") { cmd("para.alignLeft") }
                        Btn("Center") { cmd("para.alignCenter") }
                        Btn("Right") { cmd("para.alignRight") }
                        Btn("Justify") { cmd("para.justify") }
                        Btn("Bullets") { cmd("para.bullets") }
                        Btn("Numbers") { cmd("para.numbering") }
                        Btn("Cut") {
                            scope.launch {
                                clipboard.setText(AnnotatedString(state.selectedText()))
                                state.command("edit.cut")
                            }
                        }
                        Btn("Copy") {
                            scope.launch {
                                clipboard.setText(AnnotatedString(state.selectedText()))
                                state.command("edit.copy", edits = false)
                            }
                        }
                        Btn("Paste") {
                            val text = clipboard.getText()?.text.orEmpty()
                            if (text.isNotEmpty()) scope.launch { state.typeText(text) }
                        }
                        Btn("Select all") { cmd("select.all", edits = false) }
                    }
                    RibbonTab.INSERT -> {
                        Btn("New") { cmd("file.new", edits = false); title = "Untitled document"; state.dirty = false }
                        Btn("Open") { openDocument.launch(arrayOf("*/*")) }
                        Btn("Save DOCX") { saveDocx.launch("${title.substringBeforeLast('.', title)}.docx") }
                        Btn("PDF") { savePdf.launch("${title.substringBeforeLast('.', title)}.pdf") }
                        Btn("Picture") { pickPicture.launch("image/*") }
                        Btn("Table 3×3") { cmd("insert.table", "{\"rows\":3,\"cols\":3}") }
                        Btn("Page break") { cmd("insert.pageBreak") }
                        Btn("Page number") { cmd("insert.pageNumber") }
                    }
                    RibbonTab.TABLE -> {
                        Btn("Row above") { cmd("table.insertRowAbove") }
                        Btn("Row below") { cmd("table.insertRowBelow") }
                        Btn("Column left") { cmd("table.insertColumnLeft") }
                        Btn("Column right") { cmd("table.insertColumnRight") }
                        Btn("Delete row") { cmd("table.deleteRow") }
                        Btn("Delete column") { cmd("table.deleteColumn") }
                        Btn("Delete table") { cmd("table.deleteTable") }
                        Btn("Select row") { cmd("table.selectRow", edits = false) }
                        Btn("Select column") { cmd("table.selectColumn", edits = false) }
                        Btn("Select table") { cmd("table.selectTable", edits = false) }
                        Btn("Merge") { cmd("table.merge") }
                    }
                    RibbonTab.LAYOUT -> {
                        Btn("Portrait") { cmd("layout.orientation", "{\"value\":\"portrait\"}") }
                        Btn("Landscape") { cmd("layout.orientation", "{\"value\":\"landscape\"}") }
                        Btn("A4") { cmd("layout.size", "{\"name\":\"A4\"}") }
                        Btn("2 columns") { cmd("layout.columns", "{\"count\":2}") }
                        Btn("1 column") { cmd("layout.columns", "{\"count\":1}") }
                    }
                    RibbonTab.REVIEW -> {
                        Btn("Track changes") { cmd("review.trackChanges") }
                        Btn("Accept all") { cmd("review.acceptAll") }
                        Btn("Reject all") { cmd("review.rejectAll") }
                    }
                    RibbonTab.VIEW -> {
                        Btn("Zoom +") { state.zoom = (state.zoom * 1.2f).coerceAtMost(2.5f) }
                        Btn("Zoom −") { state.zoom = (state.zoom / 1.2f).coerceAtLeast(0.6f) }
                        Btn("Fit") { state.zoom = 1f }
                        Btn("Marks ¶") { cmd("view.marks", edits = false) }
                    }
                }
            }
            if (state.status.isNotBlank()) {
                Text(state.status, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    fontSize = 11.sp, color = Color(0xFF475467), maxLines = 2)
            }
            DocumentCanvas(state, Modifier.weight(1f).fillMaxWidth())
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${state.pages.size.coerceAtLeast(1)} page(s)", fontSize = 11.sp)
                    Text("${(state.zoom * 100).toInt()}% · WordCraft 0.16.0", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun Btn(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text(label, fontSize = 12.sp, maxLines = 1) }
}
