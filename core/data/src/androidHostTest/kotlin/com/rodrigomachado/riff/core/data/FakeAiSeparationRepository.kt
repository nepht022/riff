package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.model.SeparationUsage
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class FakeAiSeparationRepository : AiSeparationRepository {
    val files = mutableMapOf<String, SeparatedFiles>()
    val states = mutableMapOf<String, AiSeparationState>()
    var pruned = 0
    override val separatedIds = MutableStateFlow(emptySet<String>())
    override val usage = MutableStateFlow(SeparationUsage(0, 0))
    override suspend fun stateOf(chartId: String) = states[chartId]
    override suspend fun separatedFiles(chartId: String) = files[chartId]
    override fun workDir(chartId: String) = File("tmp/$chartId")
    override suspend fun commit(chartId: String, workDir: File) {
        states[chartId] = AiSeparationState.SEPARATED
    }
    override suspend fun decline(chartId: String) {
        states[chartId] = AiSeparationState.DECLINED
    }
    override suspend fun resetDeclines() {
        states.values.removeAll { it == AiSeparationState.DECLINED }
    }
    override suspend fun deleteAll() {
        states.values.removeAll { it == AiSeparationState.SEPARATED }
    }
    override suspend fun pruneOrphans() {
        pruned++
    }
}
