package com.rodrigomachado.riff.core.ui.theme

import android.content.res.Configuration
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.rodrigomachado.riff.core.ui.component.difficultyName
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class AppLanguageTest {
    @get:Rule
    val composeRule = createComposeRule()

    /**
     * O idioma escolhido no Riff chega à tela como no app: o `Locale` padrão do processo (o `LegacyAppLanguage` antes
     * do Android 13, o próprio Android com o LocaleManager depois) e o contexto da tela no mesmo idioma. Os textos do
     * Compose Multiplatform leem o primeiro; os do Android, o segundo.
     */
    @Test
    fun theDifficultyNamesFollowTheAppLanguage() {
        val system = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            showEasyInTheAppLanguage()
        } finally {
            Locale.setDefault(system)
        }

        composeRule.onNodeWithText("Easy").assertExists() // pt: "Fácil"
    }

    private fun showEasyInTheAppLanguage() {
        composeRule.setContent {
            val english = Configuration(LocalConfiguration.current).apply { setLocale(Locale.ENGLISH) }
            val context = LocalContext.current.createConfigurationContext(english)
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides english) {
                RiffTheme { Text(difficultyName(0)) }
            }
        }
        composeRule.waitForIdle()
    }
}
