package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import com.rodrigomachado.riff.core.model.HighwayArt
import com.rodrigomachado.riff.core.model.HighwayBackground
import com.rodrigomachado.riff.core.model.TrackLook
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import kotlin.random.Random

/** Cores da moldura da pista (aro de fora e filete de dentro) para cada fundo. */
data class EdgeColors(val outer: Color, val inner: Color)

fun edgeColors(kind: HighwayBackground): EdgeColors = when (kind) {
    HighwayBackground.STAGE -> EdgeColors(RiffColors.TourRed, RiffColors.Paper)
    HighwayBackground.AMPLIFIER -> EdgeColors(Color(0xFFC8AA5A), Color(0xFF282828))
    HighwayBackground.LEATHER -> EdgeColors(Color(0xFF96969B), Color(0xFF3C2314))
    HighwayBackground.FRETBOARD -> EdgeColors(Color(0xFFE6E1D2), Color(0xFF28190F))
    HighwayBackground.FLAMES -> EdgeColors(Color(0xFFFF7800), Color(0xFF1E0A05))
    HighwayBackground.NEON -> EdgeColors(Color(0xFFFF28C8), Color(0xFF28DCFF))
    HighwayBackground.DIAMOND_PLATE -> EdgeColors(Color(0xFFC8C8CD), Color(0xFF3C3C41))
}

/**
 * Ladrilhos da pista, [WIDTH] × [DEPTH] px, que se repetem na profundidade a cada [TILE_DISTANCE] (unidade da
 * `HighwayGeometry`). Gerados uma vez por escolha, com semente fixa: o mesmo desenho toda vez. Tudo desenhado em
 * código; o que passa da borda de baixo volta em cima, para a repetição não ter emenda.
 */
object HighwayTexture {
    const val WIDTH = 256
    const val DEPTH = 512
    const val TILE_DISTANCE = 2f
    private const val SEED = 7
    private val StampInk = Color(0xFFEADCBA)

    /** Opacidade das estampas: claras, para não brigar com as notas. */
    private val StampAlpha = Color(0x3C000000)

    // Uma de cada, guardada: as miniaturas das Configurações e a partida reaproveitam (no máximo ~6 MB).
    private val made = HashMap<Enum<*>, ImageBitmap>()

    fun background(kind: HighwayBackground): ImageBitmap? =
        if (kind == HighwayBackground.STAGE) null else remembered(kind) { makeBackground(kind) }

    fun art(kind: HighwayArt): ImageBitmap? = if (kind == HighwayArt.NONE) null else remembered(kind) { makeArt(kind) }

    // Gerado fora da trava: quem pede um ladrilho pronto não espera outro que está sendo gerado ao mesmo tempo.
    private fun remembered(kind: Enum<*>, make: () -> ImageBitmap): ImageBitmap {
        synchronized(made) { made[kind] }?.let { return it }
        val fresh = make()
        return synchronized(made) { made.getOrPut(kind) { fresh } }
    }

    /**
     * Gera os ladrilhos de [look] (chamar fora da thread da tela): o primeiro quadro da partida só pega o pronto, sem
     * o engasgo de gerar.
     */
    fun prepare(look: TrackLook) {
        background(look.background)
        art(look.art)
    }

    /** Todos os ladrilhos: as listas das Configurações mostram as miniaturas de cada um. */
    fun prepareAll() {
        HighwayBackground.entries.forEach(::background)
        HighwayArt.entries.forEach(::art)
    }

    fun isPrepared(look: TrackLook): Boolean = synchronized(made) {
        (look.background == HighwayBackground.STAGE || look.background in made) &&
            (look.art == HighwayArt.NONE || look.art in made)
    }

    private fun makeArt(kind: HighwayArt): ImageBitmap {
        val bitmap = ImageBitmap(WIDTH, DEPTH)
        val canvas = Canvas(bitmap)
        val ink = tilePaint().apply { color = StampInk }
        val holes = tilePaint().apply { blendMode = BlendMode.Clear }
        HighwayStamps.draw(kind, canvas, ink, holes)
        // Desenhadas opacas e clareadas de uma vez: partes sobrepostas não ficam mais fortes.
        canvas.fill(StampAlpha, BlendMode.DstIn)
        bitmap.prepareMipmaps() // perto do horizonte encolhe ~4×: sem mipmap, cintila
        return bitmap
    }

    private fun makeBackground(kind: HighwayBackground): ImageBitmap {
        val bitmap = ImageBitmap(WIDTH, DEPTH)
        HighwayBackgrounds.draw(kind, Canvas(bitmap), Random(SEED))
        bitmap.prepareMipmaps() // perto do horizonte encolhe ~4×: sem mipmap, as linhas finas cintilam
        return bitmap
    }
}

