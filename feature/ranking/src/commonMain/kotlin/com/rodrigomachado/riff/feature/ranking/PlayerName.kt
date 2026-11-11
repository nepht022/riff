package com.rodrigomachado.riff.feature.ranking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.data.ranking.LinkResult
import com.rodrigomachado.riff.core.data.ranking.NameResult
import com.rodrigomachado.riff.core.data.ranking.Player
import com.rodrigomachado.riff.core.data.ranking.PlayerNames
import com.rodrigomachado.riff.core.data.ranking.RankingRepository
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.PcKeyHints
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.input.menuKeys
import com.rodrigomachado.riff.core.ui.input.upDownLeave
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.ranking.resources.Res
import com.rodrigomachado.riff.feature.ranking.resources.ranking_back
import com.rodrigomachado.riff.feature.ranking.resources.ranking_keys_close
import com.rodrigomachado.riff.feature.ranking.resources.ranking_link_code_hint
import com.rodrigomachado.riff.feature.ranking.resources.ranking_link_code_title
import com.rodrigomachado.riff.feature.ranking.resources.ranking_link_confirm
import com.rodrigomachado.riff.feature.ranking.resources.ranking_link_done
import com.rodrigomachado.riff.feature.ranking.resources.ranking_link_enter
import com.rodrigomachado.riff.feature.ranking.resources.ranking_link_enter_hint
import com.rodrigomachado.riff.feature.ranking.resources.ranking_link_enter_replaces
import com.rodrigomachado.riff.feature.ranking.resources.ranking_link_enter_title
import com.rodrigomachado.riff.feature.ranking.resources.ranking_link_expired
import com.rodrigomachado.riff.feature.ranking.resources.ranking_link_field
import com.rodrigomachado.riff.feature.ranking.resources.ranking_link_invalid
import com.rodrigomachado.riff.feature.ranking.resources.ranking_link_new_code
import com.rodrigomachado.riff.feature.ranking.resources.ranking_link_no_name
import com.rodrigomachado.riff.feature.ranking.resources.ranking_link_outdated
import com.rodrigomachado.riff.feature.ranking.resources.ranking_link_removed
import com.rodrigomachado.riff.feature.ranking.resources.ranking_link_share
import com.rodrigomachado.riff.feature.ranking.resources.ranking_link_valid_for
import com.rodrigomachado.riff.feature.ranking.resources.ranking_loading
import com.rodrigomachado.riff.feature.ranking.resources.ranking_name_field
import com.rodrigomachado.riff.feature.ranking.resources.ranking_name_invalid
import com.rodrigomachado.riff.feature.ranking.resources.ranking_name_later
import com.rodrigomachado.riff.feature.ranking.resources.ranking_name_limit
import com.rodrigomachado.riff.feature.ranking.resources.ranking_name_offline
import com.rodrigomachado.riff.feature.ranking.resources.ranking_name_privacy
import com.rodrigomachado.riff.feature.ranking.resources.ranking_name_save
import com.rodrigomachado.riff.feature.ranking.resources.ranking_name_taken
import com.rodrigomachado.riff.feature.ranking.resources.ranking_name_title
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Pedir o nome: ranking configurado, ainda sem nome e sem "Agora não" nesta abertura do app. */
fun shouldAskForName(configured: Boolean, player: Player, askedLater: Boolean): Boolean =
    configured && player.name == null && !askedLater

/** O que o pedido do nome mostra: o nome, o código para outro aparelho ou o campo do código de outro aparelho. */
enum class NameStep { NAME, SHOW_CODE, ENTER_CODE }

/**
 * [saving]: esperando o script (reservar, pedir o código ou vincular). [hasName]: este aparelho já tem nome (só então
 * dá para usá-lo em outro). [code]: o digitado; [linkCode]: o mostrado, que vale mais [secondsLeft] segundos.
 */
data class PlayerNameUiState(
    val name: String = "",
    val saving: Boolean = false,
    val error: NameResult? = null,
    val done: Boolean = false,
    val hasName: Boolean = false,
    val step: NameStep = NameStep.NAME,
    val code: String = "",
    val linkCode: String? = null,
    val secondsLeft: Int = 0,
    val linkError: LinkResult? = null,
)

