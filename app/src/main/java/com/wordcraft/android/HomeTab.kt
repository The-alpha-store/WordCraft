package com.wordcraft.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BorderAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatIndentDecrease
import androidx.compose.material.icons.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material.icons.filled.Subscript
import androidx.compose.material.icons.filled.Superscript
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** What the curated Home tab asks the activity to do. */
class HomeActions(
    val run: (String, String) -> Unit,
    val pickFont: () -> Unit,
    val pickSize: () -> Unit,
    val openFind: (Boolean) -> Unit,
    val openBorders: () -> Unit
)

private val TEXT_COLORS = listOf("000000", "7F7F7F", "C00000", "ED7D31", "FFC000", "548235", "00B0F0", "185ABD", "7030A0", "FF00FF")
private val HIGHLIGHTS = listOf("FFFF00", "00FF00", "00FFFF", "FF00FF", "0000FF", "FF0000", "FFC000", "C0C0C0")

/**
 * Word's Home tab, laid out like the desktop: Clipboard, Font, Paragraph, Styles, Editing.
 * Toggles light up from the engine's live formatting state, and the Font and Size boxes show
 * the font and size at the selection.
 */
@Composable
fun HomeTabContent(state: EditorState, actions: HomeActions) {
    val f = state.fmt
    var textColor by remember { mutableStateOf("C00000") }
    var highlightColor by remember { mutableStateOf("FFFF00") }
    val off = state.disabled

    Row(
        Modifier.fillMaxWidth().height(112.dp).horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.Top
    ) {
        // Clipboard
        Group("Clipboard") {
            Column(
                Modifier.size(width = 58.dp, height = 78.dp).clickable { actions.run("edit.paste", "{}") },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.ContentPaste, "Paste", Modifier.padding(top = 10.dp).size(34.dp), tint = Color(0xFF242424))
                Text("Paste", fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Column {
                Tool(Icons.Default.ContentCut, "Cut", false, off["edit.cut"] != null) { actions.run("edit.cut", "{}") }
                Tool(Icons.Default.ContentCopy, "Copy", false, off["edit.copy"] != null) { actions.run("edit.copy", "{}") }
            }
        }
        Divider()
        // Font
        Group("Font") {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ValueBox(f.font.ifBlank { "Font" }, 128.dp, actions.pickFont)
                    ValueBox(if (f.size % 1f == 0f) f.size.toInt().toString() else f.size.toString(), 54.dp, actions.pickSize)
                    Tool(null, "A+", false, false) { actions.run("format.growFont", "{}") }
                    Tool(null, "A−", false, false) { actions.run("format.shrinkFont", "{}") }
                    Tool(Icons.Default.FormatClear, "Clear formatting", false, false) { actions.run("format.clear", "{}") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tool(Icons.Default.FormatBold, "Bold", f.bold, false) { actions.run("format.bold", "{}") }
                    Tool(Icons.Default.FormatItalic, "Italic", f.italic, false) { actions.run("format.italic", "{}") }
                    Tool(Icons.Default.FormatUnderlined, "Underline", f.underline, false) { actions.run("format.underline", "{}") }
                    Tool(Icons.Default.StrikethroughS, "Strikethrough", f.strike, false) { actions.run("format.strikethrough", "{}") }
                    Tool(Icons.Default.Subscript, "Subscript", f.sub, false) { actions.run("format.subscript", "{}") }
                    Tool(Icons.Default.Superscript, "Superscript", f.sup, false) { actions.run("format.superscript", "{}") }
                    ColorSplit(Icons.Default.FormatColorFill, "Highlight", highlightColor, HIGHLIGHTS, f.highlight,
                        onApply = { actions.run("format.highlight", "{\"color\":\"$it\"}") },
                        onPick = { highlightColor = it },
                        extra = "No colour" to { actions.run("format.highlight", "{\"color\":\"none\"}") })
                    ColorSplit(Icons.Default.FormatColorText, "Font colour", textColor, TEXT_COLORS, false,
                        onApply = { actions.run("format.color", "{\"color\":\"$it\"}") },
                        onPick = { textColor = it },
                        extra = "Automatic" to { actions.run("format.color", "{\"color\":\"auto\"}") })
                }
            }
        }
        Divider()
        // Paragraph
        Group("Paragraph") {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tool(Icons.Default.FormatListBulleted, "Bullets", f.list, false) { actions.run("para.bullets", "{}") }
                    Tool(Icons.Default.FormatListNumbered, "Numbering", false, false) { actions.run("para.numbering", "{}") }
                    Tool(Icons.Default.FormatIndentDecrease, "Decrease indent", false, false) { actions.run("para.outdent", "{}") }
                    Tool(Icons.Default.FormatIndentIncrease, "Increase indent", false, false) { actions.run("para.indent", "{}") }
                    Tool(null, "¶", state.toggles["view.marks"] == true, false) { actions.run("view.marks", "{}") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tool(Icons.Default.FormatAlignLeft, "Align left", f.align.contains("left") || f.align == "start", false) { actions.run("para.alignLeft", "{}") }
                    Tool(Icons.Default.FormatAlignCenter, "Center", f.align.contains("center"), false) { actions.run("para.alignCenter", "{}") }
                    Tool(Icons.Default.FormatAlignRight, "Align right", f.align.contains("right") || f.align == "end", false) { actions.run("para.alignRight", "{}") }
                    Tool(Icons.Default.FormatAlignJustify, "Justify", f.align.contains("just") || f.align.contains("both"), false) { actions.run("para.justify", "{}") }
                    SpacingMenu { actions.run("para.lineSpacing", "{\"value\":$it}") }
                    Tool(Icons.Default.BorderAll, "Borders and shading", false, false) { actions.openBorders() }
                }
            }
        }
        Divider()
        // Styles gallery
        Group("Styles") {
            Row(Modifier.width(300.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                STYLE_TILES.forEach { tile ->
                    StyleTile(tile, selected = styleMatches(f.style, tile.id)) { actions.run("para.style", "{\"style\":\"${tile.id}\"}") }
                }
            }
        }
        Divider()
        // Editing
        Group("Editing") {
            Column {
                TextTool(Icons.Default.Search, "Find") { actions.openFind(false) }
                TextTool(Icons.Default.FindReplace, "Replace") { actions.openFind(true) }
                TextTool(null, "Select all") { actions.run("select.all", "{}") }
            }
        }
    }
}

