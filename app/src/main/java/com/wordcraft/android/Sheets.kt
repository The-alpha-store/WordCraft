package com.wordcraft.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The one bottom sheet every picker in the app uses: rounded, keyboard-aware, easy to dismiss. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
            content = content
        )
    }
}

@Composable
fun SheetTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1B1B1B))
        if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 12.sp, color = Color(0xFF667085))
    }
}

/** A selectable pill. */
@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(end = 8.dp, bottom = 6.dp)
            .background(if (selected) Color(0xFFDCE8FA) else Color(0xFFF2F4F7), RoundedCornerShape(20.dp))
            .border(1.dp, if (selected) WordBlue else Color.Transparent, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(label, fontSize = 14.sp, color = if (selected) WordBlue else Color(0xFF242424))
    }
}

private val SWATCHES = listOf(
    "000000", "7F7F7F", "C00000", "ED7D31", "FFC000", "FFFF00", "548235", "00B0F0",
    "185ABD", "7030A0", "FF00FF", "FFFFFF"
)

/** Parameter form built from the engine's own description of the command. */
@Composable
fun ParamSheet(item: RibbonItem, onDismiss: () -> Unit, onRun: (String) -> Unit) {
    val fields = remember(item.id) { RibbonModel.fields(item.params) }
    val values = remember(item.id) { mutableStateMapOf<String, String>() }
    var raw by remember(item.id) { mutableStateOf("{}") }
    val canRun = fields.isEmpty() || fields.all { it.optional || !values[it.name].isNullOrBlank() }
    AppSheet(onDismiss) {
        SheetTitle(item.label, item.tab.takeIf { it.isNotBlank() })
        if (fields.isEmpty()) {
            Text(item.params, fontSize = 12.sp, color = Color(0xFF667085))
            OutlinedTextField(value = raw, onValueChange = { raw = it }, label = { Text("Options (JSON)") }, modifier = Modifier.fillMaxWidth())
        } else {
            fields.forEach { f -> FieldEditor(f, values) }
        }
        Button(
            onClick = { onRun(if (fields.isEmpty()) raw else buildParams(fields, values)) },
            enabled = canRun,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(50.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Apply", fontSize = 16.sp) }
    }
}

@Composable
private fun FieldEditor(f: ParamField, values: MutableMap<String, String>) {
    val options = RibbonModel.options(f.type)
    val label = f.name.replaceFirstChar { it.uppercase() } + if (f.optional) "" else " *"
    Column(Modifier.padding(bottom = 10.dp)) {
        when {
            options != null -> {
                Text(label, fontSize = 13.sp, color = Color(0xFF667085), modifier = Modifier.padding(bottom = 6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    options.forEach { opt ->
                        Pill(opt.replaceFirstChar { it.uppercase() }, values[f.name] == opt) {
                            values[f.name] = if (f.optional && values[f.name] == opt) "" else opt
                        }
                    }
                }
            }
            f.type.lowercase().contains("bool") -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), fontSize = 15.sp)
                    Switch(checked = values[f.name] == "true", onCheckedChange = { values[f.name] = it.toString() })
                }
            }
            f.name.lowercase().contains("colo") -> {
                Text(label, fontSize = 13.sp, color = Color(0xFF667085), modifier = Modifier.padding(bottom = 6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SWATCHES.forEach { hex ->
                        Box(
                            Modifier.size(36.dp)
                                .background(Color(("FF$hex").toLong(16)), CircleShape)
                                .border(if (values[f.name] == hex) 3.dp else 1.dp, if (values[f.name] == hex) WordBlue else Color(0xFFBBBBBB), CircleShape)
                                .clickable { values[f.name] = hex }
                        )
                    }
                }
                OutlinedTextField(
                    value = values[f.name].orEmpty(), onValueChange = { values[f.name] = it.removePrefix("#") },
                    label = { Text("Hex colour, e.g. 185ABD") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            else -> {
                val numeric = isNumeric(f.type)
                OutlinedTextField(
                    value = values[f.name].orEmpty(),
                    onValueChange = { values[f.name] = it },
                    label = { Text(label) },
                    supportingText = { Text(f.type, fontSize = 10.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** "Tell me what you want to do": search every engine command. */
@Composable
fun CommandSheet(
    all: List<RibbonItem>,
    disabled: Map<String, String>,
    onDismiss: () -> Unit,
    onPick: (RibbonItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val matches = remember(query, all) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) all.take(40)
        else all.filter { it.label.lowercase().contains(q) || it.id.lowercase().contains(q) || it.tab.lowercase().contains(q) }.take(60)
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AppSheet(onDismiss) {
        SheetTitle("Tell me what you want to do", "${all.size} commands")
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Try “margins”, “table”, “find”, “bold”") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
        )
        LazyColumn(Modifier.height(380.dp).padding(top = 6.dp)) {
            items(matches) { item ->
                val off = disabled[item.id] != null
                Row(
                    Modifier.fillMaxWidth().alpha(if (off) 0.4f else 1f).clickable { onPick(item) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = commandIcon(item.id)
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        if (icon != null) Icon(icon, null, Modifier.size(22.dp), tint = Color(0xFF424242))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(item.label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(item.tab.ifBlank { item.id }, fontSize = 11.sp, color = Color(0xFF667085), maxLines = 1)
                    }
                }
                HorizontalDivider(color = Color(0xFFF0F0F0))
            }
        }
    }
}

private val PINNED_FONTS = listOf("Times New Roman", "Arial", "Courier New", "Calibri", "Cambria", "Georgia", "Verdana", "Aptos")

private fun fontHint(name: String, installed: Set<String>): String? {
    if (name.lowercase() in installed) return null
    return when (name) {
        "Times New Roman" -> "Shown with Liberation Serif · same widths as Times New Roman"
        "Arial" -> "Shown with Liberation Sans · same widths as Arial"
        "Courier New" -> "Shown with Liberation Mono · same widths as Courier New"
        else -> "Not installed here · a similar font is shown, the name stays in the file"
    }
}

/** Font list with the current font ticked; the usual Word fonts first. */
@Composable
fun FontSheet(current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val installed = remember { FontSetup.families() }
    val installedLower = remember(installed) { installed.map { it.lowercase() }.toSet() }
    val all = remember(installed) { (PINNED_FONTS + installed).distinctBy { it.lowercase() } }
    val shown = all.filter { query.isBlank() || it.contains(query.trim(), ignoreCase = true) }
    AppSheet(onDismiss) {
        SheetTitle("Font", if (current.isNotBlank()) "Current: $current" else null)
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Search fonts") }, leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
        )
        LazyColumn(Modifier.height(380.dp).padding(top = 6.dp)) {
            items(shown) { name ->
                Row(Modifier.fillMaxWidth().clickable { onPick(name) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, fontSize = 17.sp)
                        fontHint(name, installedLower)?.let { Text(it, fontSize = 11.sp, color = Color(0xFF667085)) }
                    }
                    if (name.equals(current, ignoreCase = true)) Icon(Icons.Default.Check, "Current", tint = WordBlue)
                }
                HorizontalDivider(color = Color(0xFFF0F0F0))
            }
        }
    }
}

/** Font size picker: the usual Word sizes plus a custom value. */
@Composable
fun SizeSheet(current: Float, onDismiss: () -> Unit, onPick: (Float) -> Unit) {
    var custom by remember { mutableStateOf("") }
    val sizes = listOf(8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 32, 36, 48, 60, 72)
    AppSheet(onDismiss) {
        SheetTitle("Font size", "Current: ${if (current % 1f == 0f) current.toInt().toString() else current.toString()} pt")
        sizes.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { size ->
                    Box(Modifier.weight(1f)) { Pill(size.toString(), current == size.toFloat()) { onPick(size.toFloat()) } }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = custom, onValueChange = { custom = it }, singleLine = true, label = { Text("Custom size (pt)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { custom.toFloatOrNull()?.let { if (it in 1f..1638f) onPick(it) } },
                enabled = custom.toFloatOrNull() != null
            ) { Text("Apply") }
        }
    }
}

/** Word's table grid: drag across the squares and let go to insert. */
@Composable
fun TableGridSheet(onDismiss: () -> Unit, onInsert: (Int, Int) -> Unit) {
    var rows by remember { mutableIntStateOf(3) }
    var cols by remember { mutableIntStateOf(3) }
    val cell = 30.dp
    val cellPx = with(LocalDensity.current) { cell.toPx() }
    fun pick(o: Offset) {
        cols = ((o.x / cellPx).toInt() + 1).coerceIn(1, 10)
        rows = ((o.y / cellPx).toInt() + 1).coerceIn(1, 8)
    }
    AppSheet(onDismiss) {
        SheetTitle("Insert table", "$cols × $rows table (columns × rows)")
        Box(
            Modifier
                .size(cell * 10, cell * 8)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pick(it) },
                        onDrag = { change, _ -> pick(change.position) },
                        onDragEnd = { onInsert(rows, cols) }
                    )
                }
                .pointerInput(Unit) { detectTapGestures { o -> pick(o); onInsert(rows, cols) } }
        ) {
            Canvas(Modifier.size(cell * 10, cell * 8)) {
                for (r in 0 until 8) for (c in 0 until 10) {
                    val on = r < rows && c < cols
                    val tl = Offset(c * cellPx + 2f, r * cellPx + 2f)
                    val sz = Size(cellPx - 4f, cellPx - 4f)
                    if (on) drawRect(Color(0x33185ABD), tl, sz)
                    drawRect(if (on) WordBlue else Color(0xFFCCCCCC), tl, sz, style = Stroke(width = if (on) 2.5f else 1.5f))
                }
            }
        }
        Button(
            onClick = { onInsert(rows, cols) },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(50.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("Insert $cols × $rows table", fontSize = 16.sp) }
    }
}

/** Asked when leaving a document with unsaved changes. */
@Composable
fun SaveChangesSheet(name: String, onSave: () -> Unit, onDiscard: () -> Unit, onCancel: () -> Unit) {
    AppSheet(onCancel) {
        SheetTitle("Save changes to “$name”?", "Your changes will be lost if you don't save them.")
        Button(
            onClick = onSave, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)
        ) { Text("Save", fontSize = 16.sp) }
        OutlinedButton(
            onClick = onDiscard, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(50.dp), shape = RoundedCornerShape(14.dp)
        ) { Text("Don't save", fontSize = 16.sp, color = Color(0xFFB42318)) }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Text("Cancel") }
    }
}
