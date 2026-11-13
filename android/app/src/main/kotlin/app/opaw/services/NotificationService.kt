package app.opaw.services

import app.opaw.data.Reminder
import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.opaw.MainActivity
import app.opaw.OpawApplication
import app.opaw.R
import app.opaw.data.ReminderRepeat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Schedules care reminders as local notifications. Mirrors NotificationService.swift: daily reminders
 * fire at their time every day; others fire once, at 19:00 the evening before they're due.
 *
 * Android alarms don't repeat by calendar, so each alarm schedules the next one when it fires, and
 * everything is planned again after a reboot or app update.
 */
object NotificationService {
    private const val CHANNEL = "reminders"
    private const val EXTRA_ID = "reminderId"

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Care reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Meals, meds and other care reminders"
        })
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Replaces all scheduled reminders with the enabled ones for every dog. */
    fun reschedule(context: Context) {
        val app = context.applicationContext as OpawApplication
        CoroutineScope(Dispatchers.IO).launch {
            val alarms = context.getSystemService(AlarmManager::class.java)
            val reminders = app.repository.allReminders()
            // Cancel everything we may have set before, including reminders that were deleted.
            val known = app.settings.scheduledReminderIds()
            (known + reminders.map { it.id }).forEach { alarms.cancel(pending(context, it)) }
            val enabled = reminders.filter { it.isEnabled }
            enabled.forEach { reminder ->
                next(reminder)?.let { at ->
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pending(context, reminder.id))
                }
            }
            app.settings.setScheduledReminderIds(enabled.map { it.id })
        }
    }

    /** When a reminder should next fire. */
    private fun next(reminder: Reminder, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime? {
        val now = ZonedDateTime.now(zone)
        if (reminder.repeats == ReminderRepeat.DAILY) {
            var at = ZonedDateTime.of(now.toLocalDate(), LocalTime.of(reminder.hour, reminder.minute), zone)
            if (!at.isAfter(now)) at = at.plusDays(1)
            return at
        }
        val tomorrow = LocalDate.now(zone).plusDays(1)
        val day = reminder.nextOccurrence(tomorrow, zone).minusDays(1)
        val at = ZonedDateTime.of(day, LocalTime.of(19, 0), zone)
        return at.takeIf { it.isAfter(now) }
    }

    private fun pending(context: Context, reminderId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_ID, reminderId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    internal fun fire(context: Context, reminderId: String) {
        val app = context.applicationContext as OpawApplication
        CoroutineScope(Dispatchers.IO).launch {
            val reminder = app.repository.allReminders().firstOrNull { it.id == reminderId } ?: return@launch
            if (!reminder.isEnabled) return@launch
            val name = app.repository.allDogs().firstOrNull { it.id == reminder.dogId }?.name ?: "your dog"
            val kind = reminder.kind
            val body = if (reminder.repeats == ReminderRepeat.DAILY) {
                "${kind.emoji} Time for $name's ${kind.title.lowercase()}."
            } else {
                "${kind.emoji} ${reminder.title} for $name is tomorrow."
            }
            if (canNotify(context)) {
                val open = PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE,
                )
                val notification = NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(reminder.title)
                    .setContentText(body)
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build()
                runCatching { NotificationManagerCompat.from(context).notify(reminderId.hashCode(), notification) }
            }
            // Plan the next one.
            reschedule(context)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("reminderId") ?: return
        NotificationService.fire(context, id)
    }
}

/** Plans reminders again after a reboot, an app update or a time zone change. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationService.reschedule(context)
    }
}