@Composable
private fun Group(label: String, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Top, content = content)
        Text(label, fontSize = 10.sp, color = Color(0xFF616161), maxLines = 1)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 8.dp).background(Color(0xFFE1E1E1)))
}

/** A square icon (or short text) button that lights up when the format is on. */
@Composable
private fun Tool(icon: ImageVector?, label: String, active: Boolean, disabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(1.dp)
            .size(36.dp)
            .alpha(if (disabled) 0.38f else 1f)
            .background(if (active) Color(0xFFDCE8FA) else Color.Transparent, RoundedCornerShape(8.dp))
            .border(if (active) 1.dp else 0.dp, if (active) WordBlue else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(enabled = !disabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) Icon(icon, label, Modifier.size(21.dp), tint = if (active) WordBlue else Color(0xFF242424))
        else Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF242424))
    }
}

@Composable
private fun TextTool(icon: ImageVector?, label: String, onClick: () -> Unit) {
    Row(
        Modifier.height(34.dp).width(100.dp).clickable(onClick = onClick).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = Color(0xFF242424))
        else Box(Modifier.size(20.dp))
        Text(label, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

/** The Font and Size boxes: show the current value, tap to choose. */
@Composable
private fun ValueBox(text: String, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .width(width).height(34.dp)
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFCFCFCF), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(16.dp), tint = Color(0xFF616161))
    }
}

