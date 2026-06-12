package de.pixel.clashreminders.scheduling

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import de.pixel.clashreminders.api.CocApiClient
import de.pixel.clashreminders.api.dto.CurrentWarDto
import de.pixel.clashreminders.data.db.AppDatabase
import de.pixel.clashreminders.data.repository.AccountStatusLoader
import de.pixel.clashreminders.data.repository.SettingsRepository
import de.pixel.clashreminders.domain.ClanGamesCalendar
import de.pixel.clashreminders.domain.RaidWeekend
import de.pixel.clashreminders.widget.TodoWidget
import de.pixel.clashreminders.widget.TodoWidgetState
import de.pixel.clashreminders.widget.TodoWidgetState.Companion.encode

class WidgetUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
    private val database: AppDatabase,
    private val settings: SettingsRepository,
    private val api: CocApiClient,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val manager = GlanceAppWidgetManager(applicationContext)
        val glanceIds = manager.getGlanceIds(TodoWidget::class.java)
        if (glanceIds.isEmpty()) return Result.success()

        val forced = inputData.getBoolean(KEY_FORCED, false)
        val now = System.currentTimeMillis()

        if (!forced && !anyEventOngoing(now)) {
            return Result.success()
        }

        if (settings.apiKeyOnce() == null) {
            writeAndRender(glanceIds) { it.copy(hasApiKey = false, refreshing = false) }
            return Result.success()
        }

        val results = AccountStatusLoader(database, api).load(now)

        if (results.isNotEmpty() && results.none { it.playerFetched }) {
            writeAndRender(glanceIds) { it.copy(refreshing = false) }
            return Result.success()
        }

        val state = TodoWidgetState(
            updatedAt = now,
            refreshing = false,
            hasApiKey = true,
            hasAccounts = results.isNotEmpty(),
            entries = results.filter { it.status.anythingOpen }.map { loaded ->
                TodoWidgetState.Entry(
                    tag = loaded.account.tag,
                    name = loaded.account.name,
                    warState = loaded.status.warState,
                    warDone = loaded.status.warAttacksDone,
                    warRequired = loaded.status.warAttacksRequired,
                    raidAttacks = loaded.status.raidAttacks,
                    raidLimit = loaded.status.raidLimit,
                    cgPoints = loaded.status.cgPoints,
                    cgActive = loaded.status.cgActive,
                )
            },
        )
        writeAndRender(glanceIds) { state }
        return Result.success()
    }

    private suspend fun writeAndRender(
        glanceIds: List<androidx.glance.GlanceId>,
        transform: (TodoWidgetState) -> TodoWidgetState,
    ) {
        val widget = TodoWidget()
        for (id in glanceIds) {
            updateAppWidgetState(applicationContext, id) { prefs ->
                prefs[TodoWidgetState.KEY] = transform(TodoWidgetState.from(prefs)).encode()
            }
            widget.update(applicationContext, id)
        }
    }

    private suspend fun anyEventOngoing(now: Long): Boolean =
        RaidWeekend.isInWindow(now) ||
            ClanGamesCalendar.currentWindow(now) != null ||
            database.trackedClanDao().getAll().any {
                it.lastWarState == CurrentWarDto.STATE_PREPARATION ||
                    it.lastWarState == CurrentWarDto.STATE_IN_WAR
            }

    companion object {
        private const val KEY_FORCED = "forced"
        const val UNIQUE_NAME = "widget_update"

        fun enqueue(context: Context, forced: Boolean) {
            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setInputData(workDataOf(KEY_FORCED to forced))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request,
            )
        }
    }
}
