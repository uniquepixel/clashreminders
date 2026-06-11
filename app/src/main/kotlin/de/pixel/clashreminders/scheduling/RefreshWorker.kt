package de.pixel.clashreminders.scheduling

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.pixel.clashreminders.api.ApiResult
import de.pixel.clashreminders.api.CocApiClient
import de.pixel.clashreminders.api.dto.CurrentWarDto
import de.pixel.clashreminders.api.dto.LeagueGroupDto
import de.pixel.clashreminders.api.valueOrNull
import de.pixel.clashreminders.data.db.AppDatabase
import de.pixel.clashreminders.data.db.entity.ClanEntity
import de.pixel.clashreminders.data.db.entity.FiredEventEntity
import de.pixel.clashreminders.data.db.entity.ReminderEntity
import de.pixel.clashreminders.data.db.entity.ScheduledAlarmEntity
import de.pixel.clashreminders.data.repository.SettingsRepository
import de.pixel.clashreminders.domain.ClanGamesCalendar
import de.pixel.clashreminders.domain.CocTime
import de.pixel.clashreminders.domain.CwlAnalysis
import de.pixel.clashreminders.domain.EventPlanner
import de.pixel.clashreminders.domain.RaidWeekend
import de.pixel.clashreminders.domain.ReminderContentBuilder
import de.pixel.clashreminders.domain.ReminderType
import de.pixel.clashreminders.domain.WarAnalysis
import de.pixel.clashreminders.notification.NotificationHelper
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Tier 1 of the background strategy (the bot's 2-minute polling loop,
 * adapted for mobile): periodically fetches event end times per clan,
 * fires war-start notifications on state transitions and (re)arms exact
 * alarms for everything else.
 */
