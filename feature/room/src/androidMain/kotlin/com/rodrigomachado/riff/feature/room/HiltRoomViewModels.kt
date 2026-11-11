package com.rodrigomachado.riff.feature.room

import androidx.lifecycle.SavedStateHandle
import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.EncoreDownloads
import com.rodrigomachado.riff.core.data.download.ChartDownloader
import com.rodrigomachado.riff.core.data.ranking.RankingRepository
import com.rodrigomachado.riff.core.room.RoomPresence
import com.rodrigomachado.riff.core.room.RoomRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** O [RoomEntryViewModel] criado pelo Hilt no celular: o nome é o do ranking. */
@HiltViewModel
class HiltRoomEntryViewModel @Inject constructor(repository: RoomRepository, ranking: RankingRepository) :
    RoomEntryViewModel(repository, ranking.player.map { it.name })

/** O [RoomJoinViewModel] criado pelo Hilt no celular: o nome é o do ranking. */
@HiltViewModel
class HiltRoomJoinViewModel @Inject constructor(repository: RoomRepository, ranking: RankingRepository) :
    RoomJoinViewModel(repository, ranking.player.map { it.name })

/**
 * O [RoomLobbyViewModel] criado pelo Hilt no celular, com o código da rota; a música é conferida na biblioteca (o
 * repositório de mapas) e baixada do Encore pelo download da pesquisa.
 */
@HiltViewModel
class HiltRoomLobbyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: RoomRepository,
    charts: ChartRepository,
    downloads: EncoreDownloads,
    downloader: ChartDownloader,
) : RoomLobbyViewModel(
    checkNotNull(savedStateHandle["code"]),
    repository,
    RoomSongCheck.of(charts, downloads, downloader),
)

/** O [RoomResultsViewModel] criado pelo Hilt no celular, com o código e a rodada da rota e a sala da presença. */
@HiltViewModel
class HiltRoomResultsViewModel @Inject constructor(savedStateHandle: SavedStateHandle, presence: RoomPresence) :
    RoomResultsViewModel(checkNotNull(savedStateHandle["code"]), checkNotNull(savedStateHandle["round"]), presence.room)
