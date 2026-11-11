package com.rodrigomachado.riff.feature.ranking

import androidx.compose.runtime.Composable
import com.rodrigomachado.riff.core.ui.component.BackRow
import com.rodrigomachado.riff.core.ui.component.KeyHint
import com.rodrigomachado.riff.core.ui.component.backHint
import com.rodrigomachado.riff.core.ui.component.chooseHint
import com.rodrigomachado.riff.core.ui.component.moveHint
import com.rodrigomachado.riff.feature.ranking.resources.Res
import com.rodrigomachado.riff.feature.ranking.resources.ranking_back
import org.jetbrains.compose.resources.stringResource

/** O "Voltar" do topo da lista e do placar (a seta e o texto, como nas outras telas). */
@Composable
internal fun BackButton(onBack: () -> Unit) {
    // Sem som aqui: a navegação toca o de voltar ao sair da tela.
    BackRow(onBack = onBack, text = stringResource(Res.string.ranking_back))
}

/** As teclas da lista e do placar do ranking no PC. */
@Composable
internal fun rankingKeys(): List<KeyHint> = listOf(moveHint(), chooseHint(), backHint())

/** As teclas dos diálogos do nome e do vínculo no PC; o Esc faz [escape]. */
@Composable
internal fun rankingDialogKeys(escape: String): List<KeyHint> = listOf(moveHint("↑ ↓"), chooseHint(), backHint(escape))