/** O nome do ranking e o vínculo com outro aparelho; no celular o Hilt cria o [HiltPlayerNameViewModel]. */
@Suppress("TooManyFunctions") // o nome, o código mostrado e o digitado dividem o mesmo diálogo e estado
open class PlayerNameViewModel(private val ranking: RankingRepository) : ViewModel() {
    private val ui = MutableStateFlow(PlayerNameUiState())
    val state: StateFlow<PlayerNameUiState> = ui
    private var countdown: Job? = null

    init {
        // O nome atual no campo (trocar o nome começa dele), lido fora da thread da tela; o que já foi digitado vale.
        viewModelScope.launch { fillWithSavedName(keepTyped = true) }
        // O ViewModel dura o app inteiro: "tem nome" segue o gravado (posto, vinculado ou tirado nesta sessão).
        viewModelScope.launch {
            ranking.player.map { it.name != null }.distinctUntilChanged().collect { has ->
                ui.update { it.copy(hasName = has) }
            }
        }
    }

    private suspend fun fillWithSavedName(keepTyped: Boolean) {
        val saved = ranking.player.first().name.orEmpty()
        // O que já foi digitado (e o passo do vínculo, se a pessoa já foi para ele) fica.
        ui.update {
            if (keepTyped) it.copy(name = it.name.ifEmpty { saved }) else PlayerNameUiState(saved, hasName = it.hasName)
        }
    }

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
    fun reset() {
        countdown?.cancel()
        ui.update { PlayerNameUiState(name = it.name, hasName = it.hasName) }
    }

    /** Troca cancelada (tocar fora): volta ao nome gravado, sem o texto digitado nem o erro. */
    fun cancel() {
        countdown?.cancel()
        viewModelScope.launch { fillWithSavedName(keepTyped = false) }
    }

    /**
     * "Usar este nome em outro aparelho" (ou "Pedir outro"): pede o código e conta o tempo pelo que o script diz que
     * ele vale, não pelo relógio do aparelho.
     */
    fun showLinkCode() {
        countdown?.cancel()
        ui.update {
            it.copy(step = NameStep.SHOW_CODE, saving = true, linkCode = null, secondsLeft = 0, linkError = null)
        }
        viewModelScope.launch {
            when (val result = ranking.linkCode()) {
                is LinkResult.Code -> {
                    ui.update { it.copy(saving = false, linkCode = result.code, secondsLeft = result.validForSeconds) }
                    countdown = viewModelScope.launch { countDown() }
                }

                else -> ui.update { it.copy(saving = false, linkError = result) }
            }
        }
    }

    private suspend fun countDown() {
        while (ui.value.secondsLeft > 0) {
            delay(SECOND_MS)
            ui.update { it.copy(secondsLeft = (it.secondsLeft - 1).coerceAtLeast(0)) }
        }
    }

    /** "Já tenho um nome em outro aparelho": o campo do código. */
    fun enterLinkCode() {
        countdown?.cancel()
        ui.update { it.copy(step = NameStep.ENTER_CODE, code = "", linkError = null) }
    }

    fun onCode(code: String) = ui.update { it.copy(code = code.take(CODE_FIELD_MAX), linkError = null) }

    /** Com o código certo, o nome de lá vale aqui e o diálogo fecha. */
    fun link() {
        val code = ui.value.code
        ui.update { it.copy(saving = true, linkError = null) }
        viewModelScope.launch {
            when (val result = ranking.link(code)) {
                is LinkResult.Linked -> ui.update {
                    it.copy(saving = false, name = result.name, done = true)
                }

                else -> ui.update { it.copy(saving = false, linkError = result) }
            }
        }
    }

    /** Volta do código para o nome. */
    fun backToName() {
        countdown?.cancel()
        ui.update { it.copy(step = NameStep.NAME, saving = false, linkError = null) }
    }
}

private const val SECOND_MS = 1_000L

/** O código tem 6 caracteres; um traço ou espaço no meio também vale (o script e o app tiram). */
private const val CODE_FIELD_MAX = 7

