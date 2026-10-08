package com.rodrigomachado.riff.core.spotify

import com.rodrigomachado.riff.core.model.PlaybackState
import com.rodrigomachado.riff.core.model.SpotifyError
import com.spotify.android.appremote.api.error.AuthenticationFailedException
import com.spotify.android.appremote.api.error.CouldNotFindSpotifyApp
import com.spotify.android.appremote.api.error.NotLoggedInException
import com.spotify.android.appremote.api.error.OfflineModeException
import com.spotify.android.appremote.api.error.SpotifyConnectionTerminatedException
import com.spotify.android.appremote.api.error.SpotifyDisconnectedException
import com.spotify.android.appremote.api.error.UserNotAuthorizedException
import com.spotify.protocol.types.PlayerState

/** Estado do player como o app usa. Sem faixa, ou com podcast, não há o que jogar: `null`. */
internal fun PlayerState.toPlaybackState(capturedAtMs: Long): PlaybackState? {
    val current = track ?: return null
    if (current.isEpisode || current.isPodcast) return null
    return PlaybackState(
        trackUri = current.uri,
        title = current.name.orEmpty(),
        artist = current.artist?.name.orEmpty(),
        durationMs = current.duration,
        positionMs = playbackPosition,
        isPaused = isPaused,
        capturedAtElapsedMs = capturedAtMs,
    )
}

internal fun Throwable.toSpotifyError(): SpotifyError = when (this) {
    is CouldNotFindSpotifyApp -> SpotifyError.NOT_INSTALLED
    is NotLoggedInException -> SpotifyError.NOT_LOGGED_IN
    is UserNotAuthorizedException, is AuthenticationFailedException -> SpotifyError.NOT_AUTHORIZED
    is OfflineModeException -> SpotifyError.OFFLINE
    is SpotifyDisconnectedException, is SpotifyConnectionTerminatedException -> SpotifyError.DISCONNECTED
    else -> SpotifyError.UNKNOWN
}
