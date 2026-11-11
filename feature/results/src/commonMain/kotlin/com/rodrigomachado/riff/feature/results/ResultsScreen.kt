package com.rodrigomachado.riff.feature.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.model.Difficulty
import com.rodrigomachado.riff.core.ui.art.ChartArtImages
import com.rodrigomachado.riff.core.ui.component.EmptyState
import com.rodrigomachado.riff.core.ui.component.KeyHintBar
import com.rodrigomachado.riff.core.ui.component.LoadingState
import com.rodrigomachado.riff.core.ui.input.LocalGamepad
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.results.resources.Res
import com.rodrigomachado.riff.feature.results.resources.results_back
import com.rodrigomachado.riff.feature.results.resources.results_back_to_room
import com.rodrigomachado.riff.feature.results.resources.results_missing
import com.rodrigomachado.riff.feature.results.resources.results_new_best_spoken
import com.rodrigomachado.riff.feature.results.resources.results_play_again
import com.rodrigomachado.riff.feature.results.resources.results_see_room
import com.rodrigomachado.riff.feature.results.resources.results_summary
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.text.NumberFormat

object ResultsTestTags {
    /** O carimbo da nota (lido como "Nota B"). */
    const val GRADE = "results_grade"

    /** A barra de teclas do PC. */
    const val KEY_HINTS = "results_key_hints"
}

/**
 * O Resultado; numa partida de sala ([inRoom], da [ResultsRoute]), sem "Jogar de novo": "Ver a sala" ([onSeeRoom], o
 * Resultado da sala, que o app abre) e "Voltar à sala" ([onDone]).
 */
@Composable
fun ResultsScreen(
    onPlayAgain: (chartId: String, difficulty: Difficulty) -> Unit,
    onDone: () -> Unit,
    viewModel: ResultsViewModel,
    modifier: Modifier = Modifier,
    onSeeRanking: (chartId: String, title: String, difficulty: Difficulty) -> Unit = { _, _, _ -> },
    onChooseName: () -> Unit = {},
    inRoom: Boolean = false,
    onSeeRoom: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ResultsContent(
        state, onPlayAgain, onDone, modifier, onSeeRanking, onChooseName, inRoom, onSeeRoom, viewModel.images,
    )
}

/**
 * O Resultado pronto: capa, título, artista · dificuldade; os pontos com o carimbo da nota e, no recorde novo, o de
 * antes; a precisão em barra; os quatro blocos (maior combo, perfeitos, bons, erros); a linha do ranking; e embaixo,
 * lado a lado, "Voltar" e "Jogar de novo" (na sala: "Voltar à sala" e "Ver a sala"). Larga (o PC), em duas colunas;
 * no PC (com o `LocalGamepad`), a barra de teclas no rodapé. [images]: a capa (sem ela, as iniciais).
 */
@Composable
@Suppress("LongParameterList") // o estado, a capa e o que cada botão da tela faz
fun ResultsContent(
    state: ResultsUiState,
    onPlayAgain: (chartId: String, difficulty: Difficulty) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onSeeRanking: (chartId: String, title: String, difficulty: Difficulty) -> Unit = { _, _, _ -> },
    onChooseName: () -> Unit = {},
    inRoom: Boolean = false,
    onSeeRoom: (() -> Unit)? = null,
    images: ChartArtImages? = null,
) {
    val back = stringResource(if (inRoom) Res.string.results_back_to_room else Res.string.results_back)
    when (state) {
        ResultsUiState.Loading -> LoadingState(modifier)

        ResultsUiState.Missing -> EmptyState(
            title = stringResource(Res.string.results_missing),
            message = "",
            actionLabel = back,
            onAction = onDone,
            modifier = modifier,
        )

        is ResultsUiState.Ready -> {
            val record = state.record
            // Na sala, o botão principal é "Ver a sala" (sem "Jogar de novo", que abriria uma partida sozinho).
            val main = if (inRoom) {
                onSeeRoom?.let { ResultsButton(stringResource(Res.string.results_see_room), it) }
            } else {
                ResultsButton(stringResource(Res.string.results_play_again)) {
                    onPlayAgain(record.chartId, record.difficulty)
                }
            }
            val actions = ResultsActions(
                back = ResultsButton(back, onDone),
                main = main,
                onSeeRanking = { onSeeRanking(record.chartId, state.title, record.difficulty) },
                onChooseName = onChooseName,
            )
            ReadyResults(state, actions, images, modifier)
        }
    }
}

/** Um botão do Resultado: o texto e a ação. */
internal class ResultsButton(val label: String, val onClick: () -> Unit)