/** O que o vínculo pede ao diálogo do nome. */
class LinkActions(
    val onShowCode: () -> Unit = {},
    val onEnterCode: () -> Unit = {},
    val onCode: (String) -> Unit = {},
    val onLink: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * O pedido do nome por cima da tela de início (spec do ranking §2.1) e, com [renaming], a troca pelas
 * Configurações. Fica no app, fora das telas, para valer no Tocando agora e na Biblioteca.
 */
@Composable
fun PlayerNameGate(renaming: Boolean, onRenameClosed: () -> Unit, viewModel: PlayerNameViewModel) {
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
        onDismiss = if (renaming) {
            {
                viewModel.cancel()
                onRenameClosed()
            }
        } else {
            null
        },
        link = LinkActions(
            viewModel::showLinkCode,
            viewModel::enterLinkCode,
            viewModel::onCode,
            viewModel::link,
            viewModel::backToName,
        ),
    )
}

/** O pedido do nome; [link] leva às telas do vínculo (o mesmo nome em vários aparelhos). */
@Composable
fun PlayerNameDialog(
    state: PlayerNameUiState,
    onName: (String) -> Unit,
    onSave: () -> Unit,
    onLater: (() -> Unit)?,
    onDismiss: (() -> Unit)? = null,
    link: LinkActions = LinkActions(),
) {
    when (state.step) {
        NameStep.NAME -> NameDialog(state, onName, onSave, onLater, onDismiss, link)
        NameStep.SHOW_CODE -> ShowCodeDialog(state, onDismiss, link)
        NameStep.ENTER_CODE -> EnterCodeDialog(state, onDismiss, link)
    }
}

