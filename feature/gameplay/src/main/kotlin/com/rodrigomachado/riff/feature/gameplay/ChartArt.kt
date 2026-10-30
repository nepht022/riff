package com.rodrigomachado.riff.feature.gameplay

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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

/** Decodifica já reduzida (lado maior entre [maxSide] e o dobro dele): é fundo da pista, não foto. */
object ChartArtDecoder {
    fun decode(bytes: ByteArray, maxSide: Int = 1280): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }
}
