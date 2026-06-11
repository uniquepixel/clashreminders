package de.pixel.clashreminders.scheduling

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.pixel.clashreminders.api.CocApiClient
import de.pixel.clashreminders.api.valueOrNull
import de.pixel.clashreminders.data.db.AppDatabase
import de.pixel.clashreminders.data.db.entity.ClanGamesSnapshotEntity
import de.pixel.clashreminders.data.repository.SettingsRepository
import de.pixel.clashreminders.domain.ClanGamesCalendar
import de.pixel.clashreminders.domain.ReminderType

/**
 * Takes the "Games Champion" achievement baseline for every member of every
 * clan with an enabled clan games reminder — the bot's achievement_data
 * snapshot at window start (22nd 07:00 UTC).
 */
class ClanGamesSnapshotWorker(
    appContext: Context,
    params: WorkerParameters,
    private val database: AppDatabase,
    private val settings: SettingsRepository,
    private val api: CocApiClient,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (settings.apiKeyOnce() == null) return Result.success()
        val now = System.currentTimeMillis()
        val window = ClanGamesCalendar.currentWindow(now) ?: run {
            Log.d(AlarmScheduler.TAG, "Snapshot skipped: outside clan games window")
            return Result.success()
        }

        for (clan in database.clanDao().getAll()) {
            val hasCgReminder = database.reminderDao()
                .getEnabledForClan(clan.tag)
                .any { it.type == ReminderType.CLAN_GAMES_END }
            if (!hasCgReminder) continue

            val members = api.getClan(clan.tag).valueOrNull()?.memberList
            if (members == null) {
                Log.w(AlarmScheduler.TAG, "Snapshot: clan fetch failed for ${clan.tag}")
                continue
            }
            val snapshots = members.mapNotNull { member ->
                api.getPlayer(member.tag).valueOrNull()?.clanGamesPoints()?.let { points ->
                    ClanGamesSnapshotEntity(
                        clanTag = clan.tag,
                        playerTag = member.tag,
                        playerName = member.name,
                        points = points,
                        windowKey = window.windowKey,
                        takenAt = now,
                    )
                }
            }
            database.snapshotDao().insertAll(snapshots)
            Log.d(
                AlarmScheduler.TAG,
                "Snapshot stored for ${clan.tag}: ${snapshots.size}/${members.size} members"
            )
        }
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<ClanGamesSnapshotWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "cg_snapshot", ExistingWorkPolicy.KEEP, request
            )
        }
    }
}