/** Pinta o ladrilho inteiro com [color] (o `drawColor` do canvas do Android). */
internal fun Canvas.fill(color: Color, blendMode: BlendMode = BlendMode.SrcOver) {
    val paint = defaultTilePaint().apply {
        this.color = color
        this.blendMode = blendMode
    }
    drawRect(0f, 0f, HighwayTexture.WIDTH.toFloat(), HighwayTexture.DEPTH.toFloat(), paint)
}

/** Os fundos de rock: cada um preenche o ladrilho todo e repete na profundidade sem emenda. */
internal object HighwayBackgrounds {
    private const val WIDTH = HighwayTexture.WIDTH
    private const val DEPTH = HighwayTexture.DEPTH

    fun draw(kind: HighwayBackground, canvas: Canvas, random: Random) {
        when (kind) {
            HighwayBackground.AMPLIFIER -> amplifier(canvas, random)
            HighwayBackground.LEATHER -> leather(canvas, random)
            HighwayBackground.FRETBOARD -> fretboard(canvas, random)
            HighwayBackground.FLAMES -> flames(canvas, random)
            HighwayBackground.NEON -> neon(canvas, random)
            HighwayBackground.DIAMOND_PLATE -> diamondPlate(canvas, random)
            HighwayBackground.STAGE -> Unit
        }
    }

    private fun speckle(canvas: Canvas, random: Random, base: Int, spread: Int, size: Float) {
        canvas.fill(Color(base))
        val paint = defaultTilePaint()
        repeat(WIDTH * DEPTH / 40) {
            paint.color = Color(shift(base, random.nextInt(-spread, spread + 1)))
            val x = random.nextFloat() * WIDTH
            val y = random.nextFloat() * DEPTH
            canvas.drawRect(x, y, x + size, y + size, paint)
            canvas.drawRect(x, y - DEPTH, x + size, y - DEPTH + size, paint)
        }
    }

    private fun amplifier(canvas: Canvas, random: Random) {
        speckle(canvas, random, 0xFF1E1E20.toInt(), 14, 2f)
        val gold = defaultTilePaint().apply {
            color = Color(0xFF78643C)
            strokeWidth = 2f
        }
        val dim = defaultTilePaint().apply {
            color = Color(0xFF5A4B2D)
            strokeWidth = 1f
        }
        for (y in 8 until DEPTH step 16) {
            canvas.drawLine(Offset(0f, y.toFloat()), Offset(WIDTH.toFloat(), y.toFloat()), gold)
        }
        for (x in 8 until WIDTH step 16) {
            canvas.drawLine(Offset(x.toFloat(), 0f), Offset(x.toFloat(), DEPTH.toFloat()), dim)
        }
    }

    private fun leather(canvas: Canvas, random: Random) {
        speckle(canvas, random, 0xFF5A3218.toInt(), 18, 3f)
        val blotch = tilePaint(blurRadius = 18f)
        repeat(14) {
            blotch.color = if (random.nextBoolean()) Color(0x30000000) else Color(0x20FFDCA0)
            val x = random.nextFloat() * WIDTH
            val y = random.nextFloat() * DEPTH
            val r = 30f + random.nextFloat() * 30f
            for (dy in intArrayOf(-DEPTH, 0, DEPTH)) {
                canvas.drawCircle(Offset(x, y + dy), r, blotch)
            }
        }
        val rivet = tilePaint().apply { color = Color(0xFFBEBEC3) }
        val shine = tilePaint().apply { color = Color(0xFFFAFAFA) }
        for (y in 32 until DEPTH step 64) {
            for (x in floatArrayOf(14f, WIDTH - 14f)) {
                canvas.drawCircle(Offset(x, y.toFloat()), 8f, rivet)
                canvas.drawCircle(Offset(x - 2.5f, y - 3f), 3f, shine)
            }
        }
    }

    private fun fretboard(canvas: Canvas, random: Random) {
        canvas.fill(Color(0xFF3E2414))
        val grain = defaultTilePaint()
        repeat(90) {
            grain.color = if (random.nextBoolean()) Color(0x22000000) else Color(0x18FFC896)
            grain.strokeWidth = 1f + random.nextFloat() * 2f
            val x = random.nextFloat() * WIDTH
            canvas.drawLine(Offset(x, 0f), Offset(x, DEPTH.toFloat()), grain)
        }
        val fret = defaultTilePaint().apply { color = Color(0xFFC8C8CD) }
        val inlay = tilePaint().apply { color = Color(0xFFEBE8DE) }
        for (i in 0 until 4) {
            val y = i * DEPTH / 4f + DEPTH / 16f
            canvas.drawRect(0f, y, WIDTH.toFloat(), y + 5f, fret)
            if (i % 2 == 1) canvas.drawCircle(Offset(WIDTH / 2f, y + DEPTH / 8f), 11f, inlay)
        }
    }

