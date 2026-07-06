package com.dyra.calories

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.LocalTime

/** Показывает ежедневное напоминание и взводит будильник на следующий день. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!recentlyLogged(context)) {
            notifyUser(context)
        }
        // Взводим напоминание на следующий день
        ReminderScheduler.schedule(context)
    }

    /** Есть ли сегодня запись за последние 3 часа — тогда не беспокоим. */
    private fun recentlyLogged(context: Context): Boolean {
        val entries = Store(context).entriesFor(LocalDate.now())
        if (entries.isEmpty()) return false
        val now = LocalTime.now()
        return entries.any { entry ->
            runCatching { LocalTime.parse(entry.time) }.getOrNull()?.let { time ->
                !time.isAfter(now) && time.isAfter(now.minusHours(3))
            } ?: false
        }
    }

    private fun notifyUser(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_title),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.reminder_text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        runCatching { manager.notify(1, notification) }
    }

    companion object {
        private const val CHANNEL_ID = "reminders"
    }
}
