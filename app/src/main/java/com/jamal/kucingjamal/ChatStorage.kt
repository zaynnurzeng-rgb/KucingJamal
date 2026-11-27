package com.jamal.kucingjamal

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ChatStorage {
    private const val FILE_NAME = "jamal_chat_history.json"
    private const val MAX_MESSAGES = 60

    fun defaultMessages(): List<ChatMessage> = listOf(
        ChatMessage(
            role = "model",
            text = "Heh, ngapain buka-buka aplikasi ini? Kalau mau nanya PR atau ngobrol cepetan, gue sibuk!"
        )
    )

    fun load(context: Context): List<ChatMessage> = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return defaultMessages()
        val arr = JSONArray(file.readText())
        val result = mutableListOf<ChatMessage>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val role = obj.optString("role", "")
            val text = obj.optString("text", "")
            if (role.isNotEmpty() && text.isNotEmpty()) {
                result.add(ChatMessage(role, text))
            }
        }
        result.ifEmpty { defaultMessages() }
    }.getOrElse { defaultMessages() }

    fun save(context: Context, messages: List<ChatMessage>) {
        runCatching {
            val arr = JSONArray()
            messages.takeLast(MAX_MESSAGES).forEach { msg ->
                arr.put(
                    JSONObject().apply {
                        put("role", msg.role)
                        put("text", msg.text)
                    }
                )
            }
            File(context.filesDir, FILE_NAME).writeText(arr.toString())
        }
    }

    fun clear(context: Context): List<ChatMessage> {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
        return defaultMessages()
    }
}
