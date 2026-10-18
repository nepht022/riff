package com.rodrigomachado.riff.core.model

/** Por que o Spotify não está disponível. Cada valor vira uma explicação na tela. */
enum class SpotifyError {
    NO_PERMISSION,
    NOT_CONFIGURED,
    NOT_INSTALLED,
    NOT_LOGGED_IN,
    NOT_AUTHORIZED,
    OFFLINE,
    DISCONNECTED,
    UNKNOWN,
}

/** Estado da conexão com o app do Spotify. */
sealed interface SpotifyConnection {
    data object Idle : SpotifyConnection

    data object Connecting : SpotifyConnection

    data object Connected : SpotifyConnection

    data class Failed(val error: SpotifyError) : SpotifyConnection
}
