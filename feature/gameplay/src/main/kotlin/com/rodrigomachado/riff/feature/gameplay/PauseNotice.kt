package com.rodrigomachado.riff.feature.gameplay

import com.rodrigomachado.riff.core.audio.PlayerNotice

/** O texto da pausa para cada motivo de a música (Spotify) ter parado por fora. */
internal fun PlayerNotice.message(): Int = when (this) {
    PlayerNotice.GO_BACK_TO_TRACK -> R.string.gameplay_spotify_go_back
    PlayerNotice.SOURCE_LOST -> R.string.gameplay_spotify_lost
    PlayerNotice.AD -> R.string.gameplay_spotify_ad
}
