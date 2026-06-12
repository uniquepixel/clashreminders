package de.pixel.clashreminders.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.pixel.clashreminders.ClashRemindersApp
import de.pixel.clashreminders.data.repository.AccountStatus
import de.pixel.clashreminders.scheduling.RefreshWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
                app.accountStatusLoader.load { loaded ->
                    _statuses.update { it + (loaded.account.tag to loaded.status) }
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
