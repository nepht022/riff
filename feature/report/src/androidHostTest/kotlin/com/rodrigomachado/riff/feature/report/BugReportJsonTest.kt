package com.rodrigomachado.riff.feature.report

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * O JSON do celular igual, byte a byte, ao de antes da versão multiplataforma (quando saía do `org.json` do
 * Android): os textos esperados foram gravados com o código antigo.
 */
@RunWith(RobolectricTestRunner::class)
class BugReportJsonTest {
    private val device = DeviceInfo.android("1.4", "16", "samsung SM-S931B", "pt-BR")
    private val tricky = "Trava no \"refrão\"\nde a/b \\ c\ttab\r\b\u000c\u0001\u001f fim   ☃ 🎸 <tag> &amp;"

    @Test
    fun theFullPhoneReportIsTheSameAsBefore() {
        val json = BugReport(ReportKind.PROBLEM, tricky, "eu@exemplo.com", device, ReportCategory.SYNC).toJson()

        assertEquals(
            "{\"tipo\":\"problema\",\"categoria\":\"Som ou notas fora de sincronia\"," +
                "\"texto\":\"Trava no \\\"refrão\\\"\\nde a\\/b \\\\ c\\ttab\\r\\b\\f\\u0001\\u001f fim   ☃ 🎸 " +
                "<tag> &amp;\",\"contato\":\"eu@exemplo.com\",\"aparelho\":{\"versaoDoApp\":\"1.4\"," +
                "\"android\":\"16\",\"modelo\":\"samsung SM-S931B\",\"idioma\":\"pt-BR\"}}",
            json,
        )
    }

    @Test
    fun aFreeCategoryWithoutContactIsTheSameAsBefore() {
        val json = BugReport(ReportKind.OTHER, "Oi", " ", null, customCategory = "Elogio \"bom\"").toJson()

        assertEquals("{\"tipo\":\"outro\",\"categoria\":\"Elogio \\\"bom\\\"\",\"texto\":\"Oi\"}", json)
    }

    @Test
    fun theShortestReportIsTheSameAsBefore() {
        assertEquals(
            "{\"tipo\":\"sugestao\",\"texto\":\"x\"}",
            BugReport(ReportKind.SUGGESTION, "x", null, null).toJson(),
        )
    }

    /** O mesmo que o `JSONObject` do Android escreveria, como o código antigo fazia, para todo caractere até U+00FF. */
    @Test
    fun everyCharacterIsEscapedLikeTheAndroidJsonObject() {
        val text = (0..0xFF).map { it.toChar() }.joinToString("") + "  🎸"
        val report = BugReport(ReportKind.PROBLEM, text, text, device.copy(model = text), ReportCategory.CRASH)
        val before = JSONObject().apply {
            put("tipo", "problema")
            put("categoria", ReportCategory.CRASH.label)
            put("texto", text)
            put("contato", text)
            put(
                "aparelho",
                JSONObject().put("versaoDoApp", "1.4").put("android", "16").put("modelo", text).put("idioma", "pt-BR"),
            )
        }.toString()

        assertEquals(before, report.toJson())
    }

    /** No PC: o sistema inteiro em `sistema` e, sem o modelo, nada de `modelo`. */
    @Test
    fun thePcSendsTheSystemAndNoModel() {
        val pc = DeviceInfo("0.5.0", "Windows 11 10.0 (amd64)", null, "pt-BR")

        assertEquals(
            "{\"tipo\":\"sugestao\",\"texto\":\"x\",\"aparelho\":{\"versaoDoApp\":\"0.5.0\"," +
                "\"sistema\":\"Windows 11 10.0 (amd64)\",\"idioma\":\"pt-BR\"}}",
            BugReport(ReportKind.SUGGESTION, "x", null, pc).toJson(),
        )
        assertEquals("Riff 0.5.0 · Windows 11 10.0 (amd64) · pt-BR", pc.summary)
    }
}
