package com.rodrigomachado.riff.core.ui.stage

import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposePaint
import androidx.compose.ui.graphics.nativeCanvas

internal actual fun tilePaint(blurRadius: Float): Paint =
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        if (blurRadius > 0f) maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
    }.asComposePaint()

// `Paint()` não é `Paint(0)`: o construtor sem argumentos liga as opções padrão da versão do Android.
internal actual fun defaultTilePaint(): Paint = android.graphics.Paint().asComposePaint()

internal actual fun texturePaint(): Paint =
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
        .asComposePaint()

internal actual fun ImageBitmap.prepareMipmaps() {
    asAndroidBitmap().setHasMipMap(true)
}

internal actual fun TexturePerspective(): TexturePerspective = AndroidTexturePerspective()

/** A matriz do Android, `setPolyToPoly` e `concat` direto no canvas: a conversão do Compose alocaria por quadro. */
private class AndroidTexturePerspective : TexturePerspective {
    private val matrix = Matrix()

    override fun set(src: FloatArray, dst: FloatArray) {
        matrix.setPolyToPoly(src, 0, dst, 0, CORNERS)
    }

    override fun concatTo(canvas: Canvas) {
        canvas.nativeCanvas.concat(matrix)
    }

    private companion object {
        const val CORNERS = 4
    }
}
