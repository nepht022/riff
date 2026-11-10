package com.rodrigomachado.riff.core.room

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomSwitchTest {
    @Test
    fun theRoomNeedsTheKeyAndTheDatabase() {
        assertFalse(RoomSwitch.isOn("", ""))
        assertFalse(RoomSwitch.isOn("chave", ""))
        assertFalse(RoomSwitch.isOn("", "https://riff.firebaseio.com"))
        assertFalse(RoomSwitch.isOn(" ", "https://riff.firebaseio.com"))
        assertTrue(RoomSwitch.isOn("chave", "https://riff.firebaseio.com"))
    }
}
