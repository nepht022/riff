package com.rodrigomachado.riff.core.data.importer

import com.rodrigomachado.riff.core.data.ChartRepository
import com.rodrigomachado.riff.core.data.folder.FolderAccessLostException
import com.rodrigomachado.riff.core.data.folder.FolderEntry
import com.rodrigomachado.riff.core.data.folder.FolderSource
import com.rodrigomachado.riff.core.model.Chart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Árvore de pastas em memória: "raiz/Rock/Musica" vira uma pasta; arquivos têm conteúdo e tamanho declarado. */
class FakeFolderSource : FolderSource {
    private val files = mutableMapOf<String, ByteArray>()
    private val declaredSizes = mutableMapOf<String, Long>()
    val revoked = mutableSetOf<String>()
    val reads = mutableListOf<String>()

    fun file(path: String, content: String) = file(path, content.toByteArray())

    fun file(path: String, content: ByteArray, declaredSize: Long = content.size.toLong()) {
        files[path] = content
        declaredSizes[path] = declaredSize
    }

    fun remove(prefix: String) {
        files.keys.removeAll { it == prefix || it.startsWith("$prefix/") }
    }

    override suspend fun listChildren(folderUri: String): List<FolderEntry> {
        if (folderUri in revoked) throw FolderAccessLostException("revogada: $folderUri")
        val prefix = "$folderUri/"
        val paths = files.keys.filter { it.startsWith(prefix) }
        val names = paths.map { it.removePrefix(prefix).substringBefore('/') }.distinct()
        val exists = files.keys.any { it.startsWith(folderUri) }
        if (names.isEmpty() && !exists) throw FolderAccessLostException("não existe: $folderUri")
        return names.map { name ->
            val path = prefix + name
            val isDirectory = path !in files
            FolderEntry(path, name, isDirectory, if (isDirectory) 0 else declaredSizes.getValue(path))
        }
    }

    override suspend fun read(file: FolderEntry): ByteArray {
        reads += file.uri
        return files.getValue(file.uri)
    }
}

class FakeChartRepository : ChartRepository {
    private val stored = MutableStateFlow<Map<String, Pair<Chart, Long>>>(emptyMap())
    val removedScans = mutableListOf<Long>()

    val charts: List<Chart> get() = stored.value.values.map { it.first }.sortedBy { it.title }

    override fun observeCharts(): Flow<List<Chart>> = stored.map { map -> map.values.map { it.first } }

    override suspend fun saveScanned(chart: Chart, scanId: Long) {
        stored.value = stored.value + (chart.id to (chart to scanId))
    }

    override suspend fun removeNotSeenIn(scanId: Long) {
        removedScans += scanId
        stored.value = stored.value.filterValues { it.second == scanId }
    }
}

fun chartText(title: String?, artist: String?, notes: String = "  192 = N 0 0") = buildString {
    appendLine("[Song]")
    appendLine("{")
    if (title != null) appendLine("  Name = \"$title\"")
    if (artist != null) appendLine("  Artist = \"$artist\"")
    appendLine("}")
    appendLine("[ExpertSingle]")
    appendLine("{")
    appendLine(notes)
    appendLine("}")
}
