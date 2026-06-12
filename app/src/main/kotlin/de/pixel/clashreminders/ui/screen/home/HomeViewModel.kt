package de.pixel.clashreminders.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.pixel.clashreminders.ClashRemindersApp
import de.pixel.clashreminders.api.dto.CurrentWarDto
import de.pixel.clashreminders.api.dto.RaidSeasonDto
import de.pixel.clashreminders.api.valueOrNull
import de.pixel.clashreminders.data.db.entity.ClanSightingEntity
import de.pixel.clashreminders.data.repository.AccountSync
import de.pixel.clashreminders.domain.AccountRef
import de.pixel.clashreminders.domain.ClanGamesCalendar
import de.pixel.clashreminders.domain.RaidAnalysis
import de.pixel.clashreminders.domain.RaidWeekend
import de.pixel.clashreminders.domain.WarAnalysis
import de.pixel.clashreminders.scheduling.RefreshWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Live to-do status of one account, shown as chips on its card. */
data class AccountStatus(
    /** preparation/inWar when the account is in a tracked clan's war lineup. */
    val warState: String? = null,
    val warAttacksDone: Int = 0,
    val warAttacksRequired: Int = 0,
    /** Raid attacks used/limit, only set during the raid weekend. */
    val raidAttacks: Int? = null,
    val raidLimit: Int? = null,
    /** Clan games points earned this window; null = no baseline yet. */
    val cgPoints: Int? = null,
    val cgActive: Boolean = false,
)

class HomeViewModel(private val app: ClashRemindersApp) : ViewModel() {

    val accounts = app.accountRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _statuses = MutableStateFlow<Map<String, AccountStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, AccountStatus>> = _statuses.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var loadedOnce = false

    fun loadStatuses(force: Boolean = false) {
        if (_loading.value || (loadedOnce && !force)) return
        loadedOnce = true
        _loading.value = true
        if (force) _statuses.value = emptyMap()
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val warCache = mutableMapOf<String, CurrentWarDto?>()
                val raidCache = mutableMapOf<String, RaidSeasonDto?>()
                val cgWindow = ClanGamesCalendar.currentWindow(now)
                val raidActive = RaidWeekend.isInWindow(now)

                for (account in app.database.accountDao().getAll()) {
                    val player = app.apiClient.getPlayer(account.tag).valueOrNull()
                    val fresh = if (player != null) {
                        AccountSync.applyPlayer(app.database, account, player, now)
                    } else {
                        account
                    }
                    val ref = AccountRef(fresh.tag, fresh.name)

                    // current clan first, then other clans seen in the last week —
                    // the account can be in another clan's war lineup (hopping)
                    val sightingTags = app.database.clanSightingDao()
                        .getForAccount(fresh.tag)
                        .filter { it.lastSeenAt >= now - ClanSightingEntity.RETENTION_MILLIS }
                        .map { it.clanTag }
                    val candidateClans = (listOfNotNull(fresh.clanTag) + sightingTags).distinct()

                    var status = AccountStatus()
                    for (clanTag in candidateClans) {
                        val war = warCache.getOrPut(clanTag) {
                            app.apiClient.getCurrentWar(clanTag).valueOrNull()
                        } ?: continue
                        val warActive = war.state == CurrentWarDto.STATE_PREPARATION ||
                            war.state == CurrentWarDto.STATE_IN_WAR
                        if (!warActive) continue
                        val side = WarAnalysis.ourSide(war, clanTag)
                        val member = side?.members?.firstOrNull { it.tag == fresh.tag } ?: continue
                        status = status.copy(
                            warState = war.state,
                            warAttacksDone = member.attacks.size,
                            warAttacksRequired = WarAnalysis.requiredAttacks(war),
                        )
                        break
                    }

                    if (raidActive) {
                        for (clanTag in candidateClans) {
                            val raid = raidCache.getOrPut(clanTag) {
                                app.apiClient.getRaidSeasons(clanTag).valueOrNull()
                                    ?.items?.firstOrNull()
                                    ?.takeIf { it.state == RaidSeasonDto.STATE_ONGOING }
                            } ?: continue
                            val raidStatus = if (clanTag == fresh.clanTag) {
                                RaidAnalysis.accountStatuses(listOf(ref), raid).first()
                            } else {
                                // other clans only matter when the account joined
                                // their raid and still has attacks open
                                RaidAnalysis.participantStatuses(listOf(ref), raid)
                                    .firstOrNull { it.open }
                            } ?: continue
                            status = status.copy(
                                raidAttacks = raidStatus.attacks,
                                raidLimit = raidStatus.limit,
                            )
                            break
                        }
                    }

                    if (cgWindow != null && player != null) {
                        val baseline = app.database.snapshotDao()
                            .getForPlayerWindow(fresh.tag, cgWindow.windowKey)?.points
                        val points = player.clanGamesPoints()
                        status = status.copy(
                            cgActive = true,
                            cgPoints = if (baseline != null && points != null) points - baseline else null,
                        )
                    }
                    _statuses.update { it + (fresh.tag to status) }
                }
            } finally {
                _loading.value = false
            }
        }
    }

    fun refresh() {
        RefreshWorker.enqueueNow(app)
        loadStatuses(force = true)
    }

    fun deleteAccount(tag: String) {
        viewModelScope.launch {
            app.accountRepository.deleteAccount(tag)
            RefreshWorker.enqueueNow(app)
        }
    }

    companion object {
        fun factory(app: ClashRemindersApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(app) }
        }
    }
}
