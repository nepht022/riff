package com.rodrigomachado.riff.feature.licenses

import com.mikepenz.aboutlibraries.Libs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * As licenças do app do PC: a lista manual (o que não vem do Gradle ou vem sem o aviso de copyright de verdade; textos
 * em composeResources/files/licencas) e as bibliotecas do `aboutlibraries.json` que o plugin do AboutLibraries gera
 * no build do `:desktop` ([librariesJson]; null sem o arquivo). Com a guitarra por IA (etapa 4c), como no celular.
 */
class DesktopLicenseSource(private val librariesJson: () -> String?) : LicenseSource {
    override suspend fun content() = withContext(Dispatchers.IO) { CONTENT.toEntries() }

    override suspend fun libraries() = withContext(Dispatchers.IO) {
        val json = librariesJson() ?: return@withContext emptyList()
        // O mesmo item duas vezes (mesmo nome, autor e licença) aparece uma vez só.
        Libs.Builder().withJson(json).build().libraries.toEntries(IN_CONTENT).distinct()
    }

    private companion object {
        /** Na lista manual, com o aviso de copyright de verdade (o AboutLibraries traz só o modelo da BSD). */
        val IN_CONTENT = setOf(
            // A IA: MIT e BSD pedem o aviso de copyright de verdade.
            "com.microsoft.onnxruntime:onnxruntime",
            "com.github.wendykierp:JTransforms",
            "org.visnow:JLargeArrays",
            "org.lwjgl:lwjgl",
            "org.lwjgl:lwjgl-glfw",
            "androidx.datastore:datastore-preferences-external-protobuf",
        )

        val CONTENT = listOf(
            // A guitarra separada por IA: o modelo, a conversão dele para ONNX, quem o roda e a FFT.
            ManualItem("Demucs v4 (htdemucs_6s)", "Meta Platforms", "MIT", "demucs.txt"),
            ManualItem("demucs.onnx", "Sevag Hanssian", "MIT", "demucs-onnx.txt"),
            ManualItem("ONNX Runtime", "Microsoft Corporation", "MIT", "onnxruntime.txt"),
            ManualItem("JTransforms", "Piotr Wendykier", "BSD 2-Clause", "jtransforms.txt"),
            ManualItem("JLargeArrays", "University of Warsaw, ICM; visnow.org", "BSD 2-Clause", "jlargearrays.txt"),
            ManualItem("LWJGL", "Lightweight Java Game Library", "BSD 3-Clause", "lwjgl.txt"),
            // Dentro do LWJGL: o GLFW (o controle) e o libffi (no lwjgl.dll).
            ManualItem("GLFW", "Marcus Geelnard, Camilla Löwy", "zlib", "glfw.txt"),
            ManualItem("libffi", "Anthony Green, Red Hat, Inc and others", "MIT", "libffi.txt"),
            // O áudio das músicas: o FFmpeg compilado pelo JavaCPP Presets (bytedeco), LGPL 3 (--enable-version3).
            ManualItem("FFmpeg", "The FFmpeg developers", "LGPL 3.0 ou posterior", "ffmpeg-lgpl3.txt"),
            // O código nativo do instalador (etapa 4e); textos só do PC, em desktopMain/composeResources.
            ManualItem(
                "Bibliotecas dentro do FFmpeg",
                "Vários autores",
                "Várias (ver texto)",
                "ffmpeg-bibliotecas.txt",
            ),
            ManualItem(
                "OpenJDK (Java do app)",
                "Oracle e colaboradores do OpenJDK; Eclipse Adoptium",
                "GPL 2.0 com Classpath Exception",
                "openjdk-gpl2-ce.txt",
            ),
            // As DLLs do Visual C++ e do UCRT (no runtime\\bin do Java e no jar do JavaCPP): código da Microsoft.
            ManualItem(
                "Microsoft Visual C++ e UCRT",
                "Microsoft Corporation",
                "Distributable Code (Visual Studio)",
                "microsoft-vcpp.txt",
            ),
            // Dentro do skiko-windows-x64.dll: o Skia (com as bibliotecas dele) e o ICU (icudtl.dat).
            ManualItem("Skia", "Google", "BSD 3-Clause", "skia.txt"),
            ManualItem("ICU", "Unicode, Inc.", "Unicode License v3", "icu.txt"),
            ManualItem("Protocol Buffers", "Google", "BSD 3-Clause", "protobuf.txt"),
            ManualItem(
                "New Rocker",
                "Pablo Impallari, Brenda Gallo",
                "SIL Open Font License 1.1",
                "new-rocker-ofl.txt",
            ),
            ManualItem("Oswald", "The Oswald Project Authors", "SIL Open Font License 1.1", "oswald-ofl.txt"),
            ManualItem(
                "Gravações de guitarra (Freesound)",
                "chiller345, SpeedY, gazbooth, Joao_Janz",
                "CC0 1.0",
                "cc0-1.0.txt",
            ),
            ManualItem("Mapas do Encore (enchor.us)", "Comunidade do Clone Hero", "De cada autor", null),
        )
    }
}
