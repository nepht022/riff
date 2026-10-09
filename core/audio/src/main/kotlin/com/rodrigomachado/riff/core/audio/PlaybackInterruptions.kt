package com.rodrigomachado.riff.core.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * Avisa quando a música da partida deve parar por fora do jogo: perda do foco de áudio (ligação, outro app tocando)
 * ou fone desconectado (o som iria para o alto-falante). Usado no modo biblioteca; no Spotify, o próprio Spotify cuida.
 */
interface PlaybackInterruptions {
    /** Pede o foco de áudio e passa a avisar em [onInterrupted]. Chamar de novo ao continuar pede o foco outra vez. */
    fun start(onInterrupted: () -> Unit)

    fun stop()
}

fun interface PlaybackInterruptionsFactory {
    fun create(): PlaybackInterruptions
}

class AndroidPlaybackInterruptions(private val context: Context) : PlaybackInterruptions {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var onInterrupted: (() -> Unit)? = null
    private var focus: AudioFocusRequest? = null
    private var listening = false

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) onInterrupted?.invoke()
        }
    }

    override fun start(onInterrupted: () -> Unit) {
        this.onInterrupted = onInterrupted
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setOnAudioFocusChangeListener { change ->
                // Ligação ou outro app tocando pausam; "abaixar o volume" por um instante (som de notificação) não.
                val lost = change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                if (lost) this.onInterrupted?.invoke()
            }
            .build()
        focus?.let(audio::abandonAudioFocusRequest)
        focus = request
        audio.requestAudioFocus(request)
        if (!listening) {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(noisy, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(noisy, filter)
            }
            listening = true
        }
    }

    override fun stop() {
        onInterrupted = null
        focus?.let(audio::abandonAudioFocusRequest)
        focus = null
        if (listening) {
            runCatching { context.unregisterReceiver(noisy) }
            listening = false
        }
    }
}
