package de.pixel.clashreminders.scheduling

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import de.pixel.clashreminders.api.ApiResult
import de.pixel.clashreminders.api.CocApiClient
import de.pixel.clashreminders.api.dto.CurrentWarDto
import de.pixel.clashreminders.api.dto.RaidSeasonDto
import de.pixel.clashreminders.api.valueOrNull
import de.pixel.clashreminders.data.db.AppDatabase
import de.pixel.clashreminders.data.db.entity.AccountEntity
import de.pixel.clashreminders.data.db.entity.FiredEventEntity
import de.pixel.clashreminders.data.db.entity.ReminderEntity
import de.pixel.clashreminders.domain.AccountRef
import de.pixel.clashreminders.domain.ClanGamesAnalysis
import de.pixel.clashreminders.domain.ClanGamesCalendar
import de.pixel.clashreminders.domain.CocTime
import de.pixel.clashreminders.domain.CwlAnalysis
import de.pixel.clashreminders.domain.RaidAnalysis
import de.pixel.clashreminders.domain.ReminderContentBuilder
import de.pixel.clashreminders.domain.ReminderType
import de.pixel.clashreminders.domain.WarAnalysis
import de.pixel.clashreminders.notification.NotificationHelper
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Tier 2: an exact alarm fired — fetch fresh data, check what the user's
 * OWN accounts still have open and notify only then. Accounts that already
 * finished are never listed; if everything is done the reminder is skipped
 * silently. Network failures retry with backoff; after the last attempt a
 * degraded notification without the account list is posted (the bot also
 * fires conservatively when the API is unreachable).
 */
