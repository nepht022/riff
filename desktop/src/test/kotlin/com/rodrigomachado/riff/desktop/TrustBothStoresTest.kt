package com.rodrigomachado.riff.desktop

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * A confiança do app do PC: o `cacerts` do Java e o `Windows-ROOT` juntos. Os certificados A, B e C são gerados aqui
 * (autoassinados, pelo keytool do Java que roda o teste); nada acessa a rede.
 */
class TrustBothStoresTest {
    @Test
    fun theCompositeAcceptsWhatAnyOfThemAccepts() {
        val trust = TrustBothStores.anyOf(listOf(trusting(certA), trusting(certB)))
        trust.checkServerTrusted(arrayOf(certA), AUTH)
        trust.checkServerTrusted(arrayOf(certB), AUTH)
        trust.checkClientTrusted(arrayOf(certA), AUTH)
        trust.checkClientTrusted(arrayOf(certB), AUTH)
    }

    @Test
    fun theCompositeRefusesWhatNoneOfThemAccepts() {
        val trust = TrustBothStores.anyOf(listOf(trusting(certA), trusting(certB)))
        refused { trust.checkServerTrusted(arrayOf(certC), AUTH) }
        refused { trust.checkClientTrusted(arrayOf(certC), AUTH) }
    }

    @Test
    fun theAcceptedIssuersAreTheUnion() {
        val trust = TrustBothStores.anyOf(listOf(trusting(certA), trusting(certB), trusting(certA)))
        assertEquals(setOf(certA, certB), trust.acceptedIssuers.toSet())
        assertEquals(2, trust.acceptedIssuers.size)
    }

    /** Sem o Windows-ROOT (o JBR do build, o CI em Linux), fica só o `cacerts`, sem erro. */
    @Test
    fun installTrustsTheJavaRootsAndTheWindowsOnesWhenThereAreAny() {
        val before = SSLContext.getDefault()
        try {
            val installed = TrustBothStores.install()
            assertSame(installed.context, SSLContext.getDefault())
            val issuers = installed.trust.acceptedIssuers.toSet()
            val javaRoots = TrustBothStores.javaRoots().acceptedIssuers.toList()
            assertTrue(javaRoots.isNotEmpty())
            assertTrue("o cacerts inteiro", issuers.containsAll(javaRoots))
            TrustBothStores.windowsRoots()?.let { windows ->
                assertTrue("o Windows-ROOT inteiro", issuers.containsAll(TrustBothStores.certificates(windows)))
            }
        } finally {
            SSLContext.setDefault(before)
        }
    }

    private fun refused(check: () -> Unit) {
        try {
            check()
            fail("o C não devia ser aceito")
        } catch (_: CertificateException) {
            // esperado
        }
    }

    companion object {
        private const val AUTH = "ECDHE_ECDSA"
        private const val PASSWORD = "conferencia"

        private lateinit var dir: File
        private lateinit var certA: X509Certificate
        private lateinit var certB: X509Certificate
        private lateinit var certC: X509Certificate

        @BeforeClass
        @JvmStatic
        fun generate() {
            dir = Files.createTempDirectory("riff-trust").toFile()
            certA = selfSigned("A")
            certB = selfSigned("B")
            certC = selfSigned("C")
        }

        @AfterClass
        @JvmStatic
        fun cleanUp() {
            dir.deleteRecursively()
        }

        /** Um gerente do Java que confia só no [cert]. */
        private fun trusting(cert: X509Certificate): X509TrustManager {
            val store = KeyStore.getInstance("PKCS12").apply {
                load(null, null)
                setCertificateEntry("raiz", cert)
            }
            val factory = TrustManagerFactory.getInstance("PKIX").apply { init(store) }
            return factory.trustManagers.filterIsInstance<X509TrustManager>().single()
        }

        /** Um certificado EC autoassinado com `CN=<name>`, gerado pelo keytool num PKCS12 temporário. */
        private fun selfSigned(name: String): X509Certificate {
            val store = File(dir, "$name.p12")
            val windows = System.getProperty("os.name").lowercase().startsWith("windows")
            val keytool = File(System.getProperty("java.home"), if (windows) "bin/keytool.exe" else "bin/keytool")
            val process = ProcessBuilder(
                keytool.path, "-genkeypair", "-alias", name, "-keyalg", "EC", "-groupname", "secp256r1",
                "-dname", "CN=$name", "-validity", "30", "-keystore", store.path, "-storetype", "PKCS12",
                "-storepass", PASSWORD, "-keypass", PASSWORD, "-noprompt",
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor(1, TimeUnit.MINUTES) && process.exitValue() == 0) { "keytool: $output" }
            val keys = KeyStore.getInstance("PKCS12").apply {
                store.inputStream().use { load(it, PASSWORD.toCharArray()) }
            }
            return keys.getCertificate(name) as X509Certificate
        }
    }
}
