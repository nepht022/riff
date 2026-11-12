package com.rodrigomachado.riff.core.ui.art

import androidx.compose.ui.graphics.ImageBitmap
import com.rodrigomachado.riff.core.model.ChartArtUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * A imagem do mapa pronta para desenhar (atrás da pista, ou a capa nas listas), ou nada (sem imagem, ou imagem que
 * não abre). A leitura do arquivo fica em quem implementa (`DecodedChartArtImages`, montado pelo app no celular e
 * pelo `DesktopGraph` no PC); as telas só recebem esta interface.
 */
fun interface ChartArtImages {
    suspend fun load(chartId: String): ImageBitmap?
}

/** A imagem é enfeite: qualquer falha ao lê-la só deixa a tela sem ela. */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
suspend fun ChartArtImages.loadOrNull(chartId: String): ImageBitmap? = try {
    load(chartId)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

/**
 * Quantas capas ficam na memória: algumas telas de lista, sem pesar (cada capa tem até ~150 KB, 192×192 em ARGB; no
 * celular, a redução por potência de 2 pode deixá-la até 4 vezes maior): poucos MB no todo.
 */
const val CHART_THUMB_CACHE_SIZE = 64

/** Quantas capas são lidas ao mesmo tempo: a lista que abre pede todas de uma vez, e o disco e a memória sentem. */
const val CHART_THUMB_PARALLEL_READS = 3

/**
 * As capas das listas (Biblioteca, Resultado, sala, tela inicial): lidas de [source] em [dispatcher] (nunca na
 * thread da tela) e guardadas por mapa, as [capacity] usadas por último, para a lista não reler a imagem ao rolar.
 * "Sem imagem" e "imagem que não abre" também ficam guardados (as iniciais, sem tentar de novo); um cancelamento
 * (o item saiu da tela no meio da leitura) e o "não deu para procurar" ([ChartArtUnavailableException]: o mapa
 * ainda não chegou, a pasta perdeu o acesso), não. [clear] esquece tudo (ao fim de cada importação). No máximo
 * [parallelReads] leituras de uma vez (as outras esperam a vez). [source] deve decodificar já reduzida (é capa, não
 * fundo).
 */
class ChartThumbImages(
    private val source: ChartArtImages,
    private val capacity: Int = CHART_THUMB_CACHE_SIZE,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    parallelReads: Int = CHART_THUMB_PARALLEL_READS,
) : ChartArtImages {
    private val reads = Semaphore(parallelReads)

    /** O que se lê de um mapa: a imagem ou nada. */
    private class Entry(val image: ImageBitmap?)

    /**
     * Os guardados, na ordem do uso (o primeiro é o usado há mais tempo), e quantas vezes a memória foi esquecida
     * ([clear]): uma leitura começada antes não grava o que leu da pasta antiga. Sem `equals` de propósito: o
     * StateFlow ignora um valor igual ao atual, e o mesmo mapa só reordenado é igual.
     */
    private class Snapshot(val entries: Map<String, Entry>, val generation: Int)

    // Trocado de uma vez, imutável (seguro entre threads, e o [cached] lê sem esperar).
    private val state = MutableStateFlow(Snapshot(emptyMap(), generation = 0))

    /** A capa já na memória, sem ler nada (a primeira imagem da composição, sem piscar as iniciais ao rolar). */
    fun cached(chartId: String): ImageBitmap? = state.value.entries[chartId]?.image

    override suspend fun load(chartId: String): ImageBitmap? {
        state.value.entries[chartId]?.let { entry ->
            keep(chartId, entry)
            return entry.image
        }
        val generation = state.value.generation
        val entry = reads.withPermit {
            // Na fila, outra leitura do mesmo mapa pode ter acabado: sem ler de novo.
            state.value.entries[chartId] ?: withContext(dispatcher) { read(chartId) }
        } ?: return null
        keep(chartId, entry, generation)
        return entry.image
    }

    /** Esquece as capas guardadas (a importação pode ter trazido mapas e imagens novos). */
    fun clear() = state.update { Snapshot(emptyMap(), it.generation + 1) }

    /** O que [source] tem do mapa, ou null quando não deu para procurar (não vai para a memória). */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun read(chartId: String): Entry? = try {
        Entry(source.load(chartId))
    } catch (e: CancellationException) {
        throw e
    } catch (e: ChartArtUnavailableException) {
        null
    } catch (e: Exception) {
        Entry(null)
    }

    private fun keep(chartId: String, entry: Entry, generation: Int = state.value.generation) =
        state.update { current ->
            if (current.generation != generation) return@update current
            val updated = current.entries - chartId + (chartId to entry)
            Snapshot(if (updated.size > capacity) updated - updated.keys.first() else updated, generation)
        }
}
