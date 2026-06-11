package de.pixel.clashreminders.scheduling

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import de.pixel.clashreminders.api.CocApiClient
import de.pixel.clashreminders.data.db.AppDatabase
import de.pixel.clashreminders.data.repository.SettingsRepository
import de.pixel.clashreminders.notification.NotificationHelper

class AppWorkerFactory(
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val api: CocApiClient,
    private val notificationHelper: NotificationHelper,
    private val alarmScheduler: AlarmScheduler,
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        RefreshWorker::class.java.name ->
            RefreshWorker(appContext, workerParameters, database, settingsRepository, api, notificationHelper, alarmScheduler)
        FireReminderWorker::class.java.name ->
            FireReminderWorker(appContext, workerParameters, database, api, notificationHelper)
        ClanGamesSnapshotWorker::class.java.name ->
            ClanGamesSnapshotWorker(appContext, workerParameters, database, settingsRepository, api)
        else -> null
    }
}
