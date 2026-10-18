package com.rodrigomachado.riff.feature.licenses

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** MIT e BSD exigem o aviso de copyright de verdade (o do AboutLibraries vem como modelo, "<year> <holders>"). */
@RunWith(RobolectricTestRunner::class)
class AndroidLicenseSourceTest {
    private val source = AndroidLicenseSource(ApplicationProvider.getApplicationContext())

    private suspend fun textOf(name: String) = source.content().single { it.name == name }.text.orEmpty()

    @Test
    fun nativeAndMathLibrariesCarryTheirRealNotices() = runTest {
        assertTrue(textOf("ONNX Runtime").contains("Copyright (c) Microsoft Corporation"))
        assertTrue(textOf("ONNX Runtime").contains("THIRD PARTY SOFTWARE NOTICES", ignoreCase = true))
        assertTrue(textOf("JTransforms").contains("Copyright (c) 2007 onward, Piotr Wendykier"))
        assertTrue(textOf("JLargeArrays").contains("University of Warsaw, ICM"))
        assertTrue(textOf("Protocol Buffers").contains("Copyright 2008 Google Inc."))
    }

    @Test
    fun everyItemWithATextFileOpens() = runTest {
        assertTrue(source.content().filter { it.text != null }.all { it.text!!.isNotBlank() })
    }
}
