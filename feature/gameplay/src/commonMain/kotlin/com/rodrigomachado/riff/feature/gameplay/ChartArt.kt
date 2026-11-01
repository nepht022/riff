package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.ui.graphics.ImageBitmap
import com.rodrigomachado.riff.core.data.game.ChartArtLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** A imagem do mapa pronta para desenhar atrás da pista, ou nada (sem imagem, ou imagem que não abre). */
fun interface ChartArtImages {
    suspend fun load(chartId: String): ImageBitmap?
}

/** A imagem é enfeite: qualquer falha ao lê-la só deixa a pista sem ela. */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
suspend fun ChartArtImages.loadOrNull(chartId: String): ImageBitmap? = try {
    load(chartId)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

class DecodedChartArtImages @Inject constructor(private val loader: ChartArtLoader) : ChartArtImages {
    override suspend fun load(chartId: String): ImageBitmap? =
        // A leitura no IO (dentro do loader); a decodificação, trabalho de CPU, no Default.
        loader.load(chartId) { bytes -> withContext(Dispatchers.Default) { ChartArtDecoder.decode(bytes) } }
}

/** Decodifica já reduzida (lado maior perto de [maxSide]): é fundo da pista, não foto. */
object ChartArtDecoder {
    fun decode(bytes: ByteArray, maxSide: Int = CHART_ART_MAX_SIDE): ImageBitmap? = decodeReduced(bytes, maxSide)
}

private const val CHART_ART_MAX_SIDE = 1280

/**
 * A decodificação de cada plataforma, ou null se os bytes não forem uma imagem. No celular, o lado maior fica
 * entre [maxSide] e o dobro dele (redução por potência de 2); no PC, no máximo [maxSide].
 */
internal expect fun decodeReduced(bytes: ByteArray, maxSide: Int): ImageBitmap?
