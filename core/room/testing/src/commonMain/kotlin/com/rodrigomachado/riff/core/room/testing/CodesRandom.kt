package com.rodrigomachado.riff.core.room.testing

import com.rodrigomachado.riff.core.room.RoomCodes
import kotlin.random.Random

/**
 * Um sorteio de códigos previsível: o `RoomCodes.random` tira cada letra com `nextInt(23)`, e este dá as letras de
 * [codes] na ordem (e recomeça do primeiro quando acabam).
 */
class CodesRandom(vararg codes: String) : Random() {
    private val letters = codes.joinToString("").also { require(it.isNotEmpty()) }
    private var next = 0

    override fun nextInt(until: Int): Int {
        val letter = letters[next]
        next = (next + 1) % letters.length
        return RoomCodes.ALPHABET.indexOf(letter).also { require(it in 0 until until) { "letra fora do alfabeto" } }
    }

    override fun nextBits(bitCount: Int): Int = error("o sorteio do código usa só o nextInt(until)")
}
