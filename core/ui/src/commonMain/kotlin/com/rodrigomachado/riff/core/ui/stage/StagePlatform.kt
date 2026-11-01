package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint

// O que a pista pede de cada plataforma: o resto do desenho é o mesmo no celular e no PC.

/**
 * Pincel suave para assar os ladrilhos, igual ao `Paint(ANTI_ALIAS_FLAG)` do Android (sem o dither que o Compose
 * liga). Com [blurRadius] > 0, as bordas saem desfocadas com esse raio (o do `BlurMaskFilter` do Android).
 */
internal expect fun tilePaint(blurRadius: Float = 0f): Paint

/** O `Paint()` padrão do Android (suave a partir do Android 14, serrilhado antes); no PC, suave. */
internal expect fun defaultTilePaint(): Paint

/** Pincel que estampa o ladrilho na pista, filtrando ao esticar e ao encolher. */
internal expect fun texturePaint(): Paint

/** Prepara o ladrilho para encolher perto do horizonte: mipmap no Android; no PC, nada. */
internal expect fun ImageBitmap.prepareMipmaps()

/** Perspectiva do ladrilho na pista, posta no canvas sem alocar a cada quadro. */
internal interface TexturePerspective {
    /** Leva os 4 cantos [src] aos 4 cantos [dst] (x, y alternados). */
    fun set(src: FloatArray, dst: FloatArray)

    fun concatTo(canvas: Canvas)
}

internal expect fun TexturePerspective(): TexturePerspective
