package com.rodrigomachado.riff.core.data.queue

import kotlinx.coroutines.flow.Flow

/**
 * O Riff está à vista. O "Acesso às notificações" mantém o processo vivo em segundo plano; o que só deve rodar com o
 * app aberto (a fila do Spotify) espera por isto.
 */
fun interface AppVisibility {
    fun visible(): Flow<Boolean>
}
