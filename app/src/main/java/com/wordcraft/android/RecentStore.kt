package com.wordcraft.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A document the user created, opened or saved, remembered for the home screen. */
data class RecentDoc(val uri: String, val name: String, val openedAt: Long, val sizeBytes: Long, val action: String)

/** One entry of the activity timeline ("Edited Report.docx"). */
data class ActivityEvent(val time: Long, val action: String, val name: String, val uri: String)

/**
 * Remembers recent documents (with a first-page thumbnail) and a short activity timeline in
 * private app storage. Document access itself stays with Android's file picker permissions.
 */
class RecentStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("wordcraft_recents", Context.MODE_PRIVATE)
    private val thumbDir = File(context.filesDir, "thumbs").apply { mkdirs() }

    fun docs(): List<RecentDoc> = runCatching {
        val arr = JSONArray(prefs.getString("docs", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            RecentDoc(o.getString("uri"), o.getString("name"), o.getLong("t"), o.optLong("size"), o.optString("action", "Opened"))
        }.sortedByDescending { it.openedAt }
    }.getOrDefault(emptyList())

    fun events(): List<ActivityEvent> = runCatching {
        val arr = JSONArray(prefs.getString("events", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ActivityEvent(o.getLong("t"), o.getString("action"), o.getString("name"), o.optString("uri"))
        }.sortedByDescending { it.time }
    }.getOrDefault(emptyList())

    /** Add or refresh a document and record the activity ("Created", "Opened", "Edited", "Saved"...). */
    fun touch(uri: String, name: String, sizeBytes: Long, action: String) {
        val now = System.currentTimeMillis()
        val list = docs().filter { it.uri != uri }.toMutableList()
        list.add(0, RecentDoc(uri, name, now, sizeBytes, action))
        val arr = JSONArray()
        list.take(60).forEach {
            arr.put(JSONObject().put("uri", it.uri).put("name", it.name).put("t", it.openedAt).put("size", it.sizeBytes).put("action", it.action))
        }
        prefs.edit().putString("docs", arr.toString()).apply()
        log(action, name, uri)
    }

    /** Record an activity without changing the document list (for example "Exported PDF"). */
    fun log(action: String, name: String, uri: String) {
        val events = events().toMutableList()
        // Merge repeated autosaves of the same document within a minute into one line.
        val last = events.firstOrNull()
        if (last != null && last.uri == uri && last.action == action && System.currentTimeMillis() - last.time < 60_000) events.removeAt(0)
        events.add(0, ActivityEvent(System.currentTimeMillis(), action, name, uri))
        val arr = JSONArray()
        events.take(80).forEach { arr.put(JSONObject().put("t", it.time).put("action", it.action).put("name", it.name).put("uri", it.uri)) }
        prefs.edit().putString("events", arr.toString()).apply()
    }

    fun remove(uri: String) {
        val arr = JSONArray()
        docs().filter { it.uri != uri }.forEach {
            arr.put(JSONObject().put("uri", it.uri).put("name", it.name).put("t", it.openedAt).put("size", it.sizeBytes).put("action", it.action))
        }
        prefs.edit().putString("docs", arr.toString()).apply()
        thumbFile(uri).delete()
    }

    private fun thumbFile(uri: String) = File(thumbDir, "${uri.hashCode().toUInt()}.png")

    fun saveThumb(uri: String, bitmap: Bitmap) {
        runCatching { thumbFile(uri).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) } }
    }

    fun loadThumb(uri: String): Bitmap? = runCatching {
        thumbFile(uri).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }.getOrNull()
}
