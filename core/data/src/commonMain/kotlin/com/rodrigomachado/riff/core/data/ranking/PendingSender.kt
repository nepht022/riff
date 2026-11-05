package com.rodrigomachado.riff.core.data.ranking

import com.rodrigomachado.riff.core.database.RankingPendingDao
import com.rodrigomachado.riff.core.database.RankingPendingEntity
import com.rodrigomachado.riff.core.model.Difficulty

/** [REMOVED]: o dono tirou este celular do ranking (removerJogador). */
internal enum class Outcome { SENT, WAIT, NO_NAME, REMOVED }

/**
 * Manda a fila do ranking: em lotes de até [BATCH] ("enviarVarios", um pedido no limite por hora) ou, com o script de
 * antes do lote, uma por vez. Quem chama segura a trava (um envio de cada vez).
 */
internal class PendingSender(
    private val api: RankingApi,
    private val pending: RankingPendingDao,
    private val onPlaced: (scoreId: Long, placed: SubmitStatus.Placed) -> Unit,
) {
    /** Falso depois de o script responder que não conhece o envio em lote (até o app abrir de novo). */
    @Volatile private var batches = true

    /** [onAnswer]: cada resposta do script (traz o nome atual do jogador, que pode ter mudado em outro aparelho). */
    suspend fun send(key: String, onAnswer: suspend (ApiAnswer<*>) -> Unit = {}): Outcome =
        if (batches) sendInBatches(key, onAnswer) else sendOneByOne(key, onAnswer)

    private suspend fun sendInBatches(key: String, onAnswer: suspend (ApiAnswer<*>) -> Unit): Outcome {
        for (chunk in pending.all().chunked(BATCH)) {
            when (val answer = api.submitMany(key, chunk.map { it.toSubmission() })) {
                is ApiAnswer.Ok -> {
                    chunk.zip(answer.value).forEach { (entry, result) -> settle(entry, result) }
                    onAnswer(answer)
                }

                is ApiAnswer.Refused -> return when (answer.reason) {
                    "sem_nome" -> Outcome.NO_NAME

                    "removido" -> Outcome.REMOVED

                    // O script de antes do "enviarVarios" (ainda não republicado): uma por vez.
                    "pedido" -> sendOneByOne(key, onAnswer).also { batches = false }

                    else -> Outcome.WAIT
                }

                ApiAnswer.Offline -> return Outcome.WAIT
            }
        }
        return Outcome.SENT
    }

    private suspend fun sendOneByOne(key: String, onAnswer: suspend (ApiAnswer<*>) -> Unit): Outcome {
        for (entry in pending.all()) {
            val answer = api.submit(key, entry.toSubmission())
            onAnswer(answer)
            if (answer is ApiAnswer.Refused && answer.reason == "sem_nome") return Outcome.NO_NAME
            if (answer is ApiAnswer.Refused && answer.reason == "removido") return Outcome.REMOVED
            if (!settle(entry, answer)) return Outcome.WAIT
        }
        return Outcome.SENT
    }

    /** Tira da fila o que chegou (ou que o script nunca vai aceitar); falso quando a pontuação precisa esperar. */
    private suspend fun settle(entry: RankingPendingEntity, answer: ApiAnswer<SubmitStatus.Placed?>): Boolean =
        when (answer) {
            is ApiAnswer.Ok -> {
                pending.delete(entry.id)
                val placed = answer.value
                val scoreId = entry.scoreId
                if (scoreId != null && placed != null) onPlaced(scoreId, placed)
                true
            }

            // Um pedido que o script nunca vai aceitar sai da fila; limite ou outro motivo, espera.
            is ApiAnswer.Refused -> (answer.reason == "pedido").also { if (it) pending.delete(entry.id) }

            ApiAnswer.Offline -> false
        }
}

/** Pontuações por pedido "enviarVarios" (o script aceita até 50). */
private const val BATCH = 50

internal fun RankingPendingEntity.toSubmission() = Submission(
    chartId,
    Difficulty.entries.firstOrNull { it.name == difficulty } ?: Difficulty.EXPERT,
    title,
    artist,
    points,
    accuracy,
    combo,
    perfect,
    cleanMs,
    notes,
    storedTable(rankingTable),
)
