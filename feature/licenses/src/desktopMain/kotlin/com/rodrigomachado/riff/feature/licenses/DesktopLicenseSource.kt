package com.rodrigomachado.riff.feature.licenses

import com.mikepenz.aboutlibraries.Libs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * As licenças do app do PC: a lista manual (o que não vem do Gradle ou vem sem o aviso de copyright de verdade; textos
 * em composeResources/files/licencas) e as bibliotecas do `aboutlibraries.json` que o plugin do AboutLibraries gera
 * no build do `:desktop` ([librariesJson]; null sem o arquivo). Sem a IA (Demucs, ONNX Runtime): chega na etapa 4c.
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
            "org.lwjgl:lwjgl",
            "org.lwjgl:lwjgl-glfw",
            "androidx.datastore:datastore-preferences-external-protobuf",
        )

        val CONTENT = listOf(
            ManualItem("LWJGL", "Lightweight Java Game Library", "BSD 3-Clause", "lwjgl.txt"),
            // Dentro do LWJGL: o GLFW (o controle) e o libffi (no lwjgl.dll).
            ManualItem("GLFW", "Marcus Geelnard, Camilla Löwy", "zlib", "glfw.txt"),
            ManualItem("libffi", "Anthony Green, Red Hat, Inc and others", "MIT", "libffi.txt"),
            // O áudio das músicas: o FFmpeg compilado pelo JavaCPP Presets (bytedeco), LGPL 3 (--enable-version3).
            ManualItem("FFmpeg", "The FFmpeg developers", "LGPL 3.0 ou posterior", "ffmpeg-lgpl3.txt"),
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
