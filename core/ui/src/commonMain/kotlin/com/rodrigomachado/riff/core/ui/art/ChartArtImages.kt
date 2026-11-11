package com.rodrigomachado.riff.core.ui.art

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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

/** Quantas capas ficam na memória: algumas telas de lista, sem pesar (cada capa pequena tem poucas centenas de KB). */
const val CHART_THUMB_CACHE_SIZE = 64

/**
 * As capas das listas (Biblioteca, Resultado, sala, tela inicial): lidas de [source] em [dispatcher] (nunca na
 * thread da tela) e guardadas por mapa, as [capacity] usadas por último, para a lista não reler a imagem ao rolar.
 * "Sem imagem" e "imagem que não abre" também ficam guardados (as iniciais, sem tentar de novo); um cancelamento
 * (o item saiu da tela no meio da leitura), não. [source] deve decodificar já reduzida (é capa, não fundo).
 */
class ChartThumbImages(
    private val source: ChartArtImages,
    private val capacity: Int = CHART_THUMB_CACHE_SIZE,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ChartArtImages {
    /** O que se lê de um mapa: a imagem ou nada. */
    private class Entry(val image: ImageBitmap?)

    /**
     * Os guardados, na ordem do uso (o primeiro é o usado há mais tempo). Sem `equals` de propósito: o StateFlow
     * ignora um valor igual ao atual, e o mesmo mapa só reordenado é igual.
     */
    private class Snapshot(val entries: Map<String, Entry>)

    // Trocado de uma vez, imutável (seguro entre threads, e o [cached] lê sem esperar).
    private val state = MutableStateFlow(Snapshot(emptyMap()))

    /** A capa já na memória, sem ler nada (a primeira imagem da composição, sem piscar as iniciais ao rolar). */
    fun cached(chartId: String): ImageBitmap? = state.value.entries[chartId]?.image

    override suspend fun load(chartId: String): ImageBitmap? {
        state.value.entries[chartId]?.let { entry ->
            keep(chartId, entry)
            return entry.image
        }
        val image = withContext(dispatcher) { source.loadOrNull(chartId) }
        keep(chartId, Entry(image))
        return image
    }

    private fun keep(chartId: String, entry: Entry) = state.update { current ->
        val updated = current.entries - chartId + (chartId to entry)
        Snapshot(if (updated.size > capacity) updated - updated.keys.first() else updated)
    }
}
