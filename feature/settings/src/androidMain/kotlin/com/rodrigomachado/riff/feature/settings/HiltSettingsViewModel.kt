package com.rodrigomachado.riff.feature.settings

import com.rodrigomachado.riff.core.audio.SfxPlayerFactory
import com.rodrigomachado.riff.core.data.AiSeparationRepository
import com.rodrigomachado.riff.core.data.AppLanguageStore
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.data.ranking.RankingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** O [SettingsViewModel] criado pelo Hilt no celular. */
@HiltViewModel
class HiltSettingsViewModel @Inject constructor(
    repository: SettingsRepository,
    separations: AiSeparationRepository,
    languages: AppLanguageStore,
    ranking: RankingRepository,
    sfxPlayers: SfxPlayerFactory,
) : SettingsViewModel(repository, separations, languages, ranking, sfxPlayers)
