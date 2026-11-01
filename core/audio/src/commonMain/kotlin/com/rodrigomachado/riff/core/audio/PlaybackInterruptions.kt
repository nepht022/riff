package com.rodrigomachado.riff.core.audio

/**
 * Avisa quando a música da partida deve parar por fora do jogo: perda do foco de áudio (ligação, outro app tocando)
 * ou fone desconectado (o som iria para o alto-falante).
 */
interface PlaybackInterruptions {
    /**
     * Pede o foco de áudio e passa a avisar em [onInterrupted]; [onBusy] quando o Android nega o foco (no meio de uma
     * ligação). Chamar de novo ao continuar pede o foco outra vez.
     */
    fun start(onInterrupted: () -> Unit, onBusy: () -> Unit)

    fun stop()
}

fun interface PlaybackInterruptionsFactory {
    fun create(): PlaybackInterruptions
}
