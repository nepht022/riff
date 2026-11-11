package com.rodrigomachado.riff.core.audio

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class StemSongPlayerTest {
    private class FakeSource(
        override val sampleRate: Int = 44_100,
        override val channelCount: Int = 1,
        private val value: Float = 0.25f,
        private val frames: Int = 4_000,
        private val failAfterFrames: Int = Int.MAX_VALUE,
    ) : PcmSource {
        var position = 0
        var closed = false

        override fun read(buffer: FloatArray, maxFrames: Int): Int {
            if (position >= failAfterFrames) throw IllegalStateException("arquivo corrompido")
            val count = minOf(maxFrames, frames - position)
            for (i in 0 until count * channelCount) buffer[i] = value
            position += count
            return count
        }

        override fun seekTo(frame: Long) {
            position = frame.toInt()
        }

        override fun close() {
            closed = true
        }
    }

    private class FakeSink : AudioSink {
        var started = false
        var paused = false
        var stopped = false
        var released = false
        var written = 0L
        var lastBlock = FloatArray(0)
        val writes = mutableListOf<Int>()

        /** Quantos quadros a próxima escrita aceita (o AudioTrack pausado no meio devolve menos). */
        var acceptNext: Int? = null
        val calls = mutableListOf<String>()
        override var playedFrames = 0L
            get() = field.also { calls += "posição" }
        override var isPresenting = true

        override fun start() {
            started = true
            paused = false
        }

        override fun pause() {
            calls += "pausa"
            paused = true
        }

        override fun stop() {
            stopped = true
        }

        override fun write(buffer: FloatArray, offsetFrames: Int, frames: Int): Int {
            val accepted = minOf(frames, acceptNext ?: frames)
            acceptNext = null
            lastBlock = buffer.copyOfRange(offsetFrames * 2, (offsetFrames + accepted) * 2)
            written += accepted
            writes += accepted
            return accepted
        }

        override fun release() {
            released = true
        }
    }

    private val sources = mutableMapOf<String, FakeSource>()
    private val sink = FakeSink()
    private val logged = mutableListOf<String>()
    private var now = 0L

    private fun player(startThread: Boolean = false) = StemSongPlayer(
        guitarUri = "guitar.ogg",
        backingUris = listOf("song.ogg"),
        opener = { uri -> sources.getValue(uri) },
        sinks = { sink },
        log = { message, _ -> logged += message },
        startThread = startThread,
        nowMs = { now },
        raiseAudioPriority = {}, // fora do Android, Process não existe: a thread morreria e o teste passaria por acaso
    )

    private fun prepared(startThread: Boolean = false) = player(
        startThread,
    ).also { runBlocking { it.prepare("guitar.ogg") } }

    @Test
    fun guitarAndBandAreMixedIntoOneOutput() {
        sources["guitar.ogg"] = FakeSource()
        sources["song.ogg"] = FakeSource()
        val player = prepared()

        player.play()
        assertTrue(player.renderOnce())

        assertEquals(StemSongPlayer.BLOCK_FRAMES.toLong(), sink.written)
        assertEquals(0.5f, sink.lastBlock[0], 1e-6f)
    }

    @Test
    fun cuttingTheGuitarLeavesTheBandAfterTheRamp() {
        sources["guitar.ogg"] = FakeSource()
        sources["song.ogg"] = FakeSource()
        val player = prepared()
        player.play()

        player.setGuitarAudible(false)
        player.renderOnce()
        player.renderOnce() // 2.048 quadros > rampa de 1.323

        assertEquals(0.25f, sink.lastBlock[sink.lastBlock.size - 2], 1e-6f)
    }

    @Test
    fun differentSampleRatesAreRejectedAndEveryTrackIsClosed() {
        sources["guitar.ogg"] = FakeSource(sampleRate = 44_100)
        sources["song.ogg"] = FakeSource(sampleRate = 48_000)
        val player = player()

        assertThrows(IOException::class.java) { runBlocking { player.prepare("guitar.ogg") } }
        assertTrue(sources.values.all { it.closed })
        assertEquals(1, logged.size)
    }

    @Test
    fun positionComesFromTheFramesThatWerePlayed() {
        sources["guitar.ogg"] = FakeSource()
        sources["song.ogg"] = FakeSource()
        val player = prepared()

        sink.playedFrames = 44_100

        assertEquals(1_000L, player.positionMs)
    }

    @Test
    fun aShorterTrackEndsInSilenceAndTheSongEndsWithTheLongest() {
        sources["guitar.ogg"] = FakeSource(frames = 100)
        sources["song.ogg"] = FakeSource(frames = 50)
        val player = prepared()
        player.play()

        assertTrue(player.renderOnce())
        assertEquals(100L, sink.written)
        assertEquals(0.5f, sink.lastBlock[10 * 2], 1e-6f)
        assertEquals(0.25f, sink.lastBlock[70 * 2], 1e-6f)
        assertFalse(player.renderOnce())
        assertFalse(player.isCompleted) // escrito, mas ainda não tocado

        sink.playedFrames = 100
        assertTrue(player.isCompleted)
        assertFalse(player.isPlaying)
    }

    @Test
    fun aTrackThatFailsMidSongGoesSilentAndTheRestKeepsPlaying() {
        sources["guitar.ogg"] = FakeSource(failAfterFrames = StemSongPlayer.BLOCK_FRAMES)
        sources["song.ogg"] = FakeSource()
        val player = prepared()
        player.play()

        player.renderOnce()
        assertTrue(player.renderOnce())

        assertEquals(0.25f, sink.lastBlock[0], 1e-6f)
        assertEquals(1, logged.size)
    }

    @Test
    fun playAndPauseDriveTheOutput() {
        sources["guitar.ogg"] = FakeSource()
        sources["song.ogg"] = FakeSource()
        val player = prepared()

        player.play()
        assertTrue(sink.started)
        assertTrue(player.isPlaying)

        player.pause()
        assertTrue(sink.paused)
        assertFalse(player.isPlaying)
    }

    @Test
    fun releaseWhilePausedStopsTheAudioThread() {
        sources["guitar.ogg"] = FakeSource()
        sources["song.ogg"] = FakeSource()
        val player = prepared(startThread = true)
        player.play()
        player.pause()
        val thread = checkNotNull(player.audioThread)

        player.release()

        assertFalse(thread.isAlive)
        assertTrue(sink.stopped)
        assertTrue(sink.released)
        assertTrue(sources.values.all { it.closed })
    }

    @Test
    fun everythingIsHarmlessAfterRelease() {
        sources["guitar.ogg"] = FakeSource()
        sources["song.ogg"] = FakeSource()
        val player = prepared()
        sink.playedFrames = 4_410

        player.release()
        player.release()
        player.play()
        player.pause()
        player.setGuitarAudible(false)

        assertEquals(100L, player.positionMs)
        assertFalse(player.isPlaying)
        assertFalse(player.renderOnce())
    }

    @Test
    fun aWriteCutShortByAPauseIsFinishedBeforeReadingMore() {
        val guitar = FakeSource().also { sources["guitar.ogg"] = it }
        sources["song.ogg"] = FakeSource()
        val player = prepared()
        player.play()
        sink.acceptNext = 300 // o jogador pausou no meio da escrita

        assertTrue(player.renderOnce())
        assertTrue(player.renderOnce())

        assertEquals(listOf(300, StemSongPlayer.BLOCK_FRAMES - 300), sink.writes)
        assertEquals(StemSongPlayer.BLOCK_FRAMES, guitar.position) // o resto do bloco veio do mesmo bloco
        player.renderOnce()
        assertEquals(2L * StemSongPlayer.BLOCK_FRAMES, sink.written)
    }

    @Test
    fun theEndIsPushedOutWithSilenceThatDoesNotCountAsMusic() {
        sources["guitar.ogg"] = FakeSource(frames = 100)
        sources["song.ogg"] = FakeSource(frames = 100)
        val player = prepared()
        player.play()

        player.renderOnce()
        assertFalse(player.renderOnce())

        assertTrue("o fim precisa ser empurrado para a saída", sink.written > 100)
        assertTrue(sink.lastBlock.all { it == 0f })
        sink.playedFrames = 100
        assertTrue(player.isCompleted)
    }

    @Test
    fun aPlaybackHeadThatStopsShortOfTheEndStillEndsTheSong() {
        sources["guitar.ogg"] = FakeSource(frames = 100)
        sources["song.ogg"] = FakeSource(frames = 100)
        val player = prepared()
        player.play()
        player.renderOnce()
        player.renderOnce()

        sink.playedFrames = 60
        assertFalse(player.isCompleted)
        now += StemSongPlayer.STALL_MS - 1
        assertFalse(player.isCompleted)
        now += 2
        assertTrue(player.isCompleted)
    }

    @Test
    fun theAppVolumeLowersEveryTrack() {
        sources["guitar.ogg"] = FakeSource()
        sources["song.ogg"] = FakeSource()
        val player = prepared()
        player.play()

        player.setVolume(0.5f)
        player.renderOnce()

        assertEquals(0.25f, sink.lastBlock[0], 1e-6f)
    }

    @Test
    fun untilTheSoundComesOutThePlayerIsStarting() {
        sources["guitar.ogg"] = FakeSource()
        sources["song.ogg"] = FakeSource()
        val player = prepared()
        sink.isPresenting = false

        player.play()
        assertTrue(player.isStarting)
        assertTrue(player.isPlaying)

        sink.isPresenting = true
        assertFalse(player.isStarting)
        player.pause()
        assertFalse(player.isStarting)
    }

    /** Sem aquecimento (a partida sozinha), o isStarting de cada quadro não lê a posição da saída (é cara no PC). */
    @Test
    fun isStartingWithoutWarmUpDoesNotReadThePosition() {
        sources["guitar.ogg"] = FakeSource()
        sources["song.ogg"] = FakeSource()
        val player = prepared()
        player.play()
        sink.calls.clear()

        repeat(3) { assertFalse(player.isStarting) }

        assertEquals(0, sink.calls.count { it == "posição" })
    }

    @Test
    fun seekingWhilePausedRestartsEveryTrackAndTheOutputThere() {
        val guitar = FakeSource(frames = 200_000)
        val song = FakeSource(frames = 200_000)
        sources["guitar.ogg"] = guitar
        sources["song.ogg"] = song
        val outputs = mutableListOf<FakeSink>()
        val player = StemSongPlayer(
            guitarUri = "guitar.ogg",
            backingUris = listOf("song.ogg"),
            opener = { uri -> sources.getValue(uri) },
            sinks = { FakeSink().also { outputs += it } },
            startThread = false,
            nowMs = { now },
        ).also { runBlocking { it.prepare("guitar.ogg") } }
        player.play()
        player.renderOnce()
        player.pause()

        player.seekTo(1_000)

        assertEquals(44_100, guitar.position)
        assertEquals(44_100, song.position)
        assertEquals(2, outputs.size)
        assertTrue(outputs[0].released) // a saída antiga tinha áudio de depois do ponto novo
        outputs[1].playedFrames = 4_410
        assertEquals(1_100L, player.positionMs)
    }

    @Test
    fun pausingReadsWhereTheSoundIsBeforeStoppingTheOutput() {
        sources["guitar.ogg"] = FakeSource()
        sources["song.ogg"] = FakeSource()
        val player = prepared()
        player.play()
        sink.calls.clear()

        player.pause()

        // A posição apresentada só se atualiza quando lida: lida antes da pausa, ela não fica até 500 ms para trás.
        assertEquals(listOf("posição", "pausa"), sink.calls)
    }

    @Test
    fun theAudioThreadAsksForAudioPriority() {
        sources["guitar.ogg"] = FakeSource()
        sources["song.ogg"] = FakeSource()
        val raisedOn = java.util.concurrent.ArrayBlockingQueue<String>(1)
        val player = StemSongPlayer(
            guitarUri = "guitar.ogg",
            backingUris = listOf("song.ogg"),
            opener = { uri -> sources.getValue(uri) },
            sinks = { sink },
            startThread = true,
            nowMs = { now },
            raiseAudioPriority = { raisedOn.offer(Thread.currentThread().name) },
        ).also { runBlocking { it.prepare("guitar.ogg") } }

        val thread = raisedOn.poll(2, java.util.concurrent.TimeUnit.SECONDS)
        player.release()

        assertEquals("riff-audio", thread)
    }

    @Test
    fun ifTheNewOutputFailsToOpenTheSeekIsSkippedAndTheOldOutputKeepsPlaying() {
        val guitar = FakeSource(frames = 200_000)
        sources["guitar.ogg"] = guitar
        sources["song.ogg"] = FakeSource(frames = 200_000)
        var created = 0
        val player = StemSongPlayer(
            guitarUri = "guitar.ogg",
            backingUris = listOf("song.ogg"),
            opener = { uri -> sources.getValue(uri) },
            sinks = { if (created++ == 0) sink else throw IllegalStateException("AudioTrack sem recursos") },
            log = { message, _ -> logged += message },
            startThread = false,
            nowMs = { now },
        ).also { runBlocking { it.prepare("guitar.ogg") } }
        player.play()
        player.renderOnce()
        player.pause()
        val before = guitar.position

        player.seekTo(0)
        player.play()

        assertFalse(sink.released)
        assertTrue(sink.started)
        assertEquals(before, guitar.position) // as faixas continuam de onde a saída antiga parou
        assertTrue(player.renderOnce())
        assertTrue(logged.any { "voltar" in it })
    }

    @Test
    fun aFailureToRaiseThePriorityDoesNotKillTheAudioThread() {
        sources["guitar.ogg"] = FakeSource()
        sources["song.ogg"] = FakeSource()
        val player = StemSongPlayer(
            guitarUri = "guitar.ogg",
            backingUris = listOf("song.ogg"),
            opener = { uri -> sources.getValue(uri) },
            sinks = { sink },
            log = { message, _ -> logged += message },
            startThread = true,
            nowMs = { now },
            raiseAudioPriority = { throw SecurityException("sem permissão") },
        ).also { runBlocking { it.prepare("guitar.ogg") } }

        player.play()
        val deadline = System.currentTimeMillis() + 2_000
        while (sink.written == 0L && System.currentTimeMillis() < deadline) Thread.sleep(10)
        player.release()

        assertTrue(sink.written > 0)
    }

    /**
     * Aquecida (partida de sala): a saída já começa a tocar silêncio, sem a música andar; o play só troca o silêncio
     * pela música, que conta da posição 0 quando o silêncio da fila acaba de sair.
     */
    @Test
    fun warmUpPlaysSilenceAndTheMusicStartsRightAfterIt() {
        sources["guitar.ogg"] = FakeSource()
        sources["song.ogg"] = FakeSource()
        val player = prepared()

        player.warmUp()
        assertTrue(sink.started)
        player.renderOnce()
        player.renderOnce()
        assertTrue(sink.lastBlock.all { it == 0f }) // silêncio
        assertFalse(player.isPlaying)
        assertEquals(0L, player.positionMs)
        assertEquals(0, sources.getValue("guitar.ogg").position) // a música não andou
        val silence = sink.written

        sink.started = false
        player.play()
        assertFalse(sink.started) // a saída já estava tocando: não recomeça
        player.renderOnce()
        assertEquals(0.5f, sink.lastBlock[0], 1e-6f) // a música, logo depois do silêncio

        sink.playedFrames = silence - 1
        assertTrue(player.isStarting) // o silêncio ainda saindo
        assertEquals(0L, player.positionMs)
        sink.playedFrames = silence + 441
        assertFalse(player.isStarting)
        assertEquals(10L, player.positionMs) // só a música conta
    }

    /** O que falta do silêncio na fila da saída é quanto o som do play leva até o alto-falante. */
    @Test
    fun warmedUpOutputReportsItsLatency() {
        sources["guitar.ogg"] = FakeSource()
        sources["song.ogg"] = FakeSource()
        val player = prepared()
        assertEquals(null, player.outputLatencyMs) // sem aquecer, não se sabe

        player.warmUp()
        repeat(8) { player.renderOnce() }
        sink.isPresenting = false
        assertEquals(null, player.outputLatencyMs) // ainda arrancando: não se sabe
        sink.isPresenting = true
        sink.playedFrames = sink.written - 4_410 // 100 ms de silêncio ainda na fila

        assertEquals(100L, player.outputLatencyMs)
        player.play()
        assertEquals(null, player.outputLatencyMs) // depois do play, já não é a latência do começo
    }

    /** Aquecer só vale antes do primeiro play: depois, a música já tem posição (e a partida sozinha nem aquece). */
    @Test
    fun warmUpAfterPlayingDoesNothing() {
        sources["guitar.ogg"] = FakeSource()
        sources["song.ogg"] = FakeSource()
        val player = prepared()
        player.play()
        player.renderOnce()
        player.pause()
        sink.started = false

        player.warmUp()

        assertFalse(sink.started)
        assertEquals(null, player.outputLatencyMs)
    }
}
