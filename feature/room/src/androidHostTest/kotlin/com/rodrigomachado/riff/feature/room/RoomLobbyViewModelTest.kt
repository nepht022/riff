package com.rodrigomachado.riff.feature.room

import com.rodrigomachado.riff.core.room.testing.FakeFirebase
import com.rodrigomachado.riff.core.room.testing.FakePlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A sala ao vivo com o Firebase falso: os jogadores, o fim da sala, o batimento e o Sair. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoomLobbyViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private var now = 1_000_000L
    private val fake = FakeFirebase(clock = { now }, uid = "u1")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fake.seedRoom("ABCD", owner = "u1", players = listOf(FakePlayer("u1", "Ana")))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** A tela aberta: alguém olhando o estado (o batimento e o streaming só rodam assim). */
    private fun TestScope.opened(): RoomLobbyViewModel {
        val vm = RoomLobbyViewModel("ABCD", fake.repository())
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        return vm
    }

    private fun RoomLobbyViewModel.names() = (uiState.value as RoomLobbyUiState.Ready).room.players.map { it.name }

    private fun beats() = fake.writes.count { it.uid == "u1" && it.method == "PATCH" && "vistoEm" in it.body.orEmpty() }

    @Test
    fun lobbyShowsPlayersLive() = runTest(dispatcher) {
        val vm = opened()
        assertEquals(listOf("Ana"), vm.names())
        assertTrue((vm.uiState.value as RoomLobbyUiState.Ready).room.iAmOwner)

        fake.repository("u2").join("ABCD", "Bia")
        runCurrent()

        assertEquals(listOf("Ana", "Bia"), vm.names())
    }

    @Test
    fun lobbyEndsWhenTheRoomIsDeleted() = runTest(dispatcher) {
        val vm = opened()

        fake.set("salas/ABCD", "null")
        runCurrent()

        assertEquals(RoomLobbyUiState.Ended, vm.uiState.value)
    }

    @Test
    fun leaveNavigatesBack() = runTest(dispatcher) {
        val vm = opened()
        assertFalse(vm.left.value)

        vm.leave()
        runCurrent()

        assertTrue(vm.left.value)
        assertNull(fake.get("salas/ABCD")) // o último a sair apaga a sala
    }

    /** Batimento ao abrir e a cada 20 s enquanto a tela está aberta. */
    @Test
    fun heartbeatEvery20Seconds() = runTest(dispatcher) {
        opened()
        assertEquals(1, beats())

        advanceTimeBy(19_000)
        assertEquals(1, beats())
        advanceTimeBy(1_001)
        assertEquals(2, beats())
        advanceTimeBy(20_000)
        assertEquals(3, beats())
    }

    /** Sem internet ao abrir: avisa e tenta de novo sozinho. */
    @Test
    fun withoutInternetShowsNoConnectionAndRetries() = runTest(dispatcher) {
        fake.online = false
        val vm = opened()

        assertEquals(RoomLobbyUiState.NoConnection, vm.uiState.value)

        fake.online = true
        advanceTimeBy(5_001)

        assertEquals(listOf("Ana"), vm.names())
    }
}
