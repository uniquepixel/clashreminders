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
import de.pixel.clashreminders.data.db.entity.AccountEntity
import de.pixel.clashreminders.data.db.entity.FiredEventEntity
import de.pixel.clashreminders.data.db.entity.ReminderEntity
import de.pixel.clashreminders.data.db.entity.ScheduledAlarmEntity
import de.pixel.clashreminders.data.db.entity.TrackedClanEntity
import de.pixel.clashreminders.data.repository.SettingsRepository
import de.pixel.clashreminders.domain.AccountRef
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
 * Tier 1 of the background strategy, account-based: refreshes the user's
 * accounts (name, clan membership), derives the set of clans to track from
 * them, fires war-start notifications on state transitions when an account
 * is in the lineup, and (re)arms exact alarms for everything else. War and
 * CWL alarms are armed per clan with a running war; raid and clan games
 * alarms are global and check all accounts when they fire.
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

    private val accountDao = database.accountDao()
    private val trackedClanDao = database.trackedClanDao()
    private val reminderDao = database.reminderDao()
    private val firedEventDao = database.firedEventDao()
    private val snapshotDao = database.snapshotDao()
    private val scheduledAlarmDao = database.scheduledAlarmDao()

    /** Alarm wish without a request code — reconcile() assigns stable codes. */
    private data class DesiredAlarm(
        val reminderId: Long,
        val clanTag: String?,
        val type: ReminderType,
        val fireAtMillis: Long,
        val eventKey: String,
    )

    override suspend fun doWork(): Result {
        if (settings.apiKeyOnce() == null) {
            Log.d(AlarmScheduler.TAG, "Refresh skipped: no API key configured")
            return Result.success()
        }
        val now = System.currentTimeMillis()
        val accounts = refreshAccounts()
        val clans = syncTrackedClans(accounts)
        val reminders = reminderDao.getAllEnabled()
        val existing = scheduledAlarmDao.getAll()
        val desired = mutableListOf<DesiredAlarm>()

        if (accounts.isNotEmpty()) {
            for (clan in clans) {
                val accountsInClan = accounts
                    .filter { it.clanTag == clan.tag }
                    .map { AccountRef(it.tag, it.name) }
                planWar(clan, accountsInClan, reminders, existing, desired, now)
                planCwl(clan, accountsInClan, reminders, existing, desired, now)
            }
            planRaid(reminders, desired, now)
            if (planClanGames(accounts, reminders, desired, now)) {
                planSnapshotAlarm(desired, now)
            }
        }

        reconcile(existing, desired)

        firedEventDao.deleteOlderThan(now - FIRED_EVENT_RETENTION_MILLIS)
        snapshotDao.deleteOlderThan(now - SNAPSHOT_RETENTION_MILLIS)
        settings.setLastRefreshAt(now)
        Log.d(AlarmScheduler.TAG, "Refresh done: ${desired.size} alarms scheduled")
        return Result.success()
    }

    // --- Accounts & derived clans ----------------------------------------

    /** Re-resolves every account; keeps stored data when the fetch fails. */
    private suspend fun refreshAccounts(): List<AccountEntity> {
        val refreshed = mutableListOf<AccountEntity>()
        for (account in accountDao.getAll()) {
            val player = api.getPlayer(account.tag).valueOrNull()
            if (player == null) {
                refreshed += account
                continue
            }
            val updated = account.copy(
                name = player.name ?: account.name,
                townHallLevel = player.townHallLevel ?: account.townHallLevel,
                clanTag = player.clan?.tag,
                clanName = player.clan?.name,
                clanBadgeUrl = player.clan?.badgeUrls?.medium
                    ?: player.clan?.badgeUrls?.small,
            )
            if (updated != account) accountDao.upsert(updated)
            refreshed += updated
        }
        return refreshed
    }

    /**
     * The tracked clan set follows the accounts automatically: a clan is
     * checked while at least one account is in it. Existing rows keep their
     * lastWarState memory.
     */
    private suspend fun syncTrackedClans(accounts: List<AccountEntity>): List<TrackedClanEntity> {
        val current = accounts
            .filter { it.clanTag != null }
            .groupBy { it.clanTag!! }
        if (current.isEmpty()) {
            trackedClanDao.deleteAll()
            return emptyList()
        }
        for ((tag, members) in current) {
            val sample = members.first()
            trackedClanDao.insertIfAbsent(
                TrackedClanEntity(tag = tag, name = sample.clanName ?: tag, badgeUrl = sample.clanBadgeUrl)
            )
            trackedClanDao.updateInfo(tag, sample.clanName ?: tag, sample.clanBadgeUrl)
        }
        trackedClanDao.deleteAllExcept(current.keys.toList())
        return trackedClanDao.getAll()
    }

    // --- Clan War ------------------------------------------------------

    private suspend fun planWar(
        clan: TrackedClanEntity,
        accountsInClan: List<AccountRef>,
        reminders: List<ReminderEntity>,
        existing: List<ScheduledAlarmEntity>,
        desired: MutableList<DesiredAlarm>,
        now: Long,
    ) {
        val warEndReminders = reminders.filter { it.type == ReminderType.WAR_END }
        val warStartReminders = reminders.filter { it.type == ReminderType.WAR_START }
        if (warEndReminders.isEmpty() && warStartReminders.isEmpty()) return

        when (val result = api.getCurrentWar(clan.tag)) {
            is ApiResult.Success -> {
                val war = result.value
                handleWarStartTransition(clan, war, warStartReminders, accountsInClan)
                trackedClanDao.updateLastWarState(clan.tag, war.state ?: CurrentWarDto.STATE_NOT_IN_WAR)

                val endMillis = CocTime.parseMillisOrNull(war.endTime)
                val warActive = war.state == CurrentWarDto.STATE_PREPARATION ||
                    war.state == CurrentWarDto.STATE_IN_WAR
                if (warActive && endMillis != null && anyAccountInLineup(war, clan.tag, accountsInClan)) {
                    val eventKey = "war-${clan.tag}-$endMillis"
                    warEndReminders.forEach {
                        planOutcome(it, endMillis, eventKey, now, clan.tag, desired)
                    }
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
                    trackedClanDao.updateLastWarState(clan.tag, CurrentWarDto.STATE_NOT_IN_WAR)
                } else {
                    preserveExisting(existing, warEndReminders, clan.tag, desired)
                }
            }
            is ApiResult.NetworkError -> preserveExisting(existing, warEndReminders, clan.tag, desired)
        }
    }

    private fun anyAccountInLineup(
        war: CurrentWarDto,
        clanTag: String,
        accounts: List<AccountRef>,
    ): Boolean {
        val side = WarAnalysis.ourSide(war, clanTag) ?: return false
        return WarAnalysis.accountsInRoster(side, accounts).isNotEmpty()
    }

    private suspend fun handleWarStartTransition(
        clan: TrackedClanEntity,
        war: CurrentWarDto,
        warStartReminders: List<ReminderEntity>,
        accountsInClan: List<AccountRef>,
    ) {
        if (!EventPlanner.isWarStartTransition(clan.lastWarState, war.state)) return
        val side = WarAnalysis.ourSide(war, clan.tag) ?: return
        // user-based: a war the user's accounts don't play in is not worth a ping
        val rosterAccounts = WarAnalysis.accountsInRoster(side, accountsInClan)
        if (rosterAccounts.isEmpty()) return
        val eventKey = "warstart-${clan.tag}-" + (war.endTime ?: war.startTime ?: "unknown")
        val contentBuilder = ReminderContentBuilder(applicationContext)
        for (reminder in warStartReminders) {
            if (firedEventDao.exists(reminder.id, eventKey)) continue
            notificationHelper.notify(
                NotificationHelper.notificationId(reminder.id, clan.tag),
                NotificationHelper.CHANNEL_WAR_START,
                contentBuilder.warStart(clan.name, war.state, rosterAccounts.map { it.name }),
            )
            firedEventDao.insert(
                FiredEventEntity(reminderId = reminder.id, eventKey = eventKey, firedAt = System.currentTimeMillis())
            )
            Log.d(AlarmScheduler.TAG, "War start fired for ${clan.tag} (${war.state})")
        }
    }

    // --- CWL -----------------------------------------------------------

    private suspend fun planCwl(
        clan: TrackedClanEntity,
        accountsInClan: List<AccountRef>,
        reminders: List<ReminderEntity>,
        existing: List<ScheduledAlarmEntity>,
        desired: MutableList<DesiredAlarm>,
        now: Long,
    ) {
        val cwlReminders = reminders.filter { it.type == ReminderType.CWL_DAY_END }
        if (cwlReminders.isEmpty() || accountsInClan.isEmpty()) return

        when (val result = api.getLeagueGroup(clan.tag)) {
            is ApiResult.Success -> {
                val group = result.value
                if (!CwlAnalysis.isGroupActive(group)) return
                val dayWar = resolveCurrentDayWar(group, clan.tag) ?: return
                if (!anyAccountInLineup(dayWar, clan.tag, accountsInClan)) return
                val endMillis = CocTime.parseMillisOrNull(dayWar.endTime) ?: return
                val eventKey = "cwl-${clan.tag}-$endMillis"
                cwlReminders.forEach { planOutcome(it, endMillis, eventKey, now, clan.tag, desired) }
            }
            is ApiResult.HttpError -> {
                if (result.code != 404) preserveExisting(existing, cwlReminders, clan.tag, desired)
                // 404 = no league group, nothing to schedule
            }
            is ApiResult.NetworkError -> preserveExisting(existing, cwlReminders, clan.tag, desired)
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
        reminders: List<ReminderEntity>,
        desired: MutableList<DesiredAlarm>,
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
            desired += DesiredAlarm(
                reminderId = reminder.id,
                clanTag = null,
                type = ReminderType.RAID,
                fireAtMillis = fireAt,
                eventKey = eventKey,
            )
        }
    }

    // --- Clan games (deterministic window) ------------------------------

    private suspend fun planClanGames(
        accounts: List<AccountEntity>,
        reminders: List<ReminderEntity>,
        desired: MutableList<DesiredAlarm>,
        now: Long,
    ): Boolean {
        val cgReminders = reminders.filter { it.type == ReminderType.CLAN_GAMES_END }
        if (cgReminders.isEmpty()) return false

        val window = ClanGamesCalendar.currentOrNextWindow(now)
        val eventKey = "cg-${window.windowKey}"
        cgReminders.forEach { planOutcome(it, window.endUtcMillis, eventKey, now, null, desired) }

        // Catch-up: snapshot missed (app was off on the 22nd) but window is running
        val current = ClanGamesCalendar.currentWindow(now)
        if (current != null && accounts.any { !snapshotDao.hasSnapshot(it.tag, current.windowKey) }) {
            Log.d(AlarmScheduler.TAG, "CG snapshot missing for at least one account, catching up")
            ClanGamesSnapshotWorker.enqueue(applicationContext)
        }
        return true
    }

    private fun planSnapshotAlarm(desired: MutableList<DesiredAlarm>, now: Long) {
        val window = ClanGamesCalendar.currentOrNextWindow(now)
        val snapshotAt = window.startUtcMillis + 5 * 60_000L
        if (snapshotAt > now) {
            desired += DesiredAlarm(
                reminderId = ScheduledAlarmEntity.SNAPSHOT_REMINDER_ID,
                clanTag = null,
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
        clanTag: String?,
        desired: MutableList<DesiredAlarm>,
    ) {
        if (firedEventDao.exists(reminder.id, eventKey)) return
        when (val outcome = EventPlanner.planOffsetReminder(reminder, eventEndMillis, eventKey, now, clanTag)) {
            is EventPlanner.Outcome.Schedule -> desired += DesiredAlarm(
                reminderId = outcome.alarm.reminderId,
                clanTag = outcome.alarm.clanTag,
                type = outcome.alarm.type,
                fireAtMillis = outcome.alarm.fireAtMillis,
                eventKey = outcome.alarm.eventKey,
            )
            is EventPlanner.Outcome.FireNow ->
                FireReminderWorker.enqueue(applicationContext, reminder.id, eventKey, clanTag)
            is EventPlanner.Outcome.MarkFiredSilently ->
                firedEventDao.insert(
                    FiredEventEntity(reminderId = reminder.id, eventKey = eventKey, firedAt = now)
                )
        }
    }

    /** On fetch failure, keep previously armed alarms of this clan instead of cancelling them. */
    private fun preserveExisting(
        existing: List<ScheduledAlarmEntity>,
        reminders: List<ReminderEntity>,
        clanTag: String,
        desired: MutableList<DesiredAlarm>,
    ) {
        val ids = reminders.map { it.id }.toSet()
        desired += existing
            .filter { it.reminderId in ids && it.clanTag == clanTag }
            .map { DesiredAlarm(it.reminderId, it.clanTag, it.type, it.fireAtMillis, it.eventKey) }
    }

    /**
     * Cancels alarms that are no longer wanted and (re)arms the desired
     * ones. Request codes stay stable per (reminderId, eventKey) so an
     * existing PendingIntent is updated instead of duplicated.
     */
    private suspend fun reconcile(
        existing: List<ScheduledAlarmEntity>,
        desired: List<DesiredAlarm>,
    ) {
        val desiredKeys = desired.map { it.reminderId to it.eventKey }.toSet()
        for (old in existing) {
            if ((old.reminderId to old.eventKey) !in desiredKeys) {
                alarmScheduler.cancel(old)
                scheduledAlarmDao.delete(old.requestCode)
            }
        }
        val codeByKey = existing.associate { (it.reminderId to it.eventKey) to it.requestCode }
        var nextCode = (existing.maxOfOrNull { it.requestCode } ?: 0).coerceAtLeast(0) + 1
        for (wish in desired) {
            val requestCode = when {
                wish.reminderId == ScheduledAlarmEntity.SNAPSHOT_REMINDER_ID ->
                    ScheduledAlarmEntity.SNAPSHOT_REQUEST_CODE
                else -> codeByKey[wish.reminderId to wish.eventKey] ?: nextCode++
            }
            val alarm = ScheduledAlarmEntity(
                requestCode = requestCode,
                reminderId = wish.reminderId,
                clanTag = wish.clanTag,
                type = wish.type,
                fireAtMillis = wish.fireAtMillis,
                eventKey = wish.eventKey,
            )
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
