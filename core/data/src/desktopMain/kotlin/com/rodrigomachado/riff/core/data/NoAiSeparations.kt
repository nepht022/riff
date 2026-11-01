package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.model.QueuedSeparation
import com.rodrigomachado.riff.core.model.SeparationStatus
import com.rodrigomachado.riff.core.model.SeparationUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import java.io.File

/**
 * A guitarra separada por IA ainda não existe no PC (etapa 4): nada separado, nada na fila, e pedir para separar
 * não faz nada.
 */
object NoAiSeparations : AiSeparationRepository {
    override val separatedIds: Flow<Set<String>> = flowOf(emptySet())
    override val usage: Flow<SeparationUsage> = flowOf(SeparationUsage(songs = 0, bytes = 0))

    override suspend fun stateOf(chartId: String): AiSeparationState? = null

    override suspend fun separatedFiles(chartId: String): SeparatedFiles? = null

    override fun workDir(chartId: String): File = throw UnsupportedOperationException("separação por IA no PC")

    override suspend fun commit(chartId: String, workDir: File) = Unit

    override suspend fun decline(chartId: String) = Unit

    override suspend fun resetDeclines() = Unit

    override suspend fun deleteAll() = Unit

    override suspend fun pruneOrphans() = Unit
}

/** A fila da separação por IA no PC: sempre vazia até a etapa 4. */
object NoSeparationScheduler : SeparationScheduler {
    override val status: StateFlow<SeparationStatus> = MutableStateFlow(SeparationStatus.Idle)
    override val queue: StateFlow<List<QueuedSeparation>> = MutableStateFlow(emptyList())
    override val waitingForCharger: StateFlow<Boolean> = MutableStateFlow(false)
    override val lowMemoryDevice: Boolean = false

    override fun enqueue(songs: List<QueuedSeparation>) = Unit

    override fun cancelCurrent(chartId: String) = Unit

    override fun remove(chartId: String) = Unit

    override fun cancelAll() = Unit

    override fun dismissFailure() = Unit
}
