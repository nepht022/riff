package com.rodrigomachado.riff.core.spotify

import android.app.Activity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ForegroundActivityTest {
    @After
    fun tearDown() {
        ForegroundActivity.onResumed = null
    }

    @Test
    fun followsTheResumedActivityAndForgetsItWhenPaused() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        ForegroundActivity.onActivityResumed(controller.get())
        assertSame(controller.get(), ForegroundActivity.current)

        ForegroundActivity.onActivityPaused(controller.get())

        assertNull(ForegroundActivity.current)
    }

    @Test
    fun announcesEachResumedActivity() {
        val resumed = mutableListOf<Activity>()
        ForegroundActivity.onResumed = { resumed += it }
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        ForegroundActivity.onActivityResumed(activity)

        assertEquals(listOf(activity), resumed)
    }
}
