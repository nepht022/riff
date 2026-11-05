package com.rodrigomachado.riff.desktop

import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * O HTTPS do app do PC (Encore, ranking, Reportar) confia nas raízes do Java (`cacerts`) e nas do Windows
 * (`Windows-ROOT`: as de antivírus ou de redes que inspecionam o HTTPS) ao mesmo tempo. Só o `Windows-ROOT` não basta:
 * o Windows baixa as raízes sob demanda, e uma que ainda não veio faria o HTTPS falhar. Sem o `Windows-ROOT` no Java
 * (o JBR do build, o Linux) fica só o `cacerts`.
 */
object TrustBothStores {
    /** O `SSLContext` posto como padrão e o gerente de confiança dele. */
    class Installed(val context: SSLContext, val trust: X509TrustManager)

    /** O `KeyStore` das raízes do Windows (módulo `jdk.crypto.mscapi`). */
    const val WINDOWS_STORE = "Windows-ROOT"

    /**
     * Põe como `SSLContext` padrão um que confia nas duas listas. Chamado no começo do `main`, antes de qualquer
     * HTTPS: o `HttpsURLConnection` guarda a fábrica padrão na primeira conexão.
     */
    fun install(): Installed {
        val managers = listOfNotNull(javaRoots(), windowsRoots()?.let(::managerOf))
        val trust = anyOf(managers)
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        SSLContext.setDefault(context)
        return Installed(context, trust)
    }

    /** O gerente padrão do Java: o `cacerts` (ou o `javax.net.ssl.trustStore`, se alguém escolher outro). */
    fun javaRoots(): X509TrustManager = managerOf(null)

    /** As raízes do Windows, ou nulo quando o Java não tem o `Windows-ROOT` (ou ele não abre). */
    fun windowsRoots(): KeyStore? = try {
        KeyStore.getInstance(WINDOWS_STORE).apply { load(null) }
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: IOException) {
        null
    }

    /**
     * Um gerente que aceita a cadeia quando qualquer um dos [managers] aceita (na ordem dada); recusada por todos,
     * sobe a recusa do primeiro com as outras anexadas. Os emissores aceitos são a união.
     */
    fun anyOf(managers: List<X509TrustManager>): X509TrustManager {
        require(managers.isNotEmpty()) { "sem gerentes de confiança" }
        return AnyOfTrustManager(managers.toList())
    }

    /** Os certificados de um `KeyStore`. */
    fun certificates(store: KeyStore): List<X509Certificate> =
        store.aliases().toList().mapNotNull { store.getCertificate(it) as? X509Certificate }

    private fun managerOf(store: KeyStore?): X509TrustManager =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
            .trustManagers.filterIsInstance<X509TrustManager>().first()
}

private class AnyOfTrustManager(private val managers: List<X509TrustManager>) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
        firstAccepting { it.checkClientTrusted(chain, authType) }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) =
        firstAccepting { it.checkServerTrusted(chain, authType) }

    override fun getAcceptedIssuers(): Array<X509Certificate> =
        managers.flatMap { it.acceptedIssuers.asList() }.distinct().toTypedArray()

    private fun firstAccepting(check: (X509TrustManager) -> Unit) {
        val refusals = mutableListOf<CertificateException>()
        for (manager in managers) {
            try {
                check(manager)
                return
            } catch (e: CertificateException) {
                refusals += e
            }
        }
        throw refusals.first().apply { refusals.drop(1).forEach(::addSuppressed) }
    }
}
