package com.rodrigomachado.riff.core.spotify

/** Credenciais do app no Developer Dashboard, vindas do local.properties via BuildConfig. */
class SpotifyConfig(val clientId: String, val redirectUri: String) {
    val isConfigured: Boolean get() = clientId.isNotBlank()
}