@Composable
private fun NameDialog(
    state: PlayerNameUiState,
    onName: (String) -> Unit,
    onSave: () -> Unit,
    onLater: (() -> Unit)?,
    onDismiss: (() -> Unit)?,
    link: LinkActions,
) {
    AlertDialog(
        // Na primeira abertura só fecha por Salvar ou Agora não; na troca, tocar fora cancela.
        onDismissRequest = { onDismiss?.invoke() },
        // No PC as setas andam pelo diálogo; o Esc cancela a troca ou, na primeira abertura, é o "Agora não".
        modifier = Modifier.menuKeys(onBack = onDismiss ?: onLater),
        title = { Text(stringResource(Res.string.ranking_name_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onName,
                    label = { Text(stringResource(Res.string.ranking_name_field)) },
                    singleLine = true,
                    enabled = !state.saving,
                    modifier = Modifier.upDownLeave().fillMaxWidth(),
                )
                // Quem vê o nome e o que identifica o aparelho (LGPD: dito antes de enviar).
                Text(
                    stringResource(Res.string.ranking_name_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = RiffColors.PaperDim,
                )
                state.error?.let { ErrorText(stringResource(it.message())) }
                // Só quem já tem nome pode levá-lo; qualquer um pode trazer o de outro aparelho.
                if (state.hasName) LinkButton(stringResource(Res.string.ranking_link_share), link.onShowCode)
                LinkButton(stringResource(Res.string.ranking_link_enter), link.onEnterCode)
                // No PC, as teclas do diálogo (o Esc cancela a troca ou, na primeira abertura, é o "Agora não").
                val escape = if (onDismiss != null) Res.string.ranking_keys_close else Res.string.ranking_name_later
                PcKeyHints(rankingDialogKeys(stringResource(escape)))
            }
        },
        confirmButton = {
            TextButton(
                onClick = withMenuSound(MenuSound.CONFIRM, onSave),
                enabled = !state.saving,
                modifier = Modifier.focusHighlight(CircleShape),
            ) {
                Text(stringResource(Res.string.ranking_name_save))
            }
        },
        dismissButton = onLater?.let {
            {
                TextButton(
                    onClick = withMenuSound(MenuSound.BACK, it),
                    modifier = Modifier.focusHighlight(CircleShape),
                ) { Text(stringResource(Res.string.ranking_name_later)) }
            }
        },
    )
}

/** O código grande para digitar no outro aparelho, com o tempo que ainda vale. */
@Composable
private fun ShowCodeDialog(state: PlayerNameUiState, onDismiss: (() -> Unit)?, link: LinkActions) {
    val expired = state.linkCode != null && state.secondsLeft <= 0
    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        // No PC o Esc volta ao nome, como o Pronto.
        modifier = Modifier.menuKeys(onBack = link.onBack),
        title = { Text(stringResource(Res.string.ranking_link_code_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (state.saving) Text(stringResource(Res.string.ranking_loading))
                state.linkCode?.let { code ->
                    Text(
                        code,
                        style = MaterialTheme.typography.displaySmall,
                        letterSpacing = CODE_LETTER_SPACING,
                        color = if (expired) RiffColors.PaperDim else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (expired) {
                            stringResource(Res.string.ranking_link_expired)
                        } else {
                            stringResource(Res.string.ranking_link_valid_for, minutesAndSeconds(state.secondsLeft))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(Res.string.ranking_link_code_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = RiffColors.PaperDim,
                    )
                }
                state.linkError?.let { ErrorText(stringResource(it.message())) }
                PcKeyHints(rankingDialogKeys(stringResource(Res.string.ranking_back)))
            }
        },
        confirmButton = {
            TextButton(
                onClick = withMenuSound(MenuSound.BACK, link.onBack),
                modifier = Modifier.focusHighlight(CircleShape),
            ) {
                Text(stringResource(Res.string.ranking_link_done))
            }
        },
        dismissButton = if (expired || state.linkError != null) {
            {
                TextButton(
                    onClick = withMenuSound(MenuSound.CONFIRM, link.onShowCode),
                    modifier = Modifier.focusHighlight(CircleShape),
                ) {
                    Text(stringResource(Res.string.ranking_link_new_code))
                }
            }
        } else {
            null
        },
    )
}

/** O código que outro aparelho mostrou (minúsculas valem). */
@Composable
private fun EnterCodeDialog(state: PlayerNameUiState, onDismiss: (() -> Unit)?, link: LinkActions) {
    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        // No PC o Esc volta ao nome, como o Voltar.
        modifier = Modifier.menuKeys(onBack = link.onBack),
        title = { Text(stringResource(Res.string.ranking_link_enter_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = state.code,
                    onValueChange = link.onCode,
                    label = { Text(stringResource(Res.string.ranking_link_field)) },
                    singleLine = true,
                    enabled = !state.saving,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.upDownLeave().fillMaxWidth(),
                )
                Text(
                    stringResource(Res.string.ranking_link_enter_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = RiffColors.PaperDim,
                )
                // O nome deste aparelho sai dele (o script libera o nome se só ele o usava): dito antes de vincular.
                if (state.hasName) {
                    Text(
                        stringResource(Res.string.ranking_link_enter_replaces),
                        style = MaterialTheme.typography.bodySmall,
                        color = RiffColors.PaperDim,
                    )
                }
                state.linkError?.let { ErrorText(stringResource(it.message())) }
                PcKeyHints(rankingDialogKeys(stringResource(Res.string.ranking_back)))
            }
        },
        confirmButton = {
            TextButton(
                onClick = withMenuSound(MenuSound.CONFIRM, link.onLink),
                enabled = !state.saving,
                modifier = Modifier.focusHighlight(CircleShape),
            ) {
                Text(stringResource(Res.string.ranking_link_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = withMenuSound(MenuSound.BACK, link.onBack),
                modifier = Modifier.focusHighlight(CircleShape),
            ) {
                Text(stringResource(Res.string.ranking_back))
            }
        },
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = RiffColors.TourRed)
}

@Composable
private fun LinkButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = withMenuSound(MenuSound.NAVIGATE, onClick), modifier = Modifier.focusHighlight(CircleShape)) {
        Text(text)
    }
}

/** 545 → "9:05". */
internal fun minutesAndSeconds(seconds: Int): String = "%d:%02d".format(seconds / SECONDS, seconds % SECONDS)

private const val SECONDS = 60
private val CODE_LETTER_SPACING = 4.sp

private fun NameResult.message(): StringResource = when (this) {
    NameResult.Taken -> Res.string.ranking_name_taken
    NameResult.Invalid -> Res.string.ranking_name_invalid
    NameResult.Limit -> Res.string.ranking_name_limit
    NameResult.Offline, NameResult.Ok -> Res.string.ranking_name_offline
}

private fun LinkResult.message(): StringResource = when (this) {
    LinkResult.InvalidCode -> Res.string.ranking_link_invalid
    LinkResult.Limit -> Res.string.ranking_name_limit
    LinkResult.Outdated -> Res.string.ranking_link_outdated
    LinkResult.NoName -> Res.string.ranking_link_no_name
    LinkResult.Removed -> Res.string.ranking_link_removed
    LinkResult.Offline, is LinkResult.Code, is LinkResult.Linked -> Res.string.ranking_name_offline
}
