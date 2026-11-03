package com.rodrigomachado.riff.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.data.download.ChartDownloadException.Reason
import com.rodrigomachado.riff.core.encore.EncoreChart
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.BadgeTone
import com.rodrigomachado.riff.core.ui.component.DifficultyDialog
import com.rodrigomachado.riff.core.ui.component.LoadingState
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.SeparationIndicator
import com.rodrigomachado.riff.core.ui.component.StatusBadge
import com.rodrigomachado.riff.core.ui.component.TrackCard
import com.rodrigomachado.riff.core.ui.component.difficultyName
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.search.resources.Res
import com.rodrigomachado.riff.feature.search.resources.search_action
import com.rodrigomachado.riff.feature.search.resources.search_allow
import com.rodrigomachado.riff.feature.search.resources.search_back
import com.rodrigomachado.riff.feature.search.resources.search_credit
import com.rodrigomachado.riff.feature.search.resources.search_download
import com.rodrigomachado.riff.feature.search.resources.search_failed_broken
import com.rodrigomachado.riff.feature.search.resources.search_failed_network
import com.rodrigomachado.riff.feature.search.resources.search_failed_no_folder
import com.rodrigomachado.riff.feature.search.resources.search_failed_storage
import com.rodrigomachado.riff.feature.search.resources.search_format_changed
import com.rodrigomachado.riff.feature.search.resources.search_hint
import com.rodrigomachado.riff.feature.search.resources.search_in_library
import com.rodrigomachado.riff.feature.search.resources.search_more
import com.rodrigomachado.riff.feature.search.resources.search_nothing
import com.rodrigomachado.riff.feature.search.resources.search_play
import com.rodrigomachado.riff.feature.search.resources.search_retry
import com.rodrigomachado.riff.feature.search.resources.search_separate_guitar
import com.rodrigomachado.riff.feature.search.resources.search_title
import com.rodrigomachado.riff.feature.search.resources.search_unavailable
import com.rodrigomachado.riff.feature.search.resources.search_write_needed
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onPlay: (chartId: String, difficulty: Difficulty) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Escolher a mesma pasta de novo, agora com permissão de gravar (o seletor abre nela).
    val pickFolder = rememberFolderPicker(state.folderUri) { uri ->
        uri?.let(viewModel::onWriteAccessGranted)
    }
    SearchContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onSearch = viewModel::search,
        onLoadMore = viewModel::loadMore,
        onDownload = viewModel::download,
        onPlay = onPlay,
        onAllowWrite = pickFolder,
        onBack = onBack,
        modifier = modifier,
        onShowVersions = viewModel::showVersions,
        onOnlySeparateGuitar = viewModel::onlySeparateGuitar,
        onSearchMoreWithGuitar = viewModel::searchMoreWithGuitar,
    )
}

@Composable
fun SearchContent(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onLoadMore: () -> Unit,
    onDownload: (EncoreChart) -> Unit,
    onPlay: (chartId: String, difficulty: Difficulty) -> Unit,
    onAllowWrite: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onShowVersions: (Boolean) -> Unit = {},
    onOnlySeparateGuitar: (Boolean) -> Unit = {},
    onSearchMoreWithGuitar: () -> Unit = {},
) {
    var choosing by remember { mutableStateOf<Pair<EncoreChart, ItemState.Ready>?>(null) }
    Column(modifier.fillMaxSize().padding(horizontal = Spacing.md)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(Res.string.search_back), color = RiffColors.Paper) }
            Spacer(Modifier.weight(1f))
            SeparationIndicator()
        }
        PosterTitle(stringResource(Res.string.search_title), style = MaterialTheme.typography.displaySmall)
        QueryRow(state.query, onQueryChange, onSearch)
        Text(
            stringResource(Res.string.search_credit),
            style = MaterialTheme.typography.bodySmall,
            color = RiffColors.PaperDim,
            modifier = Modifier.padding(vertical = Spacing.xs),
        )
        FilterRow(state, onShowVersions, onOnlySeparateGuitar)
        if (!state.canWrite) WriteNeeded(onAllowWrite)
        state.guitarSearch?.let { GuitarSearchStatus(it, state.results.isEmpty(), onSearchMoreWithGuitar) }
        when {
            state.isSearching && state.results.isEmpty() -> LoadingState()
            state.error != null && state.results.isEmpty() -> SearchFailure(state.error, state.query, onSearch)
            else -> Results(state, onDownload, onLoadMore) { chart, ready -> choosing = chart to ready }
        }
    }
    choosing?.let { (chart, ready) ->
        DifficultyDialog(
            title = chart.name,
            options = ready.difficulties.sorted().ifEmpty { listOf(Difficulty.EXPERT) },
            label = { difficultyName(it.ordinal) },
            onChoose = {
                choosing = null
                onPlay(ready.chartId, it)
            },
            onDismiss = { choosing = null },
        )
    }
}

