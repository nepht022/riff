package com.rodrigomachado.riff.core.ui.stage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Raio zero ou NaN (Canvas ainda sem tamanho, profundidade fora da pista) não pode derrubar o app. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StageDrawingTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun gemAndButtonWithZeroOrNaNRadiusDrawNothingWithoutCrashing() {
        composeRule.setContent {
            Canvas(Modifier.size(100.dp)) {
                val cache = StageCache()
                listOf(0f, -1f, Float.NaN).forEach { radius ->
                    drawGem(cache, Offset(50f, 50f), radius, lane = 0)
                    drawFretButton(cache, Offset(50f, 50f), radius, lane = 1, pressed = true)
                    drawFretButton(cache, Offset(50f, 50f), radius, lane = 1, pressed = false)
                }
            }
        }
        composeRule.waitForIdle()
    }
}
