package com.rodrigomachado.riff.desktop

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.rodrigomachado.riff.core.audio.MenuSoundPlayer
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.feature.home.HomeTestTags
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DesktopMenuSoundTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val temp = TemporaryFolder()

    private val played = mutableListOf<MenuSound>()
    private val graph by lazy {
        DesktopGraph(temp.newFolder("dados"), menuSounds = MenuSoundPlayer { sound, _ -> played += sound })
    }

    @After
    fun close() = graph.close()

    @Test
    fun clickingSoloPlaysTheNavigateSound() {
        DesktopLocale.applyDefault()
        composeRule.setContent { RiffDesktopApp(graph) }
        composeRule.waitForIdle()

        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(HomeTestTags.SOLO))

        assertEquals(listOf(MenuSound.NAVIGATE), played)
    }

    @Test
    fun withMenuSoundsOffTheClickIsSilent() {
        runBlocking { graph.settings.update { it.copy(menuSounds = false) } }
        DesktopLocale.applyDefault()
        composeRule.setContent { RiffDesktopApp(graph) }
        composeRule.waitForIdle()

        composeRule.clickOnWindowThread(composeRule.onNodeWithTag(HomeTestTags.SOLO))

        assertEquals(emptyList<MenuSound>(), played)
    }
}
