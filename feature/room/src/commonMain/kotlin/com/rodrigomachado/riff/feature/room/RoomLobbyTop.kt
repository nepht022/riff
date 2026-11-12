package com.rodrigomachado.riff.feature.room

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rodrigomachado.riff.core.ui.component.BackRow
import com.rodrigomachado.riff.core.ui.component.KeyHint
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.core.ui.theme.oswald
import com.rodrigomachado.riff.feature.room.resources.Res
import com.rodrigomachado.riff.feature.room.resources.room_code_small
import com.rodrigomachado.riff.feature.room.resources.room_copied
import com.rodrigomachado.riff.feature.room.resources.room_copy
import com.rodrigomachado.riff.feature.room.resources.room_keys_choose
import com.rodrigomachado.riff.feature.room.resources.room_keys_leave
import com.rodrigomachado.riff.feature.room.resources.room_keys_move
import com.rodrigomachado.riff.feature.room.resources.room_leave
import com.rodrigomachado.riff.feature.room.resources.room_share
import com.rodrigomachado.riff.feature.room.resources.room_share_text
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * O topo da sala: "Sair da sala" com a seta à esquerda; à direita, o código pequeno ("SALA KQZX") e o botão de
 * compartilhar (no PC, copiar: depois dele, "Código copiado"). Em tela estreita com fonte grande, o código desce
 * para a linha de baixo em vez de cortar.
 */
@Composable
internal fun LobbyTop(code: String, onLeave: () -> Unit) {
    val share = rememberCodeShare()
    val scope = rememberCoroutineScope()
    var copied by remember(code) { mutableStateOf(false) }
    val invite = stringResource(Res.string.room_share_text, code)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            BackRow(onBack = onLeave, text = stringResource(Res.string.room_leave))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallCode(code)
                ShareButton(onClick = { scope.launch { if (share.share(code, invite)) copied = true } })
            }
        }
        if (copied) {
            Text(
                stringResource(Res.string.room_copied),
                style = MaterialTheme.typography.bodyMedium,
                color = RiffColors.PaperDim,
                // O leitor de tela avisa a cópia sem o foco sair do botão.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** "SALA KQZX": o rótulo apagado e as letras do código espaçadas, lidos juntos pelo leitor de tela. */
@Composable
private fun SmallCode(code: String) {
    Row(
        Modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.room_code_small),
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = oswald(), letterSpacing = LABEL_SPACING),
            color = RiffColors.PaperDim,
        )
        Text(
            code,
            style = TextStyle(
                fontFamily = oswald(),
                fontWeight = FontWeight.SemiBold,
                fontSize = CODE_SIZE,
                letterSpacing = CODE_SPACING,
            ),
            color = RiffColors.Paper,
        )
    }
}

/** O botão quadrado de 44 dp com o ícone de compartilhar desenhado (no PC, a descrição diz "Copiar o código"). */
@Composable
private fun ShareButton(onClick: () -> Unit) {
    val description = stringResource(if (codeShareCopies) Res.string.room_copy else Res.string.room_share)
    val shape = MaterialTheme.shapes.small
    Box(
        Modifier
            .size(SHARE_SIZE)
            .border(SHARE_BORDER, RiffColors.PaperDim, shape)
            .focusHighlight(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        ShareIcon()
    }
}

/** Três pontos ligados (o ícone comum de compartilhar), em traço de papel, sem depender de pacote de ícones. */
@Composable
private fun ShareIcon() {
    Canvas(Modifier.size(ICON_SIZE)) {
        val radius = size.minDimension * DOT_FRACTION
        val right = size.width - radius
        val top = Offset(right, radius)
        val bottom = Offset(right, size.height - radius)
        val left = Offset(radius, size.height / 2)
        val stroke = ICON_STROKE.toPx()
        drawLine(RiffColors.Paper, left, top, stroke)
        drawLine(RiffColors.Paper, left, bottom, stroke)
        listOf(top, bottom, left).forEach { center -> drawCircle(RiffColors.Paper, radius, center) }
    }
}

/** As teclas da sala no PC. */
@Composable
internal fun roomKeys() = listOf(
    KeyHint("← → ↑ ↓", pad = null, label = stringResource(Res.string.room_keys_move)),
    KeyHint("Enter", pad = "✕", label = stringResource(Res.string.room_keys_choose)),
    KeyHint("Esc", pad = "○", label = stringResource(Res.string.room_keys_leave)),
)

private const val DOT_FRACTION = 0.16f
private val CODE_SIZE = 22.sp
private val CODE_SPACING = 3.sp
private val LABEL_SPACING = 1.5.sp
private val SHARE_SIZE = 44.dp
private val SHARE_BORDER = 1.dp
private val ICON_SIZE = 22.dp
private val ICON_STROKE = 2.dp