class FireReminderWorker(
    appContext: Context,
    params: WorkerParameters,
    private val database: AppDatabase,
    private val api: CocApiClient,
    private val notificationHelper: NotificationHelper,
) : CoroutineWorker(appContext, params) {

    private val contentBuilder = ReminderContentBuilder(applicationContext)

    /** Per-type fetch/validation result. */
    private sealed class Fetch {
        /** Something is still open: notify with this content. */
        data class Notify(val content: ReminderContentBuilder.Content) : Fetch()

        /** Event over or all accounts done: record as fired, no notification. */
        object SkipSilently : Fetch()

        /** Transient failure: retry, then degrade. */
        object Failed : Fetch()
    }

    override suspend fun doWork(): Result {
        val reminderId = inputData.getLong(KEY_REMINDER_ID, -1L)
        val eventKey = inputData.getString(KEY_EVENT_KEY) ?: return Result.success()
        val clanTag = inputData.getString(KEY_CLAN_TAG)
        val reminder = database.reminderDao().getById(reminderId) ?: return Result.success()
        if (!reminder.enabled) return Result.success()
        if (database.firedEventDao().exists(reminderId, eventKey)) {
            Log.d(AlarmScheduler.TAG, "Skip duplicate fire reminder=$reminderId key=$eventKey")
            return Result.success()
        }
        val accounts = database.accountDao().getAll()
        if (accounts.isEmpty()) return Result.success()

        val fetch = when (reminder.type) {
            ReminderType.WAR_START -> fetchWarStart(clanTag, accounts)
            ReminderType.WAR_END -> fetchWarEnd(clanTag, accounts)
            ReminderType.CWL_DAY_END -> fetchCwlDay(clanTag, accounts)
            ReminderType.RAID -> fetchRaid(accounts)
            ReminderType.CLAN_GAMES_END -> fetchClanGames(reminder, accounts)
        }

        when (fetch) {
            is Fetch.Notify -> {
                val channel = if (reminder.type == ReminderType.WAR_START) {
                    NotificationHelper.CHANNEL_WAR_START
                } else {
                    NotificationHelper.CHANNEL_REMINDERS
                }
                notificationHelper.notify(
                    NotificationHelper.notificationId(reminder.id, clanTag),
                    channel,
                    fetch.content,
                )
                Log.d(AlarmScheduler.TAG, "Fired reminder=$reminderId type=${reminder.type} key=$eventKey")
            }
            is Fetch.SkipSilently ->
                Log.d(AlarmScheduler.TAG, "Skip fire reminder=$reminderId key=$eventKey (done or stale)")
            is Fetch.Failed -> {
                if (runAttemptCount < MAX_ATTEMPTS - 1) {
                    Log.w(AlarmScheduler.TAG, "Fire fetch failed, retrying (attempt $runAttemptCount)")
                    return Result.retry()
                }
                val clanName = clanTag?.let { database.trackedClanDao().getByTag(it)?.name ?: it }
                notificationHelper.notify(
                    NotificationHelper.notificationId(reminder.id, clanTag),
                    NotificationHelper.CHANNEL_REMINDERS,
                    contentBuilder.degraded(reminder.type, clanName),
                )
                Log.w(AlarmScheduler.TAG, "Fired degraded reminder=$reminderId key=$eventKey")
            }
        }

        markFired(reminder, eventKey)
        RefreshWorker.enqueueNow(applicationContext)
        return Result.success()
    }

    private suspend fun markFired(reminder: ReminderEntity, eventKey: String) {
        database.firedEventDao().insert(
            FiredEventEntity(
                reminderId = reminder.id,
                eventKey = eventKey,
                firedAt = System.currentTimeMillis(),
            )
        )
        database.scheduledAlarmDao().deleteByEvent(reminder.id, eventKey)
    }

    /** The clan an alarm refers to, falling back to the first account clan (debug fire-now). */
    private suspend fun resolveClan(clanTag: String?, accounts: List<AccountEntity>): ClanTarget? {
        val tag = clanTag ?: accounts.firstNotNullOfOrNull { it.clanTag } ?: return null
        val tracked = database.trackedClanDao().getByTag(tag)
        val name = tracked?.name
            ?: accounts.firstOrNull { it.clanTag == tag }?.clanName
            ?: tag
        return ClanTarget(tag, name, accounts.filter { it.clanTag == tag }.map { AccountRef(it.tag, it.name) })
    }

    private data class ClanTarget(val tag: String, val name: String, val accounts: List<AccountRef>)

    // --- Per-type fetch + content --------------------------------------

    private suspend fun fetchWarStart(clanTag: String?, accounts: List<AccountEntity>): Fetch {
        val clan = resolveClan(clanTag, accounts) ?: return Fetch.SkipSilently
        return when (val result = api.getCurrentWar(clan.tag)) {
            is ApiResult.Success -> {
                val war = result.value
                val state = war.state
                if (state == CurrentWarDto.STATE_PREPARATION || state == CurrentWarDto.STATE_IN_WAR) {
                    val side = WarAnalysis.ourSide(war, clan.tag)
                    val roster = side?.let { WarAnalysis.accountsInRoster(it, clan.accounts) }.orEmpty()
                    if (roster.isEmpty()) {
                        Fetch.SkipSilently
                    } else {
                        Fetch.Notify(contentBuilder.warStart(clan.name, state, roster.map { it.name }))
                    }
                } else {
                    Fetch.SkipSilently
                }
            }
            is ApiResult.HttpError ->
                if (result.code == 404) Fetch.SkipSilently else Fetch.Failed
            is ApiResult.NetworkError -> Fetch.Failed
        }
    }

    private suspend fun fetchWarEnd(clanTag: String?, accounts: List<AccountEntity>): Fetch {
        val clan = resolveClan(clanTag, accounts) ?: return Fetch.SkipSilently
        return when (val result = api.getCurrentWar(clan.tag)) {
            is ApiResult.Success -> {
                val war = result.value
                val active = war.state == CurrentWarDto.STATE_PREPARATION ||
                    war.state == CurrentWarDto.STATE_IN_WAR
                if (!active) {
                    Fetch.SkipSilently
                } else {
                    val side = WarAnalysis.ourSide(war, clan.tag) ?: return Fetch.SkipSilently
                    val open = WarAnalysis.openAccountAttacks(
                        side, WarAnalysis.requiredAttacks(war), clan.accounts
                    )
                    val now = System.currentTimeMillis()
                    val remaining = (CocTime.parseMillisOrNull(war.endTime) ?: now) - now
                    contentBuilder.warEnd(clan.name, remaining, open)
                        ?.let { Fetch.Notify(it) }
                        ?: Fetch.SkipSilently // all own accounts done — stay quiet
                }
            }
            is ApiResult.HttpError ->
                if (result.code == 404) Fetch.SkipSilently else Fetch.Failed
            is ApiResult.NetworkError -> Fetch.Failed
        }
    }

    private suspend fun fetchCwlDay(clanTag: String?, accounts: List<AccountEntity>): Fetch {
        val clan = resolveClan(clanTag, accounts) ?: return Fetch.SkipSilently
        return when (val result = api.getLeagueGroup(clan.tag)) {
            is ApiResult.Success -> {
                val group = result.value
                if (!CwlAnalysis.isGroupActive(group)) {
                    Fetch.SkipSilently
                } else {
                    val war = resolveClosestDayWar(group, clan.tag) ?: return Fetch.SkipSilently
                    val side = WarAnalysis.ourSide(war, clan.tag) ?: return Fetch.SkipSilently
                    val open = WarAnalysis.openAccountAttacks(
                        side, WarAnalysis.CWL_ATTACKS_PER_DAY, clan.accounts
                    )
                    val now = System.currentTimeMillis()
                    val remaining = (CocTime.parseMillisOrNull(war.endTime) ?: now) - now
                    contentBuilder.cwlDay(clan.name, remaining, open)
                        ?.let { Fetch.Notify(it) }
                        ?: Fetch.SkipSilently
                }
            }
            is ApiResult.HttpError ->
                if (result.code == 404) Fetch.SkipSilently else Fetch.Failed
            is ApiResult.NetworkError -> Fetch.Failed
        }
    }

    /**
     * Our CWL war whose end time is closest to now (inWar preferred, then
     * just-ended) — mirrors the bot's "closest end time to fire target"
     * selection so a reminder firing right after day end still reports the
     * correct round.
     */
    private suspend fun resolveClosestDayWar(
        group: de.pixel.clashreminders.api.dto.LeagueGroupDto,
        clanTag: String,
    ): CurrentWarDto? {
        val now = System.currentTimeMillis()
        var best: CurrentWarDto? = null
        var bestDistance = Long.MAX_VALUE
        for (round in group.rounds) {
            for (warTag in round.warTags) {
                if (!CwlAnalysis.isRealWarTag(warTag)) continue
                val war = api.getCwlWar(warTag).valueOrNull() ?: continue
                if (!WarAnalysis.isOurWar(war, clanTag)) continue
                if (war.state != CurrentWarDto.STATE_IN_WAR &&
                    war.state != CurrentWarDto.STATE_WAR_ENDED
                ) continue
                val end = CocTime.parseMillisOrNull(war.endTime) ?: continue
                val distance = abs(end - now)
                if (distance < bestDistance) {
                    best = war
                    bestDistance = distance
                }
            }
        }
        return best
    }

    /**
     * One raid reminder covers every clan the accounts are in: each clan's
     * raid is fetched and only the user's accounts with attacks left are
     * listed, grouped per clan.
     */
    private suspend fun fetchRaid(accounts: List<AccountEntity>): Fetch {
        val byClan = accounts
            .filter { it.clanTag != null }
            .groupBy { it.clanTag!! }
        if (byClan.isEmpty()) return Fetch.SkipSilently

        val perClan = mutableListOf<ReminderContentBuilder.ClanRaidOpen>()
        var anyOngoing = false
        var anyFailure = false
        var remaining = 0L
        val now = System.currentTimeMillis()

        for ((clanTag, clanAccounts) in byClan) {
            val raid = when (val result = api.getRaidSeasons(clanTag)) {
                is ApiResult.Success -> result.value.items.firstOrNull()
                is ApiResult.HttpError -> {
                    if (result.code != 404) anyFailure = true
                    continue
                }
                is ApiResult.NetworkError -> {
                    anyFailure = true
                    continue
                }
            }
            if (raid == null || raid.state != RaidSeasonDto.STATE_ONGOING) continue
            anyOngoing = true
            remaining = maxOf(remaining, (CocTime.parseMillisOrNull(raid.endTime) ?: now) - now)
            val refs = clanAccounts.map { AccountRef(it.tag, it.name) }
            val open = RaidAnalysis.accountStatuses(refs, raid).filter { it.open }
            if (open.isNotEmpty()) {
                val clanName = clanAccounts.first().clanName ?: clanTag
                perClan += ReminderContentBuilder.ClanRaidOpen(clanName, open)
            }
        }

        val content = contentBuilder.raid(remaining, perClan)
        return when {
            content != null -> Fetch.Notify(content)
            anyFailure && !anyOngoing -> Fetch.Failed
            else -> Fetch.SkipSilently // raid over everywhere or all accounts done
        }
    }

    private suspend fun fetchClanGames(reminder: ReminderEntity, accounts: List<AccountEntity>): Fetch {
        val now = System.currentTimeMillis()
        val window = ClanGamesCalendar.currentWindow(now) ?: return Fetch.SkipSilently

        var allFailed = true
        val current = accounts.map { account ->
            val points = api.getPlayer(account.tag).valueOrNull()?.clanGamesPoints()
            if (points != null) allFailed = false
            ClanGamesAnalysis.MemberProgress(
                tag = account.tag,
                name = account.name,
                points = points,
            )
        }
        if (allFailed) return Fetch.Failed

        val baseline = database.snapshotDao()
            .getForWindow(window.windowKey)
            .associate { it.playerTag to it.points }
        val threshold = reminder.cgThreshold ?: ClanGamesAnalysis.DEFAULT_THRESHOLD
        val below = ClanGamesAnalysis.belowThreshold(current, baseline, threshold)
        val content = contentBuilder.clanGames(window.endUtcMillis - now, below, threshold)
            ?: return Fetch.SkipSilently // every account is done — no notification
        return Fetch.Notify(content)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(
            NotificationHelper.WORK_NOTIFICATION_ID,
            notificationHelper.workInProgressNotification(),
        )

    companion object {
        const val KEY_REMINDER_ID = "reminderId"
        const val KEY_EVENT_KEY = "eventKey"
        const val KEY_CLAN_TAG = "clanTag"
        private const val MAX_ATTEMPTS = 3

        fun enqueue(context: Context, reminderId: Long, eventKey: String, clanTag: String? = null) {
            val request = OneTimeWorkRequestBuilder<FireReminderWorker>()
                .setInputData(
                    workDataOf(
                        KEY_REMINDER_ID to reminderId,
                        KEY_EVENT_KEY to eventKey,
                        KEY_CLAN_TAG to clanTag,
                    )
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "fire_${reminderId}_$eventKey", ExistingWorkPolicy.KEEP, request
            )
        }
    }
}
