package com.rodrigomachado.riff.feature.room

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.BackRow
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.component.WithPcKeyHints
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.input.menuKeys
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.room.resources.Res
import com.rodrigomachado.riff.feature.room.resources.room_back
import com.rodrigomachado.riff.feature.room.resources.room_create
import com.rodrigomachado.riff.feature.room.resources.room_join_with_code
import com.rodrigomachado.riff.feature.room.resources.room_loading
import com.rodrigomachado.riff.feature.room.resources.room_title
import org.jetbrains.compose.resources.stringResource

/**
 * A escolha da Sala. [onOpenRoom] leva o código da sala criada; [onChooseName] abre o diálogo do nome do ranking (sem
 * nome, criar espera por ele).
 */
@Composable
fun RoomEntryScreen(
    onBack: () -> Unit,
    onJoin: () -> Unit,
    onOpenRoom: (String) -> Unit,
    onChooseName: () -> Unit,
    viewModel: RoomEntryViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.created) {
        state.created?.let { code ->
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
    RoomEntryContent(state, viewModel::create, onJoin, modifier, onBack)
}

/** Dois quadros grandes no estilo pôster da tela inicial: "Criar sala" e "Entrar com código". */
@Composable
fun RoomEntryContent(
    state: RoomEntryUiState,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    WithPcKeyHints(roomMenuKeys(), modifier.menuKeys(onBack)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            BackRow(onBack = onBack, text = stringResource(Res.string.room_back))
            PosterTitle(stringResource(Res.string.room_title), style = MaterialTheme.typography.displayMedium)
            val enabled = !state.creating
            BigTile(
                stringResource(Res.string.room_create),
                RiffColors.TourRed to RiffColors.Paper,
                onClick = onCreate.takeIf { enabled },
                sound = MenuSound.CONFIRM,
            )
            BigTile(
                stringResource(Res.string.room_join_with_code),
                RiffColors.Paper to RiffColors.Ink,
                onClick = onJoin.takeIf { enabled },
                sound = MenuSound.NAVIGATE,
            )
            if (state.creating) {
                Text(
                    stringResource(Res.string.room_loading),
                    style = MaterialTheme.typography.bodyLarge,
                    color = RiffColors.PaperDim,
                )
            }
            state.error?.let { RoomErrorText(it) }
        }
    }
}

/** Quadro largo com sombra dura, como os da tela inicial; sem [onClick], apagado. */
@Composable
private fun BigTile(text: String, colors: Pair<Color, Color>, onClick: (() -> Unit)?, sound: MenuSound) {
    val (background, content) = colors
    val shape = MaterialTheme.shapes.small
    val action = if (onClick != null) {
        Modifier.focusHighlight(shape).clickable(role = Role.Button, onClick = withMenuSound(sound, onClick))
    } else {
        Modifier.alpha(DISABLED_ALPHA)
    }
    Box(
        Modifier.fillMaxWidth().padding(end = HardShadow, bottom = HardShadow).height(TILE_HEIGHT)
            .drawBehind { drawRect(Color.Black, Offset(HardShadow.toPx(), HardShadow.toPx()), size) }
            .background(background, shape)
            .then(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.headlineMedium,
            color = content,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(Spacing.md),
        )
    }
}

private val TILE_HEIGHT = 140.dp
private const val DISABLED_ALPHA = 0.45f
