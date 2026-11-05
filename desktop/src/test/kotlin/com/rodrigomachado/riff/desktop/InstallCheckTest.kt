package com.rodrigomachado.riff.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A conferência das peças nativas do app do PC. Ela é do Windows (o `KeyStore` `Windows-ROOT` e o GLFW com tela):
 * no CI em Linux, sem tela, os testes são pulados.
 */
class InstallCheckTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Before
    fun onWindows() {
        assumeTrue("a conferência é do Windows", System.getProperty("os.name").lowercase().startsWith("windows"))
    }

    /**
     * O JBR do Android Studio (o Java do build) não traz o jdk.crypto.mscapi: nele o tls falha, e só por isso. O
     * Java empacotado tem de trazê-lo (lá a conferência não tem exceção).
     */
    private val buildJavaGaps =
        if (ModuleLayer.boot().findModule(InstallCheck.MSCAPI_MODULE).isPresent) emptySet() else setOf("tls")

    @Test
    fun everyPartLoadsInTheBuildJava() {
        val failures = InstallCheck.run(temp.root)
        assertEquals(emptyList<InstallCheck.Failure>(), failures.filterNot { it.part in buildJavaGaps })
        failures.forEach { assertTrue(it.error, it.error.contains(InstallCheck.MSCAPI_MODULE)) }
    }

    @Test
    fun aBrokenPartIsReportedWithoutStoppingTheOthers() {
        // Pasta de trabalho que é um arquivo: ffmpeg e sqlite falham, o resto passa.
        val notADir = temp.newFile("arquivo")
        val parts = InstallCheck.run(notADir).map { it.part }.toSet()
        assertEquals(setOf("ffmpeg", "sqlite") + buildJavaGaps, parts)
    }
}

/** As linhas que o `verifyDistributable` lê: uma por peça e o total no fim (vale em qualquer sistema). */
class InstallCheckReportTest {
    @Test
    fun theReportHasOneLinePerPartAndTheTotal() {
        val lines = InstallCheck.report(listOf(InstallCheck.Failure("ffmpeg", "sem DLL")))
        val parts = InstallCheck.PARTS
        assertEquals(parts.size + 1, lines.size)
        assertEquals("FALHOU ffmpeg: sem DLL", lines[parts.indexOf("ffmpeg")])
        assertEquals(parts.size - 1, lines.count { it.startsWith("ok ") })
        assertEquals("total ${parts.size}", lines.last())
    }
}
