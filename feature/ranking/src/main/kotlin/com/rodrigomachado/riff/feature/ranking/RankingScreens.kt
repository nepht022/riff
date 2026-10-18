package com.rodrigomachado.riff.feature.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.data.ranking.RankedSong
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

@Composable
fun RankingSongsScreen(
    onBack: () -> Unit,
    onSong: (RankedSong) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RankingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RankingSongsContent(state, viewModel::onQuery, onSong, viewModel::load, onBack, modifier)
}

/** As músicas com pontuação no ranking (maquete D), com a busca por música ou artista. */
@Composable
fun RankingSongsContent(
    state: RankingSongsUiState,
    onQuery: (String) -> Unit,
    onSong: (RankedSong) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { BackButton(onBack) }
        item { PosterTitle(stringResource(R.string.ranking_title), style = MaterialTheme.typography.displaySmall) }
        if (state.notConfigured) {
            item { Message(stringResource(R.string.ranking_not_configured)) }
            return@LazyColumn
        }
        item { SearchField(state.query, stringResource(R.string.ranking_search_song), onQuery) }
        if (state.offline) item { Offline(onRetry) }
        if (!state.offline && !state.loading && state.songs.isEmpty()) {
            item { Message(stringResource(R.string.ranking_empty)) }
        }
        items(state.songs, key = { it.chartId }) { song -> SongCard(song) { onSong(song) } }
    }
}

@Composable
private fun SongCard(song: RankedSong, onClick: () -> Unit) {
    val players = stringResource(R.string.ranking_players, song.players)
    val place = song.yourPosition?.let { " · " + stringResource(R.string.ranking_your_place, it) }.orEmpty()
    Column(
        Modifier.fillMaxWidth().background(RiffColors.Paper, MaterialTheme.shapes.small)
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

@Composable
internal fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    Box(
        Modifier.background(if (selected) RiffColors.TourRed else Color.Transparent, shape)
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
        modifier = Modifier.fillMaxWidth(),
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
        Message(stringResource(R.string.ranking_offline))
        TextButton(onClick = withMenuSound(MenuSound.NAVIGATE, onRetry)) {
            Text(stringResource(R.string.ranking_retry), color = RiffColors.Paper)
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
    TextButton(onClick = onBack) { Text(stringResource(R.string.ranking_back), color = RiffColors.Paper) }
}

private const val SECONDARY_ALPHA = 0.7f
internal val CHIP_GAP = 6.dp
private val CHIP_PADDING_H = 10.dp
private val CHIP_PADDING_V = 6.dp
