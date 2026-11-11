package com.wordcraft.android

import org.json.JSONArray
import org.json.JSONObject

/** One ribbon button: a desktop WordCraft feature mapped to an engine command id. */
data class RibbonItem(
    val tab: String,
    val group: String,
    val label: String,
    val id: String,
    val params: String,
    val shortcut: String
)

data class RibbonGroup(val name: String, val items: List<RibbonItem>)
data class RibbonTab(val name: String, val groups: List<RibbonGroup>)

/** One parameter parsed from a command's parameter description, e.g. `"size": number`. */
data class ParamField(val name: String, val optional: Boolean, val type: String)

object RibbonModel {
    private val HIDDEN = setOf("Quick Access", "Title Bar")
    private val ORDER = listOf(
        "File", "Home", "Insert", "Draw", "Design", "Layout", "References", "Mailings",
        "Review", "View", "Developer", "Help"
    )

    /** Builds every tab/group/button the desktop ribbon has and this engine build implements. */
    fun load(): List<RibbonTab> {
        val items = ArrayList<RibbonItem>()
        val array: JSONArray = JSONObject(NativeBridge.nativeRibbon()).optJSONArray("items") ?: JSONArray()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            if (!o.optBoolean("exists")) continue
            val tab = o.getString("tab")
            if (tab in HIDDEN) continue
            items += RibbonItem(
                tab, o.getString("group"), o.getString("label"), o.getString("id"),
                o.optString("params", "{}"), o.optString("shortcut", "")
            )
        }
        val byTab = items.groupBy { it.tab }
        val names = ORDER.filter { it in byTab } + byTab.keys.filter { it !in ORDER }
        return names.map { name ->
            val groups = byTab.getValue(name).groupBy { it.group }.map { (g, list) ->
                RibbonGroup(g, list.distinctBy { it.id })
            }
            RibbonTab(name, groups)
        }
    }

    /** All commands, for the search palette. */
    fun loadAll(): List<RibbonItem> {
        val array = runCatching { JSONArray(NativeBridge.nativeCommandRegistry()) }.getOrDefault(JSONArray())
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            RibbonItem(
                o.optString("location"), o.optString("location"), o.optString("label"),
                o.getString("id"), o.optString("params", "{}"), o.optString("shortcut")
            )
        }
    }

    /** Parameters described as `{"size": number, "name"?: string}`; empty when none. */
    fun fields(params: String): List<ParamField> {
        val regex = Regex("\"(\\w+)\"(\\?)?\\s*:\\s*([^,}]+)")
        return regex.findAll(params).map { m ->
            ParamField(m.groupValues[1], m.groupValues[2] == "?", m.groupValues[3].trim())
        }.toList()
    }

    /**
     * True when the command needs the user to choose something before running: a required
     * parameter, an enumerated choice, or values that are all optional but not on/off switches.
     * Simple on/off toggles (an optional boolean) run immediately.
     */
    fun needsInput(params: String): Boolean {
        val f = fields(params)
        if (f.isEmpty()) return false
        if (f.any { !it.optional }) return true
        if (f.any { options(it.type) != null }) return true
        return f.any { !it.type.lowercase().contains("bool") }
    }

    /** Choices of an enumerated parameter such as `"normal|narrow|wide"`, or null. */
    fun options(type: String): List<String>? {
        val parts = type.replace("\"", "").split('|').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        val ok = parts.all { Regex("[A-Za-z0-9_.+-]{1,24}").matches(it) }
        return if (ok) parts else null
    }
}
