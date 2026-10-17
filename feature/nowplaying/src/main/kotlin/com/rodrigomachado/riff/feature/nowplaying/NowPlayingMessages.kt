package com.rodrigomachado.riff.feature.nowplaying

import com.rodrigomachado.riff.core.data.queue.QueueMiss
import com.rodrigomachado.riff.core.model.SpotifyError

/** O que a fila tentou no Encore para esta música. */
internal fun QueueMiss.message(): Int = when (this) {
    QueueMiss.NOTHING_FOUND -> R.string.nowplaying_queue_not_found
    QueueMiss.ONLY_OFFICIAL -> R.string.nowplaying_queue_only_official
    QueueMiss.ONLY_OTHER_DURATION -> R.string.nowplaying_queue_other_duration
    QueueMiss.SEARCH_FAILED -> R.string.nowplaying_queue_search_failed
    QueueMiss.DOWNLOAD_FAILED -> R.string.nowplaying_queue_download_failed
}

internal fun SpotifyError.message(): Int = when (this) {
    SpotifyError.NOT_LOGGED_IN -> R.string.nowplaying_error_not_logged_in
    SpotifyError.NOT_AUTHORIZED -> R.string.nowplaying_error_not_authorized
    SpotifyError.OFFLINE -> R.string.nowplaying_error_offline
    SpotifyError.DISCONNECTED -> R.string.nowplaying_error_disconnected
    else -> R.string.nowplaying_error_unknown
}
