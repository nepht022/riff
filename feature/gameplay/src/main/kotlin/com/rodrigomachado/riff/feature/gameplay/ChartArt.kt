package com.rodrigomachado.riff.feature.gameplay

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.rodrigomachado.riff.core.data.game.ChartArtLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** A imagem do mapa pronta para desenhar atrás da pista, ou nada (sem imagem, ou imagem que não abre). */
fun interface ChartArtImages {
    suspend fun load(chartId: String): ImageBitmap?
}

class DecodedChartArtImages @Inject constructor(private val loader: ChartArtLoader) : ChartArtImages {
    override suspend fun load(chartId: String): ImageBitmap? =
        loader.load(chartId)?.let { withContext(Dispatchers.Default) { ChartArtDecoder.decode(it) } }
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
