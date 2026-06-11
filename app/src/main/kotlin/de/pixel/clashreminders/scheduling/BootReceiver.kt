package de.pixel.clashreminders.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import de.pixel.clashreminders.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-arms all alarms from the scheduled_alarms mirror after reboot/update
 * (no network needed) and triggers a refresh for fresh planning.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scheduler = AlarmScheduler(context)
                val alarms = AppDatabase.getInstance(context).scheduledAlarmDao().getAll()
                val now = System.currentTimeMillis()
                alarms.filter { it.fireAtMillis > now }.forEach { scheduler.schedule(it) }
                Log.d(AlarmScheduler.TAG, "BootReceiver re-armed ${alarms.size} alarms ($action)")
                RefreshWorker.enqueueNow(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
