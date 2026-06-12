package de.pixel.clashreminders.scheduling

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.pixel.clashreminders.api.CocApiClient
import de.pixel.clashreminders.api.valueOrNull
import de.pixel.clashreminders.data.db.AppDatabase
import de.pixel.clashreminders.data.repository.AccountSync
import de.pixel.clashreminders.data.repository.SettingsRepository
import java.util.concurrent.TimeUnit

/**
 * Lightweight presence check: every 15 minutes (the WorkManager minimum for
 * periodic work) it fetches just the player profile of each account and
 * records a clan sighting. This catches clan-hopping — going over to another
 * clan only for war hits — that the 3-hour refresh would miss. When an
 * account shows up in a clan that is not tracked yet, a full refresh is
 * triggered immediately so wars there are discovered right away.
 */
class PresenceWorker(
    appContext: Context,
    params: WorkerParameters,
    private val database: AppDatabase,
    private val settings: SettingsRepository,
    private val api: CocApiClient,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (settings.apiKeyOnce() == null) return Result.success()
        val accounts = database.accountDao().getAll()
        if (accounts.isEmpty()) return Result.success()

        var newClanFound = false
        for (account in accounts) {
            val player = api.getPlayer(account.tag).valueOrNull() ?: continue
            val updated = AccountSync.applyPlayer(database, account, player)
            val clanTag = updated.clanTag
            if (clanTag != null && database.trackedClanDao().getByTag(clanTag) == null) {
                newClanFound = true
            }
        }
        if (newClanFound) {
            Log.d(AlarmScheduler.TAG, "Presence check found a new clan, refreshing")
            RefreshWorker.enqueueNow(applicationContext)
        }
        return Result.success()
    }

    companion object {
        private const val PERIODIC_NAME = "presence_periodic"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<PresenceWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
