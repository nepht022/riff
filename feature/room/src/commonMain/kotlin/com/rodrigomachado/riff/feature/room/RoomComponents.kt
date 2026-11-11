package com.rodrigomachado.riff.feature.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    /** A linha do que falta na sala ("2 prontos · esperando…"). */
    const val MISSING = "room_missing"

    /** A barra de teclas da sala (só no PC). */
    const val KEY_HINTS = "room_key_hints"
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

/** Carimbo chapado (como o StatusBadge), sem caixa alta: o texto é o que o teste e o leitor de tela leem. */
@Composable
internal fun Stamp(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = STAMP_SPACING),
        color = RiffColors.Paper,
        modifier = modifier.background(color).padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}

private val STAMP_SPACING = 1.5.sp

/** A sombra dura dos quadros e cartazes do app. */
internal val HardShadow = 4.dp
