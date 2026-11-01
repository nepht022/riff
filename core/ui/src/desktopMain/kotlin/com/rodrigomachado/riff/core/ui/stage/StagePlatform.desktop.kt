package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.asComposeShader
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.skiaPaint
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.MipmapMode

/** Raio do `BlurMaskFilter` do Android → sigma do Skia (a mesma conversão que o Android faz por dentro). */
private const val BLUR_SIGMA_SCALE = 0.57735f
private const val BLUR_SIGMA_BIAS = 0.5f

internal actual fun tilePaint(blurRadius: Float): Paint = Paint().apply {
    isAntiAlias = true
    skiaPaint.isDither = false
    if (blurRadius > 0f) {
        skiaPaint.maskFilter =
            MaskFilter.makeBlur(FilterBlurMode.NORMAL, blurRadius * BLUR_SIGMA_SCALE + BLUR_SIGMA_BIAS)
    }
}

internal actual fun defaultTilePaint(): Paint = tilePaint()

/** O filtro da textura no PC vem do próprio shader ([tiledShader]). */
internal actual fun texturePaint(): Paint = Paint()

internal actual fun ImageBitmap.prepareMipmaps() = Unit

internal actual fun TexturePerspective(): TexturePerspective = DesktopTexturePerspective()

/** A homografia calculada em comum ([quadToQuad]) numa matriz do Compose. */
private class DesktopTexturePerspective : TexturePerspective {
    private val matrix = Matrix()

    override fun set(src: FloatArray, dst: FloatArray) {
        val m = quadToQuad(src, dst)
        val v = matrix.values
        matrix.reset()
        v[Matrix.ScaleX] = m[0]
        v[Matrix.SkewX] = m[1]
        v[Matrix.TranslateX] = m[2]
        v[Matrix.SkewY] = m[3]
        v[Matrix.ScaleY] = m[4]
        v[Matrix.TranslateY] = m[5]
        v[Matrix.Perspective0] = m[6]
        v[Matrix.Perspective1] = m[7]
        v[Matrix.Perspective2] = m[8]
    }

    override fun concatTo(canvas: Canvas) {
        canvas.concat(matrix)
    }
}

/**
 * O shader de imagem do Compose no PC amostra sem filtro (vizinho mais próximo): esticado, o ladrilho sai em
 * blocos e cintila perto do horizonte. Aqui o Skia filtra linear e usa mipmap.
 */
internal actual fun tiledShader(image: ImageBitmap): Shader = Image.makeFromBitmap(image.asSkiaBitmap()).makeShader(
    FilterTileMode.CLAMP,
    FilterTileMode.REPEAT,
    FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
    null,
).asComposeShader()
