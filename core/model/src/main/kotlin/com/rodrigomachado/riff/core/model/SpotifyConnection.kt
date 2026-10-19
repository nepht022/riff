package com.rodrigomachado.riff.core.model

/** Por que o Riff não consegue acompanhar o Spotify agora. Cada valor vira uma explicação na tela. */
enum class SpotifyError {
    /** O Riff não está ligado em "Acesso às notificações" (sem isso o Android não mostra a sessão do Spotify). */
    NO_PERMISSION,
    NOT_INSTALLED,
}

/** Estado da conexão com o app do Spotify. */
sealed interface SpotifyConnection {
    data object Idle : SpotifyConnection

    data object Connecting : SpotifyConnection

    data object Connected : SpotifyConnection

    data class Failed(val error: SpotifyError) : SpotifyConnection
}
