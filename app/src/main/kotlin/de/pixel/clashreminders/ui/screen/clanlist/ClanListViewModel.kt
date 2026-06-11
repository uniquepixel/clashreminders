package de.pixel.clashreminders.ui.screen.clanlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.pixel.clashreminders.ClashRemindersApp
import de.pixel.clashreminders.api.dto.CurrentWarDto
import de.pixel.clashreminders.api.valueOrNull
import de.pixel.clashreminders.scheduling.RefreshWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Per-clan war status line, loaded lazily once per app session. */
sealed class ClanStatus {
    object Loading : ClanStatus()
    object NoWar : ClanStatus()
    data class War(val state: String) : ClanStatus()
}

class ClanListViewModel(private val app: ClashRemindersApp) : ViewModel() {

    val clans = app.clanRepository.observeClans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reminders = app.clanRepository.observeAllReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _statuses = MutableStateFlow<Map<String, ClanStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, ClanStatus>> = _statuses.asStateFlow()

    fun loadStatus(clanTag: String) {
        if (_statuses.value.containsKey(clanTag)) return
        _statuses.update { it + (clanTag to ClanStatus.Loading) }
        viewModelScope.launch {
            val war = app.apiClient.getCurrentWar(clanTag).valueOrNull()
            val status = when (war?.state) {
                CurrentWarDto.STATE_PREPARATION,
                CurrentWarDto.STATE_IN_WAR,
                CurrentWarDto.STATE_WAR_ENDED -> ClanStatus.War(war.state!!)
                else -> ClanStatus.NoWar
            }
            _statuses.update { it + (clanTag to status) }
        }
    }

    fun refresh() {
        _statuses.value = emptyMap()
        RefreshWorker.enqueueNow(app)
    }

    companion object {
        fun factory(app: ClashRemindersApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { ClanListViewModel(app) }
        }
    }
}
