package com.rodrigomachado.riff.feature.home

import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.ScoreRepository
import com.rodrigomachado.riff.core.ui.art.ChartThumbImages
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** O [HomeViewModel] criado pelo Hilt no celular, com as capas pequenas do app ([ChartThumbImages]). */
@HiltViewModel
class HiltHomeViewModel @Inject constructor(
    scores: ScoreRepository,
    charts: ChartRepository,
    thumbs: ChartThumbImages,
) : HomeViewModel(scores, charts, thumbs)