/** Word's split button: tap the icon to apply the last colour; tap the arrow to pick another. */
@Composable
private fun ColorSplit(
    icon: ImageVector,
    label: String,
    current: String,
    palette: List<String>,
    active: Boolean,
    onApply: (String) -> Unit,
    onPick: (String) -> Unit,
    extra: Pair<String, () -> Unit>
) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.size(width = 34.dp, height = 36.dp).clickable { onApply(current) },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, label, Modifier.padding(top = 4.dp).size(19.dp), tint = if (active) WordBlue else Color(0xFF242424))
            Box(Modifier.padding(top = 1.dp).width(18.dp).height(4.dp).background(Color(("FF$current").toLong(16))))
        }
        Box {
            Box(Modifier.size(width = 16.dp, height = 36.dp).clickable { open = true }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.KeyboardArrowDown, "$label options", Modifier.size(16.dp), tint = Color(0xFF616161))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                Text(label, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontSize = 12.sp, color = Color(0xFF667085))
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    palette.take(5).forEach { hex -> Swatch(hex) { open = false; onPick(hex); onApply(hex) } }
                }
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    palette.drop(5).forEach { hex -> Swatch(hex) { open = false; onPick(hex); onApply(hex) } }
                }
                DropdownMenuItem(text = { Text(extra.first) }, onClick = { open = false; extra.second() })
            }
        }
    }
}

@Composable
private fun Swatch(hex: String, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp)
            .background(Color(("FF$hex").toLong(16)), CircleShape)
            .border(1.dp, Color(0xFFBBBBBB), CircleShape)
            .clickable(onClick = onClick)
    )
}

@Composable
private fun SpacingMenu(onPick: (Double) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Tool(Icons.Default.FormatLineSpacing, "Line spacing", false, false) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(1.0, 1.15, 1.5, 2.0, 2.5, 3.0).forEach { v ->
                DropdownMenuItem(text = { Text(v.toString()) }, onClick = { open = false; onPick(v) })
            }
        }
    }
}

/** Preview tile of a paragraph style, drawn like Word's Styles gallery. */
class StyleTileSpec(val id: String, val label: String, val sample: String, val size: Int, val color: Long, val weight: FontWeight)

val STYLE_TILES = listOf(
    StyleTileSpec("Normal", "Normal", "AaBbCcDd", 15, 0xFF000000, FontWeight.Normal),
    StyleTileSpec("NoSpacing", "No Spacing", "AaBbCcDd", 15, 0xFF000000, FontWeight.Normal),
    StyleTileSpec("Heading1", "Heading 1", "AaBbCc", 19, 0xFF0F4761, FontWeight.Normal),
    StyleTileSpec("Title", "Title", "AaBb", 24, 0xFF000000, FontWeight.Normal),
    StyleTileSpec("Heading2", "Heading 2", "AaBbCc", 17, 0xFF0F4761, FontWeight.Normal),
    StyleTileSpec("Heading3", "Heading 3", "AaBbCc", 15, 0xFF0F4761, FontWeight.Medium),
    StyleTileSpec("Subtitle", "Subtitle", "AaBbCcD", 15, 0xFF595959, FontWeight.Normal),
    StyleTileSpec("Quote", "Quote", "AaBbCcDd", 15, 0xFF404040, FontWeight.Normal),
    StyleTileSpec("IntenseQuote", "Intense Quote", "AaBbCc", 15, 0xFF0F4761, FontWeight.Medium),
    StyleTileSpec("ListParagraph", "List Paragraph", "AaBbCcDd", 14, 0xFF000000, FontWeight.Normal)
)

/** True when the engine's current style (an id or a display name) is this tile. */
fun styleMatches(current: String, id: String): Boolean {
    fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
    return current.isNotBlank() && norm(current) == norm(id)
}

@Composable
private fun StyleTile(tile: StyleTileSpec, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .size(width = 86.dp, height = 78.dp)
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) WordBlue else Color(0xFFD5D5D5), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(tile.sample, fontSize = tile.size.sp, color = Color(tile.color), fontWeight = tile.weight, maxLines = 1, overflow = TextOverflow.Clip)
        }
        Text(
            tile.label, Modifier.fillMaxWidth().background(if (selected) Color(0xFFDCE8FA) else Color(0xFFF5F5F5)).padding(vertical = 4.dp),
            fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
