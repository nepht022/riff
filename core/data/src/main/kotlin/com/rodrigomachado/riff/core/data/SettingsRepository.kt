package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.datastore.RiffPreferences
import com.rodrigomachado.riff.core.model.Settings
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface SettingsRepository {
    val settings: Flow<Settings>

    /** Grava na hora; a próxima partida já usa o valor novo. */
    suspend fun update(transform: (Settings) -> Settings)
}

class PreferencesSettingsRepository @Inject constructor(private val preferences: RiffPreferences) :
    SettingsRepository {
    override val settings: Flow<Settings> = preferences.settings

    override suspend fun update(transform: (Settings) -> Settings) = preferences.updateSettings(transform)
}
