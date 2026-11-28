package com.jamal.kucingjamal

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class JamalReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!AppSettingsStorage.load(context).notificationsEnabled) return
        JamalReminderScheduler.showNotification(context, isTest = false)
        // Jadwalkan pengingat berikutnya (6 jam kemudian) jika user belum membuka aplikasi
        JamalReminderScheduler.scheduleReminder(context, delayMs = 6L * 60L * 60L * 1000L)
    }
}

object JamalReminderScheduler {
    private const val CHANNEL_ID = "jamal_reminder_channel"
    private const val NOTIF_ID = 1001
    private const val REQ_CODE = 4041

    private val hungryMessages = listOf(
        "Woi! Perut gue udah bunyi nih, kasih makan dulu kek!",
        "Lu lupa punya kucing ya? Gue laper berat nih!",
        "Heh, jangan buka aplikasi pas ada PR doang! Kasih gue makan!"
    )

    private val tsundereMessages = listOf(
        "Hmph, bukan berarti gue kangen ya... tapi lu gak ada PR hari ini?",
        "Sepi amat sih, lu ke mana aja?! Sini main kuis kilat sama gue!",
        "Woi, udah lama gak mampir! Gue gak nungguin kok, cuma bosen aja.",
        "Heh! Kalo lagi males belajar, sini gue bantuin... tapi jangan ngelunjak!"
    )

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    "Pengingat Kucing Jamal",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifikasi omelan tsundere Kucing Jamal saat lapar atau lama dicuekin"
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showNotification(context: Context, isTest: Boolean = false): Boolean {
        if (!hasNotificationPermission(context)) return false
        ensureChannel(context)

        val petStatus = PetStateStorage.load(context)
        val title = when {
            isTest -> "Kucing Jamal (Tes Pengingat)"
            petStatus.hunger <= 40 -> "Kucing Jamal Lagi Kelaparan!"
            else -> "Kucing Jamal Manggil Lu!"
        }
        val body = when {
            isTest -> "Heh! Notifikasi udah nyala. Awas aja nanti dicuekin pas gue laper!"
            petStatus.hunger <= 40 -> hungryMessages.random()
            else -> tsundereMessages.random()
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeIcon = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_jamal)
        }.getOrNull()

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_jamal)
            .apply { if (largeIcon != null) setLargeIcon(largeIcon) }
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notif)
        return true
    }

    fun scheduleReminder(context: Context, delayMs: Long = 4L * 60L * 60L * 1000L) {
        if (!AppSettingsStorage.load(context).notificationsEnabled) {
            cancelReminder(context)
            return
        }
        runCatching {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, JamalReminderReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                REQ_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAt = System.currentTimeMillis() + delayMs
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancelReminder(context: Context) {
        runCatching {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, JamalReminderReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                REQ_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pi)
        }
    }

    fun clearDelivered(context: Context) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIF_ID)
        }
    }
}
