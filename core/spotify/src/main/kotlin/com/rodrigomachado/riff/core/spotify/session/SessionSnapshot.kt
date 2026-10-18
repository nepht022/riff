package com.rodrigomachado.riff.core.spotify.session

import com.rodrigomachado.riff.core.model.PlaybackState

/** O único player acompanhado por enquanto (spec: só o Spotify); outros entram por aqui. */
const val SPOTIFY_PACKAGE = "com.spotify.music"

private const val TRACK_PREFIX = "spotify:track:"

/**
 * O que a sessão de mídia do Spotify mostra num instante: os metadados e o estado do player. [state] usa as
 * constantes de `android.media.session.PlaybackState`; [updatedAtElapsedMs] é o `lastPositionUpdateTime`, no relógio
 * `elapsedRealtime`.
 */
data class SessionSnapshot(
    val mediaId: String?,
    val title: String?,
    val artist: String?,
    val durationMs: Long?,
    val state: Int,
    val positionMs: Long,
    val updatedAtElapsedMs: Long,
    val speed: Float,
    val isAd: Boolean,
)

/** Um item da fila da sessão; [mediaId] é o `spotify:track:…` da música. */
data class SessionQueueItem(val queueId: Long, val mediaId: String?, val title: String?, val artist: String?)

/** O identificador é de uma música (não episódio, anúncio ou nada). */
internal fun String?.isTrack(): Boolean = this?.startsWith(TRACK_PREFIX) == true

/**
 * O estado da música para o resto do app; `null` quando não é uma música (episódio, anúncio, nada). [correctionMs]
 * vale só tocando: medido no S25, a posição extrapolada depois de tocar fica à frente do áudio.
 */
fun SessionSnapshot.toPlaybackState(correctionMs: Long): PlaybackState? {
    if (isAd || !mediaId.isTrack()) return null
    val playing = state == android.media.session.PlaybackState.STATE_PLAYING
    return PlaybackState(
        trackUri = mediaId.orEmpty(),
        title = title.orEmpty(),
        artist = artist.orEmpty(),
        durationMs = durationMs ?: 0,
        positionMs = (if (playing) positionMs + correctionMs else positionMs).coerceAtLeast(0),
        isPaused = !playing,
        capturedAtElapsedMs = updatedAtElapsedMs,
    )
}
