package com.rodrigomachado.riff.feature.room

import androidx.lifecycle.SavedStateHandle
import com.rodrigomachado.riff.core.data.ranking.RankingRepository
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

/** O [RoomLobbyViewModel] criado pelo Hilt no celular, com o código da rota. */
@HiltViewModel
class HiltRoomLobbyViewModel @Inject constructor(savedStateHandle: SavedStateHandle, repository: RoomRepository) :
    RoomLobbyViewModel(checkNotNull(savedStateHandle["code"]), repository)
