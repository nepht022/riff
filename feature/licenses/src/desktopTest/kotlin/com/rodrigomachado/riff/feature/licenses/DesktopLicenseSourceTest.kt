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

    /** O código nativo que vai no instalador do PC (etapa 4e): o Java, o que vem dentro do FFmpeg e do Skiko. */
    @Test
    fun theNativeCodeInsideThePcAppHasItsNotices() = runBlocking {
        val content = DesktopLicenseSource { null }.content().associateBy { it.name }

        for (name in listOf("OpenJDK (Java do app)", "Bibliotecas dentro do FFmpeg", "Skia", "ICU")) {
            assertNotNull("$name na lista do PC", content[name]?.text)
        }
        val openJdk = content.getValue("OpenJDK (Java do app)").text.orEmpty()
        assertTrue(openJdk.contains("\"CLASSPATH\" EXCEPTION TO THE GPL"))
        assertTrue(content.getValue("Skia").text.orEmpty().contains("Copyright (c) 2011 Google Inc."))
        assertTrue(content.getValue("ICU").text.orEmpty().contains("UNICODE LICENSE V3"))
    }

    /** As DLLs do Visual C++ e do UCRT da Microsoft (no runtime\\bin do Java e no jar do JavaCPP), com o aviso. */
    @Test
    fun theMicrosoftRuntimeDllsHaveTheirNotice() = runBlocking {
        val text = source.content().single { it.name == "Microsoft Visual C++ e UCRT" }.text.orEmpty()

        assertTrue(text.contains("Copyright (c) Microsoft Corporation"))
        listOf("vcruntime140.dll", "msvcp140.dll", "ucrtbase.dll", "concrt140.dll", "vcomp140.dll", "api-ms-win-")
            .forEach { assertTrue("$it no aviso", text.contains(it)) }
    }

    /** Patentes de codecs: o aviso diz quais formatos o FFmpeg do app traz e que o Riff só lê áudio. */
    @Test
    fun theFfmpegLibrariesNoticeMentionsCodecPatents() = runBlocking {
        val text = source.content().single { it.name == "Bibliotecas dentro do FFmpeg" }.text.orEmpty()

        assertTrue(text.contains("Patentes"))
        listOf("MP3", "AMR", "H.264").forEach { assertTrue("$it no aviso de patentes", text.contains(it)) }
    }

    /** GPL 2 e LGPL: a cópia do código-fonte vai junto do instalador, na página de cada versão no GitHub. */
    @Test
    fun copyleftNoticesPointToTheSourceNextToTheInstaller() = runBlocking {
        val content = source.content().associateBy { it.name }

        for (name in listOf("OpenJDK (Java do app)", "FFmpeg", "Bibliotecas dentro do FFmpeg")) {
            val text = content.getValue(name).text.orEmpty()
            assertTrue("$name aponta a página das versões", text.contains("https://github.com/nepht022/riff/releases"))
            assertTrue("$name cita os arquivos fonte-", text.contains("fonte-"))
        }
    }

    @Test
    fun theFfmpegNoticeSaysWhereTheSourceIs() = runBlocking {
        val text = source.content().single { it.name == "FFmpeg" }.text.orEmpty()

        // O código-fonte exato: o FFmpeg 8.1.2 e a receita do build na tag 1.5.14 do javacpp-presets.
        assertTrue(text.contains("https://ffmpeg.org/releases/ffmpeg-8.1.2.tar"))
        assertTrue(text.contains("https://github.com/bytedeco/javacpp-presets/tree/1.5.14/ffmpeg"))
    }

    @Test
    fun everyLibraryInsideFfmpegIsNamed() = runBlocking {
        val text = source.content().single { it.name == "Bibliotecas dentro do FFmpeg" }.text.orEmpty()

        listOf(
            "LAME", "Speex", "opencore-amr", "vo-amrwbenc", "OpenSSL", "OpenH264", "libvpx", "FreeType",
            "HarfBuzz", "Opus", "libxml2", "SRT", "libwebp", "AOM", "SVT-AV1", "zimg", "libmfx", "winpthreads",
            "zlib", "nv-codec-headers",
        ).forEach { assertTrue("$it no aviso", text.contains(it, ignoreCase = true)) }
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
