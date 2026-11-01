package com.rodrigomachado.riff.feature.gameplay

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import kotlin.math.roundToInt

@Suppress("TooGenericExceptionCaught", "SwallowedException") // bytes que não são imagem: a pista fica sem ela
internal actual fun decodeReduced(bytes: ByteArray, maxSide: Int): ImageBitmap? {
    val image = try {
        Image.makeFromEncoded(bytes)
    } catch (e: Exception) {
        return null
    }
    val longest = maxOf(image.width, image.height)
    if (longest <= 0) return null
    if (longest <= maxSide) return image.toComposeImageBitmap()
    // Redesenha menor com filtro linear (a imagem cheia de uma capa grande pesa à toa na memória da placa).
    val scale = maxSide.toFloat() / longest
    val width = (image.width * scale).roundToInt().coerceAtLeast(1)
    val height = (image.height * scale).roundToInt().coerceAtLeast(1)
    val surface = Surface.makeRasterN32Premul(width, height)
    surface.canvas.drawImageRect(
        image,
        Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
        Rect.makeWH(width.toFloat(), height.toFloat()),
        FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE),
        null,
        true,
    )
    return surface.makeImageSnapshot().toComposeImageBitmap()
}
