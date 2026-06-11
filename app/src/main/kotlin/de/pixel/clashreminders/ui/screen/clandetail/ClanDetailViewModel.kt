package de.pixel.clashreminders.ui.screen.clandetail

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

class ClanDetailViewModel(
    private val app: ClashRemindersApp,
    private val clanTag: String,
) : ViewModel() {

    val clan = app.clanRepository.observeClan(clanTag)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val reminders = app.clanRepository.observeReminders(clanTag)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addReminder(new: NewReminder) {
        viewModelScope.launch {
            app.clanRepository.addReminder(
                ReminderEntity(
                    clanTag = clanTag,
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
            app.clanRepository.updateReminder(reminder.copy(enabled = enabled))
            RefreshWorker.enqueueNow(app)
        }
    }

    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            app.clanRepository.deleteReminder(reminder.id)
            RefreshWorker.enqueueNow(app)
        }
    }

    fun deleteClan(onDeleted: () -> Unit) {
        viewModelScope.launch {
            app.clanRepository.deleteClan(clanTag)
            RefreshWorker.enqueueNow(app)
            onDeleted()
        }
    }

    /** Debug shortcut: run the full fire path with a unique event key. */
    fun fireNow(reminder: ReminderEntity) {
        FireReminderWorker.enqueue(app, reminder.id, "debug-${System.currentTimeMillis()}")
    }

    companion object {
        fun factory(app: ClashRemindersApp, clanTag: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ClanDetailViewModel(app, clanTag) }
            }
    }
}
