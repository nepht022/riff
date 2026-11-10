package com.rodrigomachado.riff.feature.room

import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.room.Room
import com.rodrigomachado.riff.core.room.RoomDevice
import com.rodrigomachado.riff.core.room.RoomPlayer
import com.rodrigomachado.riff.core.room.RoomSong
import com.rodrigomachado.riff.core.room.SongStatus

/** Salas prontas para os testes de tela: [names] na ordem de entrada, o primeiro é o dono. */
internal fun sampleRoom(
    vararg names: String,
    me: String = names.first(),
    gone: Set<String> = emptySet(),
    pcs: Set<String> = emptySet(),
): Room = Room(
    code = "KQZX",
    owner = names.first(),
    players = names.mapIndexed { i, name ->
        RoomPlayer(
            uid = name,
            name = name,
            device = if (name in pcs) RoomDevice.PC else RoomDevice.PHONE,
            joinedAt = i.toLong(),
            seenAt = i.toLong(),
            gone = name in gone,
        )
    },
    me = me,
)

/** A mesma sala com a música [song] escolhida. */
internal fun Room.withSong(song: RoomSong = SONG_A): Room = copy(song = song)

/** A mesma sala com o jogador [name] neste estado para a música. */
internal fun Room.withPlayer(
    name: String,
    song: SongStatus? = SongStatus.HAS,
    difficulty: Difficulty? = Difficulty.EXPERT,
    ready: Boolean = false,
): Room = copy(
    players = players.map {
        if (it.name == name) it.copy(song = song, difficulty = difficulty, ready = ready) else it
    },
)
