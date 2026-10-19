package com.rodrigomachado.riff.feature.gameplay

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * O primeiro ajuste de uma música do Spotify: o começo detectado no áudio do mapa, limitado pela sobra do áudio do mapa
 * em relação ao Spotify e pela primeira nota.
 */
class InitialSongOffsetTest {
    /** Mapa oficial: a mesma gravação do Spotify, com introdução baixa (plateia): o certo é 0. */
    @Test
    fun theSameRecordingStartsTogether() {
        assertEquals(
            0,
            initialSongOffset(startMs = 3_600, chartAudioMs = 250_000, spotifyMs = 250_100, firstNoteMs = 9_000),
        )
    }

    /** Diferença de poucos ms é o enchimento do codificador, não introdução. */
    @Test
    fun aTinyDifferenceIsEncoderPadding() {
        assertEquals(
            0,
            initialSongOffset(startMs = 3_600, chartAudioMs = 250_200, spotifyMs = 250_000, firstNoteMs = 9_000),
        )
    }

    /** Contagem de entrada de 3 s no áudio do mapa (o Spotify começa direto): continua 3 s. */
    @Test
    fun aCountInIsKept() {
        assertEquals(
            3_000,
            initialSongOffset(startMs = 3_000, chartAudioMs = 253_000, spotifyMs = 250_000, firstNoteMs = 9_000),
        )
    }

    /** Introdução baixa e 1 s de silêncio a mais: o ajuste não passa da sobra do áudio do mapa. */
    @Test
    fun theOffsetIsCappedByTheExtraAudio() {
        assertEquals(
            1_000,
            initialSongOffset(startMs = 2_500, chartAudioMs = 251_000, spotifyMs = 250_000, firstNoteMs = 9_000),
        )
    }

    /** A música não "começa" depois da primeira nota: o começo detectado depois dela é erro da detecção. */
    @Test
    fun theOffsetNeverPassesTheFirstNote() {
        assertEquals(
            4_000,
            initialSongOffset(startMs = 5_000, chartAudioMs = null, spotifyMs = null, firstNoteMs = 4_000),
        )
    }

    /** Sem as durações (áudio sem metadado, Spotify sem duração), vale o começo detectado, como antes. */
    @Test
    fun withoutDurationsTheDetectionStands() {
        assertEquals(3_000, initialSongOffset(startMs = 3_000, chartAudioMs = null, spotifyMs = 0, firstNoteMs = 9_000))
    }

    @Test
    fun withoutADetectedStartItIsZero() {
        assertEquals(
            0,
            initialSongOffset(startMs = null, chartAudioMs = 250_000, spotifyMs = 240_000, firstNoteMs = 9_000),
        )
    }
}
