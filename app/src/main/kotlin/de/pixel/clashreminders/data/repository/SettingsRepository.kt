package de.pixel.clashreminders.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "clashreminders_settings")

class SettingsRepository(private val context: Context) {

    private val keyApiKey = stringPreferencesKey("api_key")
    private val keyLastRefreshAt = longPreferencesKey("last_refresh_at")

    val apiKey: Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[keyApiKey]?.takeIf { it.isNotBlank() } }

    val lastRefreshAt: Flow<Long?> =
        context.dataStore.data.map { prefs -> prefs[keyLastRefreshAt] }

    suspend fun apiKeyOnce(): String? = apiKey.first()

    suspend fun setApiKey(value: String) {
        context.dataStore.edit { prefs -> prefs[keyApiKey] = value.trim() }
    }

    suspend fun setLastRefreshAt(value: Long) {
        context.dataStore.edit { prefs -> prefs[keyLastRefreshAt] = value }
    }
}
