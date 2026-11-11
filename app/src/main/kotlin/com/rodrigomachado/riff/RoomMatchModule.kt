package com.rodrigomachado.riff

import com.rodrigomachado.riff.core.game.TimeSource
import com.rodrigomachado.riff.core.room.RoomPresence
import com.rodrigomachado.riff.core.room.RoomRepository
import com.rodrigomachado.riff.feature.gameplay.LiveBoardFactory
import com.rodrigomachado.riff.feature.gameplay.MatchStartFactory
import com.rodrigomachado.riff.feature.room.RoomLiveBoard
import com.rodrigomachado.riff.feature.room.RoomMatchStart
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * A partida de sala no celular: a partida (`:feature:gameplay`) não conhece a sala; o app liga as duas, montando o
 * [RoomMatchStart] com o repositório da sala e o relógio da partida (o mesmo [TimeSource] da `GameSession`), e o placar
 * ao vivo ([RoomLiveBoard]) com a sala que a presença já observa.
 */
@Module
@InstallIn(SingletonComponent::class)
object RoomMatchModule {
    @Provides
    fun matchStarts(repository: RoomRepository, timeSource: TimeSource): MatchStartFactory =
        MatchStartFactory { code, round -> RoomMatchStart(code, round, repository, timeSource::nowMs) }

    @Provides
    fun liveBoards(repository: RoomRepository, presence: RoomPresence): LiveBoardFactory =
        LiveBoardFactory { code, round -> RoomLiveBoard(code, round, repository, presence.room) }
}
