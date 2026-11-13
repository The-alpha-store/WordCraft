package com.wordcraft.android

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.runtime.DisposableEffect
import java.io.File
import androidx.core.content.FileProvider
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.TextToSpeech
import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(applicationContext)
        // "Open with WordCraft": remember the file, but only on a fresh start (not after rotation).
        incomingUri = if (savedInstanceState == null && intent?.action == Intent.ACTION_VIEW) intent?.data else null
        setContent { WordCraftApp() }
    }
}

/** Open-document picker that asks for a lasting read and write grant, so Save can write back. */
private class OpenDocumentRW : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
}

/** A document another app asked us to open. */
private var incomingUri: Uri? = null

/** One editor state for the whole process, so rotating the phone never loses the open document. */
private val sharedEditorState = EditorState()

/** False in a freshly started process (after the system killed the app), true afterwards. */
private object ProcessFlag { var alive = false }

/** A document that needs its password before it can open. */
private class PendingOpen(val uri: Uri, val name: String, val bytes: ByteArray, val wrong: Boolean)

private val CONTEXTUAL_TABS = setOf(
    "Table Design", "Table Layout", "Picture Format", "Shape Format", "Chart Design",
    "SmartArt Design", "Equation", "Header & Footer", "Outlining"
)

private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

@Composable
private fun WordCraftApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val density = LocalDensity.current
    val state = sharedEditorState
    val recovery = remember { RecoveryStore(context) }
    val recents = remember { RecentStore(context) }
    val tabs = remember { RibbonModel.load() }
    val allCommands = remember { RibbonModel.loadAll() }

    // --- document session -------------------------------------------------------------
    var screen by rememberSaveable { mutableStateOf("home") }
    var title by rememberSaveable { mutableStateOf("Document1") }
    var currentUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var docCounter by rememberSaveable { mutableStateOf(1) }
    var autoSave by rememberSaveable { mutableStateOf(false) }
    var crashReport by remember { mutableStateOf(CrashLog.read(context)) }
    var recoveryTick by remember { mutableIntStateOf(0) }
    var recentTick by remember { mutableIntStateOf(0) }
    var goHomeAfterSave by remember { mutableStateOf(false) }

    // --- chrome state -----------------------------------------------------------------
    var selectedTab by remember { mutableStateOf("Home") }
    var ribbonOpen by remember { mutableStateOf(true) }
    var focusMode by remember { mutableStateOf(false) }
    var showNav by remember { mutableStateOf(false) }
    var showStyles by remember { mutableStateOf(false) }
    var showFormat by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var findReplace by remember { mutableStateOf(false) }

    // --- sheets -----------------------------------------------------------------------
    var pendingParams by remember { mutableStateOf<RibbonItem?>(null) }
    var showCommands by remember { mutableStateOf(false) }
    var showFont by remember { mutableStateOf(false) }
    var showSize by remember { mutableStateOf(false) }
    var showTable by remember { mutableStateOf(false) }
    var showSaveChanges by remember { mutableStateOf(false) }
    var showTableFormat by remember { mutableStateOf(false) }
    var showBorders by remember { mutableStateOf(false) }
    var showReview by remember { mutableStateOf(false) }
    var reviewTab by remember { mutableStateOf("Changes") }
    var showThemes by remember { mutableStateOf(false) }
    var pendingOpen by remember { mutableStateOf<PendingOpen?>(null) }
    var reading by remember { mutableStateOf(false) }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun cmd(id: String, params: String = "{}", edits: Boolean = true) {
        scope.launch { state.command(id, params, edits) }
    }

    fun baseName() = title.substringBeforeLast('.', title)

    LaunchedEffect(Unit) {
        if (!ProcessFlag.alive) {
            ProcessFlag.alive = true
            screen = "home"
        }
    }

    // Register the bundled Liberation fonts (Times New Roman / Arial / Courier New metrics) once.
    LaunchedEffect(Unit) {
        FontSetup.registerBundled(context)
        state.refresh()
    }

    // Short messages fade on their own.
    LaunchedEffect(state.status) {
        if (state.status.isNotBlank()) {
            delay(4500)
            state.status = ""
        }
    }

    // --- file helpers -----------------------------------------------------------------
    fun persist(uri: Uri) {
        val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val ok = runCatching { context.contentResolver.takePersistableUriPermission(uri, rw) }.isSuccess
        if (!ok) runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    fun queryInfo(uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment ?: "document.docx"
        var size = 0L
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    size = c.getLong(1)
                }
            }
        }
        return name to size
    }

    suspend fun updateThumb(uri: String) {
        state.renderPage(0, 0.32f)?.let { recents.saveThumb(uri, it) }
    }

    suspend fun completeOpen(uri: Uri, name: String, bytes: ByteArray, result: String) {
        if (result == "PASSWORD_REQUIRED" || result == "WRONG_PASSWORD") {
            pendingOpen = PendingOpen(uri, name, bytes, result == "WRONG_PASSWORD")
            return
        }
        pendingOpen = null
        title = name
        currentUri = uri
        state.dirty = false
        state.refresh()
        state.scrollTo(0)
        screen = "editor"
        if (result.contains("\"ok\":false") || result.startsWith("Could not")) state.status = result.take(160)
        recents.touch(uri.toString(), name, bytes.size.toLong(), "Opened")
        recentTick++
        updateThumb(uri.toString())
        recentTick++
    }

    suspend fun loadDocument(uri: Uri) {
        runCatching {
            persist(uri)
            val bytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not read the file")
            }
            val (name, _) = queryInfo(uri)
            val result = withContext(Dispatchers.IO) { NativeBridge.nativeOpenDocument(name, bytes) }
            completeOpen(uri, name, bytes, result)
        }.onFailure { toast("Can't open this file: ${it.message}") }
    }

    fun newDocument(template: String = "blank") {
        scope.launch {
            val params = JSONObject().put("template", template).toString()
            if (state.command("file.new", params, edits = false)) {
                recovery.clear()
                docCounter += 1
                title = "Document$docCounter"
                currentUri = null
                state.dirty = false
                state.scrollTo(0)
                state.refresh()
                screen = "editor"
                recents.log("Created", title, "")
                recentTick++
            }
        }
    }

    LaunchedEffect(Unit) {
        incomingUri?.let { uri ->
            incomingUri = null
            loadDocument(uri)
        }
    }

    /** Write the document to [uri] as DOCX. Returns true when the write worked. */
    suspend fun writeDocx(uri: Uri, name: String, auto: Boolean): Boolean {
        return runCatching {
            val bytes = state.save(name) ?: error("The engine could not export the document")
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("Could not open the file for writing")
            }
            state.dirty = false
            recovery.clear()
            recents.touch(uri.toString(), name, bytes.size.toLong(), if (auto) "Edited" else "Saved")
            recentTick++
            if (!auto) state.status = "Saved $name"
            updateThumb(uri.toString())
            true
        }.getOrElse {
            state.status = "Save failed: ${it.message}"
            false
        }
    }

    val openDocument = rememberLauncherForActivityResult(OpenDocumentRW()) { uri: Uri? ->
        if (uri != null) scope.launch { loadDocument(uri) }
    }

    val createDocx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(DOCX_MIME)) { uri: Uri? ->
        val returnHome = goHomeAfterSave
        goHomeAfterSave = false
        if (uri != null) scope.launch {
            persist(uri)
            val (name, _) = queryInfo(uri)
            if (writeDocx(uri, name, auto = false)) {
                currentUri = uri
                title = name
                if (returnHome) screen = "home"
            }
        }
    }

    val createPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching {
                val (name, _) = queryInfo(uri)
                val bytes = state.save(name.substringBeforeLast('.', name) + ".pdf") ?: error("The engine could not export a PDF")
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("Could not open the file for writing")
                }
                persist(uri)
                state.status = "Exported $name"
                recents.touch(uri.toString(), name, bytes.size.toLong(), "Exported")
                recentTick++
            }.onFailure { state.status = "PDF export failed: ${it.message}" }
        }
    }

    val createCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(DOCX_MIME)) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching {
                val (name, _) = queryInfo(uri)
                val bytes = state.save(name) ?: error("The engine could not export the document")
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("Could not open the file for writing")
                }
                persist(uri)
                state.status = "Saved a copy as $name"
                recents.touch(uri.toString(), name, bytes.size.toLong(), "Saved")
                recentTick++
            }.onFailure { state.status = "Save a copy failed: ${it.message}" }
        }
    }

    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not read the picture")
                }
                val data = Base64.encodeToString(bytes, Base64.NO_WRAP)
                // Width in points; the engine scales the height and keeps the picture inline.
                state.command("insert.picture", JSONObject().put("data", data).put("width", 300).toString())
            }.onFailure { state.status = "Picture insert failed: ${it.message}" }
        }
    }

    /** Save to the file this document came from, or ask where to put it. */
    fun saveDocument(thenHome: Boolean = false) {
        val uri = currentUri
        val isDocx = title.lowercase().endsWith(".docx")
        if (uri != null && isDocx) {
            scope.launch {
                val ok = writeDocx(uri, title, auto = false)
                if (ok && thenHome) screen = "home"
                if (!ok) { goHomeAfterSave = thenHome; createDocx.launch("${baseName()}.docx") }
            }
        } else {
            goHomeAfterSave = thenHome
            createDocx.launch("${baseName()}.docx")
        }
    }

    // AutoSave: write a couple of seconds after the last change, when there is a file to write to.
    LaunchedEffect(state.revision, autoSave, state.dirty, currentUri, screen) {
        val uri = currentUri
        if (screen == "editor" && autoSave && state.dirty && uri != null && title.lowercase().endsWith(".docx")) {
            delay(2500)
            writeDocx(uri, title, auto = true)
        }
    }

    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { if (utteranceId == "wcLast") reading = false }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { reading = false }
        })
        tts.value = engine
        onDispose { engine.stop(); engine.shutdown() }
    }

    fun stopReading() {
        tts.value?.stop()
        reading = false
    }

    fun readAloud() {
        val engine = tts.value ?: run { state.status = "Read aloud isn't available on this device."; return }
        scope.launch {
            val selected = state.selectedText()
            val text = if (selected.isNotBlank()) selected else withContext(Dispatchers.IO) { NativeBridge.nativeDocumentText() }
            if (text.isBlank()) { state.status = "Nothing to read yet."; return@launch }
            val chunks = text.chunked(3500)
            engine.stop()
            chunks.forEachIndexed { i, chunk ->
                val last = i == chunks.size - 1
                engine.speak(chunk, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, if (last) "wcLast" else "wc$i")
            }
            reading = true
            state.status = "Reading aloud… tap ⋮ then Stop reading"
        }
    }

    /** Export the document to a private cache file and open Android's share sheet. */
    fun shareExport(fileName: String, mime: String) {
        scope.launch {
            runCatching {
                val bytes = state.save(fileName) ?: error("The engine could not export the document")
                val dir = File(context.cacheDir, "share").apply { mkdirs() }
                val file = File(dir, fileName)
                withContext(Dispatchers.IO) { file.writeBytes(bytes) }
                val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Share"))
            }.onFailure { state.status = "Can't share: ${it.message}" }
        }
    }

    // Spelling and grammar squiggles refresh when typing pauses.
    LaunchedEffect(state.editCount, state.proofing, screen, title) {
        if (screen == "editor") {
            delay(900)
            state.refreshProof()
        }
    }

    // Open the review panel on Comments when a margin marker is tapped.
    LaunchedEffect(state.reviewRequest) {
        if (state.reviewRequest > 0) {
            reviewTab = "Comments"
            showReview = true
        }
    }

    // Keep a private copy of unsaved work so a crash or a killed app never loses typing.
    LaunchedEffect(state.revision, state.dirty, screen) {
        if (screen == "editor" && state.dirty) {
            delay(2000)
            state.save("recovery.docx")?.let { recovery.write(title, currentUri?.toString().orEmpty(), it) }
        }
    }

    fun goHome() {
        if (!state.dirty) recovery.clear()
        recoveryTick++
        findOpen = false
        showNav = false; showStyles = false; showFormat = false
        recentTick++
        screen = "home"
    }

    fun openFind(replace: Boolean) {
        findReplace = replace
        findOpen = true
    }

    // Platform-specific commands are handled here; everything else runs in the engine.
    fun invoke(item: RibbonItem) {
        when (item.id) {
            "file.open" -> openDocument.launch(arrayOf("*/*"))
            "file.save" -> saveDocument()
            "file.saveAs" -> { goHomeAfterSave = false; createDocx.launch("${baseName()}.docx") }
            "file.exportPdf", "file.printPdf", "file.print" -> createPdf.launch("${baseName()}.pdf")
            "insert.picture" -> pickPicture.launch("image/*")
            "insert.table" -> showTable = true
            "format.font" -> showFont = true
            "format.size" -> showSize = true
            "edit.find" -> openFind(false)
            "edit.replace", "edit.findReplace" -> openFind(true)
            "view.zoomIn" -> state.zoom = (state.zoom * 1.2f).coerceAtMost(2.5f)
            "view.zoomOut" -> state.zoom = (state.zoom / 1.2f).coerceAtLeast(0.6f)
            "view.pageWidth", "view.onePage", "view.multiplePages" -> state.zoom = 1f
            "view.navigationPane" -> showNav = !showNav
            "styles.pane" -> showStyles = !showStyles
            "file.new" -> newDocument()
            "file.share" -> shareExport(baseName() + ".docx", DOCX_MIME)
            "file.sharePdf" -> shareExport(baseName() + ".pdf", "application/pdf")
            "file.saveCopy" -> createCopy.launch(baseName() + " copy.docx")
            "review.readAloud", "app.read" -> if (reading) stopReading() else readAloud()
            "app.proofing" -> { state.proofing = !state.proofing; scope.launch { state.refreshProof() } }
            "app.autosave" -> {
                autoSave = !autoSave
                if (autoSave && currentUri == null) state.status = "AutoSave starts once the document has been saved to a file."
            }
            "app.focus" -> focusMode = !focusMode
            "app.review" -> { reviewTab = "Changes"; showReview = !showReview }
            "app.lookup" -> scope.launch {
                val q = state.selectedText().trim()
                if (q.isNotEmpty()) {
                    val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q.take(120))
                    runCatching { context.startActivity(search) }.onFailure { state.status = "No app can look this up." }
                }
            }
            "design.theme", "design.themes" -> showThemes = true
            "edit.copy", "edit.cut" -> scope.launch {
                clipboard.setText(AnnotatedString(state.selectedText()))
                state.command(item.id, edits = item.id == "edit.cut")
            }
            "edit.paste" -> {
                val text = clipboard.getText()?.text.orEmpty()
                if (text.isNotEmpty()) scope.launch { state.typeText(text) } else cmd(item.id)
            }
            else -> if (RibbonModel.needsInput(item.params)) pendingParams = item else cmd(item.id)
        }
    }

    /** Run a command by id with ready-made params (quick bar, panels, curated Home tab). */
    fun run(id: String, params: String) {
        if (params == "{}") invoke(RibbonItem("", "", id, id, "{}", "")) else cmd(id, params)
    }

    BackHandler(enabled = screen == "editor") {
        when {
            showFont || showSize || showTable || showCommands || showTableFormat || showBorders || showThemes || pendingOpen != null || pendingParams != null -> {
                showThemes = false; pendingOpen = null
                showFont = false; showSize = false; showTable = false; showCommands = false
                showTableFormat = false; showBorders = false; pendingParams = null
            }
            findOpen -> findOpen = false
            showNav || showStyles || showFormat -> { showNav = false; showStyles = false; showFormat = false }
            focusMode -> focusMode = false
            state.dirty -> showSaveChanges = true
            else -> goHome()
        }
    }

    // --- sheets -------------------------------------------------------------------------
    pendingParams?.let { item ->
        ParamSheet(item, onDismiss = { pendingParams = null }, onRun = { json ->
            pendingParams = null
            cmd(item.id, json)
        })
    }
    if (showCommands) {
        CommandSheet(allCommands, state.disabled, onDismiss = { showCommands = false }, onPick = { item ->
            showCommands = false
            val reason = state.disabled[item.id]
            if (reason != null) state.status = "${item.label}: $reason" else invoke(item)
        })
    }
    if (showFont) {
        FontSheet(state.fmt.font, onDismiss = { showFont = false }, onPick = { name ->
            showFont = false
            scope.launch {
                val ok = state.command("format.font", JSONObject().put("name", name).toString())
                if (ok && name == "Times New Roman") {
                    state.status = "Times New Roman is shown with Liberation Serif: same widths, so line and page breaks match Word."
                }
            }
        })
    }
    if (showSize) {
        SizeSheet(state.fmt.size, onDismiss = { showSize = false }, onPick = { size ->
            showSize = false
            cmd("format.size", JSONObject().put("size", size.toDouble()).toString())
        })
    }
    if (showTable) {
        TableGridSheet(onDismiss = { showTable = false }, onInsert = { rows, cols ->
            showTable = false
            cmd("insert.table", JSONObject().put("rows", rows).put("cols", cols).toString())
        })
    }
    pendingOpen?.let { p ->
        PasswordSheet(
            name = p.name, wrong = p.wrong,
            onSubmit = { pw ->
                scope.launch {
                    val result = withContext(Dispatchers.IO) { NativeBridge.nativeOpenDocumentWithPassword(p.name, p.bytes, pw) }
                    completeOpen(p.uri, p.name, p.bytes, result)
                }
            },
            onCancel = { pendingOpen = null }
        )
    }
    if (showThemes) ThemesSheet(onDismiss = { showThemes = false }, onPick = { name ->
        showThemes = false
        cmd("design.theme", JSONObject().put("name", name).toString())
    })
    if (showTableFormat) TableFormatSheet(onDismiss = { showTableFormat = false }, onRun = { id, p -> cmd(id, p) })
    if (showBorders) ParaBorderSheet(onDismiss = { showBorders = false }, onRun = { id, p -> cmd(id, p) })
    if (showSaveChanges) {
        SaveChangesSheet(
            name = title,
            onSave = { showSaveChanges = false; saveDocument(thenHome = true) },
            onDiscard = { showSaveChanges = false; state.dirty = false; recovery.clear(); goHome() },
            onCancel = { showSaveChanges = false }
        )
    }

    // --- screens ------------------------------------------------------------------------
    if (screen == "home") {
        val docs = remember(recentTick) { recents.docs() }
        val events = remember(recentTick) { recents.events() }
        val thumbs = remember(recentTick) { HashMap<String, android.graphics.Bitmap?>() }
        val recoverable = remember(recoveryTick, recentTick) { recovery.read() }
        HomeScreen(
            docs = docs,
            events = events,
            thumb = { uri -> thumbs.getOrPut(uri) { recents.loadThumb(uri) } },
            recoveryTitle = recoverable?.first,
            crashReport = crashReport,
            onRecover = {
                val r = recovery.read()
                if (r != null) scope.launch {
                    val name = if (r.first.lowercase().endsWith(".docx")) r.first else r.first + ".docx"
                    withContext(Dispatchers.IO) { NativeBridge.nativeOpenDocument(name, r.third) }
                    title = name
                    currentUri = r.second.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                    state.dirty = true
                    state.refresh()
                    state.scrollTo(0)
                    screen = "editor"
                }
            },
            onDiscardRecovery = { recovery.clear(); recoveryTick++ },
            onShareCrash = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "WordCraft crash report")
                    putExtra(Intent.EXTRA_TEXT, crashReport.orEmpty())
                }
                runCatching { context.startActivity(Intent.createChooser(send, "Share crash report")) }
                CrashLog.clear(context)
                crashReport = null
            },
            onDismissCrash = { CrashLog.clear(context); crashReport = null },
            onNewTemplate = { kind -> newDocument(kind) },
            onOpenFile = { openDocument.launch(arrayOf("*/*")) },
            onOpenRecent = { doc ->
                val ext = doc.name.substringAfterLast('.', "").lowercase()
                if (ext == "pdf" || ext == "html" || ext == "htm") {
                    // Exports open in whichever app handles them.
                    val view = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(doc.uri), if (ext == "pdf") "application/pdf" else "text/html")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(view) }.onFailure { toast("No app can open this file") }
                } else scope.launch { loadDocument(Uri.parse(doc.uri)) }
            },
            onRemove = { doc -> recents.remove(doc.uri); recentTick++ },
            onTogglePin = { doc -> recents.togglePin(doc.uri); recentTick++ },
            onClearRecent = { recents.clearUnpinned(); recentTick++ }
        )
        return
    }

    val visibleTabs = tabs.filter { t ->
        t.name !in CONTEXTUAL_TABS || t.groups.any { g -> g.items.any { state.disabled[it.id] == null } }
    }
    val activeTab = visibleTabs.firstOrNull { it.name == selectedTab } ?: visibleTabs.firstOrNull { it.name == "Home" } ?: visibleTabs.firstOrNull()
    val homeActions = remember {
        HomeActions(
            run = { id, params -> run(id, params) },
            pickFont = { showFont = true },
            pickSize = { showSize = true },
            openFind = { replace -> openFind(replace) },
            openBorders = { showBorders = true }
        )
    }

    LaunchedEffect(state.selObject != null) {
        if (state.selObject?.picture == true) { selectedTab = "Picture Format"; ribbonOpen = true }
        else if (selectedTab == "Picture Format") selectedTab = "Home"
    }

    LaunchedEffect(selectedTab) { if (selectedTab != "Draw") state.drawTool = null }

    // Hardware keyboard: Word's shortcuts and caret movement.
    fun handleKey(e: KeyEvent): Boolean {
        if (e.type != KeyEventType.KeyDown) return false
        val ctrl = e.isCtrlPressed || e.isMetaPressed
        val extend = "{\"extend\":${e.isShiftPressed}}"
        // While a text box (find, review, navigation) has focus, leave its own keys alone.
        val typingElsewhere = findOpen || showReview || showNav
        if (typingElsewhere && !(ctrl && (e.key == Key.F || e.key == Key.H || e.key == Key.S))) return false
        return if (ctrl) when (e.key) {
            Key.B -> { run("format.bold", "{}"); true }
            Key.I -> { run("format.italic", "{}"); true }
            Key.U -> { run("format.underline", "{}"); true }
            Key.Z -> { run(if (e.isShiftPressed) "edit.redo" else "edit.undo", "{}"); true }
            Key.Y -> { run("edit.redo", "{}"); true }
            Key.S -> { saveDocument(); true }
            Key.F -> { openFind(false); true }
            Key.H -> { openFind(true); true }
            Key.A -> { run("select.all", "{}"); true }
            Key.C -> { run("edit.copy", "{}"); true }
            Key.X -> { run("edit.cut", "{}"); true }
            Key.V -> { run("edit.paste", "{}"); true }
            Key.E -> { run("para.alignCenter", "{}"); true }
            Key.L -> { run("para.alignLeft", "{}"); true }
            Key.R -> { run("para.alignRight", "{}"); true }
            Key.J -> { run("para.justify", "{}"); true }
            Key.P -> { createPdf.launch(baseName() + ".pdf"); true }
            Key.N -> { newDocument(); true }
            Key.O -> { openDocument.launch(arrayOf("*/*")); true }
            Key.DirectionLeft -> { cmd("caret.wordLeft", extend, false); true }
            Key.DirectionRight -> { cmd("caret.wordRight", extend, false); true }
            Key.DirectionUp -> { cmd("caret.paraUp", extend, false); true }
            Key.DirectionDown -> { cmd("caret.paraDown", extend, false); true }
            Key.MoveHome -> { cmd("caret.docStart", extend, false); true }
            Key.MoveEnd -> { cmd("caret.docEnd", extend, false); true }
            Key.Backspace -> { cmd("text.deleteWordBack"); true }
            Key.Delete -> { cmd("text.deleteWordForward"); true }
            else -> false
        } else when (e.key) {
            Key.DirectionLeft -> { cmd("caret.left", extend, false); true }
            Key.DirectionRight -> { cmd("caret.right", extend, false); true }
            Key.DirectionUp -> { cmd("caret.up", extend, false); true }
            Key.DirectionDown -> { cmd("caret.down", extend, false); true }
            Key.MoveHome -> { cmd("caret.home", extend, false); true }
            Key.MoveEnd -> { cmd("caret.end", extend, false); true }
            Key.PageUp -> { cmd("caret.pageUp", extend, false); true }
            Key.PageDown -> { cmd("caret.pageDown", extend, false); true }
            Key.Delete -> { cmd("text.delete"); true }
            else -> false
        }
    }

    val menuItems = listOf(
        "New" to "file.new",
        "Open…" to "file.open",
        "Save as DOCX…" to "file.saveAs",
        "Save a copy…" to "file.saveCopy",
        "Export PDF…" to "file.exportPdf",
        "Share…" to "file.share",
        "Share as PDF…" to "file.sharePdf",
        "Review pane" to "app.review",
        (if (state.proofing) "Spell check: On" else "Spell check: Off") to "app.proofing",
        (if (state.toggles["view.ruler"] == true) "Ruler: On" else "Ruler: Off") to "view.ruler",
        (if (state.toggles["view.darkMode"] == true) "Dark page: On" else "Dark page: Off") to "view.darkMode",
        (if (reading) "Stop reading" else "Read aloud") to "app.read",
        (if (autoSave) "AutoSave: On" else "AutoSave: Off") to "app.autosave",
        (if (focusMode) "Exit focus mode" else "Focus mode") to "app.focus",
        "Encrypt with password…" to "file.encrypt",
        "Fit page to screen" to "view.pageWidth"
    )

    Column(
        Modifier.fillMaxSize().background(ChromeGrey).statusBarsPadding().navigationBarsPadding().imePadding()
            .onPreviewKeyEvent { handleKey(it) }
    ) {
        WordTitleBar(
            title = title,
            saved = !state.dirty,
            autoSave = autoSave,
            canUndo = state.disabled["edit.undo"] == null,
            canRedo = state.disabled["edit.redo"] == null,
            menuItems = menuItems,
            onHome = {
                if (state.dirty) showSaveChanges = true else goHome()
            },
            onSave = { saveDocument() },
            onUndo = { cmd("edit.undo") },
            onRedo = { cmd("edit.redo") },
            onSearch = { showCommands = true },
            onMenuItem = { id -> invoke(allCommands.firstOrNull { it.id == id } ?: RibbonItem("", "", id, id, "{}", "")) }
        )

        if (!focusMode) {
            RibbonTabBar(
                tabs = visibleTabs,
                selectedTab = activeTab?.name ?: "Home",
                expanded = ribbonOpen,
                onSelectTab = { name ->
                    // Tapping the open tab again collapses the ribbon; any tab while collapsed opens it.
                    if (ribbonOpen && name == (activeTab?.name ?: "")) ribbonOpen = false else { selectedTab = name; ribbonOpen = true }
                },
                onToggle = { ribbonOpen = !ribbonOpen }
            )
            AnimatedVisibility(visible = ribbonOpen) {
                when (activeTab?.name) {
                    "Home" -> Column(Modifier.fillMaxWidth().background(Color(0xFFFBFBFB))) {
                        HomeTabContent(state, homeActions)
                        HorizontalDivider(color = Color(0xFFE1E1E1))
                    }
                    "Draw" -> Column(Modifier.fillMaxWidth().background(Color(0xFFFBFBFB))) {
                        DrawTabContent(state)
                        HorizontalDivider(color = Color(0xFFE1E1E1))
                    }
                    else -> RibbonBody(state, activeTab, onInvoke = { invoke(it) })
                }
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val boundsW = with(density) { maxWidth.toPx() }
            val boundsH = with(density) { maxHeight.toPx() }
            // On tablets and unfolded foldables the Navigation pane docks beside the page.
            val docked = maxWidth >= 840.dp

            Row(Modifier.fillMaxSize()) {
                if (docked && showNav) {
                    Column(Modifier.width(320.dp).fillMaxHeight().background(Color.White).padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Navigation", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("Close", Modifier.clickable { showNav = false }.padding(8.dp), fontSize = 13.sp, color = WordBlue)
                        }
                        NavigationPanelContent(state)
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    DocumentCanvas(state, onCommand = { id, params -> run(id, params) }, modifier = Modifier.fillMaxSize())
                    // While a header or footer is being edited, one tap gets back to the page.
                    if (state.disabled.isNotEmpty() && state.disabled["insert.closeHeader"] == null) {
                        Box(
                            Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                                .background(WordBlue, RoundedCornerShape(20.dp))
                                .clickable { cmd("insert.closeHeader", "{}", false) }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) { Text("Close header & footer", color = Color.White, fontSize = 13.sp) }
                    }
                    StatusPill(state.status, Modifier.align(Alignment.BottomCenter))
                }
            }

            if (showNav && !docked) {
                FloatingPanel("Navigation", boundsW, boundsH, Offset(boundsW * 0.04f, 24f), onClose = { showNav = false }, bodyHeight = 380.dp) {
                    NavigationPanelContent(state)
                }
            }
            if (showStyles) {
                FloatingPanel("Styles", boundsW, boundsH, Offset(boundsW * 0.30f, 90f), onClose = { showStyles = false }, bodyHeight = 330.dp) {
                    StylesPanelContent { id, params -> run(id, params) }
                }
            }
            if (showFormat) {
                FloatingPanel("Format", boundsW, boundsH, Offset(boundsW * 0.20f, 160f), onClose = { showFormat = false }, bodyHeight = 300.dp) {
                    FormatPanelContent(onPickFont = { showFont = true }) { id, params -> run(id, params) }
                }
            }
            if (showReview) {
                FloatingPanel("Review", boundsW, boundsH, Offset(boundsW * 0.06f, 40f), onClose = { showReview = false }, bodyHeight = 400.dp) {
                    ReviewPanelContent(state, reviewTab) { id, params -> run(id, params) }
                }
            }
        }

        if (state.inTable && !focusMode) TableBar(onRun = { id, p -> run(id, p) }, onFormat = { showTableFormat = true })
        if (findOpen) FindBar(state, findReplace, onClose = { findOpen = false })
        if (!focusMode) {
            QuickBar(
                fmt = state.fmt,
                navOpen = showNav, stylesOpen = showStyles, formatOpen = showFormat,
                onToggleNav = { showNav = !showNav },
                onToggleStyles = { showStyles = !showStyles },
                onToggleFormat = { showFormat = !showFormat },
                onAction = { id, params -> run(id, params) }
            )
        }
        WordStatusBar(state, focusMode) { focusMode = !focusMode }
    }
}
