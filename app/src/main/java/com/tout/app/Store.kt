package com.tout.app

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Entry(
    val id: String = UUID.randomUUID().toString(),
    val ts: Long = System.currentTimeMillis(),
    val date: String, // yyyy-MM-dd
    val type: String, // money|food|note
    val amount: Double? = null,
    val text: String? = null,
    val tags: List<String> = emptyList(),
)

object Store {
    const val FILE = "entries.jsonl"
    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    fun toJson(e: Entry): String = JSONObject()
        .put("id", e.id)
        .put("ts", e.ts)
        .put("date", e.date)
        .put("type", e.type)
        .put("amount", e.amount)
        .put("text", e.text)
        .put("tags", JSONArray(e.tags))
        .toString()

    // ponytail: strict parse here — import is a trust boundary, skip bad lines
    fun parseLine(line: String): Entry? {
        return try {
        val o = JSONObject(line)
        val type = o.getString("type")
        if (type != "money" && type != "food" && type != "note") return null
        Entry(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            ts = o.optLong("ts", System.currentTimeMillis()),
            date = o.getString("date"),
            type = type,
            amount = if (o.isNull("amount")) null else o.optDouble("amount"),
            text = if (o.isNull("text")) null else o.optString("text"),
            tags = o.optJSONArray("tags")?.let { a ->
                List(a.length()) { a.optString(it) }.filter { it.isNotBlank() }
            } ?: emptyList(),
        )
    } catch (_: Exception) {
        null
    }
    }

    // ponytail: append-only file, no DB — Room when on-device search matters
    fun append(ctx: Context, e: Entry) {
        val f = file(ctx)
        f.appendText(toJson(e) + "\n")
    }

    fun readIds(ctx: Context): Set<String> {
        val f = file(ctx)
        if (!f.exists()) return emptySet()
        return f.bufferedReader().lineSequence()
            .mapNotNull { runCatching { JSONObject(it).optString("id") }.getOrNull() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun exportTo(ctx: Context, uri: Uri) {
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            val f = file(ctx)
            if (f.exists()) f.inputStream().copyTo(out) // empty file exports empty, fine
        }
    }

    /** Returns Pair(imported, skipped). */
    fun importFrom(ctx: Context, uri: Uri): Pair<Int, Int> {
        val seen = readIds(ctx).toMutableSet()
        var ok = 0
        var bad = 0
        ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { r ->
            r.lineSequence().forEach { line ->
                if (line.isBlank()) return@forEach
                val e = parseLine(line)
                if (e == null || !seen.add(e.id)) {
                    bad++
                } else {
                    append(ctx, e)
                    ok++
                }
            }
        }
        return ok to bad
    }
}
