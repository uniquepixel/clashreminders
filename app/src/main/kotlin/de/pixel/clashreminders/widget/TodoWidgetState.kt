package de.pixel.clashreminders.widget

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class TodoWidgetState(
    val updatedAt: Long = 0L,
    val refreshing: Boolean = false,
    val hasApiKey: Boolean = true,
    val hasAccounts: Boolean = true,
    /** Only accounts with something open; empty + updatedAt > 0 means "All done". */
    val entries: List<Entry> = emptyList(),
) {
    @Serializable
    data class Entry(
        val tag: String,
        val name: String,
        val warState: String? = null,
        val warDone: Int = 0,
        val warRequired: Int = 0,
        val raidAttacks: Int? = null,
        val raidLimit: Int? = null,
        val cgPoints: Int? = null,
        val cgActive: Boolean = false,
    )

    companion object {
        val KEY = stringPreferencesKey("todo_widget_state_json")
        private val json = Json { ignoreUnknownKeys = true }

        fun from(prefs: Preferences): TodoWidgetState =
            prefs[KEY]?.let { runCatching { json.decodeFromString<TodoWidgetState>(it) }.getOrNull() }
                ?: TodoWidgetState()

        fun TodoWidgetState.encode(): String = json.encodeToString(this)
    }
}
