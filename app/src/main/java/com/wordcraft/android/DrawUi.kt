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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val INK_COLORS = listOf("000000", "C00000", "ED7D31", "FFC000", "548235", "00B0F0", "185ABD", "7030A0", "FF00FF", "FFFF00")
private val INK_WIDTHS = listOf(1f, 2.5f, 4f, 6f, 10f)

/**
 * The Draw tab: pick a pen, pencil, highlighter or eraser and draw on the page with a finger or a
 * stylus. While a tool is active the page does not scroll or place the cursor; tap Done to
 * go back to typing.
 */
@Composable
fun DrawTabContent(state: EditorState) {
    Row(
        Modifier.fillMaxWidth().height(112.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row {
                listOf("pen" to "Pen", "pencil" to "Pencil", "highlighter" to "Highlighter", "eraser" to "Eraser").forEach { (id, label) ->
                    ToolTile(label, state.drawTool == id) { state.drawTool = id }
                }
                ToolTile("Done", false, danger = state.drawTool != null) { state.drawTool = null }
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                INK_WIDTHS.forEach { w ->
                    Box(
                        Modifier.padding(end = 6.dp).size(34.dp)
                            .background(if (state.drawWidth == w) Color(0xFFDCE8FA) else Color(0xFFF2F4F7), RoundedCornerShape(8.dp))
                            .clickable { state.drawWidth = w },
                        contentAlignment = Alignment.Center
                    ) { Box(Modifier.size(width = 22.dp, height = (w.coerceAtMost(10f) + 1f).dp).background(Color(0xFF242424), RoundedCornerShape(4.dp))) }
                }
                Box(
                    Modifier.padding(start = 6.dp)
                        .background(if (state.stylusOnly) Color(0xFFDCE8FA) else Color(0xFFF2F4F7), RoundedCornerShape(16.dp))
                        .clickable { state.stylusOnly = !state.stylusOnly }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) { Text("Stylus only", fontSize = 12.sp, color = if (state.stylusOnly) WordBlue else Color(0xFF344054)) }
            }
        }
        Box(Modifier.width(12.dp))
        Column {
            Row {
                INK_COLORS.take(5).forEach { hex -> InkSwatch(hex, state.drawColor == hex) { state.drawColor = hex } }
            }
            Row(Modifier.padding(top = 6.dp)) {
                INK_COLORS.drop(5).forEach { hex -> InkSwatch(hex, state.drawColor == hex) { state.drawColor = hex } }
            }
        }
    }
}

@Composable
private fun ToolTile(label: String, selected: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.padding(end = 6.dp)
            .background(if (selected) WordBlue else if (danger) Color(0xFFFDECEA) else Color(0xFFF2F4F7), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (selected) Color.White else if (danger) Color(0xFFB42318) else Color(0xFF242424))
    }
}

@Composable
private fun InkSwatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(end = 6.dp).size(34.dp)
            .background(Color(("FF$hex").toLong(16)), CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) WordBlue else Color(0xFFBBBBBB), CircleShape)
            .clickable(onClick = onClick)
    )
}
