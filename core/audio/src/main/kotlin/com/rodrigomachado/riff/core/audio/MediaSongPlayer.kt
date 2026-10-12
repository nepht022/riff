package com.rodrigomachado.riff.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import com.rodrigomachado.riff.core.chart.sng.RandomReadOpener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Um arquivo de áudio só: da pasta do mapa ou de dentro de um `.sng` ([opener] abre o pacote). */
class MediaSongPlayer(
    private val context: Context,
    private val opener: RandomReadOpener,
    private val player: MediaPlayer = MediaPlayer(),
) : SongPlayer {

    @Volatile
    private var completed = false

    // Depois de liberado o MediaPlayer lança exceção em qualquer chamada; a tela ainda pode
    // desenhar alguns frames enquanto sai, então tudo aqui vira inofensivo.
    @Volatile
    private var released = false
    private var positionAtRelease = 0L

    // Sair da partida (thread principal) durante o carregamento (IO): liberar o MediaPlayer no meio do prepare
    // quebra o player nativo, então o release espera o carregamento terminar.
    private val lock = Any()
    private var preparing = false

    override suspend fun prepare(uri: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (released) return@withContext
            preparing = true
        }
        try {
            load(uri)
        } finally {
            synchronized(lock) {
                preparing = false
                if (released) player.release()
            }
        }
    }

    private fun load(uri: String) {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        val packaged = audioDataSource(uri, opener)
        if (packaged != null) player.setDataSource(packaged) else player.setDataSource(context, Uri.parse(uri))
        player.setOnCompletionListener { completed = true }
        player.prepare()
    }

    override fun play() {
        if (!released) player.start()
    }

    override fun pause() {
        if (isPlaying) player.pause()
    }

    override fun seekTo(positionMs: Long) {
        if (!released) player.seekTo(positionMs, MediaPlayer.SEEK_CLOSEST)
    }

    override fun setVolume(volume: Float) {
        if (!released) volume.coerceIn(0f, 1f).let { player.setVolume(it, it) }
    }

    override fun release() {
        synchronized(lock) {
            if (released) return
            if (!preparing) positionAtRelease = runCatching { player.currentPosition.toLong() }.getOrDefault(0L)
            released = true
            if (!preparing) player.release()
        }
    }

    override val positionMs: Long get() = if (released) positionAtRelease else player.currentPosition.toLong()

    override val isPlaying: Boolean get() = !released && player.isPlaying

    override val isCompleted: Boolean get() = completed
}
