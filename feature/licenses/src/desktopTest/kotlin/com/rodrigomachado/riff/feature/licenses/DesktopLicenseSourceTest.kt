package com.rodrigomachado.riff.feature.licenses

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

    /** A guitarra por IA no PC (etapa 4c): o modelo, o ONNX Runtime e a FFT, com os avisos de copyright de verdade. */
    @Test
    fun thePcHasTheAiModelToo() = runBlocking {
        val content = source.content().associateBy { it.name }

        assertTrue(content.getValue("Demucs v4 (htdemucs_6s)").text.orEmpty().contains("Meta Platforms"))
        assertTrue(content.getValue("demucs.onnx").text.orEmpty().contains("Sevag H"))
        assertTrue(content.getValue("ONNX Runtime").text.orEmpty().contains("Microsoft Corporation"))
        assertNotNull(content["JTransforms"]?.text)
        assertNotNull(content["JLargeArrays"]?.text)
    }

    @Test
    fun theAiLibrariesFromGradleStayOnlyInTheManualList() = runBlocking {
        val json = JSON.replace(
            "\"libraries\":[",
            """"libraries":[
              {"uniqueId":"com.microsoft.onnxruntime:onnxruntime","artifactVersion":"1.18.0","name":"onnxruntime",
               "developers":[],"licenses":["BSD-3-Clause"]},
              {"uniqueId":"com.github.wendykierp:JTransforms","artifactVersion":"3.1","name":"JTransforms",
               "developers":[],"licenses":["BSD-3-Clause"]},
              {"uniqueId":"org.visnow:JLargeArrays","artifactVersion":"1.6","name":"JLargeArrays",
               "developers":[],"licenses":["BSD-3-Clause"]},""",
        )

        assertEquals(listOf("Compose Runtime"), DesktopLicenseSource { json }.libraries().map { it.name })
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
