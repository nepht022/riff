package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.datastore.RiffPreferences
import com.rodrigomachado.riff.core.model.Settings
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface SettingsRepository {
    val settings: Flow<Settings>
}

class PreferencesSettingsRepository @Inject constructor(preferences: RiffPreferences) : SettingsRepository {
    override val settings: Flow<Settings> = preferences.settings
}
