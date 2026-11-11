package com.rodrigomachado.riff.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.Chart
import com.rodrigomachado.riff.core.ui.art.ChartArtImages
import com.rodrigomachado.riff.core.ui.component.ChartThumb
import com.rodrigomachado.riff.core.ui.component.KeyHint
import com.rodrigomachado.riff.core.ui.component.difficultyName
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.library.resources.Res
import com.rodrigomachado.riff.feature.library.resources.library_keys_back
import com.rodrigomachado.riff.feature.library.resources.library_keys_choose
import com.rodrigomachado.riff.feature.library.resources.library_keys_move
import com.rodrigomachado.riff.feature.library.resources.library_no_record
import com.rodrigomachado.riff.feature.library.resources.library_record
import com.rodrigomachado.riff.feature.library.resources.library_unknown_artist
import org.jetbrains.compose.resources.stringResource
import java.text.NumberFormat

/**
 * O cartão de um mapa: a capa, o título, artista · duração e o recorde ([best], o maior em qualquer dificuldade) ou
 * "Ainda sem recorde"; à direita, a dificuldade mais alta e "GUITARRA" ou o selo "IA" ([aiSeparated], ver [CardSide]).
 */
@Composable
@Suppress("LongParameterList") // o mapa, o que se mostra dele e o toque
internal fun LibraryCard(
    chart: Chart,
    best: ChartBest?,
    aiSeparated: Boolean,
    images: ChartArtImages?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().padding(end = SHADOW, bottom = SHADOW)) {
        Box(Modifier.matchParentSize().offset(SHADOW, SHADOW).background(RiffColors.Ink))
        Row(
            Modifier
                .fillMaxWidth()
                .background(RiffColors.Paper, MaterialTheme.shapes.small)
                .focusHighlight(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChartThumb(chart.id, chart.title, THUMB, images)
            Column(Modifier.weight(1f)) {
                Text(
                    chart.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = RiffColors.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    artistLine(chart),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RiffColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                RecordLine(best)
            }
            CardSide(chart, aiSeparated)
        }
    }
}

/** "Recorde 412.380 · Expert" ou "Ainda sem recorde". */
@Composable
private fun RecordLine(best: ChartBest?) {
    if (best == null) {
        Text(
            stringResource(Res.string.library_no_record),
            style = MaterialTheme.typography.bodySmall,
            color = RiffColors.Ink,
        )
        return
    }
    Text(
        stringResource(Res.string.library_record, formatScore(best.score), difficultyName(best.difficulty.ordinal)),
        style = MaterialTheme.typography.labelLarge,
        color = RiffColors.Ink,
    )
}

/** "Artista · 3:25" (sem a duração, só o artista; sem artista, "Artista desconhecido"). */
@Composable
private fun artistLine(chart: Chart): String {
    val artist = chart.artist.ifBlank { stringResource(Res.string.library_unknown_artist) }
    return chart.durationMs?.let { "$artist · ${formatDuration(it)}" } ?: artist
}

/** "3:25" (ou "1:02:07" a partir de uma hora). */
internal fun formatDuration(ms: Long): String {
    val total = ms / MS_PER_SECOND
    val hours = total / SECONDS_PER_HOUR
    val minutes = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = total % SECONDS_PER_MINUTE
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/** Os pontos no idioma da interface (no celular, o da configuração; no PC, o do sistema), como na tela inicial. */
@Composable
private fun formatScore(score: Long): String {
    val locale = java.util.Locale.forLanguageTag(Locale.current.toLanguageTag())
    return remember(score, locale) { NumberFormat.getIntegerInstance(locale).format(score) }
}

/** As teclas da Biblioteca no PC: as setas movem, Enter (✕) escolhe, Esc (○) volta (ou limpa o filtro). */
@Composable
internal fun libraryKeys() = listOf(
    KeyHint("← → ↑ ↓", pad = null, label = stringResource(Res.string.library_keys_move)),
    KeyHint("Enter", pad = "✕", label = stringResource(Res.string.library_keys_choose)),
    KeyHint("Esc", pad = "○", label = stringResource(Res.string.library_keys_back)),
)

private val SHADOW = 4.dp
private val THUMB = 56.dp
private const val MS_PER_SECOND = 1_000
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3_600
