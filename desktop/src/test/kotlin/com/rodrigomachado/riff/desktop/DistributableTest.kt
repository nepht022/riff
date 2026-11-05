package com.rodrigomachado.riff.desktop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/** O app empacotado (`:desktop:createDistributable`), relativo à pasta do módulo. */
private val APP = File("build/compose/binaries/main/app/Riff")

/**
 * O app empacotado leva o que precisa para abrir num PC sem nada instalado. Sem o pacote gerado (o
 * `:desktop:test` comum e o CI), os testes são pulados.
 */
class DistributableTest {
    /** O ORT 1.18 precisa do runtime do Visual C++ ao lado do `jvm.dll`, não no `System32`. */
    @Test
    fun msvcRuntimeShipsWithTheApp() {
        val bin = File(APP, "runtime/bin")
        assumeTrue(bin.isDirectory)
        listOf("msvcp140.dll", "vcruntime140.dll", "vcruntime140_1.dll", "ucrtbase.dll").forEach {
            assertTrue("$it no runtime do app", File(bin, it).isFile)
        }
    }

    @Test
    fun theRuntimeIsAJava21WithTheCryptoModule() {
        val release = File(APP, "runtime/release")
        assumeTrue(release.isFile)
        val text = release.readText()
        assertTrue(text, text.contains("JAVA_VERSION=\"21"))
        assertTrue(text, text.contains("jdk.crypto.ec"))
        assertTrue(text, text.contains("jdk.unsupported"))
        assertTrue(text, text.contains("jdk.crypto.mscapi"))
        assertTrue(text, text.contains("jdk.localedata"))
    }

    /** O `trustStoreType=Windows-ROOT` trocaria o `cacerts` pelas raízes do Windows (o TrustBothStores junta tudo). */
    @Test
    fun theAppKeepsTheJavaRoots() {
        val cfg = File(APP, "app/Riff.cfg")
        assumeTrue(cfg.isFile)
        val text = cfg.readText()
        assertTrue(text, text.contains("-Djava.net.useSystemProxies=true"))
        assertFalse(text, text.contains("trustStoreType"))
    }

    @Test
    fun theModelGoesInsideTheApp() {
        val jars = File(APP, "app").listFiles { f -> f.name.endsWith(".jar") }.orEmpty()
        assumeTrue(jars.isNotEmpty())
        assertTrue(jars.any { jar -> ZipFile(jar).use { it.getEntry("riff/htdemucs_6s_seg4.onnx") != null } })
    }
}
