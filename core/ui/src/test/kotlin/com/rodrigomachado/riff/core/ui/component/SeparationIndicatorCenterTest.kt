package com.rodrigomachado.riff.core.ui.component

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.rodrigomachado.riff.core.model.SeparationStatus
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * A palheta é larga em cima e fina na ponta: centrada pela caixa, ela parece subida dentro do anel. O centro de massa
 * dos pixels vermelhos tem de cair no centro do anel (até 1 dp).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "xhdpi")
class SeparationIndicatorCenterTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun thePickSitsInTheMiddleOfTheRing() {
        composeRule.setContent {
            RiffTheme { SeparationIndicatorContent(SeparationStatus.Running("m", "Música", 0.4f, null), {}, {}) }
        }

        val pixels = composeRule.onNodeWithTag(SeparationIndicatorTags.ICON).captureToImage().toPixelMap()
        var sumY = 0.0
        var count = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val c = pixels[x, y]
                if (c.red > 0.55f && c.green < 0.25f && c.blue < 0.3f) {
                    sumY += y
                    count++
                }
            }
        }
        val centroidY = sumY / count
        val middle = pixels.height / 2.0
        val oneDp = 2.0 // xhdpi
        val centered = count > 0 && abs(centroidY - middle) <= oneDp
        assertTrue("palheta em y=$centroidY, meio=$middle ($count pixels)", centered)
    }
}
