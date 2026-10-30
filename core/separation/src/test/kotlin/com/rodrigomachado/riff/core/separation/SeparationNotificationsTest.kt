package com.rodrigomachado.riff.core.separation

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rodrigomachado.riff.core.model.SeparationStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class SeparationNotificationsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** O ícone de download do Android (seta animada) fazia parecer que ainda estava baixando depois de pronto. */
    @Test
    fun theDoneNotificationUsesTheRiffIconNotADownloadArrow() {
        SeparationNotifications(context).done("Música")

        val notification = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals(R.drawable.ic_stat_separation, notification.smallIcon.resId)
    }

    @Test
    fun theProgressSaysHowManyAreWaiting() {
        val running = SeparationStatus.Running("m", "Música", progress = 0.42f, remainingMs = null)
        val text = { queued: Int ->
            SeparationNotifications(context).foregroundInfo(running, queued).notification.extras
                .getCharSequence(android.app.Notification.EXTRA_TEXT).toString()
        }

        assertEquals("Separando a guitarra… 42%", text(0))
        assertEquals("Separando a guitarra… 42% · mais 2 na fila", text(2))
    }
}
