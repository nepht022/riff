package com.rodrigomachado.riff.feature.room

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.room.RoomCodes
import com.rodrigomachado.riff.core.ui.component.BackRow
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.input.menuKeys
import com.rodrigomachado.riff.core.ui.input.upDownLeave
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.core.ui.theme.oswald
import com.rodrigomachado.riff.feature.room.resources.Res
import com.rodrigomachado.riff.feature.room.resources.room_back
import com.rodrigomachado.riff.feature.room.resources.room_code
import com.rodrigomachado.riff.feature.room.resources.room_join
import com.rodrigomachado.riff.feature.room.resources.room_join_with_code
import com.rodrigomachado.riff.feature.room.resources.room_loading
import org.jetbrains.compose.resources.stringResource

/** Entrar com o código. [onOpenRoom] leva o código da sala em que entrou; [onChooseName] como na escolha. */
@Composable
fun RoomJoinScreen(
    onBack: () -> Unit,
    onOpenRoom: (String) -> Unit,
    onChooseName: () -> Unit,
    viewModel: RoomJoinViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.joined) {
        state.joined?.let { code ->
            viewModel.onOpened()
            onOpenRoom(code)
        }
    }
    LaunchedEffect(state.askName) {
        if (state.askName) {
            viewModel.onAskedName()
            onChooseName()
        }
    }
    RoomJoinContent(state, viewModel::onCode, viewModel::join, modifier, onBack)
}

/** As 4 caixas de letra do código, o Entrar e o erro (sala não encontrada, cheia ou sem conexão). */
@Composable
fun RoomJoinContent(
    state: RoomJoinUiState,
    onCode: (String) -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    Column(
        modifier.fillMaxSize().menuKeys(onBack).verticalScroll(rememberScrollState()).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        BackRow(onBack = onBack, text = stringResource(Res.string.room_back))
        PosterTitle(stringResource(Res.string.room_join_with_code), style = MaterialTheme.typography.displaySmall)
        Text(
            stringResource(Res.string.room_code),
            style = MaterialTheme.typography.titleMedium,
            color = RiffColors.PaperDim,
            modifier = Modifier.padding(top = Spacing.md),
        )
        CodeBoxes(state.code, enabled = !state.joining, onCode = onCode, onDone = { if (state.canJoin) onJoin() })
        state.error?.let { RoomErrorText(it) }
        if (state.joining) {
            Text(
                stringResource(Res.string.room_loading),
                style = MaterialTheme.typography.bodyLarge,
                color = RiffColors.PaperDim,
            )
        }
        PosterButton(
            stringResource(Res.string.room_join),
            onClick = onJoin,
            enabled = state.canJoin,
            sound = MenuSound.CONFIRM,
            minWidth = JOIN_WIDTH,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.md),
        )
    }
}

/**
 * Um campo de texto desenhado como 4 caixas de letra (cartões de papel; a próxima a preencher com a borda vermelha).
 * O teclado já sugere maiúsculas; o ViewModel guarda só o que pode ser código.
 */
@Composable
private fun CodeBoxes(code: String, enabled: Boolean, onCode: (String) -> Unit, onDone: () -> Unit) {
    val letterStyle = TextStyle(fontFamily = oswald(), fontWeight = FontWeight.SemiBold, fontSize = LETTER_SIZE)
    BasicTextField(
        value = code,
        onValueChange = onCode,
        enabled = enabled,
        singleLine = true,
        cursorBrush = SolidColor(RiffColors.TourRed),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(onGo = { onDone() }),
        // No PC, ↑/↓ saem da caixa para os botões (←/→ andam nas letras).
        modifier = Modifier.upDownLeave().testTag(RoomTestTags.CODE),
        decorationBox = { innerTextField ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                repeat(RoomCodes.LENGTH) { index ->
                    val next = index == code.length
                    Box(
                        Modifier.size(BOX_SIZE)
                            .background(RiffColors.Paper, MaterialTheme.shapes.small)
                            .border(
                                BORDER,
                                if (next) RiffColors.TourRed else RiffColors.Ink,
                                MaterialTheme.shapes.small,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(code.getOrNull(index)?.toString().orEmpty(), style = letterStyle, color = RiffColors.Ink)
                    }
                }
                // O campo de verdade (o cursor e a seleção) fica escondido: as caixas mostram as letras.
                Box(Modifier.size(1.dp).alpha(0f)) { innerTextField() }
            }
        },
    )
}

private val BOX_SIZE = 64.dp
private val BORDER = 2.dp
private val LETTER_SIZE = 36.sp
private val JOIN_WIDTH = 200.dp
