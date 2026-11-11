package com.rodrigomachado.riff.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.ui.component.BadgeTone
import com.rodrigomachado.riff.core.ui.component.StatusBadge
import com.rodrigomachado.riff.core.ui.component.difficultyName
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.library.resources.Res
import com.rodrigomachado.riff.feature.library.resources.library_ai_seal
import com.rodrigomachado.riff.feature.library.resources.library_ai_separated
import com.rodrigomachado.riff.feature.library.resources.library_guitar_tag
import com.rodrigomachado.riff.feature.library.resources.library_own_guitar
import com.rodrigomachado.riff.feature.library.resources.library_up_to
import org.jetbrains.compose.resources.stringResource

/**
 * O lado direito do cartão (opção A, escolhida pelo usuário em 2026-11-11): a etiqueta da dificuldade mais alta do
 * mapa e, abaixo, "GUITARRA" quando o mapa já veio com a guitarra ([Chart.separateGuitar]) ou o selo "IA" quando ela
 * foi separada pela IA ([aiSeparated]); com as duas, só "GUITARRA". Largura fixa (a mesma em todos os cartões; só
 * cresce se a fonte grande não couber): o título, à esquerda, é que corta.
 */
@Composable
internal fun CardSide(chart: Chart, aiSeparated: Boolean) {
    Column(
        Modifier.widthIn(min = SIDE_WIDTH),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(SIDE_GAP),
    ) {
        chart.difficulties.maxOrNull()?.let { HighestDifficulty(difficultyName(it.ordinal)) }
        when {
            chart.separateGuitar -> GuitarTag()
            aiSeparated -> AiSeal()
        }
    }
}

/** "EXPERT" no selo cinza-papel (o tom do selo neutro); o leitor de tela lê "Até Expert". */
@Composable
private fun HighestDifficulty(name: String) {
    Tag(name, stringResource(Res.string.library_up_to, name), Modifier.background(RiffColors.PaperDim))
}

/** "GUITARRA" com contorno de tinta, sem fundo: o mapa já veio com a guitarra separada ("Guitarra do mapa"). */
@Composable
private fun GuitarTag() {
    Tag(
        stringResource(Res.string.library_guitar_tag),
        stringResource(Res.string.library_own_guitar),
        Modifier.border(TAG_BORDER, RiffColors.Ink),
    )
}

/**
 * Etiqueta em maiúsculas espaçadas, Oswald, tinta, numa linha só (com fonte grande ela alarga o lado em vez de
 * partir a palavra). O leitor de tela lê só [description]; o texto visível continua na árvore para os testes.
 */
@Composable
private fun Tag(text: String, description: String, look: Modifier) {
    val shown = text.uppercase()
    Text(
        text = shown,
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = TAG_SPACING),
        color = RiffColors.Ink,
        softWrap = false,
        modifier = Modifier
            .clearAndSetSemantics {
                contentDescription = description
                this.text = AnnotatedString(shown)
            }
            .then(look)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
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

private val SIDE_WIDTH = 84.dp
private val SIDE_GAP = 6.dp
private val TAG_BORDER = 1.5.dp
private val TAG_SPACING = 1.5.sp
