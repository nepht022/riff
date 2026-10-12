package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.theme.JudgmentTextStyle
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.ScoreTextStyle
import com.rodrigomachado.riff.core.ui.theme.Spacing
import java.text.NumberFormat

private val JudgmentGlow = Color(0xFFFF6A00)
private val JudgmentColor = Color(0xFFFFF3D6)
private const val STAMP_TILT_DEGREES = -4f
private val JudgmentGlowRadius = 12.dp

/** Placar com separador de milhar do idioma do aparelho, no estilo letreiro. */
@Composable
fun ScoreText(score: Long, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val formatted = remember(score, locale) { NumberFormat.getIntegerInstance(locale).format(score) }
    PosterTitle(formatted, modifier, style = ScoreTextStyle, heading = false)
}

/** Multiplicador como carimbo vermelho inclinado. */
@Composable
fun MultiplierStamp(multiplier: Int, modifier: Modifier = Modifier) {
    Text(
        text = "x$multiplier",
        color = RiffColors.Paper,
        style = ScoreTextStyle.copy(fontSize = 22.sp),
        modifier = modifier
            .rotate(STAMP_TILT_DEGREES)
            .background(RiffColors.TourRed)
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
    )
}

/** Texto de julgamento ("PERFEITO!", "BOM!") com brilho laranja. A animação de salto fica no gameplay. */
@Composable
fun JudgmentText(text: String, modifier: Modifier = Modifier) {
    // O raio do brilho é em px: converte de dp para ficar do mesmo tamanho em qualquer tela.
    val glow = with(LocalDensity.current) { JudgmentGlowRadius.toPx() }
    Text(
        text = text,
        color = JudgmentColor,
        style = JudgmentTextStyle.copy(shadow = Shadow(color = JudgmentGlow, blurRadius = glow)),
        modifier = modifier,
    )
}
