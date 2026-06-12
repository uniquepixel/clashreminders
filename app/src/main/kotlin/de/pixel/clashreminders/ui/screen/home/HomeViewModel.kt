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
    /** preparation/inWar when the account is in the current war lineup. */
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
                        account.copy(
                            name = player.name ?: account.name,
                            townHallLevel = player.townHallLevel ?: account.townHallLevel,
                            clanTag = player.clan?.tag,
                            clanName = player.clan?.name,
                            clanBadgeUrl = player.clan?.badgeUrls?.medium
                                ?: player.clan?.badgeUrls?.small,
                        ).also { if (it != account) app.database.accountDao().upsert(it) }
                    } else {
                        account
                    }

                    var status = AccountStatus()
                    val clanTag = fresh.clanTag
                    if (clanTag != null) {
                        val war = warCache.getOrPut(clanTag) {
                            app.apiClient.getCurrentWar(clanTag).valueOrNull()
                        }
                        val warActive = war?.state == CurrentWarDto.STATE_PREPARATION ||
                            war?.state == CurrentWarDto.STATE_IN_WAR
                        if (war != null && warActive) {
                            val side = WarAnalysis.ourSide(war, clanTag)
                            val member = side?.members?.firstOrNull { it.tag == fresh.tag }
                            if (member != null) {
                                status = status.copy(
                                    warState = war.state,
                                    warAttacksDone = member.attacks.size,
                                    warAttacksRequired = WarAnalysis.requiredAttacks(war),
                                )
                            }
                        }
                        if (raidActive) {
                            val raid = raidCache.getOrPut(clanTag) {
                                app.apiClient.getRaidSeasons(clanTag).valueOrNull()
                                    ?.items?.firstOrNull()
                                    ?.takeIf { it.state == RaidSeasonDto.STATE_ONGOING }
                            }
                            if (raid != null) {
                                val raidStatus = RaidAnalysis
                                    .accountStatuses(listOf(AccountRef(fresh.tag, fresh.name)), raid)
                                    .first()
                                status = status.copy(
                                    raidAttacks = raidStatus.attacks,
                                    raidLimit = raidStatus.limit,
                                )
                            }
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
