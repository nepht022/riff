package com.rodrigomachado.riff.core.spotify.session

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SpotifySession] com o `MediaSessionManager`: acompanha a sessão do Spotify enquanto o Riff tiver "Acesso às
 * notificações". Os avisos do Android chegam na thread principal.
 */
@Singleton
class AndroidSpotifySession @Inject constructor(@ApplicationContext private val context: Context) : SpotifySession {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, RiffNotificationListener::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var listening = false
    private val snapshotState = MutableStateFlow<SessionSnapshot?>(null)
    private val queueState = MutableStateFlow<List<SessionQueueItem>>(emptyList())
    private val activeState = MutableStateFlow<Long?>(null)

    override val snapshot: StateFlow<SessionSnapshot?> = snapshotState.asStateFlow()
    override val queue: StateFlow<List<SessionQueueItem>> = queueState.asStateFlow()
    override val activeQueueId: StateFlow<Long?> = activeState.asStateFlow()

    override val hasPermission: Boolean
        get() = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    override val isInstalled: Boolean
        get() = try {
            context.packageManager.getPackageInfo(SPOTIFY_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener {
        attach(
            spotifyIn(it.orEmpty()),
        )
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()

        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) = publish()

        override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) = publish()

        override fun onSessionDestroyed() = attach(null)
    }

    override fun recheck() {
        if (!hasPermission) {
            stopListening()
            attach(null)
            return
        }
        try {
            if (!listening) {
                manager.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler)
                listening = true
            }
            attach(spotifyIn(manager.getActiveSessions(listener)))
        } catch (_: SecurityException) {
            // A permissão caiu entre a checagem e a leitura: fica sem sessão até a próxima, e o listener que deu certo
            // sai (senão a próxima conferência o registraria de novo).
            runCatching { stopListening() }
            listening = false
            attach(null)
        }
    }

    private fun stopListening() {
        if (!listening) return
        manager.removeOnActiveSessionsChangedListener(sessionsChanged)
        listening = false
    }

    private fun spotifyIn(sessions: List<MediaController>) = sessions.firstOrNull { it.packageName == SPOTIFY_PACKAGE }

    private fun attach(next: MediaController?) {
        if (next?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = next
            next?.registerCallback(callback, handler)
        }
        publish()
    }

    private fun publish() {
        val current = controller
        snapshotState.value = current?.toSnapshot()
        queueState.value = current?.queue.orEmpty().map {
            SessionQueueItem(
                it.queueId,
                it.description.mediaId,
                it.description.title?.toString(),
                it.description.subtitle?.toString(),
            )
        }
        activeState.value = current?.playbackState?.activeQueueItemId?.takeIf { it != UNKNOWN_QUEUE_ID }
    }

    override fun play() {
        controller?.transportControls?.play()
    }

    override fun pause() {
        controller?.transportControls?.pause()
    }

    override fun seekTo(ms: Long) {
        controller?.transportControls?.seekTo(ms)
    }

    override fun skipToNext() {
        controller?.transportControls?.skipToNext()
    }

    override fun skipToPrevious() {
        controller?.transportControls?.skipToPrevious()
    }

    private companion object {
        const val ADVERTISEMENT = "android.media.metadata.ADVERTISEMENT"
        val UNKNOWN_QUEUE_ID = MediaSession.QueueItem.UNKNOWN_ID.toLong()
    }

    private fun MediaController.toSnapshot(): SessionSnapshot? {
        val state = playbackState ?: return null
        val meta = metadata
        return SessionSnapshot(
            mediaId = meta?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
            title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 },
            state = state.state,
            positionMs = state.position,
            updatedAtElapsedMs = state.lastPositionUpdateTime,
            speed = state.playbackSpeed,
            isAd = (meta?.getLong(ADVERTISEMENT) ?: 0L) != 0L,
        )
    }
}
