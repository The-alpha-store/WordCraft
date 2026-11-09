package com.wordcraft.android

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WordCraftApp() }
    }
}

private enum class RibbonTab { HOME, INSERT, LAYOUT, REVIEW, VIEW }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordCraftApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("Untitled document") }
    var content by remember { mutableStateOf(runCatching { NativeBridge.nativeGetText() }.getOrDefault("")) }
    var status by remember { mutableStateOf(runCatching { NativeBridge.engineStatus() }.getOrDefault("Rust engine unavailable")) }
    var pages by remember { mutableStateOf(runCatching { NativeBridge.nativePageCount().coerceAtLeast(1) }.getOrDefault(1)) }
    var selectedTab by remember { mutableStateOf(RibbonTab.HOME) }
    var isDirty by remember { mutableStateOf(false) }
    var bodyTextDirty by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    suspend fun commitBodyTextIfNeeded() {
        if (bodyTextDirty) {
            withContext(Dispatchers.IO) { NativeBridge.nativeSetText(content) }
            bodyTextDirty = false
        }
    }

    fun runCommand(id: String, params: String = "{}") {
        scope.launch {
            commitBodyTextIfNeeded()
            val rawResult = withContext(Dispatchers.IO) { NativeBridge.nativeRunCommand(id, params) }
            status = runCatching {
                val response = org.json.JSONObject(rawResult)
                if (response.optBoolean("ok")) response.opt("result")?.toString() ?: "Command completed"
                else "Command failed: ${response.optString("error", "Unknown engine error")}"
            }.getOrDefault(rawResult)
            if (id == "file.new") {
                content = withContext(Dispatchers.IO) { NativeBridge.nativeGetText() }
                bodyTextDirty = false
                title = "Untitled document"
                isDirty = false
            } else {
                isDirty = true
            }
            pages = withContext(Dispatchers.IO) { NativeBridge.nativePageCount().coerceAtLeast(1) }
        }
    }

    fun applyAll(command: String) {
        scope.launch {
            commitBodyTextIfNeeded()
            status = withContext(Dispatchers.IO) { NativeBridge.nativeFormatAll(command) }
            pages = withContext(Dispatchers.IO) { NativeBridge.nativePageCount().coerceAtLeast(1) }
            isDirty = true
        }
    }

    suspend fun saveTo(uri: Uri, extension: String) {
        commitBodyTextIfNeeded()
        val fileName = "${title.substringBeforeLast('.', title)}.$extension"
        val bytes = withContext(Dispatchers.IO) { NativeBridge.nativeSaveDocument(fileName) }
            ?: error("WordCraft could not export .$extension")
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("Could not open the selected destination")
        }
        pages = withContext(Dispatchers.IO) { NativeBridge.nativePageCount().coerceAtLeast(1) }
        isDirty = false
        status = "Saved $fileName with WordCraft 0.16.0"
    }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching {
                // Opening another file intentionally abandons any unsaved plain-text buffer.
                bodyTextDirty = false
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Could not read the selected file")
                }
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    ?: (uri.lastPathSegment ?: "document.docx")
                status = withContext(Dispatchers.IO) { NativeBridge.nativeOpenDocument(name, bytes) }
                content = withContext(Dispatchers.IO) { NativeBridge.nativeGetText() }
                bodyTextDirty = false
                pages = withContext(Dispatchers.IO) { NativeBridge.nativePageCount().coerceAtLeast(1) }
                title = name
                isDirty = false
            }.onFailure { status = "Open failed: ${it.message}" }
        }
    }

    val saveDocx = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ) { uri: Uri? -> if (uri != null) scope.launch {
        runCatching { saveTo(uri, "docx") }.onFailure { status = "DOCX save failed: ${it.message}" }
    } }

    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching { saveTo(uri, "pdf") }.onFailure { status = "PDF export failed: ${it.message}" }
        }
    }

    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching {
                commitBodyTextIfNeeded()
                val suggestedName = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
                    ?.takeIf { it.contains('.') } ?: "${title.substringBeforeLast('.', title)}.docx"
                val bytes = withContext(Dispatchers.IO) { NativeBridge.nativeSaveDocument(suggestedName) }
                    ?: error("WordCraft does not support exporting this file extension")
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("Could not open the selected destination")
                }
                title = suggestedName
                isDirty = false
                status = "Saved $suggestedName using the original WordCraft engine"
            }.onFailure { status = "Save As failed: ${it.message}" }
        }
    }

    // The first mobile adapter uses the engine's whole-body replacement command. Debouncing
    // avoids invoking the Rust layout/edit pipeline for every single IME keystroke.
    LaunchedEffect(content, bodyTextDirty) {
        if (bodyTextDirty) {
            delay(500)
            runCatching {
                val message = withContext(Dispatchers.IO) { NativeBridge.nativeSetText(content) }
                bodyTextDirty = false
                pages = withContext(Dispatchers.IO) { NativeBridge.nativePageCount().coerceAtLeast(1) }
                status = message
            }.onFailure { status = "Edit sync failed: ${it.message}" }
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("WordCraft", fontWeight = FontWeight.Bold)
                            Text(if (isDirty) "$title • edited" else title, fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    },
                    actions = {
                        IconButton(onClick = { scope.launch {
                            commitBodyTextIfNeeded()
                            status = withContext(Dispatchers.IO) { NativeBridge.nativeUndo() }
                            content = withContext(Dispatchers.IO) { NativeBridge.nativeGetText() }
                            bodyTextDirty = false
                            isDirty = false
                            pages = withContext(Dispatchers.IO) { NativeBridge.nativePageCount().coerceAtLeast(1) }
                        } }) { Icon(Icons.Default.Undo, contentDescription = "Undo") }
                        IconButton(onClick = { scope.launch {
                            commitBodyTextIfNeeded()
                            status = withContext(Dispatchers.IO) { NativeBridge.nativeRedo() }
                            content = withContext(Dispatchers.IO) { NativeBridge.nativeGetText() }
                            bodyTextDirty = false
                            isDirty = false
                            pages = withContext(Dispatchers.IO) { NativeBridge.nativePageCount().coerceAtLeast(1) }
                        } }) { Icon(Icons.Default.Redo, contentDescription = "Redo") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).background(Color(0xFFE9EDF3))) {
                Row(
                    Modifier.fillMaxWidth().background(Color.White).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    RibbonTab.values().forEach { tab ->
                        TextButton(onClick = { selectedTab = tab }) {
                            Text(tab.name.lowercase().replaceFirstChar { it.uppercase() },
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                RibbonActions(
                    tab = selectedTab,
                    onCommand = ::runCommand,
                    onFormatAll = ::applyAll,
                    onOpen = { openDocument.launch(arrayOf("*/*")) },
                    onSaveDocx = { saveDocx.launch("${title.substringBeforeLast('.', title)}.docx") },
                    onSavePdf = { savePdf.launch("${title.substringBeforeLast('.', title)}.pdf") },
                    onSaveAs = { saveAs.launch("${title.substringBeforeLast('.', title)}.docx") },
                    searchText = searchText,
                    onSearchText = { searchText = it },
                    onSearch = { runCommand("edit.find", "{\"text\":${org.json.JSONObject.quote(searchText)}}") }
                )
                Text(status, Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
                    fontSize = 11.sp, color = Color(0xFF475467), maxLines = 2)
                Card(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 5.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(Modifier.fillMaxSize().padding(14.dp)) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it; isDirty = true },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Document name") }, singleLine = true
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("DOCUMENT BODY", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it; bodyTextDirty = true; isDirty = true },
                            modifier = Modifier.fillMaxSize().weight(1f),
                            placeholder = { Text("Start writing your document…") },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            textStyle = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        val wordCount = content.trim().split(Regex("\\s+")).count { it.isNotBlank() }
                        Text("$wordCount words · ${content.length} characters", fontSize = 11.sp)
                        Text("$pages page(s) · WordCraft 0.16.0", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RibbonActions(
    tab: RibbonTab,
    onCommand: (String, String) -> Unit,
    onFormatAll: (String) -> Unit,
    onOpen: () -> Unit,
    onSaveDocx: () -> Unit,
    onSavePdf: () -> Unit,
    onSaveAs: () -> Unit,
    searchText: String,
    onSearchText: (String) -> Unit,
    onSearch: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(Color(0xFFF9FAFB)).padding(horizontal = 8.dp, vertical = 6.dp)) {
        when (tab) {
            RibbonTab.HOME -> {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onFormatAll("format.bold") }) { Icon(Icons.Default.FormatBold, "Bold all") }
                    IconButton(onClick = { onFormatAll("format.italic") }) { Icon(Icons.Default.FormatItalic, "Italic all") }
                    IconButton(onClick = { onFormatAll("format.underline") }) { Icon(Icons.Default.FormatUnderlined, "Underline all") }
                    RibbonButton("Strike") { onFormatAll("format.strikethrough") }
                    RibbonButton("Left") { onCommand("para.alignLeft", "{}") }
                    RibbonButton("Center") { onCommand("para.alignCenter", "{}") }
                    RibbonButton("Right") { onCommand("para.alignRight", "{}") }
                    RibbonButton("Bullets") { onCommand("para.bullets", "{}") }
                    RibbonButton("Numbering") { onCommand("para.numbering", "{}") }
                    RibbonButton("Open") { onOpen() }
                    RibbonButton("Save DOCX") { onSaveDocx() }
                    RibbonButton("Save As") { onSaveAs() }
                    RibbonButton("PDF") { onSavePdf() }
                }
            }
            RibbonTab.INSERT -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                RibbonButton("New document") { onCommand("file.new", "{}") }
                RibbonButton("Page break") { onCommand("insert.pageBreak", "{}") }
                RibbonButton("3 × 3 table") { onCommand("insert.table", "{\"rows\":3,\"cols\":3}") }
                RibbonButton("New comment") { onCommand("review.newComment", "{\"text\":\"Comment\"}") }
                RibbonButton("Page number") { onCommand("insert.pageNumber", "{}") }
                RibbonButton("Header") { onCommand("insert.header", "{}") }
                RibbonButton("Footer") { onCommand("insert.footer", "{}") }
            }
            RibbonTab.LAYOUT -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                RibbonButton("Margins") { onCommand("layout.margins", "{\"preset\":\"normal\"}") }
                RibbonButton("Portrait") { onCommand("layout.orientation", "{\"value\":\"portrait\"}") }
                RibbonButton("A4 paper") { onCommand("layout.size", "{\"name\":\"A4\"}") }
                RibbonButton("2 columns") { onCommand("layout.columns", "{\"count\":2}") }
                RibbonButton("Indent") { onCommand("para.indents", "{}") }
                RibbonButton("Spacing") { onCommand("para.spacing", "{}") }
            }
            RibbonTab.REVIEW -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                RibbonButton("Word count") { onCommand("review.wordCount", "{}") }
                RibbonButton("Spelling") { onCommand("review.spelling", "{}") }
                RibbonButton("Track changes") { onCommand("review.trackChanges", "{}") }
                RibbonButton("Comments") { onCommand("review.comments", "{}") }
                RibbonButton("Accept all") { onCommand("review.acceptAll", "{}") }
                RibbonButton("Reject all") { onCommand("review.rejectAll", "{}") }
            }
            RibbonTab.VIEW -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                RibbonButton("Print layout") { onCommand("view.printLayout", "{}") }
                RibbonButton("Read mode") { onCommand("view.readMode", "{}") }
                RibbonButton("Web layout") { onCommand("view.webLayout", "{}") }
                RibbonButton("Zoom +") { onCommand("view.zoomIn", "{}") }
                RibbonButton("Zoom −") { onCommand("view.zoomOut", "{}") }
                RibbonButton("100%") { onCommand("view.zoom100", "{}") }
            }
        }
        if (tab == RibbonTab.HOME) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = searchText, onValueChange = onSearchText,
                    modifier = Modifier.weight(1f), singleLine = true, label = { Text("Find text") })
                Button(onClick = onSearch, enabled = searchText.isNotBlank()) { Text("Find") }
            }
        }
    }
}

@Composable
private fun RibbonButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text(label, fontSize = 12.sp, maxLines = 1) }
}