class RefreshWorker(
    appContext: Context,
    params: WorkerParameters,
    private val database: AppDatabase,
    private val settings: SettingsRepository,
    private val api: CocApiClient,
    private val notificationHelper: NotificationHelper,
    private val alarmScheduler: AlarmScheduler,
) : CoroutineWorker(appContext, params) {

    private val clanDao = database.clanDao()
    private val reminderDao = database.reminderDao()
    private val firedEventDao = database.firedEventDao()
    private val snapshotDao = database.snapshotDao()
    private val scheduledAlarmDao = database.scheduledAlarmDao()

    override suspend fun doWork(): Result {
        if (settings.apiKeyOnce() == null) {
            Log.d(AlarmScheduler.TAG, "Refresh skipped: no API key configured")
            return Result.success()
        }
        val now = System.currentTimeMillis()
        val existing = scheduledAlarmDao.getAll()
        val desired = mutableListOf<ScheduledAlarmEntity>()
        var anyCgReminder = false

        for (clan in clanDao.getAll()) {
            val reminders = reminderDao.getEnabledForClan(clan.tag)
            planWar(clan, reminders, existing, desired, now)
            planCwl(clan, reminders, existing, desired, now)
            planRaid(clan, reminders, desired, now)
            anyCgReminder = planClanGames(clan, reminders, desired, now) || anyCgReminder
        }

        if (anyCgReminder) {
            planSnapshotAlarm(desired, now)
        }

        reconcile(existing, desired)

        firedEventDao.deleteOlderThan(now - FIRED_EVENT_RETENTION_MILLIS)
        snapshotDao.deleteOlderThan(now - SNAPSHOT_RETENTION_MILLIS)
        settings.setLastRefreshAt(now)
        Log.d(AlarmScheduler.TAG, "Refresh done: ${desired.size} alarms scheduled")
        return Result.success()
    }

    // --- Clan War ------------------------------------------------------

    private suspend fun planWar(
        clan: ClanEntity,
        reminders: List<ReminderEntity>,
        existing: List<ScheduledAlarmEntity>,
        desired: MutableList<ScheduledAlarmEntity>,
        now: Long,
    ) {
        val warEndReminders = reminders.filter { it.type == ReminderType.WAR_END }
        val warStartReminders = reminders.filter { it.type == ReminderType.WAR_START }
        if (warEndReminders.isEmpty() && warStartReminders.isEmpty()) return

        when (val result = api.getCurrentWar(clan.tag)) {
            is ApiResult.Success -> {
                val war = result.value
                handleWarStartTransition(clan, war, warStartReminders)
                clanDao.updateLastWarState(clan.tag, war.state ?: CurrentWarDto.STATE_NOT_IN_WAR)

                val endMillis = CocTime.parseMillisOrNull(war.endTime)
                val warActive = war.state == CurrentWarDto.STATE_PREPARATION ||
                    war.state == CurrentWarDto.STATE_IN_WAR
                if (warActive && endMillis != null) {
                    val eventKey = "war-$endMillis"
                    warEndReminders.forEach { planOutcome(it, endMillis, eventKey, now, desired) }
                }
                if (war.state == CurrentWarDto.STATE_PREPARATION) {
                    CocTime.parseMillisOrNull(war.startTime)?.let { start ->
                        if (start > now) enqueueDelayed(applicationContext, start - now + 5 * 60_000L)
                    }
                }
            }
            is ApiResult.HttpError -> {
                if (result.code == 404) {
                    // war log private or no war — authoritative "not in war"
                    clanDao.updateLastWarState(clan.tag, CurrentWarDto.STATE_NOT_IN_WAR)
                } else {
                    preserveExisting(existing, warEndReminders, desired)
                }
            }
            is ApiResult.NetworkError -> preserveExisting(existing, warEndReminders, desired)
        }
    }

    private suspend fun handleWarStartTransition(
        clan: ClanEntity,
        war: CurrentWarDto,
        warStartReminders: List<ReminderEntity>,
    ) {
        if (!EventPlanner.isWarStartTransition(clan.lastWarState, war.state)) return
        val eventKey = "warstart-" + (war.endTime ?: war.startTime ?: "unknown")
        val contentBuilder = ReminderContentBuilder(applicationContext)
        for (reminder in warStartReminders) {
            if (firedEventDao.exists(reminder.id, eventKey)) continue
            notificationHelper.notify(
                reminder.id.toInt(),
                NotificationHelper.CHANNEL_WAR_START,
                contentBuilder.warStart(clan.name, war.state),
                clan.tag,
            )
            firedEventDao.insert(
                FiredEventEntity(reminderId = reminder.id, eventKey = eventKey, firedAt = System.currentTimeMillis())
            )
            Log.d(AlarmScheduler.TAG, "War start fired for ${clan.tag} (${war.state})")
        }
    }

    // --- CWL -----------------------------------------------------------

    private suspend fun planCwl(
        clan: ClanEntity,
        reminders: List<ReminderEntity>,
        existing: List<ScheduledAlarmEntity>,
        desired: MutableList<ScheduledAlarmEntity>,
        now: Long,
    ) {
        val cwlReminders = reminders.filter { it.type == ReminderType.CWL_DAY_END }
        if (cwlReminders.isEmpty()) return

        when (val result = api.getLeagueGroup(clan.tag)) {
            is ApiResult.Success -> {
                val group = result.value
                if (!CwlAnalysis.isGroupActive(group)) return
                val dayWar = resolveCurrentDayWar(group, clan.tag) ?: return
                val endMillis = CocTime.parseMillisOrNull(dayWar.endTime) ?: return
                val eventKey = "cwl-$endMillis"
                cwlReminders.forEach { planOutcome(it, endMillis, eventKey, now, desired) }
            }
            is ApiResult.HttpError -> {
                if (result.code != 404) preserveExisting(existing, cwlReminders, desired)
                // 404 = no league group, nothing to schedule
            }
            is ApiResult.NetworkError -> preserveExisting(existing, cwlReminders, desired)
        }
    }

    /**
     * Latest of our CWL wars that is inWar, otherwise the upcoming
     * preparation war — port of the day-war selection in ListeningEvent.java.
     */
    private suspend fun resolveCurrentDayWar(group: LeagueGroupDto, clanTag: String): CurrentWarDto? {
        var preparationFallback: CurrentWarDto? = null
        for (round in group.rounds.reversed()) {
            for (warTag in round.warTags) {
                if (!CwlAnalysis.isRealWarTag(warTag)) continue
                val war = api.getCwlWar(warTag).valueOrNull() ?: continue
                if (!WarAnalysis.isOurWar(war, clanTag)) continue
                when (war.state) {
                    CurrentWarDto.STATE_IN_WAR -> return war
                    CurrentWarDto.STATE_PREPARATION -> preparationFallback = war
                }
            }
        }
        return preparationFallback
    }

    // --- Raid weekend (deterministic, no API) ---------------------------

    private suspend fun planRaid(
        clan: ClanEntity,
        reminders: List<ReminderEntity>,
        desired: MutableList<ScheduledAlarmEntity>,
        now: Long,
    ) {
        val zone = ZoneId.systemDefault()
        for (reminder in reminders.filter { it.type == ReminderType.RAID }) {
            val day = RaidWeekend.dayFromName(reminder.raidDay) ?: continue
            val timeMinutes = reminder.raidTimeMinutes ?: continue
            var fireAt = RaidWeekend.nextFireTime(day, timeMinutes, now, zone)
            var eventKey = RaidWeekend.weekendKey(fireAt, zone)
            if (firedEventDao.exists(reminder.id, eventKey)) {
                fireAt = RaidWeekend.nextFireTime(day, timeMinutes, fireAt, zone)
                eventKey = RaidWeekend.weekendKey(fireAt, zone)
            }
            desired += ScheduledAlarmEntity(
                requestCode = reminder.id.toInt(),
                reminderId = reminder.id,
                clanTag = clan.tag,
                type = ReminderType.RAID,
                fireAtMillis = fireAt,
                eventKey = eventKey,
            )
        }
    }

    // --- Clan games (deterministic window) ------------------------------

    private suspend fun planClanGames(
        clan: ClanEntity,
        reminders: List<ReminderEntity>,
        desired: MutableList<ScheduledAlarmEntity>,
        now: Long,
    ): Boolean {
        val cgReminders = reminders.filter { it.type == ReminderType.CLAN_GAMES_END }
        if (cgReminders.isEmpty()) return false

        val window = ClanGamesCalendar.currentOrNextWindow(now)
        val eventKey = "cg-${window.windowKey}"
        cgReminders.forEach { planOutcome(it, window.endUtcMillis, eventKey, now, desired) }

        // Catch-up: snapshot missed (app was off on the 22nd) but window is running
        val current = ClanGamesCalendar.currentWindow(now)
        if (current != null && !snapshotDao.hasSnapshot(clan.tag, current.windowKey)) {
            Log.d(AlarmScheduler.TAG, "CG snapshot missing for ${clan.tag}, catching up")
            ClanGamesSnapshotWorker.enqueue(applicationContext)
        }
        return true
    }

    private fun planSnapshotAlarm(desired: MutableList<ScheduledAlarmEntity>, now: Long) {
        val window = ClanGamesCalendar.currentOrNextWindow(now)
        val snapshotAt = window.startUtcMillis + 5 * 60_000L
        if (snapshotAt > now) {
            desired += ScheduledAlarmEntity(
                requestCode = ScheduledAlarmEntity.SNAPSHOT_REQUEST_CODE,
                reminderId = ScheduledAlarmEntity.SNAPSHOT_REMINDER_ID,
                clanTag = "",
                type = ReminderType.CLAN_GAMES_END,
                fireAtMillis = snapshotAt,
                eventKey = "snapshot-${window.windowKey}",
            )
        }
    }

    // --- Shared planning helpers ----------------------------------------

    private suspend fun planOutcome(
        reminder: ReminderEntity,
        eventEndMillis: Long,
        eventKey: String,
        now: Long,
        desired: MutableList<ScheduledAlarmEntity>,
    ) {
        if (firedEventDao.exists(reminder.id, eventKey)) return
        when (val outcome = EventPlanner.planOffsetReminder(reminder, eventEndMillis, eventKey, now)) {
            is EventPlanner.Outcome.Schedule -> desired += outcome.alarm.toEntity()
            is EventPlanner.Outcome.FireNow ->
                FireReminderWorker.enqueue(applicationContext, reminder.id, eventKey)
            is EventPlanner.Outcome.MarkFiredSilently ->
                firedEventDao.insert(
                    FiredEventEntity(reminderId = reminder.id, eventKey = eventKey, firedAt = now)
                )
        }
    }

    /** On fetch failure, keep previously armed alarms instead of cancelling them. */
    private fun preserveExisting(
        existing: List<ScheduledAlarmEntity>,
        reminders: List<ReminderEntity>,
        desired: MutableList<ScheduledAlarmEntity>,
    ) {
        val ids = reminders.map { it.id }.toSet()
        desired += existing.filter { it.reminderId in ids }
    }

    private suspend fun reconcile(
        existing: List<ScheduledAlarmEntity>,
        desired: List<ScheduledAlarmEntity>,
    ) {
        val desiredCodes = desired.map { it.requestCode }.toSet()
        for (old in existing) {
            if (old.requestCode !in desiredCodes) {
                alarmScheduler.cancel(old)
                scheduledAlarmDao.delete(old.requestCode)
            }
        }
        for (alarm in desired) {
            alarmScheduler.schedule(alarm)
            scheduledAlarmDao.upsert(alarm)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(
            NotificationHelper.WORK_NOTIFICATION_ID,
            notificationHelper.workInProgressNotification(),
        )

    companion object {
        private const val PERIODIC_NAME = "refresh_periodic"
        private const val ONE_SHOT_NAME = "refresh_now"
        private const val DELAYED_NAME = "refresh_delayed"
        private const val FIRED_EVENT_RETENTION_MILLIS = 60L * 24 * 60 * 60 * 1000
        private const val SNAPSHOT_RETENTION_MILLIS = 90L * 24 * 60 * 60 * 1000

        private fun networkConstraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(3, TimeUnit.HOURS)
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_NAME, ExistingWorkPolicy.REPLACE, request
            )
        }

        fun enqueueDelayed(context: Context, delayMillis: Long) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(networkConstraints())
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                DELAYED_NAME, ExistingWorkPolicy.REPLACE, request
            )
        }
    }
}

private fun de.pixel.clashreminders.domain.PlannedAlarm.toEntity() = ScheduledAlarmEntity(
    requestCode = reminderId.toInt(),
    reminderId = reminderId,
    clanTag = clanTag,
    type = type,
    fireAtMillis = fireAtMillis,
    eventKey = eventKey,
)
