package com.rodrigomachado.riff.feature.gameplay

import androidx.lifecycle.SavedStateHandle
import com.rodrigomachado.riff.core.audio.LocalSong
import com.rodrigomachado.riff.core.audio.PlaybackInterruptionsFactory
import com.rodrigomachado.riff.core.audio.SfxPlayerFactory
import com.rodrigomachado.riff.core.audio.SongPlayerFactory
import com.rodrigomachado.riff.core.audio.StemSongPlayerFactory
import com.rodrigomachado.riff.core.data.ChartOffsetRepository
import com.rodrigomachado.riff.core.data.ScoreRepository
import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.data.game.ChartTrackLoader
import com.rodrigomachado.riff.core.data.ranking.RankingRepository
import com.rodrigomachado.riff.core.game.TimeSource
import com.rodrigomachado.riff.core.model.Difficulty
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * O [GameplayViewModel] criado pelo Hilt no celular: o mapa e a dificuldade vêm dos argumentos da rota; com o
 * `roomCode` ([GameplayRoute]), a partida de sala, com o [MatchStart] e o [LiveBoard] montados pelas fábricas do app
 * ([MatchStartFactory], [LiveBoardFactory]).
 */
@HiltViewModel
class HiltGameplayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    loader: ChartTrackLoader,
    settingsRepository: SettingsRepository,
    scores: ScoreRepository,
    chartOffsets: ChartOffsetRepository,
    @LocalSong songPlayers: SongPlayerFactory,
    stemPlayers: StemSongPlayerFactory,
    sfxPlayers: SfxPlayerFactory,
    timeSource: TimeSource,
    interruptionsFactory: PlaybackInterruptionsFactory,
    artImages: ChartArtImages,
    ranking: RankingRepository,
    matchStarts: MatchStartFactory,
    liveBoards: LiveBoardFactory,
) : GameplayViewModel(
    checkNotNull(savedStateHandle["chartId"]),
    Difficulty.valueOf(checkNotNull(savedStateHandle["difficulty"])),
    loader,
    settingsRepository,
    scores,
    chartOffsets,
    songPlayers,
    stemPlayers,
    sfxPlayers,
    timeSource,
    interruptionsFactory,
    artImages,
    ranking,
    matchStart = savedStateHandle.get<String>("roomCode")?.let { code ->
        matchStarts.create(code, savedStateHandle.get<Int>("round") ?: 0)
    },
    liveBoard = savedStateHandle.get<String>("roomCode")?.let { code ->
        liveBoards.create(code, savedStateHandle.get<Int>("round") ?: 0)
    },
)
