package com.rodrigomachado.riff.feature.room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** O que deu errado ao criar ou entrar numa sala. */
enum class RoomError { NOT_FOUND, FULL, NO_CONNECTION }

/**
 * O nome do jogador na sala é o do ranking. Sem nome, [run] pede o nome (o mesmo diálogo do ranking, aberto pelo app)
 * e guarda a ação: ela roda sozinha quando o nome chegar, enquanto a tela estiver aberta.
 */
internal class NameGate(scope: CoroutineScope, playerName: Flow<String?>) {
    private val names = playerName.map { it?.takeIf(String::isNotBlank) }
    private var pending: (suspend (String) -> Unit)? = null

    init {
        scope.launch {
            names.collect { name ->
                val action = pending
                if (name != null && action != null) {
                    pending = null
                    action(name)
                }
            }
        }
    }

    /** [action] com o nome; sem nome, [onAsk] agora e [action] quando o nome for posto. */
    suspend fun run(onAsk: () -> Unit, action: suspend (String) -> Unit) {
        val name = names.first()
        if (name != null) {
            action(name)
        } else {
            pending = action
            onAsk()
        }
    }
}
