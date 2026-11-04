package com.rodrigomachado.riff.feature.licenses

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** As licenças do app do PC: a lista manual (com os avisos de verdade) e as bibliotecas do aboutlibraries.json. */
class DesktopLicenseSourceTest {
    private val source = DesktopLicenseSource { JSON }

    @Test
    fun theManualListHasWhatGoesWithThePc() = runBlocking {
        val content = source.content().associateBy { it.name }

        for (name in listOf("LWJGL", "GLFW", "libffi", "FFmpeg", "New Rocker", "Oswald", "Protocol Buffers")) {
            assertNotNull("$name não está na lista", content[name]?.text)
        }
        val lwjgl = content.getValue("LWJGL").text.orEmpty()
        assertTrue(lwjgl.contains("Copyright (c) 2012-present Lightweight Java Game Library"))
        assertTrue(content.getValue("GLFW").text.orEmpty().contains("Copyright (c) 2006-2019 Camilla Löwy"))
        assertTrue(content.getValue("FFmpeg").text.orEmpty().contains("GNU LESSER GENERAL PUBLIC LICENSE"))
        assertTrue(content.keys.any { it.startsWith("Mapas do Encore") })
        assertTrue(content.keys.any { it.startsWith("Gravações de guitarra") })
    }

    @Test
    fun onlyThePhoneHasTheAiModel() = runBlocking {
        val names = source.content().map { it.name }

        for (phoneOnly in listOf("Demucs", "demucs.onnx", "ONNX Runtime", "JTransforms", "JLargeArrays")) {
            assertFalse("$phoneOnly no PC", names.any { it.startsWith(phoneOnly) })
        }
    }

    @Test
    fun theLibrariesComeFromTheJsonWithoutTheManualOnes() = runBlocking {
        val libraries = source.libraries()

        assertEquals(listOf("Compose Runtime"), libraries.map { it.name })
        assertEquals("Apache License 2.0", libraries.single().license)
        assertEquals("Texto da Apache", libraries.single().text)
    }

    @Test
    fun withoutTheJsonTheListIsEmpty() = runBlocking {
        assertEquals(emptyList<LicenseEntry>(), DesktopLicenseSource { null }.libraries())
    }

    private companion object {
        /** O formato do aboutlibraries.json do plugin: o LWJGL e o protobuf ficam só na lista manual. */
        val JSON = """
            {"libraries":[
              {"uniqueId":"org.lwjgl:lwjgl","artifactVersion":"3.3.6","name":"LWJGL","developers":[],
               "licenses":["BSD-3-Clause"]},
              {"uniqueId":"androidx.datastore:datastore-preferences-external-protobuf","artifactVersion":"1.2.1",
               "name":"Preferences External Protobuf","developers":[],"licenses":["BSD-3-Clause"]},
              {"uniqueId":"org.jetbrains.compose.runtime:runtime","artifactVersion":"1.12.2",
               "name":"Compose Runtime","developers":[{"name":"The Android Open Source Project"}],
               "licenses":["Apache-2.0"]}
            ],
            "licenses":{
              "Apache-2.0":{"name":"Apache License 2.0","url":"https://spdx.org/licenses/Apache-2.0.html",
                "content":"Texto da Apache","hash":"Apache-2.0"},
              "BSD-3-Clause":{"name":"BSD 3-Clause","url":"https://spdx.org/licenses/BSD-3-Clause.html",
                "content":"Modelo da BSD","hash":"BSD-3-Clause"}
            }}
        """.trimIndent()
    }
}
