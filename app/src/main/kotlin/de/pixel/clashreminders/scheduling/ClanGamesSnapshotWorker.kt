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
 * Takes the "Games Champion" achievement baseline for every account at the
 * start of the clan games window (22nd 07:00 UTC). Account-based, so only
 * a handful of player fetches instead of whole clan member lists.
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
        val hasCgReminder = database.reminderDao()
            .getAllEnabled()
            .any { it.type == ReminderType.CLAN_GAMES_END }
        if (!hasCgReminder) return Result.success()

        val accounts = database.accountDao().getAll()
        val snapshots = accounts.mapNotNull { account ->
            api.getPlayer(account.tag).valueOrNull()?.clanGamesPoints()?.let { points ->
                ClanGamesSnapshotEntity(
                    playerTag = account.tag,
                    points = points,
                    windowKey = window.windowKey,
                    takenAt = now,
                )
            }
        }
        database.snapshotDao().insertAll(snapshots)
        Log.d(
            AlarmScheduler.TAG,
            "CG snapshot stored: ${snapshots.size}/${accounts.size} accounts"
        )
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