    private fun flames(canvas: Canvas, random: Random) {
        canvas.fill(Color(0xFF0E0A0A))
        val glow = tilePaint(blurRadius = 4f)
        val colors = listOf(Color(0xFFC81E0A), Color(0xFFFF7800), Color(0xFFFFD23C))
        val m = Matrix()
        for (side in 0..1) {
            for (k in 0 until 5) {
                val h = 70f + random.nextFloat() * 60f
                val w = 24f + random.nextFloat() * 16f
                val baseY = k * DEPTH / 5f + DEPTH / 10f
                val x = if (side == 0) w * 0.6f else WIDTH - w * 0.6f
                for ((layer, color) in colors.withIndex()) {
                    val f = 1f - layer * 0.28f
                    glow.color = color
                    for (dy in intArrayOf(-DEPTH, 0, DEPTH)) {
                        val copy = Path().also { RockShapes.flame(it) }
                        m.setScaleTranslate(w * f, h * f / 2f, x, baseY + dy)
                        copy.transform(m)
                        canvas.drawPath(copy, glow)
                    }
                }
            }
        }
    }

    private fun neon(canvas: Canvas, random: Random) {
        canvas.fill(Color(0xFF1E0E36))
        val glow = tilePaint(blurRadius = 8f).apply {
            style = PaintingStyle.Stroke
            strokeWidth = 7f
        }
        val core = tilePaint().apply {
            style = PaintingStyle.Stroke
            strokeWidth = 2f
            color = Color.White
        }
        repeat(4) { k ->
            val path = Path()
            var x = 40f + random.nextFloat() * (WIDTH - 80f)
            var y = random.nextFloat() * DEPTH
            path.moveTo(x, y)
            repeat(5) {
                x = (x + (if (random.nextBoolean()) 1 else -1) * (15f + random.nextFloat() * 30f)).coerceIn(
                    10f,
                    WIDTH - 10f,
                )
                y += 30f + random.nextFloat() * 40f
                path.lineTo(x, y)
            }
            glow.color = if (k % 2 == 0) Color(0xFFFF28C8) else Color(0xFF28DCFF)
            // Uma altura para cima e para baixo: o brilho que passa de qualquer borda reaparece do outro lado.
            for (dy in intArrayOf(-DEPTH, 0, DEPTH)) {
                val shifted = Path().apply { addPath(path, Offset(0f, dy.toFloat())) }
                canvas.drawPath(shifted, glow)
                canvas.drawPath(shifted, core)
            }
        }
    }

    private fun diamondPlate(canvas: Canvas, random: Random) {
        speckle(canvas, random, 0xFF6E6E72.toInt(), 12, 1f)
        val raised = tilePaint().apply { color = Color(0xFFA5A5AA) }
        val shadow = tilePaint().apply { color = Color(0xFF46464A) }
        val step = 32
        for (row in 0 until DEPTH / step) {
            for (col in -1..WIDTH / step) {
                val cx = col * step + if (row % 2 == 1) step / 2f else 0f
                val cy = row * step + step / 2f
                canvas.drawPath(diamond(cx + 1.5f, cy + 1.5f), shadow)
                canvas.drawPath(diamond(cx, cy), raised)
            }
        }
    }

    private fun diamond(cx: Float, cy: Float) = Path().apply {
        moveTo(cx - 11f, cy + 3f)
        lineTo(cx + 8f, cy - 8f)
        lineTo(cx + 11f, cy - 3f)
        lineTo(cx - 8f, cy + 8f)
        close()
    }

    private fun shift(color: Int, by: Int): Int {
        fun ch(shiftBits: Int) = (((color shr shiftBits) and 0xFF) + by).coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

/** Escala e depois translada (o `postScale` + `postTranslate` do Android), sem rotação. */
internal fun Matrix.setScaleTranslate(sx: Float, sy: Float, tx: Float, ty: Float) {
    reset()
    values[Matrix.ScaleX] = sx
    values[Matrix.ScaleY] = sy
    values[Matrix.TranslateX] = tx
    values[Matrix.TranslateY] = ty
}
