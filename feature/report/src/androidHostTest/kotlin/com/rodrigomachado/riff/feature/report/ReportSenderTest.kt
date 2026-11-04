package com.rodrigomachado.riff.feature.report

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReportSenderTest {
    private val device =
        DeviceInfo.android(appVersion = "1.4", release = "16", model = "samsung SM-S931B", language = "pt-BR")
    private val sent = mutableListOf<Pair<String, String>>()
    private var answer = 200 to """{"ok":true}"""

    private val sender = AppsScriptReportSender("https://script.example/exec") { url, json ->
        sent += url to json
        answer
    }

    @Test
    fun theReportGoesToTheScriptWithEveryField() = runTest {
        sender.send(BugReport(ReportKind.PROBLEM, "A nota some", "eu@exemplo.com", device, ReportCategory.SYNC))

        val (url, json) = sent.single()
        val body = JSONObject(json)
        assertEquals("https://script.example/exec", url)
        assertEquals("problema", body.getString("tipo"))
        assertEquals("A nota some", body.getString("texto"))
        assertEquals("Som ou notas fora de sincronia", body.getString("categoria"))
        assertEquals("eu@exemplo.com", body.getString("contato"))
        assertEquals("1.4", body.getJSONObject("aparelho").getString("versaoDoApp"))
        assertEquals("samsung SM-S931B", body.getJSONObject("aparelho").getString("modelo"))
    }

    @Test
    fun withoutDeviceInfoOrContactNothingExtraIsSent() = runTest {
        sender.send(BugReport(ReportKind.SUGGESTION, "Mais músicas", contact = null, device = null))

        val body = JSONObject(sent.single().second)
        assertEquals("sugestao", body.getString("tipo"))
        assertFalse(body.has("contato"))
        assertFalse(body.has("categoria"))
        assertFalse(body.has("aparelho"))
    }

    @Test
    fun anErrorFromTheScriptFails() = runTest {
        answer = 500 to "erro"

        assertThrows(ReportFailedException::class.java) { kotlinx.coroutines.runBlocking { sender.send(report()) } }
    }

    /** Publicado sem "Qualquer pessoa": o Google responde 200 com a página de login, e o relato não chegou. */
    @Test
    fun aPageThatIsNotTheScriptAnswerFails() = runTest {
        answer = 200 to "<html>Fazer login</html>"

        assertThrows(ReportFailedException::class.java) { kotlinx.coroutines.runBlocking { sender.send(report()) } }
    }

    @Test
    fun withoutAnHttpsAddressSendingIsNotConfigured() {
        for (url in listOf("", "  ", "http://script.example/exec", "script.example/exec")) {
            assertFalse(url, AppsScriptReportSender(url) { _, _ -> 200 to "" }.isConfigured)
        }
    }

    private fun report() = BugReport(ReportKind.OTHER, "Oi", null, null)

    @Test
    fun aFreeCategoryGoesAsTheCategory() = runTest {
        sender.send(BugReport(ReportKind.OTHER, "Gostei", null, null, customCategory = "Elogio"))

        assertEquals("Elogio", JSONObject(sent.single().second).getString("categoria"))
    }
}
