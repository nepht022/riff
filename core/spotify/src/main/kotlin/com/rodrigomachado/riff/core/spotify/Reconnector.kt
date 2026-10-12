package com.rodrigomachado.riff.core.spotify

import com.rodrigomachado.riff.core.model.SpotifyError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Reconexão com backoff ([ReconnectPolicy]). A mesma queda chega por dois caminhos (a assinatura do estado e o
 * listener da conexão): com uma tentativa já marcada, o segundo aviso é ignorado.
 */
internal class Reconnector(private val scope: CoroutineScope, private val reconnect: () -> Unit) {
    private var attempt = 0
    private var pending: Job? = null

    fun lost(error: SpotifyError) {
        if (!ReconnectPolicy.shouldRetry(error) || pending?.isActive == true) return
        val wait = ReconnectPolicy.delayMs(attempt++)
        pending = scope.launch {
            delay(wait)
            reconnect()
        }
    }

    fun connected() {
        attempt = 0
        pending?.cancel()
        pending = null
    }
}

/**
 * Espera uma resposta do SDK por callbacks. O SDK pode não chamar nenhum dos dois (Spotify fechado no meio):
 * depois de [timeoutMs], devolve null em vez de prender quem pediu.
 */
internal suspend fun <T : Any> awaitCallback(
    timeoutMs: Long,
    register: (onResult: (T) -> Unit, onError: () -> Unit) -> Unit,
): T? = withTimeoutOrNull(timeoutMs) {
    suspendCancellableCoroutine<T?> { continuation ->
        register(
            { if (continuation.isActive) continuation.resume(it) },
            { if (continuation.isActive) continuation.resume(null) },
        )
    }
}
