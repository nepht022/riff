package com.rodrigomachado.riff.core.ui.stage

import android.graphics.Shader
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.rodrigomachado.riff.core.ui.theme.RiffColors

/** A superfície de sempre (Palco): degradê escuro e trama fina. */
internal fun DrawScope.drawStageSurface(cache: StageCache) {
    val geometry = cache.geometry
    drawPath(cache.outline, cache.highwayBrush)
    val weave = RiffColors.Paper.copy(alpha = WEAVE_ALPHA)
    for (k in 0..WEAVE_LONGITUDINAL) along(geometry, k.toFloat() / WEAVE_LONGITUDINAL, weave, 1f)
    var distance = geometry.nearDistance
    while (distance < geometry.visibleDistance) {
        across(geometry, distance, weave, 1f)
        distance += WEAVE_STEP
    }
}

internal fun DrawScope.drawTexture(cache: StageCache, shader: Shader) {
    cache.texturePaint.shader = shader
    drawIntoCanvas {
        val canvas = it.nativeCanvas
        canvas.save()
        canvas.concat(cache.textureMatrix)
        canvas.drawRect(0f, cache.textureTop, HighwayTexture.WIDTH.toFloat(), cache.textureBottom, cache.texturePaint)
        canvas.restore()
    }
}
