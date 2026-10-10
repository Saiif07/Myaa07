package com.myraa.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.UUID

object Notifier {
    private const val CH = "myraa_alerts"

    fun show(c: Context, title: String, text: String, id: Int) {
        try {
            showIntent(c, title, text, PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE), id)
        } catch (e: Exception) { }
    }

    fun showIntent(c: Context, title: String, text: String, pi: PendingIntent, id: Int) {
        try {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CH, "MYRAA alerts", NotificationManager.IMPORTANCE_DEFAULT))
            val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, CH) else Notification.Builder(c)
            nm.notify(id, b.setContentTitle(title).setContentText(text).setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentIntent(pi).setAutoCancel(true).build())
        } catch (e: Exception) { }
    }
}

/** Local reminders (AlarmManager). Stored as "id|whenMs|everyMs|text" lines so they survive reboot. */
object Reminders {
    private const val KEY = "rem"
    private fun load(c: Context): MutableList<List<String>> =
        Cfg.get(c, KEY).split("\n").filter { it.isNotBlank() }.map { it.split("|", limit = 4) }.filter { it.size == 4 }.toMutableList()

    private fun save(c: Context, l: List<List<String>>) = Cfg.put(c, KEY, l.joinToString("\n") { it.joinToString("|") })

    private fun arm(c: Context, id: String, whenMs: Long, every: Long, text: String) {
        try {
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val i = Intent(c, ReminderReceiver::class.java).putExtra("id", id).putExtra("every", every).putExtra("text", text)
            val pi = PendingIntent.getBroadcast(c, id.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
        } catch (e: Exception) { }
    }

    fun add(c: Context, text: String, delayMs: Long, every: Long) {
        val id = UUID.randomUUID().toString().take(8)
        val at = System.currentTimeMillis() + delayMs
        val clean = text.replace("|", " ").replace("\n", " ")
        val l = load(c)
        l.add(listOf(id, at.toString(), every.toString(), clean))
        save(c, l)
        arm(c, id, at, every, clean)
    }

    fun fired(c: Context, id: String, every: Long, text: String) {
        val l = load(c).filter { it[0] != id }.toMutableList()
        if (every > 0L) {
            val at = System.currentTimeMillis() + every
            l.add(listOf(id, at.toString(), every.toString(), text))
            arm(c, id, at, every, text)
        }
        save(c, l)
    }

    fun rescheduleAll(c: Context) {
        for (r in load(c)) arm(c, r[0], maxOf(r[1].toLongOrNull() ?: 0L, System.currentTimeMillis() + 2000L), r[2].toLongOrNull() ?: 0L, r[3])
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        try {
            val text = i.getStringExtra("text") ?: return
            Notifier.show(c, "MYRAA reminder", text, (System.currentTimeMillis() % 100000L).toInt() + 1000)
            Reminders.fired(c, i.getStringExtra("id") ?: "", i.getLongExtra("every", 0L), text)
        } catch (e: Exception) { }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        try {
            if (i.action != Intent.ACTION_BOOT_COMPLETED) return
            Reminders.rescheduleAll(c)
            if (Cfg.get(c, "wake_on") == "1") {
                if (Build.VERSION.SDK_INT < 30) {
                    val s = Intent(c, WakeService::class.java)
                    if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s) else c.startService(s)
                } else {
                    // Android 11+ blocks microphone access for services started from boot: ask the user to tap once.
                    Notifier.show(c, "MYRAA", "Tap karke 'Hey Myraa' ON karo", 77)
                }
            }
        } catch (e: Exception) { }
    }
}
