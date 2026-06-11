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
import de.pixel.clashreminders.api.valueOrNull
import de.pixel.clashreminders.data.db.AppDatabase
import de.pixel.clashreminders.data.db.entity.ClanEntity
import de.pixel.clashreminders.data.db.entity.FiredEventEntity
import de.pixel.clashreminders.data.db.entity.ReminderEntity
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
 * Tier 2: an exact alarm fired — fetch fresh data, validate the event is
 * still live (port of Bot.shouldEventFire) and post the notification.
 * Network failures retry with backoff; after the last attempt a degraded
 * notification without the member list is posted (the bot also fires
 * conservatively when the API is unreachable).
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
        /** Event is live: notify with this content. */
        data class Notify(val content: ReminderContentBuilder.Content) : Fetch()

        /** Event no longer live (war over, raid ended …): record as fired, no notification. */
        object SkipSilently : Fetch()

        /** Transient failure: retry, then degrade. */
        object Failed : Fetch()
    }

    override suspend fun doWork(): Result {
        val reminderId = inputData.getLong(KEY_REMINDER_ID, -1L)
        val eventKey = inputData.getString(KEY_EVENT_KEY) ?: return Result.success()
        val reminder = database.reminderDao().getById(reminderId) ?: return Result.success()
        if (!reminder.enabled) return Result.success()
        if (database.firedEventDao().exists(reminderId, eventKey)) {
            Log.d(AlarmScheduler.TAG, "Skip duplicate fire reminder=$reminderId key=$eventKey")
            return Result.success()
        }
        val clan = database.clanDao().getByTag(reminder.clanTag) ?: return Result.success()

        val fetch = when (reminder.type) {
            ReminderType.WAR_START -> fetchWarStart(clan)
            ReminderType.WAR_END -> fetchWarEnd(clan)
            ReminderType.CWL_DAY_END -> fetchCwlDay(clan)
            ReminderType.RAID -> fetchRaid(clan)
            ReminderType.CLAN_GAMES_END -> fetchClanGames(clan, reminder)
        }

        when (fetch) {
            is Fetch.Notify -> {
                val channel = if (reminder.type == ReminderType.WAR_START) {
                    NotificationHelper.CHANNEL_WAR_START
                } else {
                    NotificationHelper.CHANNEL_REMINDERS
                }
                notificationHelper.notify(reminder.id.toInt(), channel, fetch.content, clan.tag)
                Log.d(AlarmScheduler.TAG, "Fired reminder=$reminderId type=${reminder.type} key=$eventKey")
            }
            is Fetch.SkipSilently ->
                Log.d(AlarmScheduler.TAG, "Skip stale fire reminder=$reminderId key=$eventKey")
            is Fetch.Failed -> {
                if (runAttemptCount < MAX_ATTEMPTS - 1) {
                    Log.w(AlarmScheduler.TAG, "Fire fetch failed, retrying (attempt $runAttemptCount)")
                    return Result.retry()
                }
                notificationHelper.notify(
                    reminder.id.toInt(),
                    NotificationHelper.CHANNEL_REMINDERS,
                    contentBuilder.degraded(reminder.type, clan.name),
                    clan.tag,
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
        database.scheduledAlarmDao().delete(reminder.id.toInt())
    }

    // --- Per-type fetch + content --------------------------------------

    private suspend fun fetchWarStart(clan: ClanEntity): Fetch =
        when (val result = api.getCurrentWar(clan.tag)) {
            is ApiResult.Success -> {
                val state = result.value.state
                if (state == CurrentWarDto.STATE_PREPARATION || state == CurrentWarDto.STATE_IN_WAR) {
                    Fetch.Notify(contentBuilder.warStart(clan.name, state))
                } else {
                    Fetch.SkipSilently
                }
            }
            is ApiResult.HttpError ->
                if (result.code == 404) Fetch.SkipSilently else Fetch.Failed
            is ApiResult.NetworkError -> Fetch.Failed
        }

    private suspend fun fetchWarEnd(clan: ClanEntity): Fetch =
        when (val result = api.getCurrentWar(clan.tag)) {
            is ApiResult.Success -> {
                val war = result.value
                val active = war.state == CurrentWarDto.STATE_PREPARATION ||
                    war.state == CurrentWarDto.STATE_IN_WAR
                if (!active) {
                    Fetch.SkipSilently
                } else {
                    val side = WarAnalysis.ourSide(war, clan.tag) ?: war.clan
                    val open = side?.let {
                        WarAnalysis.openAttackers(it, WarAnalysis.requiredAttacks(war))
                    } ?: emptyList()
                    val now = System.currentTimeMillis()
                    val remaining = (CocTime.parseMillisOrNull(war.endTime) ?: now) - now
                    Fetch.Notify(contentBuilder.warEnd(clan.name, remaining, open))
                }
            }
            is ApiResult.HttpError ->
                if (result.code == 404) Fetch.SkipSilently else Fetch.Failed
            is ApiResult.NetworkError -> Fetch.Failed
        }

    private suspend fun fetchCwlDay(clan: ClanEntity): Fetch =
        when (val result = api.getLeagueGroup(clan.tag)) {
            is ApiResult.Success -> {
                val group = result.value
                if (!CwlAnalysis.isGroupActive(group)) {
                    Fetch.SkipSilently
                } else {
                    val war = resolveClosestDayWar(group, clan.tag) ?: return Fetch.SkipSilently
                    val side = WarAnalysis.ourSide(war, clan.tag) ?: return Fetch.SkipSilently
                    val open = WarAnalysis.openAttackers(side, WarAnalysis.CWL_ATTACKS_PER_DAY)
                    val now = System.currentTimeMillis()
                    val remaining = (CocTime.parseMillisOrNull(war.endTime) ?: now) - now
                    Fetch.Notify(contentBuilder.cwlDay(clan.name, remaining, open))
                }
            }
            is ApiResult.HttpError ->
                if (result.code == 404) Fetch.SkipSilently else Fetch.Failed
            is ApiResult.NetworkError -> Fetch.Failed
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

    private suspend fun fetchRaid(clan: ClanEntity): Fetch {
        val raid = when (val result = api.getRaidSeasons(clan.tag)) {
            is ApiResult.Success -> result.value.items.firstOrNull()
            is ApiResult.HttpError ->
                return if (result.code == 404) Fetch.SkipSilently else Fetch.Failed
            is ApiResult.NetworkError -> return Fetch.Failed
        }
        if (raid == null || raid.state != de.pixel.clashreminders.api.dto.RaidSeasonDto.STATE_ONGOING) {
            return Fetch.SkipSilently
        }
        val members = when (val result = api.getClan(clan.tag)) {
            is ApiResult.Success -> result.value.memberList
            is ApiResult.HttpError -> return Fetch.Failed
            is ApiResult.NetworkError -> return Fetch.Failed
        }
        val analysis = RaidAnalysis.analyze(members, raid)
        val now = System.currentTimeMillis()
        val remaining = (CocTime.parseMillisOrNull(raid.endTime) ?: now) - now
        return Fetch.Notify(contentBuilder.raid(clan.name, remaining, analysis))
    }

    private suspend fun fetchClanGames(clan: ClanEntity, reminder: ReminderEntity): Fetch {
        val now = System.currentTimeMillis()
        val window = ClanGamesCalendar.currentWindow(now) ?: return Fetch.SkipSilently
        val members = when (val result = api.getClan(clan.tag)) {
            is ApiResult.Success -> result.value.memberList
            is ApiResult.HttpError ->
                return if (result.code == 404) Fetch.SkipSilently else Fetch.Failed
            is ApiResult.NetworkError -> return Fetch.Failed
        }
        val baseline = database.snapshotDao()
            .getForClanWindow(clan.tag, window.windowKey)
            .associate { it.playerTag to it.points }
        val current = members.map { member ->
            ClanGamesAnalysis.MemberProgress(
                tag = member.tag,
                name = member.name,
                points = api.getPlayer(member.tag).valueOrNull()?.clanGamesPoints(),
            )
        }
        val threshold = reminder.cgThreshold ?: ClanGamesAnalysis.DEFAULT_THRESHOLD
        val below = ClanGamesAnalysis.belowThreshold(current, baseline, threshold)
        val content = contentBuilder.clanGames(clan.name, window.endUtcMillis - now, below)
            ?: return Fetch.SkipSilently // everyone is done — no notification
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
        private const val MAX_ATTEMPTS = 3

        fun enqueue(context: Context, reminderId: Long, eventKey: String) {
            val request = OneTimeWorkRequestBuilder<FireReminderWorker>()
                .setInputData(
                    workDataOf(KEY_REMINDER_ID to reminderId, KEY_EVENT_KEY to eventKey)
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
