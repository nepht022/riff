package com.rodrigomachado.riff

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoSpotifyTest {
    /** Sem o Spotify, o Riff não pede o "Acesso às notificações": nenhum serviço de notificações declarado. */
    @Test
    fun noNotificationListenerIsDeclared() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val listeners = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_SERVICES)
            .services.orEmpty()
            .filter { it.permission == "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" }
            .map { it.name }

        assertEquals(emptyList<String>(), listeners)
    }
}
