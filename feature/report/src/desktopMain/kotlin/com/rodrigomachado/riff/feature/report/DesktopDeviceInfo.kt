package com.rodrigomachado.riff.feature.report

import java.util.Locale

/**
 * As informações do PC que vão com o relato: o sistema como o Java o vê ("Windows 11 10.0 (amd64)"), a versão do
 * Riff e o idioma do jogo ([language]). Nada do nome do computador, do usuário nem de pastas (LGPD: não ajudam a
 * reproduzir o problema). [property] lê as propriedades do sistema (nos testes, um mapa).
 */
class DesktopDeviceInfo(
    private val language: () -> String = { Locale.getDefault().toLanguageTag() },
    private val property: (String) -> String? = System::getProperty,
) : DeviceInfoSource {
    override fun read() = DeviceInfo(
        appVersion = ReportConfig.APP_VERSION,
        system = system(),
        model = null,
        language = language(),
    )

    private fun system(): String {
        val name = property("os.name")?.trim().orEmpty().ifEmpty { UNKNOWN }
        val version = property("os.version")?.trim().orEmpty()
        val arch = property("os.arch")?.trim().orEmpty()
        return buildString {
            append(name)
            if (version.isNotEmpty()) append(' ').append(version)
            if (arch.isNotEmpty()) append(" (").append(arch).append(')')
        }
    }

    private companion object {
        const val UNKNOWN = "Sistema desconhecido"
    }
}
