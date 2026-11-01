package com.rodrigomachado.riff.feature.gameplay

import java.util.WeakHashMap

/**
 * Toque e soltura por trilha nos testes, pelo mesmo caminho dos dedos na tela ([GameSession.pressPointer]): cada
 * toque é um dedo novo, e soltar a trilha solta um dos dedos que estão nela.
 */
private val fingersOnLanes = WeakHashMap<GameSession, MutableMap<Int, ArrayDeque<Long>>>()
private var nextPointer = 1_000L

fun GameSession.press(lane: Int, lateByMs: Long = 0) {
    val pointer = nextPointer++
    fingersOnLanes.getOrPut(this) { mutableMapOf() }.getOrPut(lane) { ArrayDeque() }.addLast(pointer)
    pressPointer(pointer, lane, lateByMs)
}

fun GameSession.release(lane: Int, lateByMs: Long = 0) {
    val pointer = fingersOnLanes[this]?.get(lane)?.removeFirstOrNull() ?: return
    releasePointer(pointer, lateByMs)
}