/** Os botões do Resultado: o de voltar, o principal (nulo: nenhum) e os do ranking. */
internal class ResultsActions(
    val back: ResultsButton,
    val main: ResultsButton?,
    val onSeeRanking: () -> Unit,
    val onChooseName: () -> Unit,
)

@Composable
private fun ReadyResults(
    state: ResultsUiState.Ready,
    actions: ResultsActions,
    images: ChartArtImages?,
    modifier: Modifier,
) {
    val record = state.record
    // O idioma da interface (no celular, o da configuração; no PC, o do sistema), como no placar do Hud.
    val locale = java.util.Locale.forLanguageTag(Locale.current.toLanguageTag())
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val score = remember(record.score, numbers) { numbers.format(record.score) }
    val accuracy = remember(record.accuracy, locale) { NumberFormat.getPercentInstance(locale).format(record.accuracy) }
    val texts = ResultsTexts(score, accuracy, state.previousBest?.let { numbers.format(it) })
    // Frase para o TalkBack: "1 ponto" no singular e o recorde como frase (o selo em maiúsculas seria soletrado).
    val summary = pluralStringResource(Res.plurals.results_summary, record.score.toPluralCount(), score, accuracy) +
        if (state.isNewBest) " " + stringResource(Res.string.results_new_best_spoken) else ""
    val pad = LocalGamepad.current
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            // O TalkBack lê o título do painel quando a tela aparece: o resultado é anunciado sem tocar em nada.
            .semantics { paneTitle = summary },
    ) {
        val wide = maxWidth >= WIDE_MIN_WIDTH
        Column(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                // Rola quando não cabe (fonte grande, janela baixa); no PC, quando cabe, fica no meio da altura.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight)
                        .padding(Spacing.md),
                    verticalArrangement = if (wide) Arrangement.Center else Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (wide) {
                        WideResults(state, texts, actions, images)
                    } else {
                        PhoneResults(state, texts, actions, images)
                    }
                }
            }
            if (pad != null) {
                val connected by pad.connected.collectAsState()
                KeyHintBar(resultsKeys(), connected, Modifier.testTag(ResultsTestTags.KEY_HINTS))
            }
        }
    }
}

/** Os números já no formato do idioma: os pontos, a precisão e o recorde de antes (nulo: não havia). */
internal class ResultsTexts(val score: String, val accuracy: String, val previousBest: String?)

/** O celular (e o celular deitado): tudo numa coluna, que rola com a fonte grande. */
@Composable
private fun PhoneResults(
    state: ResultsUiState.Ready,
    texts: ResultsTexts,
    actions: ResultsActions,
    images: ChartArtImages?,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        ResultsHeader(state, images, PHONE_COVER)
        ScoreAndGrade(state, texts, PHONE_SCORE)
        AccuracyBar(state.record, texts.accuracy)
        StatBlocks(state.record, columns = 2)
        if (state.rankingOn) RankingCard(state.ranking, actions.onSeeRanking, actions.onChooseName)
        ResultsButtons(actions)
    }
}

/** O PC: à esquerda a capa, o título e os pontos com a nota; à direita os números, o ranking e os botões. */
@Composable
private fun WideResults(
    state: ResultsUiState.Ready,
    texts: ResultsTexts,
    actions: ResultsActions,
    images: ChartArtImages?,
) {
    // O widthIn vem antes do fillMaxWidth: estica até o máximo do PC, nunca além.
    Row(Modifier.widthIn(max = WIDE_CONTENT).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            ResultsHeader(state, images, WIDE_COVER)
            ScoreAndGrade(state, texts, WIDE_SCORE)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            AccuracyBar(state.record, texts.accuracy)
            StatBlocks(state.record, columns = 2)
            if (state.rankingOn) RankingCard(state.ranking, actions.onSeeRanking, actions.onChooseName)
            ResultsButtons(actions)
        }
    }
}

/** Só 1 é singular: no português o "one" vale também para 0, e "0 ponto" soa errado. */
private fun Long.toPluralCount(): Int = if (this == 1L) 1 else 2

/** A partir desta largura, o layout do PC (o celular deitado, ~780 dp, continua no do celular). */
private val WIDE_MIN_WIDTH = 800.dp

/** A largura máxima do conteúdo no PC. */
private val WIDE_CONTENT = 960.dp

private val PHONE_COVER = 72.dp
private val WIDE_COVER = 112.dp
private val PHONE_SCORE = 52.sp
private val WIDE_SCORE = 72.sp
