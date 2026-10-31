package com.rodrigomachado.riff.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import com.rodrigomachado.riff.feature.home.HomeScreen
import java.util.Locale

/** O Riff no PC: por enquanto só a tela inicial; os botões levam às telas nas próximas etapas. */
@Composable
fun RiffDesktopApp() {
    RiffTheme {
        StageBackground(Modifier.fillMaxSize()) {
            HomeScreen(onSolo = {}, onRanking = {}, onSettings = {}, onReport = {})
        }
    }
}

/** Português é o padrão, mesmo num Windows em inglês (como no celular); escolher o idioma no PC vem depois. */
object DesktopLocale {
    fun applyDefault() = Locale.setDefault(Locale.forLanguageTag("pt-BR"))
}
