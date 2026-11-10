package com.rodrigomachado.riff.feature.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.room.resources.Res
import com.rodrigomachado.riff.feature.room.resources.room_full
import com.rodrigomachado.riff.feature.room.resources.room_no_connection
import com.rodrigomachado.riff.feature.room.resources.room_not_found
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

object RoomTestTags {
    /** A caixa do código da sala. */
    const val CODE = "room_code"
}

/** O botão de texto do topo (Voltar, Sair da sala), sem som: a navegação toca o de voltar ao sair da tela. */
@Composable
internal fun TopTextButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.focusHighlight(CircleShape)) {
        Text(text, color = RiffColors.Paper)
    }
}

/** O erro carimbado em vermelho escuro (vermelho do pôster sobre o preto não carrega texto). */
@Composable
internal fun RoomErrorText(error: RoomError, modifier: Modifier = Modifier) {
    Text(
        stringResource(error.message()),
        style = MaterialTheme.typography.bodyLarge,
        color = RiffColors.Paper,
        modifier = modifier.background(RiffColors.StampError, MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    )
}

private fun RoomError.message(): StringResource = when (this) {
    RoomError.NOT_FOUND -> Res.string.room_not_found
    RoomError.FULL -> Res.string.room_full
    RoomError.NO_CONNECTION -> Res.string.room_no_connection
}

/** A sombra dura dos quadros e cartazes do app. */
internal val HardShadow = 4.dp
