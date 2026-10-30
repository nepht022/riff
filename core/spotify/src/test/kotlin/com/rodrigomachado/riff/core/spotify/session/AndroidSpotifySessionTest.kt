package com.rodrigomachado.riff.core.spotify.session

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AndroidSpotifySessionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(MediaSessionManager::class.java)

    /** O "Acesso às notificações" do Riff ligado (ou desligado) nas configurações do Android. */
    private fun allow(allowed: Boolean) {
        val listener = ComponentName(context, RiffNotificationListener::class.java).flattenToString()
        Settings.Secure.putString(
            context.contentResolver,
            "enabled_notification_listeners",
            if (allowed) listener else "",
        )
    }

    private fun spotifyPlaying(title: String): MediaController {
        val session = MediaSession(context, "spotify")
        val controller = MediaController(context, session.sessionToken)
        shadowOf(controller).setPackageName(SPOTIFY_PACKAGE)
        shadowOf(controller).setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "spotify:track:abc")
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .build(),
        )
        shadowOf(controller).setPlaybackState(
            android.media.session.PlaybackState.Builder()
                .setState(android.media.session.PlaybackState.STATE_PLAYING, 1_000, 1f)
                .build(),
        )
        shadowOf(manager).addController(controller)
        return controller
    }

    @Test
    fun withoutTheAccessThereIsNoSession() {
        allow(false)
        spotifyPlaying("Música")
        val session = AndroidSpotifySession(context)

        session.recheck()

        assertFalse(session.hasPermission)
        assertNull(session.snapshot.value)
    }

    @Test
    fun withTheAccessTheSpotifySessionIsFollowed() {
        allow(true)
        spotifyPlaying("Música")
        val session = AndroidSpotifySession(context)

        session.recheck()

        assertTrue(session.hasPermission)
        assertEquals("spotify:track:abc", session.snapshot.value?.mediaId)
    }

    @Test
    fun anotherAppPlayingIsIgnored() {
        allow(true)
        val other = MediaController(context, MediaSession(context, "outro").sessionToken)
        shadowOf(other).setPackageName("com.outro.player")
        shadowOf(manager).addController(other)
        val session = AndroidSpotifySession(context)

        session.recheck()

        assertNull(session.snapshot.value)
    }

    @Test
    fun aSpotifySessionThatAppearsLaterIsFollowed() {
        allow(true)
        val session = AndroidSpotifySession(context)
        session.recheck()

        spotifyPlaying("Música")
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertEquals("spotify:track:abc", session.snapshot.value?.mediaId)
    }

    @Test
    fun theAccessTakenAwayDropsTheSessionAndStopsListening() {
        allow(true)
        spotifyPlaying("Música")
        val session = AndroidSpotifySession(context)
        session.recheck()

        allow(false)
        session.recheck()
        shadowOf(manager).clearControllers()
        spotifyPlaying("Outra")
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        assertNull(session.snapshot.value)
    }
}
