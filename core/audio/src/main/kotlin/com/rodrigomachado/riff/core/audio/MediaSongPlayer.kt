package com.rodrigomachado.riff.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaSongPlayer(private val context: Context, private val player: MediaPlayer = MediaPlayer()) : SongPlayer {

    @Volatile
    private var completed = false

    // Depois de liberado o MediaPlayer lança exceção em qualquer chamada; a tela ainda pode
    // desenhar alguns frames enquanto sai, então tudo aqui vira inofensivo.
    @Volatile
    private var released = false
    private var positionAtRelease = 0L

    override suspend fun prepare(uri: String) = withContext(Dispatchers.IO) {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        player.setDataSource(context, Uri.parse(uri))
        player.setOnCompletionListener { completed = true }
        player.prepare()
    }

    override fun play() {
        if (!released) player.start()
    }

    override fun pause() {
        if (isPlaying) player.pause()
    }

    override fun release() {
        if (released) return
        positionAtRelease = runCatching { player.currentPosition.toLong() }.getOrDefault(0L)
        released = true
        player.release()
    }

    override val positionMs: Long get() = if (released) positionAtRelease else player.currentPosition.toLong()

    override val isPlaying: Boolean get() = !released && player.isPlaying

    override val isCompleted: Boolean get() = completed
}
