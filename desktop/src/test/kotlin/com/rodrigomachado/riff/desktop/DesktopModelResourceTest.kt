package com.rodrigomachado.riff.desktop

import com.rodrigomachado.riff.core.separation.DesktopSeparation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.io.File

/** O modelo da IA vai nos recursos do app do PC, copiado do asset do celular no build (um arquivo só no git). */
class DesktopModelResourceTest {
    @Test
    fun theModelIsAResourceOfTheApp() {
        val asset = File("../core/separation/src/main/assets/htdemucs_6s_seg4.onnx")
        assumeFalse(
            "${asset.path} é só o ponteiro do Git LFS (rode `git lfs pull`): teste com o modelo pulado",
            asset.length() < LFS_POINTER_MAX_BYTES,
        )
        assertTrue("o modelo tem só ${asset.length()} bytes", asset.length() > MIN_MODEL_BYTES)

        val resource = javaClass.getResource(DesktopSeparation.MODEL_RESOURCE)
        assertNotNull("${DesktopSeparation.MODEL_RESOURCE} fora dos recursos", resource)
        assertEquals(asset.length(), resource!!.openConnection().contentLengthLong)
    }

    private companion object {
        const val LFS_POINTER_MAX_BYTES = 1024L
        const val MIN_MODEL_BYTES = 100L shl 20
    }
}
