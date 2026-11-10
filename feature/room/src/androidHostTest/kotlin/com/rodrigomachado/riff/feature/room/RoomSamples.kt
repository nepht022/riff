package com.rodrigomachado.riff.feature.room

import com.rodrigomachado.riff.core.room.Room
import com.rodrigomachado.riff.core.room.RoomDevice
import com.rodrigomachado.riff.core.room.RoomPlayer

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