@Composable
private fun QueryRow(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    val search = withMenuSound(MenuSound.NAVIGATE, onSearch)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text(stringResource(Res.string.search_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }),
            modifier = Modifier.weight(1f),
        )
        PosterButton(stringResource(Res.string.search_action), onClick = onSearch, sound = MenuSound.NAVIGATE)
    }
}

@Composable
private fun WriteNeeded(onAllow: () -> Unit) {
    Column(Modifier.padding(vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(stringResource(Res.string.search_write_needed), color = RiffColors.Paper)
        PosterButton(stringResource(Res.string.search_allow), onClick = onAllow, sound = MenuSound.NAVIGATE)
    }
}

@Composable
private fun SearchFailure(error: SearchError, query: String, onRetry: () -> Unit) {
    Column(Modifier.padding(vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        val message = when (error) {
            SearchError.UNAVAILABLE -> stringResource(Res.string.search_unavailable)
            SearchError.FORMAT_CHANGED -> stringResource(Res.string.search_format_changed)
            SearchError.NOTHING_FOUND -> stringResource(Res.string.search_nothing, query)
        }
        Text(message, color = RiffColors.Paper)
        if (error == SearchError.UNAVAILABLE) {
            TextButton(onClick = withMenuSound(MenuSound.CONFIRM, onRetry)) {
                Text(stringResource(Res.string.search_retry), color = RiffColors.Paper)
            }
        }
    }
}

@Composable
private fun Results(
    state: SearchUiState,
    onDownload: (EncoreChart) -> Unit,
    onLoadMore: () -> Unit,
    onChoose: (EncoreChart, ItemState.Ready) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = PaddingValues(vertical = Spacing.md),
    ) {
        items(state.results, key = { it.chart.md5 }) { item ->
            ResultCard(item, state.canWrite, onDownload, onChoose)
        }
        if (state.hasMore) {
            item {
                if (state.isSearching) {
                    LoadingState()
                } else {
                    TextButton(onClick = withMenuSound(MenuSound.NAVIGATE, onLoadMore)) {
                        Text(stringResource(Res.string.search_more), color = RiffColors.Paper)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    item: ResultItem,
    canWrite: Boolean,
    onDownload: (EncoreChart) -> Unit,
    onChoose: (EncoreChart, ItemState.Ready) -> Unit,
) {
    val chart = item.chart
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        TrackCard(
            title = chart.name,
            artist = listOfNotNull(chart.artist.ifBlank { null }, chart.charter, chart.durationMs?.let(::duration))
                .joinToString(" · "),
            badge = if (item.separateGuitar == true) {
                { StatusBadge(stringResource(Res.string.search_separate_guitar), BadgeTone.SUCCESS) }
            } else {
                null
            },
        )
        val difficulties = chart.guitarDifficulties.sorted().map { difficultyName(it.ordinal) }
        Text(
            difficulties.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = RiffColors.PaperDim,
        )
        ItemAction(item, canWrite, onDownload, onChoose)
    }
}

@Composable
private fun ItemAction(
    item: ResultItem,
    canWrite: Boolean,
    onDownload: (EncoreChart) -> Unit,
    onChoose: (EncoreChart, ItemState.Ready) -> Unit,
) {
    when (val state = item.state) {
        ItemState.Available -> if (canWrite) {
            PosterButton(
                stringResource(Res.string.search_download),
                onClick = { onDownload(item.chart) },
                sound = MenuSound.CONFIRM,
            )
        }

        is ItemState.Downloading -> LinearProgressIndicator(
            progress = { state.progress },
            color = RiffColors.TourRed,
            modifier = Modifier.fillMaxWidth(),
        )

        is ItemState.Ready -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(Res.string.search_in_library), color = RiffColors.Paper)
            PosterButton(
                stringResource(Res.string.search_play),
                onClick = { onChoose(item.chart, state) },
                sound = MenuSound.CONFIRM,
            )
        }

        is ItemState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(state.reason.message()), color = RiffColors.Paper, modifier = Modifier.weight(1f))
            TextButton(onClick = withMenuSound(MenuSound.CONFIRM) { onDownload(item.chart) }) {
                Text(stringResource(Res.string.search_retry), color = RiffColors.Paper)
            }
        }
    }
}

private fun Reason.message(): StringResource = when (this) {
    Reason.NETWORK -> Res.string.search_failed_network
    Reason.BROKEN_FILE -> Res.string.search_failed_broken
    Reason.STORAGE, Reason.NO_WRITE_ACCESS -> Res.string.search_failed_storage
    Reason.NO_FOLDER -> Res.string.search_failed_no_folder
}

private fun duration(ms: Long): String {
    val seconds = ms / MS_PER_SECOND
    return "${seconds / SECONDS_PER_MINUTE}:${(seconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
}

private const val MS_PER_SECOND = 1_000
private const val SECONDS_PER_MINUTE = 60
