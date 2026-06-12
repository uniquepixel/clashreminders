package de.pixel.clashreminders.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Thin alarm target: hands off to an expedited worker that fetches fresh
 * data and posts the notification (tier 2 of the background strategy).
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_FIRE -> {
                val reminderId = intent.getLongExtra(AlarmScheduler.EXTRA_REMINDER_ID, -1)
                val eventKey = intent.getStringExtra(AlarmScheduler.EXTRA_EVENT_KEY)
                val clanTag = intent.getStringExtra(AlarmScheduler.EXTRA_CLAN_TAG)
                if (reminderId == -1L || eventKey == null) return
                Log.d(AlarmScheduler.TAG, "Alarm fired for reminder=$reminderId key=$eventKey")
                FireReminderWorker.enqueue(context, reminderId, eventKey, clanTag)
            }
            AlarmScheduler.ACTION_CG_SNAPSHOT -> {
                Log.d(AlarmScheduler.TAG, "Clan games snapshot alarm fired")
                ClanGamesSnapshotWorker.enqueue(context)
            }
        }
    }
}
