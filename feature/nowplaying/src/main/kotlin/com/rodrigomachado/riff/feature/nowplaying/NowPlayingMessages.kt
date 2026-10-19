package com.rodrigomachado.riff.feature.nowplaying

import com.rodrigomachado.riff.core.data.queue.QueueMiss

/** O que a fila tentou no Encore para esta música. */
internal fun QueueMiss.message(): Int = when (this) {
    QueueMiss.NOTHING_FOUND -> R.string.nowplaying_queue_not_found
    QueueMiss.ONLY_OTHER_DURATION -> R.string.nowplaying_queue_other_duration
    QueueMiss.SEARCH_FAILED -> R.string.nowplaying_queue_search_failed
    QueueMiss.DOWNLOAD_FAILED -> R.string.nowplaying_queue_download_failed
}
