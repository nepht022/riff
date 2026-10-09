package com.rodrigomachado.riff

import android.content.Context
import android.content.pm.PackageManager
import android.util.TypedValue
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** O app abre no preto do palco e tem ícone próprio (nada do robô cinza do Android). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AppIdentityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theAppHasItsOwnIcon() {
        val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)

        assertNotEquals(0, info.icon)
        assertEquals("ic_launcher", context.resources.getResourceEntryName(info.icon))
    }

    @Test
    fun theWindowOpensOnTheStageBlack() {
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        val theme = context.resources.newTheme().apply { applyStyle(info.theme, true) }
        val background = TypedValue()

        theme.resolveAttribute(android.R.attr.windowBackground, background, true)

        assertEquals(0xFF120D0B.toInt(), background.data)
    }
}
