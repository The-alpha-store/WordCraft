package com.wordcraft.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

private val BORDER_COLORS = listOf("000000", "595959", "A6A6A6", "C00000", "ED7D31", "FFC000", "548235", "00B0F0", "185ABD", "7030A0")
private val SHADE_COLORS = listOf("FFFFFF", "F2F2F2", "D9D9D9", "FFF2CC", "FCE4D6", "E2EFDA", "DDEBF7", "E4DFEC", "FFFF00", "BDD7EE", "C6E0B4", "F8CBAD")
private val WIDTHS = listOf(0.5, 1.0, 1.5, 2.25, 3.0, 4.5, 6.0)

private val TABLE_STYLES = listOf(
    "Plain" to "PlainTable1",
    "Grid" to "TableGrid",
    "Light grid" to "GridTable1Light",
    "Blue" to "GridTable4AccentBlue",
    "Orange" to "GridTable4AccentOrange",
    "Green" to "GridTable4AccentGreen",
    "List blue" to "ListTable3AccentBlue"
)

private val ALIGNS = listOf(
    listOf("topLeft", "topCenter", "topRight"),
    listOf("centerLeft", "center", "centerRight"),
    listOf("bottomLeft", "bottomCenter", "bottomRight")
)

@Composable
private fun SectionLabel(text: String) {
    Text(text, Modifier.padding(top = 14.dp, bottom = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF667085))
}

@Composable
private fun ColorRow(selected: String?, colors: List<String>, onPick: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { hex ->
            Box(
                Modifier.size(36.dp)
                    .background(Color(("FF$hex").toLong(16)), CircleShape)
                    .border(if (selected == hex) 3.dp else 1.dp, if (selected == hex) WordBlue else Color(0xFFBBBBBB), CircleShape)
                    .clickable { onPick(hex) }
            )
        }
    }
}

/** Docked toolbar shown while the caret is in a table: the edits people reach for most. */
@Composable
fun TableBar(onRun: (String, String) -> Unit, onFormat: () -> Unit) {
    val actions = listOf(
        "Row ↑" to "table.insertRowAbove", "Row ↓" to "table.insertRowBelow",
        "Col ←" to "table.insertColumnLeft", "Col →" to "table.insertColumnRight",
        "Delete row" to "table.deleteRow", "Delete col" to "table.deleteColumn",
        "Merge" to "table.merge", "Split" to "table.split",
        "Select table" to "table.selectTable", "Delete table" to "table.deleteTable"
    )
    Column(Modifier.fillMaxWidth().background(Color(0xFFEEF3FB))) {
        HorizontalDivider(color = Color(0xFFD5DDEB))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.padding(end = 8.dp).background(WordBlue, RoundedCornerShape(16.dp)).clickable(onClick = onFormat)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) { Text("Format table", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium) }
            actions.forEach { (label, id) ->
                Box(
                    Modifier.padding(end = 6.dp).background(Color.White, RoundedCornerShape(16.dp)).border(1.dp, Color(0xFFD5DDEB), RoundedCornerShape(16.dp))
                        .clickable { onRun(id, "{}") }.padding(horizontal = 14.dp, vertical = 8.dp)
                ) { Text(label, fontSize = 13.sp) }
            }
        }
    }
}

/**
 * Everything about how a table looks, in one place: borders (which sides, thickness, colour),
 * shading, cell alignment, AutoFit, ready-made table styles and exact column and row sizes.
 */
