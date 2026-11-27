package com.jamal.kucingjamal

import android.content.Context
import org.json.JSONArray
import java.io.File

object MemoryStorage {
    private const val FILE_NAME = "jamal_memory.json"
    private const val MAX_ITEMS = 25

    fun load(context: Context): List<String> = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optString(i, "").trim()
                if (item.isNotEmpty()) add(item)
            }
        }
    }.getOrElse { emptyList() }

    fun save(context: Context, memories: List<String>) {
        runCatching {
            val arr = JSONArray()
            memories.takeLast(MAX_ITEMS).forEach { arr.put(it) }
            File(context.filesDir, FILE_NAME).writeText(arr.toString())
        }
    }

    fun merge(context: Context, current: List<String>, newFacts: List<String>): List<String> {
        val result = current.toMutableList()
        for (raw in newFacts) {
            val clean = raw.trim().replace(Regex("\\s+"), " ").take(120)
            if (clean.length < 3) continue
            val exists = result.any { it.equals(clean, ignoreCase = true) }
            if (!exists) {
                result.add(clean)
            }
        }
        val trimmed = result.takeLast(MAX_ITEMS)
        save(context, trimmed)
        return trimmed
    }

    fun removeAt(context: Context, current: List<String>, index: Int): List<String> {
        if (index !in current.indices) return current
        val updated = current.toMutableList().apply { removeAt(index) }
        save(context, updated)
        return updated
    }

    fun clear(context: Context): List<String> {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
        return emptyList()
    }
}
