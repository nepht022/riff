package com.rodrigomachado.riff.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaSongPlayer(private val context: Context) : SongPlayer {
    private val player = MediaPlayer()

    @Volatile
    private var completed = false

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

    override fun play() = player.start()

    override fun pause() {
        if (player.isPlaying) player.pause()
    }

    override fun release() = player.release()

    override val positionMs: Long get() = player.currentPosition.toLong()

    override val isPlaying: Boolean get() = player.isPlaying

    override val isCompleted: Boolean get() = completed
}
