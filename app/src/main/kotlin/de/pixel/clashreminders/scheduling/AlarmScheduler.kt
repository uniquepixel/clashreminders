package de.pixel.clashreminders.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import de.pixel.clashreminders.data.db.entity.ScheduledAlarmEntity

/**
 * Exact-alarm wrapper, pattern from todo-reminder-android's ReminderScheduler.
 * Falls back to a 15-minute window when exact alarms are not permitted.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun schedule(alarm: ScheduledAlarmEntity) {
        val pendingIntent = pendingIntent(alarm)
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, alarm.fireAtMillis, pendingIntent
            )
        } else {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP, alarm.fireAtMillis, INEXACT_WINDOW_MILLIS, pendingIntent
            )
        }
        Log.d(TAG, "Scheduled alarm rc=${alarm.requestCode} type=${alarm.type} at=${alarm.fireAtMillis}")
    }

    fun cancel(alarm: ScheduledAlarmEntity) {
        alarmManager.cancel(pendingIntent(alarm))
        Log.d(TAG, "Cancelled alarm rc=${alarm.requestCode}")
    }

    private fun pendingIntent(alarm: ScheduledAlarmEntity): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = if (alarm.reminderId == ScheduledAlarmEntity.SNAPSHOT_REMINDER_ID) {
                ACTION_CG_SNAPSHOT
            } else {
                ACTION_FIRE
            }
            putExtra(EXTRA_REMINDER_ID, alarm.reminderId)
            putExtra(EXTRA_EVENT_KEY, alarm.eventKey)
            alarm.clanTag?.let { putExtra(EXTRA_CLAN_TAG, it) }
        }
        return PendingIntent.getBroadcast(
            context,
            alarm.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val TAG = "ClashReminders"
        const val ACTION_FIRE = "de.pixel.clashreminders.ACTION_FIRE_REMINDER"
        const val ACTION_CG_SNAPSHOT = "de.pixel.clashreminders.ACTION_CG_SNAPSHOT"
        const val EXTRA_REMINDER_ID = "reminderId"
        const val EXTRA_EVENT_KEY = "eventKey"
        const val EXTRA_CLAN_TAG = "clanTag"
        private const val INEXACT_WINDOW_MILLIS = 15 * 60 * 1000L
    }
}
