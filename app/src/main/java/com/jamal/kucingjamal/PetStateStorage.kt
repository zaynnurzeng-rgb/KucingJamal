package com.jamal.kucingjamal

import android.content.Context

data class PetStatus(
    val hunger: Int = 78, // 0 = kelaparan, 100 = kenyang banget
    val mood: Int = 68    // 0 = ngamuk, 100 = tsundere jinak
) {
    fun toPromptSummary(): String {
        val hungerDesc = when {
            hunger <= 25 -> "SANGAT LAPAR & keroncongan (kamu makin galak, kesal, dan suka menyinggung minta makan)"
            hunger <= 55 -> "agak lapar (sedikit lebih sewot dari biasanya)"
            hunger >= 85 -> "kenyang banget (lebih santai walau tetap sok ketus)"
            else -> "cukup kenyang"
        }
        val moodDesc = when {
            mood <= 25 -> "LAGI NGAMUK / BAD MOOD BERAT (baru dijahilin/dicuekin, jawab makin pedas dan ketus tapi tetap jawab soal PR)"
            mood <= 55 -> "agak kesal dan jutek seperti biasa"
            mood >= 80 -> "lagi lumayan senang (sisi tsundere pedulimu lebih sering keluar, walau tetap gengsi ngaku)"
            else -> "standar tsundere jutek"
        }
        return "Status fisik & emosimu saat ini: Kenyang $hunger/100 ($hungerDesc); Mood $mood/100 ($moodDesc)."
    }
}

object PetStateStorage {
    private const val PREF_NAME = "jamal_pet_state"
    private const val KEY_HUNGER = "hunger"
    private const val KEY_MOOD = "mood"
    private const val KEY_LAST_TS = "last_ts"

    fun load(context: Context): PetStatus {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val savedHunger = prefs.getInt(KEY_HUNGER, 78)
        val savedMood = prefs.getInt(KEY_MOOD, 68)
        val lastTs = prefs.getLong(KEY_LAST_TS, now)

        val elapsedMin = ((now - lastTs) / 60_000L).coerceAtLeast(0L)
        val hungerDrop = (elapsedMin / 8L).toInt()
        val moodDrop = (elapsedMin / 12L).toInt()

        val current = PetStatus(
            hunger = (savedHunger - hungerDrop).coerceIn(5, 100),
            mood = (savedMood - moodDrop).coerceIn(5, 100)
        )
        if (hungerDrop > 0 || moodDrop > 0 || !prefs.contains(KEY_LAST_TS)) {
            save(context, current)
        }
        return current
    }

    fun save(context: Context, status: PetStatus) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_HUNGER, status.hunger.coerceIn(0, 100))
            .putInt(KEY_MOOD, status.mood.coerceIn(0, 100))
            .putLong(KEY_LAST_TS, System.currentTimeMillis())
            .apply()
    }

    fun adjust(context: Context, current: PetStatus, deltaHunger: Int = 0, deltaMood: Int = 0): PetStatus {
        val updated = PetStatus(
            hunger = (current.hunger + deltaHunger).coerceIn(0, 100),
            mood = (current.mood + deltaMood).coerceIn(0, 100)
        )
        save(context, updated)
        return updated
    }
}
