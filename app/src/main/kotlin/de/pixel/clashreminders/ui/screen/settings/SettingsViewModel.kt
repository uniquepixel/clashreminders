package de.pixel.clashreminders.ui.screen.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.pixel.clashreminders.ClashRemindersApp
import de.pixel.clashreminders.R
import de.pixel.clashreminders.api.ApiResult
import de.pixel.clashreminders.scheduling.RefreshWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class TestState {
    object Idle : TestState()
    object Loading : TestState()
    data class Done(val messageRes: Int, val success: Boolean) : TestState()
}

class SettingsViewModel(private val app: ClashRemindersApp) : ViewModel() {

    val savedApiKey = app.settingsRepository.apiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val lastRefreshAt = app.settingsRepository.lastRefreshAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var keyInput by mutableStateOf("")

    var keySaved by mutableStateOf(false)
        private set

    var testState by mutableStateOf<TestState>(TestState.Idle)
        private set

    fun saveKey() {
        if (keyInput.isBlank()) return
        viewModelScope.launch {
            app.settingsRepository.setApiKey(keyInput)
            keySaved = true
            testState = TestState.Idle
            RefreshWorker.enqueueNow(app)
        }
    }

    fun testConnection() {
        testState = TestState.Loading
        viewModelScope.launch {
            testState = when (val result = app.apiClient.testKey()) {
                is ApiResult.Success -> TestState.Done(R.string.settings_test_success, true)
                is ApiResult.HttpError ->
                    if (result.code == 403) {
                        TestState.Done(R.string.settings_test_forbidden, false)
                    } else {
                        TestState.Done(R.string.error_generic, false)
                    }
                is ApiResult.NetworkError ->
                    TestState.Done(R.string.settings_test_network_error, false)
            }
        }
    }

    companion object {
        fun factory(app: ClashRemindersApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(app) }
        }
    }
}
