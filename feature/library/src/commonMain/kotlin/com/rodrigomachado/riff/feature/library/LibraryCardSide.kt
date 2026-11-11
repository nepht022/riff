package com.rodrigomachado.riff.feature.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.ui.component.BadgeTone
import com.rodrigomachado.riff.core.ui.component.StatusBadge
import com.rodrigomachado.riff.core.ui.component.difficultyName
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.feature.library.resources.Res
import com.rodrigomachado.riff.feature.library.resources.library_ai_seal
import com.rodrigomachado.riff.feature.library.resources.library_ai_separated
import com.rodrigomachado.riff.feature.library.resources.library_difficulties
import com.rodrigomachado.riff.feature.library.resources.library_list_last
import com.rodrigomachado.riff.feature.library.resources.library_own_guitar
import org.jetbrains.compose.resources.stringResource

/**
 * O lado direito do cartão (opção B, escolhida pelo usuário em 2026-11-11): os quatro quadradinhos das dificuldades e,
 * abaixo, a guitarra do mapa ([Chart.separateGuitar]) ou, se ela foi separada pela IA ([aiSeparated]), o selo "IA".
 * Com as duas, só a do mapa (ela já tem a guitarra). Largura fixa: o título, à esquerda, é que corta.
 */
@Composable
internal fun CardSide(chart: Chart, aiSeparated: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SIDE_GAP),
    ) {
        DifficultySquares(chart.difficulties)
        when {
            chart.separateGuitar -> OwnGuitar()
            aiSeparated -> AiSeal()
        }
    }
}

/** Fácil, Médio, Difícil e Expert, cheio o que o mapa tem; o leitor lê "Dificuldades: Fácil, Médio e Difícil". */
@Composable
private fun DifficultySquares(levels: Set<Difficulty>) {
    val names = Difficulty.entries.filter { it in levels }.map { difficultyName(it.ordinal) }
    val list = if (names.size < 2) {
        names.joinToString()
    } else {
        stringResource(Res.string.library_list_last, names.dropLast(1).joinToString(), names.last())
    }
    val description = stringResource(Res.string.library_difficulties, list)
    Row(
        Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(SQUARE_GAP),
    ) {
        Difficulty.entries.forEach { level ->
            val square = Modifier.size(SQUARE).border(SQUARE_BORDER, RiffColors.Ink)
            Box(if (level in levels) square.background(RiffColors.Ink) else square)
        }
    }
}

/** O ícone de guitarra (traço de tinta): o mapa já veio com a guitarra separada. */
@Composable
private fun OwnGuitar() {
    Image(GuitarIcon, stringResource(Res.string.library_own_guitar), Modifier.size(GUITAR))
}

/** "IA" no cartão; o leitor de tela lê "Guitarra separada pela IA". */
@Composable
private fun AiSeal() {
    val description = stringResource(Res.string.library_ai_separated)
    StatusBadge(
        stringResource(Res.string.library_ai_seal),
        BadgeTone.SUCCESS,
        Modifier.clearAndSetSemantics { contentDescription = description },
    )
}

/** Uma guitarra na diagonal: corpo embaixo à esquerda, braço e cabeça subindo à direita, cordas e ponte no corpo. */
private val GuitarIcon: ImageVector by lazy {
    val ink = SolidColor(RiffColors.Ink)
    ImageVector.Builder("Guitar", GUITAR, GUITAR, VIEWPORT, VIEWPORT).apply {
        listOf(
            // o corpo, em oito
            "M9.5 11.5 C8 10.5 6 11 5.5 12.5 C5 14 3 13.8 2.5 15.5 C2 17.5 3.5 20.5 6.5 21.5 " +
                "C9 22.3 10.5 20.5 10.5 19 C10.5 17.5 12.5 17 12.5 15 C12.5 13.5 11 12.5 9.5 11.5 Z",
            // o braço
            "M8 16 L19 5",
            // a cabeça
            "M18.5 3.5 L20.5 1.5 L22.5 3.5 L20.5 5.5 Z",
            // a ponte
            "M5.5 17.5 L8.5 20.5",
        ).forEach {
            addPath(
                pathData = addPathNodes(it),
                stroke = ink,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
}

private val SQUARE = 10.dp
private val SQUARE_BORDER = 1.5.dp
private val SQUARE_GAP = 3.dp
private val SIDE_GAP = 6.dp
private val GUITAR = 22.dp
private const val VIEWPORT = 24f
private const val STROKE = 1.5f
