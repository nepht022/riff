package com.rodrigomachado.riff.feature.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** As informações do PC que vão no relato: só o sistema, a versão do Riff e o idioma do jogo. */
class DesktopDeviceInfoTest {
    private val properties = mapOf(
        "os.name" to "Windows 11",
        "os.version" to "10.0",
        "os.arch" to "amd64",
        "user.name" to "Fulano",
        "user.home" to "C:\\Users\\Fulano",
    )

    @Test
    fun theSystemComesFromTheOsProperties() {
        val info = DesktopDeviceInfo(language = { "pt-BR" }, property = properties::get).read()

        assertEquals("Windows 11 10.0 (amd64)", info.system)
        assertNull(info.model)
        assertEquals("pt-BR", info.language)
        assertEquals(ReportConfig.APP_VERSION, info.appVersion)
        assertEquals("Riff ${ReportConfig.APP_VERSION} · Windows 11 10.0 (amd64) · pt-BR", info.summary)
    }

    @Test
    fun theJsonHasTheSystemAndNoModel() {
        val info = DesktopDeviceInfo(language = { "en" }, property = properties::get).read()

        val json = BugReport(ReportKind.PROBLEM, "Oi", null, info, ReportCategory.CRASH).toJson()

        assertTrue(json, json.contains(""""sistema":"Windows 11 10.0 (amd64)""""))
        assertFalse(json, json.contains("modelo"))
        assertFalse(json, json.contains("android"))
    }

    @Test
    fun nothingPersonalGoes() {
        val info = DesktopDeviceInfo(language = { "pt-BR" }, property = properties::get).read()

        val json = BugReport(ReportKind.PROBLEM, "Oi", null, info, ReportCategory.CRASH).toJson()

        for (personal in listOf("Fulano", "Users")) {
            assertFalse("$personal na linha", info.summary.contains(personal))
            assertFalse("$personal no JSON", json.contains(personal))
        }
        // O nome do computador (Windows) nem é lido; aqui, o do PC que roda o teste.
        System.getenv("COMPUTERNAME")?.let { assertFalse(json.contains(it)) }
    }

    @Test
    fun withoutTheOsPropertiesTheLineStillReads() {
        val info = DesktopDeviceInfo(language = { "pt-BR" }, property = { null }).read()

        assertTrue(info.system.isNotBlank())
    }
}
