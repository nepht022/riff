package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

/** Trastes que cabem no array: com a velocidade mínima (0,5×) aparecem uns 11. */
private const val MAX_FRETS = 32
private const val OUTER_STROKE_DP = 7f
private const val INNER_STROKE_DP = 3f
private const val FRET_LINE_DP = 2f

/**
 * Formas e pincéis da pista prontos, para desenhar cada quadro sem alocar (spec do polimento §3). O que
 * depende do tamanho da tela é refeito em [update] só quando o tamanho (ou a densidade) muda; gemas, alvos e
 * chamas usam pincéis em "tamanho 1", posicionados por transformação.
 */
class StageCache {
    private var builtFor: Size? = null
    private var builtDensity = 0f

    var geometry = HighwayGeometry(Size(1f, 1f))
        private set

    internal val outline = Path()
    internal var highwayBrush: Brush = Brush.verticalGradient(listOf(HighwayFar, HighwayNear))
        private set
    internal var outerStroke = Stroke(1f)
        private set
    internal var innerStroke = Stroke(1f)
        private set
    internal var laneLineWidth = 1f
        private set
    internal var fretLineWidth = 1f
        private set

    /** Trastes do quadro atual: as [fretCount] primeiras posições, em distância à frente da linha de acerto. */
    val fretDistances = FloatArray(MAX_FRETS)
    var fretCount = 0

    /** Aro cromado em tamanho 1 (de −1 a 1 na horizontal). */
    internal val chrome: Brush =
        Brush.horizontalGradient(listOf(ChromeLight, ChromeMid, ChromeLight), startX = -1f, endX = 1f)

    fun update(size: Size, density: Float): HighwayGeometry {
        if (size == builtFor && density == builtDensity) return geometry
        builtFor = size
        builtDensity = density
        val g = HighwayGeometry(size)
        geometry = g
        val far = g.visibleDistance
        val near = g.nearDistance
        outline.rewind()
        outline.moveTo(g.leftAt(far), g.yAt(far))
        outline.lineTo(g.leftAt(far) + g.widthAt(far), g.yAt(far))
        outline.lineTo(g.leftAt(near) + g.widthAt(near), g.yAt(near))
        outline.lineTo(g.leftAt(near), g.yAt(near))
        outline.close()
        highwayBrush = Brush.verticalGradient(listOf(HighwayFar, HighwayNear), g.yAt(far), g.yAt(near))
        outerStroke = Stroke(width = OUTER_STROKE_DP * density)
        innerStroke = Stroke(width = INNER_STROKE_DP * density)
        laneLineWidth = density
        fretLineWidth = FRET_LINE_DP * density
        return g
    }
}
