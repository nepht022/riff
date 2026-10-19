package com.rodrigomachado.riff.feature.nowplaying

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Celulares sem a tela "Acesso às notificações" (Android Go): o Permitir não pode fechar o app. */
@RunWith(RobolectricTestRunner::class)
class NotificationAccessTest {
    @Test
    fun opensTheNotificationAccessScreen() {
        val opened = mutableListOf<String?>()
        openNotificationAccess { opened += it.action }
        assertEquals(listOf(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), opened)
    }

    @Test
    fun withoutThatScreenItOpensTheSettingsInstead() {
        val opened = mutableListOf<String?>()
        openNotificationAccess { intent: Intent ->
            if (intent.action == Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) throw ActivityNotFoundException()
            opened += intent.action
        }
        assertEquals(listOf(Settings.ACTION_SETTINGS), opened)
    }

    @Test
    fun withoutAnySettingsScreenNothingBreaks() {
        openNotificationAccess { throw ActivityNotFoundException() }
    }
}
