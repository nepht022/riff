package com.rodrigomachado.riff.core.separation

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SeparationNotificationsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** O ícone de download do Android (seta animada) fazia parecer que ainda estava baixando depois de pronto. */
    @Test
    fun theDoneNotificationUsesTheRiffIconNotADownloadArrow() {
        SeparationNotifications(context).done("Música")

        val notification = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals(R.drawable.ic_stat_separation, notification.smallIcon.resId)
    }
}
