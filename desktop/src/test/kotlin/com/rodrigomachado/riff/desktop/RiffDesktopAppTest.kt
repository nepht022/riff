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
import com.github.takahirom.roborazzi.RoborazziOptions
import com.rodrigomachado.riff.desktop.pad.NoPadSource
import com.rodrigomachado.riff.feature.home.HomeTestTags
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Locale

/**
 * O Skia do PC suaviza o contorno das letras de um jeito no Windows e de outro no Linux do CI (0,39% dos pixels da
 * tela inicial, todos nos textos, medido em 2026-10-31). Até 1% passa; layout e cores continuam conferidos, e os
 * textos são conferidos pelos testes de conteúdo. As screenshots do celular seguem exatas.
 */
private val TextRasterTolerance =
    RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f))

class RiffDesktopAppTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private val system = Locale.getDefault()
    private val graph by lazy { DesktopGraph(temp.newFolder("dados"), padSource = NoPadSource) }

    @After
    fun restore() {
        Locale.setDefault(system)
        graph.close()
    }

    @Test
    fun opensOnTheHomeScreenInPortugueseEvenOnAnEnglishWindows() {
        Locale.setDefault(Locale.US)
        DesktopLocale.applyDefault()
        composeRule.setContent { RiffDesktopApp(graph) }

        composeRule.onNodeWithText("Sozinho").assertExists()
        composeRule.onNodeWithContentDescription("Sala, em breve").assertExists()
    }

    @Test
    fun aSmallWindowStillReachesTheRanking() {
        DesktopLocale.applyDefault()
        composeRule.setContent { Box(Modifier.size(800.dp, 500.dp)) { RiffDesktopApp(graph) } }

        composeRule.onNodeWithTag(HomeTestTags.RANKING).performScrollTo().assertIsDisplayed()
    }

    /** Na janela padrão dos testes do Compose para desktop (1024×768). */
    @Test
    fun homeScreenshot() {
        DesktopLocale.applyDefault()
        composeRule.setContent { RiffDesktopApp(graph) }

        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/Home_desktop.png", roborazziOptions = TextRasterTolerance)
    }
}
