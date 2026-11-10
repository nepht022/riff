package com.rodrigomachado.riff.feature.room

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.rodrigomachado.riff.core.room.Room
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h780dp-xhdpi")
class RoomScreenshotTest {
    @Test
    fun entry() = snapshot("Room_entry") { RoomEntryContent(RoomEntryUiState(), onCreate = {}, onJoin = {}) }

    @Test
    fun joinEmpty() = snapshot("Room_join_empty") { RoomJoinContent(RoomJoinUiState(), onCode = {}, onJoin = {}) }

    @Test
    fun joinError() = snapshot("Room_join_error") {
        RoomJoinContent(RoomJoinUiState(code = "KQZX", error = RoomError.NOT_FOUND), onCode = {}, onJoin = {})
    }

    @Test
    fun lobbyOwnerAlone() = snapshot("Room_lobby_owner_1") { lobby(sampleRoom("Rodrigo")) }

    @Test
    fun lobbyOwnerFull() = snapshot("Room_lobby_owner_4") {
        lobby(sampleRoom("Rodrigo", "Ana", "Bruno_88", "Carla", pcs = setOf("Bruno_88")))
    }

    @Test
    fun lobbyGuest() = snapshot("Room_lobby_guest") { lobby(sampleRoom("Ana", "Rodrigo", me = "Rodrigo")) }

    @Test
    fun lobbyPlayerGone() = snapshot("Room_lobby_gone") {
        lobby(sampleRoom("Rodrigo", "Ana", "Bruno_88", gone = setOf("Ana"), pcs = setOf("Bruno_88")))
    }

    @Composable
    private fun lobby(room: Room) = RoomLobbyContent(RoomLobbyUiState.Ready(room), room.code, onLeave = {})

    private fun snapshot(name: String, content: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/$name.png") {
            RiffTheme { StageBackground(Modifier.fillMaxSize()) { content() } }
        }
    }
}
