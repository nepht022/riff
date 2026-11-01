package com.rodrigomachado.riff.core.audio

import com.rodrigomachado.riff.core.chart.sng.RandomReadOpener

/**
 * Os players do PC: tudo pelo [StemSongPlayer] comum, com o FFmpeg decodificando e o Java Sound tocando. A música
 * de arquivo único é uma faixa só, embrulhada para não ser [GuitarTrackControl]: a partida corta a guitarra de
 * qualquer player que seja, e cortaria a música inteira a cada erro.
 */
object DesktopPlayers {
    fun songPlayers(randomRead: RandomReadOpener) = SongPlayerFactory {
        SingleSongPlayer(randomRead)
    }

    fun stemPlayers(randomRead: RandomReadOpener) = StemSongPlayerFactory { guitarUri, backingUris ->
        StemSongPlayer(guitarUri, backingUris, opener(randomRead), sinks)
    }

    /** No PC não há chamada nem fone desconectado que pause a música. */
    val interruptions = PlaybackInterruptionsFactory {
        object : PlaybackInterruptions {
            override fun start(onInterrupted: () -> Unit, onBusy: () -> Unit) = Unit

            override fun stop() = Unit
        }
    }

    private val sinks = AudioSinkFactory { JavaSoundSink(it) }

    private fun opener(randomRead: RandomReadOpener) = PcmSourceOpener { FfmpegPcmSource.open(it, randomRead) }

    /** A música de um arquivo só: o [uri] chega no [prepare]. */
    private class SingleSongPlayer(private val randomRead: RandomReadOpener) : SongPlayer {
        private var stems: StemSongPlayer? = null

        override suspend fun prepare(uri: String) {
            stems?.release()
            stems = StemSongPlayer(uri, emptyList(), opener(randomRead), sinks).also { it.prepare(uri) }
        }

        override fun play() = stems?.play() ?: Unit

        override fun pause() = stems?.pause() ?: Unit

        override fun seekTo(positionMs: Long) = stems?.seekTo(positionMs) ?: Unit

        override fun setVolume(volume: Float) = stems?.setVolume(volume) ?: Unit

        override fun release() = stems?.release() ?: Unit

        override val positionMs: Long get() = stems?.positionMs ?: 0

        override val isPlaying: Boolean get() = stems?.isPlaying ?: false

        override val isStarting: Boolean get() = stems?.isStarting ?: false

        override val isCompleted: Boolean get() = stems?.isCompleted ?: false
    }
}
