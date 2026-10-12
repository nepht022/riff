package com.rodrigomachado.riff.core.chart.sng

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SngRefTest {
    private val file = "content://p/tree/primary%3AC/document/primary%3AC%2FSong.sng"

    @Test
    fun packageAndEntryUrisRoundTrip() {
        assertEquals(file, SngRef.packageFileOf(SngRef.packageUri(file)))
        assertEquals(file to "song.opus", SngRef.entryOf(SngRef.entryUri(file, "song.opus")))
        assertNull(SngRef.entryOf(file))
        assertNull(SngRef.packageFileOf("content://p/tree/x/document/y"))
    }

    @Test
    fun thePackageUriKeepsTheFileUriAsPrefix() {
        // keepFolder (subpasta ilegível) casa por prefixo do URI da pasta.
        assertEquals(true, SngRef.packageUri(file).startsWith(file))
    }
}
