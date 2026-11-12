package com.rodrigomachado.riff.feature.library

import com.rodrigomachado.riff.core.data.AiSeparationRepository
import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.LibraryFolderStore
import com.rodrigomachado.riff.core.data.ScoreRepository
import com.rodrigomachado.riff.core.data.SeparationScheduler
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.data.folder.FolderAccess
import com.rodrigomachado.riff.core.data.game.ChartAudioProbe
import com.rodrigomachado.riff.core.data.importer.ChartImporter
import com.rodrigomachado.riff.core.data.library.ChartDeleter
import com.rodrigomachado.riff.core.ui.art.ChartThumbImages
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * O [LibraryViewModel] criado pelo Hilt no celular (a classe comum não conhece o Hilt), com as capas pequenas do app
 * ([ChartThumbImages]).
 */
@HiltViewModel
class HiltLibraryViewModel @Inject constructor(
    chartRepository: ChartRepository,
    folderStore: LibraryFolderStore,
    importer: ChartImporter,
    folderAccess: FolderAccess,
    separations: AiSeparationRepository,
    scheduler: SeparationScheduler,
    probe: ChartAudioProbe,
    settings: SettingsRepository,
    deleter: ChartDeleter,
    scores: ScoreRepository,
    thumbs: ChartThumbImages,
) : LibraryViewModel(
    chartRepository, folderStore, importer, folderAccess, separations, scheduler, probe, settings, deleter, scores,
    thumbs,
)
