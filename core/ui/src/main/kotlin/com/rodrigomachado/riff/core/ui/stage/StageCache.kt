package com.rodrigomachado.riff.core.ui.stage

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.rodrigomachado.riff.core.model.HighwayBackground
import com.rodrigomachado.riff.core.model.TrackLook
import com.rodrigomachado.riff.core.ui.theme.LaneColors

/** Trastes que cabem no array: com a velocidade mínima (0,5×) aparecem uns 11. */
private const val MAX_FRETS = 32
private const val OUTER_STROKE_DP = 7f
private const val INNER_STROKE_DP = 3f
private const val FRET_LINE_DP = 2f

private const val LANES = 5

/** Quanto a textura escurece no fundo da pista. */
private const val FAR_FADE = 0.85f

/** Tampa da gema em tamanho 1: o mesmo degradê de antes, com o centro do brilho no canto de cima. */
private fun unitGemCap(color: Color) = Brush.radialGradient(
    0f to Color.White,
    0.45f to color,
    1f to lerp(color, Color.Black, 0.6f),
    center = Offset(-0.25f, -GEM_SIDE - TILT * 0.3f),
    radius = 0.8f,
)

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

    /** O visual escolhido; trocar refaz as texturas na próxima [update]. */
    var look: TrackLook = TrackLook.DEFAULT

    /** Quanto a música andou, em unidades de distância: a textura rola junto com os trastes. */
    var scrollDistance = 0f

    private var texturesFor: TrackLook? = null
    internal var backgroundShader: BitmapShader? = null
        private set
    internal var artShader: BitmapShader? = null
        private set
    internal var edges = edgeColors(HighwayBackground.STAGE)
        private set
    internal val texturePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    internal val androidOutline get() = outline.asAndroidPath()

    /** Escurece a textura para o fundo, como o degradê da pista de sempre. */
    internal var fade: Brush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent))
        private set
    private val textureMatrix = Matrix()
    private val polySrc = FloatArray(8)
    private val polyDst = FloatArray(8)

    private fun rebuildTexturesIfNeeded() {
        if (texturesFor == look) return
        texturesFor = look
        backgroundShader = HighwayTexture.background(look.background)?.let(::tiled)
        artShader = HighwayTexture.art(look.art)?.let(::tiled)
        edges = edgeColors(look.background)
    }

    private fun tiled(bitmap: Bitmap) = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.REPEAT)

    /**
     * Põe o ladrilho na pista em perspectiva, já rolado: a faixa do ladrilho que cobre da borda de baixo da pista
     * ao fundo vai para o trapézio. `setPolyToPoly` com 4 pontos é exata para um plano visto em perspectiva.
     */
    internal fun updateTextureMatrix() {
        if (backgroundShader == null && artShader == null) return
        val g = geometry
        val near = g.nearDistance
        val far = g.visibleDistance
        val perUnit = HighwayTexture.DEPTH / HighwayTexture.TILE_DISTANCE
        // Só a fase importa (o ladrilho se repete): mantém os números pequenos numa música longa.
        val scroll = scrollDistance.mod(HighwayTexture.TILE_DISTANCE)
        val vNear = -(near + scroll) * perUnit
        val vFar = -(far + scroll) * perUnit
        val w = HighwayTexture.WIDTH.toFloat()
        polySrc[0] = 0f
        polySrc[1] = vNear
        polySrc[2] = w
        polySrc[3] = vNear
        polySrc[4] = w
        polySrc[5] = vFar
        polySrc[6] = 0f
        polySrc[7] = vFar
        polyDst[0] = g.leftAt(near)
        polyDst[1] = g.yAt(near)
        polyDst[2] = g.leftAt(near) + g.widthAt(near)
        polyDst[3] = g.yAt(near)
        polyDst[4] = g.leftAt(far) + g.widthAt(far)
        polyDst[5] = g.yAt(far)
        polyDst[6] = g.leftAt(far)
        polyDst[7] = g.yAt(far)
        textureMatrix.setPolyToPoly(polySrc, 0, polyDst, 0, 4)
        backgroundShader?.setLocalMatrix(textureMatrix)
        artShader?.setLocalMatrix(textureMatrix)
    }

    fun update(size: Size, density: Float): HighwayGeometry {
        rebuildTexturesIfNeeded()
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
        fade = Brush.verticalGradient(
            0f to Color.Black.copy(alpha = FAR_FADE),
            1f to Color.Transparent,
            startY = g.yAt(far),
            endY = g.yAt(near),
        )
        outerStroke = Stroke(width = OUTER_STROKE_DP * density)
        innerStroke = Stroke(width = INNER_STROKE_DP * density)
        laneLineWidth = density
        fretLineWidth = FRET_LINE_DP * density
        return g
    }

    private val gemCaps = Array(LANES * 2) { i ->
        val color = LaneColors.forLane(i / 2)
        unitGemCap(if (i % 2 == 1) color.copy(alpha = MISSED_GEM_ALPHA) else color)
    }
    private val fretLit = Array(LANES) {
        Brush.radialGradient(0f to Color.White, 1f to LaneColors.forLane(it), center = Offset.Zero, radius = FRET_INNER)
    }
    private val fretIdle = Brush.radialGradient(
        0f to Color(0xFF222222),
        1f to Color(0xFF0C0C0C),
        center = Offset.Zero,
        radius = FRET_INNER,
    )

    internal fun gemCap(lane: Int, missed: Boolean): Brush = gemCaps[lane * 2 + if (missed) 1 else 0]

    internal fun fretInner(lane: Int, pressed: Boolean): Brush = if (pressed) fretLit[lane] else fretIdle

    internal val pickPath = UnitShapes.pick()
    internal val starPath = UnitShapes.star()
    internal val boltPath = UnitShapes.bolt()
    internal val flamePath = UnitShapes.flame()

    /** Uma forma por camada da chama, refeita no lugar (`rewind`) a cada desenho. */
    internal val flamePaths = Array(FLAME_LAYERS) { Path() }

    /** Degradês das camadas em altura 1: de y = −1 (ponta) a y = 0 (base). */
    internal val flameBrushes = arrayOf(
        Brush.verticalGradient(
            0f to FlameRed.copy(alpha = 0f),
            0.3f to FlameRed,
            0.7f to FlameOrange,
            1f to FlameYellow,
            startY = -1f,
            endY = 0f,
        ),
        Brush.verticalGradient(
            0f to FlameYellow.copy(alpha = 0f),
            0.4f to FlameYellow,
            1f to FlameCore,
            startY = -1f,
            endY = 0f,
        ),
        Brush.verticalGradient(0f to FlameYellow.copy(alpha = 0f), 1f to FlameYellow, startY = -1f, endY = 0f),
    )
}
