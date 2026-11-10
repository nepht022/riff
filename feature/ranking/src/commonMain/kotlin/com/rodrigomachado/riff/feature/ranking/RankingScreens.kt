package com.rodrigomachado.riff.feature.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.data.ranking.RankedSong
import com.rodrigomachado.riff.core.data.ranking.TableStanding
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.RankingTable
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.input.upDownLeave
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.ranking.resources.Res
import com.rodrigomachado.riff.feature.ranking.resources.ranking_back
import com.rodrigomachado.riff.feature.ranking.resources.ranking_empty
import com.rodrigomachado.riff.feature.ranking.resources.ranking_loading
import com.rodrigomachado.riff.feature.ranking.resources.ranking_not_configured
import com.rodrigomachado.riff.feature.ranking.resources.ranking_offline
import com.rodrigomachado.riff.feature.ranking.resources.ranking_players
import com.rodrigomachado.riff.feature.ranking.resources.ranking_retry
import com.rodrigomachado.riff.feature.ranking.resources.ranking_search_song
import com.rodrigomachado.riff.feature.ranking.resources.ranking_table_empty
import com.rodrigomachado.riff.feature.ranking.resources.ranking_table_pc_no_strum
import com.rodrigomachado.riff.feature.ranking.resources.ranking_table_pc_strum
import com.rodrigomachado.riff.feature.ranking.resources.ranking_table_phone
import com.rodrigomachado.riff.feature.ranking.resources.ranking_title
import com.rodrigomachado.riff.feature.ranking.resources.ranking_your_place
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** [onSong] leva a tabela escolhida, para o placar abrir nela. */
@Composable
fun RankingSongsScreen(
    onBack: () -> Unit,
    onSong: (RankedSong, RankingTable) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RankingViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RankingSongsContent(
        state,
        viewModel::onQuery,
        { onSong(it, state.table) },
        viewModel::load,
        onBack,
        modifier,
        viewModel::onTable,
    )
}

/** As músicas com pontuação no ranking (maquete D), com a busca por música ou artista e a troca de tabela. */
@Composable
fun RankingSongsContent(
    state: RankingSongsUiState,
    onQuery: (String) -> Unit,
    onSong: (RankedSong) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onTable: (RankingTable) -> Unit = {},
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { BackButton(onBack) }
        item { PosterTitle(stringResource(Res.string.ranking_title), style = MaterialTheme.typography.displaySmall) }
        if (state.notConfigured) {
            item { Message(stringResource(Res.string.ranking_not_configured)) }
            return@LazyColumn
        }
        item { TableChips(state.table, onTable) }
        item { SearchField(state.query, stringResource(Res.string.ranking_search_song), onQuery) }
        if (state.offline) item { Offline(onRetry) }
        if (state.loading && state.songs.isEmpty()) item { Message(stringResource(Res.string.ranking_loading)) }
        if (!state.offline && !state.loading && state.shown.isEmpty()) {
            val empty = if (state.tableEmpty) Res.string.ranking_table_empty else Res.string.ranking_empty
            item { Message(stringResource(empty)) }
        }
        items(state.shown, key = { it.chartId }) { song -> SongCard(song, state.table) { onSong(song) } }
    }
}

/** Os números da [table] (a lista só tem músicas com alguém nela). */
@Composable
private fun SongCard(song: RankedSong, table: RankingTable, onClick: () -> Unit) {
    val standing = song.inTable(table) ?: TableStanding(0, null)
    val players = pluralStringResource(Res.plurals.ranking_players, standing.players, standing.players)
    val place = standing.yourPosition?.let { " · " + stringResource(Res.string.ranking_your_place, it) }.orEmpty()
    Column(
        Modifier.fillMaxWidth().focusHighlight(MaterialTheme.shapes.small)
            .background(RiffColors.Paper, MaterialTheme.shapes.small)
            .clickable(onClick = withMenuSound(MenuSound.NAVIGATE, onClick)).padding(Spacing.md),
    ) {
        Text(song.title, style = MaterialTheme.typography.titleMedium, color = RiffColors.Ink)
        Text(
            "${song.artist} · $players$place",
            style = MaterialTheme.typography.bodyMedium,
            color = RiffColors.Ink.copy(alpha = SECONDARY_ALPHA),
        )
    }
}

/** As três tabelas do ranking, uma por vez (decisão 6 da 4d). No PC, ←/→ andam entre elas e Enter escolhe. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TableChips(selected: RankingTable, onTable: (RankingTable) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
        verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
    ) {
        for (table in RankingTable.entries) {
            Chip(stringResource(table.label()), table == selected) { onTable(table) }
        }
    }
}

private fun RankingTable.label(): StringResource = when (this) {
    RankingTable.PHONE -> Res.string.ranking_table_phone
    RankingTable.PC_STRUM -> Res.string.ranking_table_pc_strum
    RankingTable.PC_NO_STRUM -> Res.string.ranking_table_pc_no_strum
}

/** Um filtro do ranking; [selected] também para a acessibilidade (e para os testes do PC). */
@Composable
internal fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    Box(
        Modifier.semantics { this.selected = selected }
            .focusHighlight(shape)
            .background(if (selected) RiffColors.TourRed else Color.Transparent, shape)
            .border(1.dp, if (selected) RiffColors.TourRed else RiffColors.PaperDim, shape)
            .clickable(onClick = withMenuSound(MenuSound.NAVIGATE, onClick))
            .padding(horizontal = CHIP_PADDING_H, vertical = CHIP_PADDING_V),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = RiffColors.Paper) }
}

@Composable
internal fun SearchField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        // No PC ↑/↓ saem do campo (o de uma linha as usaria para o cursor, e o foco ficaria preso nele).
        modifier = Modifier.upDownLeave().fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = RiffColors.Paper,
            unfocusedTextColor = RiffColors.Paper,
            unfocusedBorderColor = RiffColors.PaperDim,
            unfocusedLabelColor = RiffColors.PaperDim,
        ),
    )
}

@Composable
internal fun Offline(onRetry: () -> Unit) {
    Column {
        Message(stringResource(Res.string.ranking_offline))
        TextButton(
            onClick = withMenuSound(MenuSound.NAVIGATE, onRetry),
            modifier = Modifier.focusHighlight(CircleShape),
        ) {
            Text(stringResource(Res.string.ranking_retry), color = RiffColors.Paper)
        }
    }
}

@Composable
internal fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = RiffColors.PaperDim)
}

@Composable
internal fun BackButton(onBack: () -> Unit) {
    // Sem som aqui: a navegação toca o de voltar ao sair da tela.
    TextButton(onClick = onBack, modifier = Modifier.focusHighlight(CircleShape)) {
        Text(stringResource(Res.string.ranking_back), color = RiffColors.Paper)
    }
}

private const val SECONDARY_ALPHA = 0.7f
internal val CHIP_GAP = 6.dp
private val CHIP_PADDING_H = 10.dp
private val CHIP_PADDING_V = 6.dp
