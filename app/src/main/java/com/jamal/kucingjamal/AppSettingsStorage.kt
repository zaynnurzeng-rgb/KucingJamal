package com.jamal.kucingjamal

import android.content.Context
import java.io.File
import java.util.Locale

data class AppSettings(
    val voiceName: String = "Puck",
    val mimicPitch: Float = 1.48f,
    val animFps: Int = 16,
    val notificationsEnabled: Boolean = true
)

data class VoiceOption(
    val name: String,
    val label: String
)

data class CacheStats(
    val fileCount: Int,
    val totalBytes: Long
) {
    fun formatted(): String {
        val sizeText = when {
            totalBytes >= 1024L * 1024L -> String.format(Locale.US, "%.2f MB", totalBytes / (1024f * 1024f))
            totalBytes >= 1024L -> String.format(Locale.US, "%.1f KB", totalBytes / 1024f)
            else -> "$totalBytes B"
        }
        return "$fileCount file • $sizeText"
    }
}

object AppSettingsStorage {
    private const val PREF_NAME = "jamal_app_settings"
    private const val KEY_VOICE = "voice_name"
    private const val KEY_PITCH = "mimic_pitch"
    private const val KEY_FPS = "anim_fps"
    private const val KEY_NOTIF = "notifications_enabled"

    val availableVoices = listOf(
        VoiceOption("Puck", "Usil"),
        VoiceOption("Fenrir", "Berat"),
        VoiceOption("Charon", "Serius"),
        VoiceOption("Kore", "Cewek"),
        VoiceOption("Aoede", "Lembut")
    )

    fun load(context: Context): AppSettings {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val voice = prefs.getString(KEY_VOICE, "Puck").orEmpty().ifBlank { "Puck" }
        val pitch = prefs.getFloat(KEY_PITCH, 1.48f).coerceIn(1.15f, 1.75f)
        val fps = prefs.getInt(KEY_FPS, 16).coerceIn(12, 24)
        val notif = prefs.getBoolean(KEY_NOTIF, true)
        return AppSettings(
            voiceName = voice,
            mimicPitch = pitch,
            animFps = fps,
            notificationsEnabled = notif
        )
    }

    fun save(context: Context, settings: AppSettings) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_VOICE, settings.voiceName)
            .putFloat(KEY_PITCH, settings.mimicPitch.coerceIn(1.15f, 1.75f))
            .putInt(KEY_FPS, settings.animFps.coerceIn(12, 24))
            .putBoolean(KEY_NOTIF, settings.notificationsEnabled)
            .apply()
    }

    private fun cacheDirs(context: Context): List<File> = listOf(
        File(context.filesDir, "jamal_tts_v4"),
        File(context.filesDir, "jamal_tts_v3"),
        File(context.cacheDir, "jamal_tts_v2")
    )

    fun getTtsCacheStats(context: Context): CacheStats {
        var count = 0
        var bytes = 0L
        for (dir in cacheDirs(context)) {
            if (!dir.exists()) continue
            dir.listFiles()?.forEach { f ->
                if (f.isFile) {
                    count++
                    bytes += f.length()
                }
            }
        }
        return CacheStats(count, bytes)
    }

    fun clearTtsCache(context: Context): CacheStats {
        for (dir in cacheDirs(context)) {
            if (!dir.exists()) continue
            dir.listFiles()?.forEach { f ->
                if (f.isFile) runCatching { f.delete() }
            }
        }
        return getTtsCacheStats(context)
    }
}
