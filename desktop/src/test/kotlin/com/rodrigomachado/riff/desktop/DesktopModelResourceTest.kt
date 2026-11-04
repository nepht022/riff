package com.rodrigomachado.riff.desktop

import com.rodrigomachado.riff.core.separation.DesktopSeparation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

/** O modelo da IA vai nos recursos do app do PC, copiado do asset do celular no build (um arquivo só no git). */
class DesktopModelResourceTest {
    @Test
    fun theModelIsAResourceOfTheApp() {
        val resource = javaClass.getResource(DesktopSeparation.MODEL_RESOURCE)
        assertNotNull("${DesktopSeparation.MODEL_RESOURCE} fora dos recursos", resource)

        val asset = File("../core/separation/src/main/assets/htdemucs_6s_seg4.onnx")
        assertEquals(asset.length(), resource!!.openConnection().contentLengthLong)
    }
}
