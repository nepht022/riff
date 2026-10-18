package com.rodrigomachado.riff.feature.ranking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.data.ranking.NameResult
import com.rodrigomachado.riff.core.data.ranking.Player
import com.rodrigomachado.riff.core.data.ranking.PlayerNames
import com.rodrigomachado.riff.core.data.ranking.RankingRepository
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/** Pedir o nome: ranking configurado, ainda sem nome e sem "Agora não" nesta abertura do app. */
fun shouldAskForName(configured: Boolean, player: Player, askedLater: Boolean): Boolean =
    configured && player.name == null && !askedLater

data class PlayerNameUiState(
    val name: String = "",
    val saving: Boolean = false,
    val error: NameResult? = null,
    val done: Boolean = false,
)

@HiltViewModel
class PlayerNameViewModel @Inject constructor(private val ranking: RankingRepository) : ViewModel() {
    // O nome atual já no campo (trocar o nome começa dele); a leitura é do DataStore, rápida.
    private val ui = MutableStateFlow(
        PlayerNameUiState(name = runBlocking { ranking.player.first().name.orEmpty() }),
    )
    val state: StateFlow<PlayerNameUiState> = ui

    /** Quando a porteira do app pede o nome (primeira abertura). */
    val askNow: StateFlow<Boolean> = combine(ranking.player, ranking.askedLater) { player, later ->
        shouldAskForName(ranking.isConfigured, player, later)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun onName(name: String) = ui.update { it.copy(name = name, error = null) }

    fun save() {
        val name = ui.value.name
        if (!PlayerNames.isValid(name)) {
            ui.update { it.copy(error = NameResult.Invalid) }
            return
        }
        ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val result = ranking.register(name)
            ui.update {
                it.copy(
                    saving = false,
                    error = result.takeUnless { r -> r == NameResult.Ok },
                    done = result == NameResult.Ok,
                )
            }
        }
    }

    fun later() = ranking.askLater()

    /** O diálogo fechou: a próxima abertura começa de novo. */
    fun reset() = ui.update { PlayerNameUiState(name = it.name) }
}

/**
 * O pedido do nome por cima da tela de início (spec do ranking §2.1) e, com [renaming], a troca pelas
 * Configurações. Fica no app, fora das telas, para valer no Tocando agora e na Biblioteca.
 */
@Composable
fun PlayerNameGate(renaming: Boolean, onRenameClosed: () -> Unit, viewModel: PlayerNameViewModel = hiltViewModel()) {
    val askNow by viewModel.askNow.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) {
        if (state.done) {
            viewModel.reset()
            onRenameClosed()
        }
    }
    if (!renaming && !askNow) return
    PlayerNameDialog(
        state,
        onName = viewModel::onName,
        onSave = viewModel::save,
        onLater = if (renaming) null else viewModel::later,
        onDismiss = if (renaming) onRenameClosed else null,
    )
}

@Composable
fun PlayerNameDialog(
    state: PlayerNameUiState,
    onName: (String) -> Unit,
    onSave: () -> Unit,
    onLater: (() -> Unit)?,
    onDismiss: (() -> Unit)? = null,
) {
    AlertDialog(
        // Na primeira abertura só fecha por Salvar ou Agora não; na troca, tocar fora cancela.
        onDismissRequest = { onDismiss?.invoke() },
        title = { Text(stringResource(R.string.ranking_name_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onName,
                    label = { Text(stringResource(R.string.ranking_name_field)) },
                    singleLine = true,
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Quem vê o nome e o que identifica o celular (LGPD: dito antes de enviar).
                Text(
                    stringResource(R.string.ranking_name_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = RiffColors.PaperDim,
                )
                state.error?.let {
                    Text(
                        stringResource(it.message()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RiffColors.TourRed,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = withMenuSound(MenuSound.CONFIRM, onSave), enabled = !state.saving) {
                Text(stringResource(R.string.ranking_name_save))
            }
        },
        dismissButton = onLater?.let {
            {
                TextButton(
                    onClick = withMenuSound(MenuSound.BACK, it),
                ) { Text(stringResource(R.string.ranking_name_later)) }
            }
        },
    )
}

private fun NameResult.message(): Int = when (this) {
    NameResult.Taken -> R.string.ranking_name_taken
    NameResult.Invalid -> R.string.ranking_name_invalid
    NameResult.Limit -> R.string.ranking_name_limit
    NameResult.Offline, NameResult.Ok -> R.string.ranking_name_offline
}
