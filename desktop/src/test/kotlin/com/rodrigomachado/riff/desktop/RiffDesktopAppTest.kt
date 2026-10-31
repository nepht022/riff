package com.rodrigomachado.riff.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.feature.home.HomeTestTags
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class RiffDesktopAppTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val system = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(system)

    @Test
    fun opensOnTheHomeScreenInPortugueseEvenOnAnEnglishWindows() {
        Locale.setDefault(Locale.US)
        DesktopLocale.applyDefault()
        composeRule.setContent { RiffDesktopApp() }

        composeRule.onNodeWithText("Sozinho").assertExists()
        composeRule.onNodeWithContentDescription("Sala, em breve").assertExists()
    }

    @Test
    fun aSmallWindowStillReachesTheRanking() {
        DesktopLocale.applyDefault()
        composeRule.setContent { Box(Modifier.size(800.dp, 500.dp)) { RiffDesktopApp() } }

        composeRule.onNodeWithTag(HomeTestTags.RANKING).performScrollTo().assertIsDisplayed()
    }

    /** Na janela padrão dos testes do Compose para desktop (1024×768). */
    @Test
    fun homeScreenshot() {
        DesktopLocale.applyDefault()
        composeRule.setContent { RiffDesktopApp() }

        composeRule.onRoot().captureRoboImage("src/test/screenshots/Home_desktop.png")
    }
}
