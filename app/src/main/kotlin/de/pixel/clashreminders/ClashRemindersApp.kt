package de.pixel.clashreminders

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import de.pixel.clashreminders.api.CocApiClient
import de.pixel.clashreminders.data.db.AppDatabase
import de.pixel.clashreminders.data.repository.AccountRepository
import de.pixel.clashreminders.data.repository.SettingsRepository
import de.pixel.clashreminders.notification.NotificationHelper
import de.pixel.clashreminders.scheduling.AlarmScheduler
import de.pixel.clashreminders.scheduling.AppWorkerFactory
import de.pixel.clashreminders.scheduling.RefreshWorker

class ClashRemindersApp : Application(), Configuration.Provider {

    val database by lazy { AppDatabase.getInstance(this) }

    val settingsRepository by lazy { SettingsRepository(this) }

    val apiClient by lazy { CocApiClient { settingsRepository.apiKeyOnce() } }

    val accountRepository by lazy {
        AccountRepository(database.accountDao(), database.reminderDao(), apiClient)
    }

    val notificationHelper by lazy { NotificationHelper(this) }

    val alarmScheduler by lazy { AlarmScheduler(this) }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(
                AppWorkerFactory(database, settingsRepository, apiClient, notificationHelper, alarmScheduler)
            )
            .build()

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannels()
        WorkManager.initialize(this, workManagerConfiguration)
        RefreshWorker.schedulePeriodic(this)
        RefreshWorker.enqueueNow(this)
    }
}
