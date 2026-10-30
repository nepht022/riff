package com.rodrigomachado.riff.core.ui.stage

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.ui.graphics.Color
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
    private const val STAMP_INK = 0xFFEADCBA.toInt()

    /** Opacidade das estampas: claras, para não brigar com as notas. */
    private const val STAMP_ALPHA = 0x3C000000

    // Uma de cada, guardada: as miniaturas das Configurações e a partida reaproveitam (no máximo ~6 MB).
    private val made = HashMap<Enum<*>, Bitmap>()

    fun background(kind: HighwayBackground): Bitmap? =
        if (kind == HighwayBackground.STAGE) null else remembered(kind) { makeBackground(kind) }

    fun art(kind: HighwayArt): Bitmap? = if (kind == HighwayArt.NONE) null else remembered(kind) { makeArt(kind) }

    // Gerado fora da trava: quem pede um ladrilho pronto não espera outro que está sendo gerado ao mesmo tempo.
    private fun remembered(kind: Enum<*>, make: () -> Bitmap): Bitmap {
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

    private fun makeArt(kind: HighwayArt): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, DEPTH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = STAMP_INK }
        val holes = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        HighwayStamps.draw(kind, canvas, ink, holes)
        // Desenhadas opacas e clareadas de uma vez: partes sobrepostas não ficam mais fortes.
        canvas.drawColor(STAMP_ALPHA, PorterDuff.Mode.DST_IN)
        bitmap.setHasMipMap(true) // perto do horizonte encolhe ~4×: sem mipmap, cintila
        return bitmap
    }

    private fun makeBackground(kind: HighwayBackground): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, DEPTH, Bitmap.Config.ARGB_8888)
        HighwayBackgrounds.draw(kind, Canvas(bitmap), Random(SEED))
        bitmap.setHasMipMap(true) // perto do horizonte encolhe ~4×: sem mipmap, as linhas finas cintilam
        return bitmap
    }
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
        canvas.drawColor(base)
        val paint = Paint()
        repeat(WIDTH * DEPTH / 40) {
            paint.color = shift(base, random.nextInt(-spread, spread + 1))
            val x = random.nextFloat() * WIDTH
            val y = random.nextFloat() * DEPTH
            canvas.drawRect(x, y, x + size, y + size, paint)
            canvas.drawRect(x, y - DEPTH, x + size, y - DEPTH + size, paint)
        }
    }

    private fun amplifier(canvas: Canvas, random: Random) {
        speckle(canvas, random, 0xFF1E1E20.toInt(), 14, 2f)
        val gold = Paint().apply {
            color = 0xFF78643C.toInt()
            strokeWidth = 2f
        }
        val dim = Paint().apply {
            color = 0xFF5A4B2D.toInt()
            strokeWidth = 1f
        }
        for (y in 8 until DEPTH step 16) {
            canvas.drawLine(0f, y.toFloat(), WIDTH.toFloat(), y.toFloat(), gold)
        }
        for (x in 8 until WIDTH step 16) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), DEPTH.toFloat(), dim)
        }
    }

    private fun leather(canvas: Canvas, random: Random) {
        speckle(canvas, random, 0xFF5A3218.toInt(), 18, 3f)
        val blotch = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL) }
        repeat(14) {
            blotch.color = if (random.nextBoolean()) 0x30000000 else 0x20FFDCA0
            val x = random.nextFloat() * WIDTH
            val y = random.nextFloat() * DEPTH
            val r = 30f + random.nextFloat() * 30f
            for (dy in intArrayOf(
                -DEPTH,
                0,
                DEPTH,
            )) {
                canvas.drawCircle(x, y + dy, r, blotch)
            }
        }
        val rivet = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFBEBEC3.toInt() }
        val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFAFAFA.toInt() }
        for (y in 32 until DEPTH step 64) {
            for (x in floatArrayOf(14f, WIDTH - 14f)) {
                canvas.drawCircle(x, y.toFloat(), 8f, rivet)
                canvas.drawCircle(x - 2.5f, y - 3f, 3f, shine)
            }
        }
    }

    private fun fretboard(canvas: Canvas, random: Random) {
        canvas.drawColor(0xFF3E2414.toInt())
        val grain = Paint()
        repeat(90) {
            grain.color = if (random.nextBoolean()) 0x22000000 else 0x18FFC896
            grain.strokeWidth = 1f + random.nextFloat() * 2f
            val x = random.nextFloat() * WIDTH
            canvas.drawLine(x, 0f, x, DEPTH.toFloat(), grain)
        }
        val fret = Paint().apply { color = 0xFFC8C8CD.toInt() }
        val inlay = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEBE8DE.toInt() }
        for (i in 0 until 4) {
            val y = i * DEPTH / 4f + DEPTH / 16f
            canvas.drawRect(0f, y, WIDTH.toFloat(), y + 5f, fret)
            if (i % 2 == 1) canvas.drawCircle(WIDTH / 2f, y + DEPTH / 8f, 11f, inlay)
        }
    }

    private fun flames(canvas: Canvas, random: Random) {
        canvas.drawColor(0xFF0E0A0A.toInt())
        val unit = Path().also { RockShapes.flame(it) }
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL) }
        val colors = intArrayOf(0xFFC81E0A.toInt(), 0xFFFF7800.toInt(), 0xFFFFD23C.toInt())
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
                        val copy = Path(unit)
                        m.reset()
                        m.postScale(w * f, h * f / 2f)
                        m.postTranslate(x, baseY + dy)
                        copy.transform(m)
                        canvas.drawPath(copy, glow)
                    }
                }
            }
        }
    }

    private fun neon(canvas: Canvas, random: Random) {
        canvas.drawColor(0xFF1E0E36.toInt())
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 7f
            maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
        }
        val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = 0xFFFFFFFF.toInt()
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
            glow.color = if (k % 2 == 0) 0xFFFF28C8.toInt() else 0xFF28DCFF.toInt()
            // Uma altura para cima e para baixo: o brilho que passa de qualquer borda reaparece do outro lado.
            for (dy in intArrayOf(-DEPTH, 0, DEPTH)) {
                val shifted = Path(path).apply { offset(0f, dy.toFloat()) }
                canvas.drawPath(shifted, glow)
                canvas.drawPath(shifted, core)
            }
        }
    }

    private fun diamondPlate(canvas: Canvas, random: Random) {
        speckle(canvas, random, 0xFF6E6E72.toInt(), 12, 1f)
        val raised = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFA5A5AA.toInt() }
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF46464A.toInt() }
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
