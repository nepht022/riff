package com.rodrigomachado.riff.core.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Avisa quando a música da partida deve parar por fora do jogo: perda do foco de áudio (ligação, outro app tocando)
 * ou fone desconectado (o som iria para o alto-falante). Usado no modo biblioteca; no Spotify, o próprio Spotify cuida.
 */
interface PlaybackInterruptions {
    /**
     * Pede o foco de áudio e passa a avisar em [onInterrupted]; [onBusy] quando o Android nega o foco (no meio de uma
     * ligação). Chamar de novo ao continuar pede o foco outra vez.
     */
    fun start(onInterrupted: () -> Unit, onBusy: () -> Unit)

    fun start(onInterrupted: () -> Unit) = start(onInterrupted, onInterrupted)

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
    private val main = Handler(Looper.getMainLooper())

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) onInterrupted?.invoke()
        }
    }

    override fun start(onInterrupted: () -> Unit, onBusy: () -> Unit) {
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
        if (audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            // Negado (por exemplo, no meio de uma ligação): pausa logo depois, fora do "continuar" em andamento.
            main.post { if (this.onInterrupted != null) onBusy() }
        }
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
        main.removeCallbacksAndMessages(null)
        focus?.let(audio::abandonAudioFocusRequest)
        focus = null
        if (listening) {
            runCatching { context.unregisterReceiver(noisy) }
            listening = false
        }
    }
}
