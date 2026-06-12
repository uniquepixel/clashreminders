package de.pixel.clashreminders.ui.screen.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.pixel.clashreminders.ClashRemindersApp
import de.pixel.clashreminders.data.db.entity.ReminderEntity
import de.pixel.clashreminders.scheduling.FireReminderWorker
import de.pixel.clashreminders.scheduling.RefreshWorker
import de.pixel.clashreminders.ui.component.NewReminder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RemindersViewModel(private val app: ClashRemindersApp) : ViewModel() {

    val reminders = app.accountRepository.observeReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addReminder(new: NewReminder) {
        viewModelScope.launch {
            app.accountRepository.addReminder(
                ReminderEntity(
                    type = new.type,
                    offsetMinutes = new.offsetMinutes,
                    cgThreshold = new.cgThreshold,
                    raidDay = new.raidDay?.name,
                    raidTimeMinutes = new.raidTimeMinutes,
                )
            )
            RefreshWorker.enqueueNow(app)
        }
    }

    fun setEnabled(reminder: ReminderEntity, enabled: Boolean) {
        viewModelScope.launch {
            app.accountRepository.updateReminder(reminder.copy(enabled = enabled))
            RefreshWorker.enqueueNow(app)
        }
    }

    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            app.accountRepository.deleteReminder(reminder.id)
            RefreshWorker.enqueueNow(app)
        }
    }

    /** Debug shortcut: run the full fire path with a unique event key. */
    fun fireNow(reminder: ReminderEntity) {
        FireReminderWorker.enqueue(app, reminder.id, "debug-${System.currentTimeMillis()}")
    }

    companion object {
        fun factory(app: ClashRemindersApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { RemindersViewModel(app) }
        }
    }
}
