package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.ui.graphics.ImageBitmap
import com.rodrigomachado.riff.core.data.game.ChartArtLoader
import com.rodrigomachado.riff.core.ui.art.ChartArtImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * As imagens dos mapas lidas pelo [loader] e decodificadas já reduzidas (lado maior perto de [maxSide]): o fundo da
 * pista com o padrão; as capas das listas com [ChartArtDecoder.THUMB_MAX_SIDE] (dentro de um `ChartThumbImages`).
 */
class DecodedChartArtImages(private val loader: ChartArtLoader, private val maxSide: Int) : ChartArtImages {
    /** O fundo da pista (o que o Hilt injeta como [ChartArtImages]). */
    @Inject constructor(loader: ChartArtLoader) : this(loader, ChartArtDecoder.TRACK_MAX_SIDE)

    override suspend fun load(chartId: String): ImageBitmap? =
        // A leitura no IO (dentro do loader); a decodificação, trabalho de CPU, no Default.
        loader.load(chartId) { bytes -> withContext(Dispatchers.Default) { ChartArtDecoder.decode(bytes, maxSide) } }
}

/** Decodifica já reduzida (lado maior perto de [maxSide]): é fundo da pista ou capa, não foto. */
object ChartArtDecoder {
    /** O fundo da pista. */
    const val TRACK_MAX_SIDE = 1280

    /** A capa das listas (até ~96 dp nas telas de densidade alta). */
    const val THUMB_MAX_SIDE = 256

    fun decode(bytes: ByteArray, maxSide: Int = TRACK_MAX_SIDE): ImageBitmap? = decodeReduced(bytes, maxSide)
}

/**
 * A decodificação de cada plataforma, ou null se os bytes não forem uma imagem. No celular, o lado maior fica
 * entre [maxSide] e o dobro dele (redução por potência de 2); no PC, no máximo [maxSide].
 */
internal expect fun decodeReduced(bytes: ByteArray, maxSide: Int): ImageBitmap?
