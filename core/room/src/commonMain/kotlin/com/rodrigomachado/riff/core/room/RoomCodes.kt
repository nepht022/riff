package com.rodrigomachado.riff.core.room

import kotlin.random.Random

/** O código da sala: 4 letras sem as que se confundem (`I`, `L`, `O`) e sem números, 23⁴ = 279.841 códigos. */
object RoomCodes {
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ"
    const val LENGTH = 4

    /** Um código sorteado (cada letra com `random.nextInt(23)`). */
    fun random(random: Random): String = String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })

    /** O código digitado em maiúsculas e sem espaços; null se não forem 4 letras do alfabeto. */
    fun normalize(input: String): String? = input.filterNot { it.isWhitespace() }.uppercase()
        .takeIf { code -> code.length == LENGTH && code.all { it in ALPHABET } }
}