@Composable
fun TableFormatSheet(onDismiss: () -> Unit, onRun: (String, String) -> Unit) {
    var kind by remember { mutableStateOf("all") }
    var width by remember { mutableStateOf(1.0) }
    var color by remember { mutableStateOf("000000") }
    var header by remember { mutableStateOf(true) }
    var banded by remember { mutableStateOf(true) }
    var firstCol by remember { mutableStateOf(false) }
    var total by remember { mutableStateOf(false) }
    var colWidth by remember { mutableStateOf("") }
    var rowHeight by remember { mutableStateOf("") }

    AppSheet(onDismiss) {
        SheetTitle("Format table", "Changes apply to the selected cells, or the whole table")
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            SectionLabel("Borders")
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                listOf("all" to "All", "outside" to "Outside", "inside" to "Inside", "top" to "Top", "bottom" to "Bottom",
                    "left" to "Left", "right" to "Right", "none" to "None").forEach { (k, label) ->
                    Pill(label, kind == k) { kind = k }
                }
            }
            Text("Thickness", Modifier.padding(top = 8.dp, bottom = 4.dp), fontSize = 12.sp, color = Color(0xFF667085))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                WIDTHS.forEach { w -> Pill("$w pt", width == w) { width = w } }
            }
            Text("Colour", Modifier.padding(top = 8.dp, bottom = 6.dp), fontSize = 12.sp, color = Color(0xFF667085))
            ColorRow(color, BORDER_COLORS) { color = it }
            Button(
                onClick = {
                    val p = JSONObject().put("kind", kind)
                    if (kind != "none") p.put("width", width).put("color", color)
                    onRun("table.borders", p.toString())
                },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(12.dp)
            ) { Text("Apply borders") }

            SectionLabel("Shading")
            ColorRow(null, SHADE_COLORS) { onRun("table.shading", JSONObject().put("color", it).toString()) }
            TextButton(onClick = { onRun("table.shading", "{\"color\":null}") }) { Text("No shading") }

            SectionLabel("Cell alignment")
            ALIGNS.forEach { row ->
                Row {
                    row.forEach { a ->
                        Box(
                            Modifier.padding(end = 8.dp, bottom = 8.dp).size(width = 54.dp, height = 40.dp)
                                .background(Color(0xFFF2F4F7), RoundedCornerShape(8.dp))
                                .clickable { onRun("table.cellAlign", JSONObject().put("value", a).toString()) },
                            contentAlignment = Alignment.Center
                        ) {
                            val label = when {
                                a.startsWith("top") -> "▔"
                                a.startsWith("bottom") -> "▁"
                                else -> "▬"
                            }
                            val align = when {
                                a.endsWith("Left") -> Alignment.CenterStart
                                a.endsWith("Right") -> Alignment.CenterEnd
                                else -> Alignment.Center
                            }
                            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp), contentAlignment = align) { Text(label, fontSize = 14.sp) }
                        }
                    }
                }
            }

            SectionLabel("Size and fit")
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Pill("AutoFit contents", false) { onRun("table.autofit", "{\"mode\":\"contents\"}") }
                Pill("Fit window", false) { onRun("table.autofit", "{\"mode\":\"window\"}") }
                Pill("Fixed width", false) { onRun("table.autofit", "{\"mode\":\"fixed\"}") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Pill("Equal columns", false) { onRun("table.distributeColumns", "{}") }
                Pill("Equal rows", false) { onRun("table.distributeRows", "{}") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = colWidth, onValueChange = { colWidth = it }, singleLine = true, label = { Text("Column width (pt)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f)
                )
                TextButton(enabled = colWidth.toDoubleOrNull() != null, onClick = {
                    onRun("table.columnWidth", JSONObject().put("width", colWidth.toDouble()).toString())
                }) { Text("Set") }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = rowHeight, onValueChange = { rowHeight = it }, singleLine = true, label = { Text("Row height (pt)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f)
                )
                TextButton(enabled = rowHeight.toDoubleOrNull() != null, onClick = {
                    onRun("table.rowHeight", JSONObject().put("height", rowHeight.toDouble()).toString())
                }) { Text("Set") }
            }

            SectionLabel("Table style")
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TABLE_STYLES.forEach { (label, id) -> Pill(label, false) { onRun("table.style", JSONObject().put("style", id).toString()) } }
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Pill("Header row", header) { header = !header; onRun("table.look", JSONObject().put("headerRow", header).toString()) }
                Pill("Banded rows", banded) { banded = !banded; onRun("table.look", JSONObject().put("bandedRows", banded).toString()) }
                Pill("First column", firstCol) { firstCol = !firstCol; onRun("table.look", JSONObject().put("firstColumn", firstCol).toString()) }
                Pill("Total row", total) { total = !total; onRun("table.look", JSONObject().put("totalRow", total).toString()) }
            }
        }
        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(12.dp)) { Text("Done") }
    }
}

/** Paragraph borders in every line style Word has, plus paragraph shading. */
@Composable
fun ParaBorderSheet(onDismiss: () -> Unit, onRun: (String, String) -> Unit) {
    var kind by remember { mutableStateOf("bottom") }
    var style by remember { mutableStateOf("single") }
    var width by remember { mutableStateOf(1.0) }
    var color by remember { mutableStateOf("000000") }
    AppSheet(onDismiss) {
        SheetTitle("Borders and shading", "For the selected paragraphs")
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            SectionLabel("Where")
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                listOf("bottom" to "Bottom", "top" to "Top", "left" to "Left", "right" to "Right", "all" to "All sides",
                    "outside" to "Outside", "horizontalLine" to "Line", "none" to "None").forEach { (k, label) ->
                    Pill(label, kind == k) { kind = k }
                }
            }
            SectionLabel("Line style")
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                listOf("single" to "Solid", "double" to "Double", "dotted" to "Dotted", "dashed" to "Dashed", "thick" to "Thick",
                    "triple" to "Triple", "dotDash" to "Dot-dash", "wave" to "Wave").forEach { (k, label) ->
                    Pill(label, style == k) { style = k }
                }
            }
            SectionLabel("Thickness")
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                WIDTHS.forEach { w -> Pill("$w pt", width == w) { width = w } }
            }
            SectionLabel("Colour")
            ColorRow(color, BORDER_COLORS) { color = it }
            Button(
                onClick = {
                    val p = JSONObject().put("kind", kind)
                    if (kind != "none") p.put("style", style).put("width", width).put("color", color)
                    onRun("para.borders", p.toString())
                },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(12.dp)
            ) { Text("Apply border") }

            SectionLabel("Shading")
            ColorRow(null, SHADE_COLORS) { onRun("para.shading", JSONObject().put("color", it).toString()) }
            TextButton(onClick = { onRun("para.shading", "{\"color\":null}") }) { Text("No shading") }
        }
        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(12.dp)) { Text("Done") }
    }
}
