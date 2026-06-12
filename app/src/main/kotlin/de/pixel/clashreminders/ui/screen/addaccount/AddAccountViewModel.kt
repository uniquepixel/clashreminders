package de.pixel.clashreminders.ui.screen.addaccount

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
import de.pixel.clashreminders.api.dto.PlayerDto
import de.pixel.clashreminders.data.repository.AccountRepository
import de.pixel.clashreminders.scheduling.RefreshWorker
import kotlinx.coroutines.launch

sealed class LookupState {
    object Idle : LookupState()
    object Loading : LookupState()
    data class Found(val player: PlayerDto) : LookupState()
    data class Error(val messageRes: Int) : LookupState()
}

class AddAccountViewModel(private val app: ClashRemindersApp) : ViewModel() {

    var tagInput by mutableStateOf("")
        private set

    var lookupState by mutableStateOf<LookupState>(LookupState.Idle)
        private set

    fun onTagChanged(value: String) {
        tagInput = value
        lookupState = LookupState.Idle
    }

    fun lookup() {
        if (tagInput.isBlank()) return
        lookupState = LookupState.Loading
        viewModelScope.launch {
            val normalized = AccountRepository.normalizeTag(tagInput)
            if (app.accountRepository.getAccount(normalized) != null) {
                lookupState = LookupState.Error(R.string.add_account_already_added)
                return@launch
            }
            lookupState = when (val result = app.accountRepository.lookupPlayer(tagInput)) {
                is ApiResult.Success -> LookupState.Found(result.value)
                is ApiResult.HttpError -> when (result.code) {
                    404 -> LookupState.Error(R.string.add_account_not_found)
                    403 -> LookupState.Error(R.string.add_account_forbidden)
                    else -> LookupState.Error(R.string.error_generic)
                }
                is ApiResult.NetworkError -> LookupState.Error(R.string.add_account_network_error)
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        val found = (lookupState as? LookupState.Found)?.player ?: return
        viewModelScope.launch {
            app.accountRepository.addAccount(found)
            RefreshWorker.enqueueNow(app)
            onSaved()
        }
    }

    companion object {
        fun factory(app: ClashRemindersApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { AddAccountViewModel(app) }
        }
    }
}
