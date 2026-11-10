package com.rodrigomachado.riff.feature.room

import com.rodrigomachado.riff.core.room.testing.CodesRandom
import com.rodrigomachado.riff.core.room.testing.FakeFirebase
import com.rodrigomachado.riff.core.room.testing.FakePlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Entrar com o código e criar a sala, com o Firebase falso (o org.json do Android vem do Robolectric). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoomJoinViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = 1_000_000L
    private val fake = FakeFirebase(clock = { now }, uid = "u1")
    private val name = MutableStateFlow<String?>("Ana")

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun joinViewModel() = RoomJoinViewModel(fake.repository(), name)

    private fun seed(code: String, vararg uids: String) =
        fake.seedRoom(code, owner = uids.first(), players = uids.map { FakePlayer(it) })

    /**
     * Minúsculas e espaços viram o código; mais de 4 letras ficam nas 4 primeiras; letra fora do alfabeto não entra.
     */
    @Test
    fun codeInputNormalizes() {
        val vm = joinViewModel()

        vm.onCode("ab c")
        assertEquals("ABC", vm.state.value.code)
        vm.onCode("abcde")
        assertEquals("ABCD", vm.state.value.code)
        vm.onCode("")
        vm.onCode("o")
        assertEquals("", vm.state.value.code)
        vm.onCode("AB")
        vm.onCode("AB0")
        vm.onCode("AB1")
        vm.onCode("ABI")
        assertEquals("AB", vm.state.value.code)
        assertFalse(vm.state.value.canJoin)
        vm.onCode("ABCD")
        assertTrue(vm.state.value.canJoin)
    }

    @Test
    fun joinOpensTheRoom() = runTest(dispatcher) {
        seed("KQZX", "u2")
        val vm = joinViewModel()

        vm.onCode("kqzx")
        vm.join()
        advanceUntilIdle()

        assertEquals("KQZX", vm.state.value.joined)
        assertNull(vm.state.value.error)
        val me = (fake.get("salas/KQZX/jogadores/u1") as JSONObject)
        assertEquals("Ana", me.getString("nome"))
        vm.onOpened()
        assertNull(vm.state.value.joined)
    }

    @Test
    fun joinShowsNotFound() = runTest(dispatcher) {
        val vm = joinViewModel()

        vm.onCode("KQZX")
        vm.join()
        advanceUntilIdle()

        assertEquals(RoomError.NOT_FOUND, vm.state.value.error)
        assertNull(vm.state.value.joined)
        assertFalse(vm.state.value.joining)
        // digitar de novo tira o erro
        vm.onCode("KQZ")
        assertNull(vm.state.value.error)
    }

    @Test
    fun joinShowsFull() = runTest(dispatcher) {
        seed("KQZX", "u2", "u3", "u4", "u5")
        val vm = joinViewModel()

        vm.onCode("KQZX")
        vm.join()
        advanceUntilIdle()

        assertEquals(RoomError.FULL, vm.state.value.error)
    }

    @Test
    fun joinWithoutInternetShowsNoConnection() = runTest(dispatcher) {
        seed("KQZX", "u2")
        fake.online = false
        val vm = joinViewModel()

        vm.onCode("KQZX")
        vm.join()
        advanceUntilIdle()

        assertEquals(RoomError.NO_CONNECTION, vm.state.value.error)
        assertFalse(vm.state.value.joining)
    }

    /** Sem nome no ranking: pede o nome antes; com o nome posto, entra sozinho. */
    @Test
    fun withoutANameJoinAsksForTheNameFirst() = runTest(dispatcher) {
        seed("KQZX", "u2")
        name.value = null
        val vm = joinViewModel()

        vm.onCode("KQZX")
        vm.join()
        advanceUntilIdle()

        assertTrue(vm.state.value.askName)
        assertNull(fake.get("salas/KQZX/jogadores/u1"))
        vm.onAskedName()
        assertFalse(vm.state.value.askName)

        name.value = "Bia"
        advanceUntilIdle()

        assertEquals("KQZX", vm.state.value.joined)
        assertEquals("Bia", (fake.get("salas/KQZX/jogadores/u1") as JSONObject).getString("nome"))
    }

    @Test
    fun createOpensTheNewRoom() = runTest(dispatcher) {
        val vm = RoomEntryViewModel(fake.repository(random = CodesRandom("MPRT")), name)

        vm.create()
        advanceUntilIdle()

        assertEquals("MPRT", vm.state.value.created)
        assertEquals("u1", (fake.get("salas/MPRT") as JSONObject).getString("dono"))
        vm.onOpened()
        assertNull(vm.state.value.created)
    }

    @Test
    fun createWithoutInternetShowsNoConnection() = runTest(dispatcher) {
        fake.online = false
        val vm = RoomEntryViewModel(fake.repository(), name)

        vm.create()
        advanceUntilIdle()

        assertEquals(RoomError.NO_CONNECTION, vm.state.value.error)
        assertFalse(vm.state.value.creating)
        assertNull(vm.state.value.created)

        // tentar de novo, já com internet
        fake.online = true
        vm.create()
        advanceUntilIdle()

        assertNull(vm.state.value.error)
        assertTrue(vm.state.value.created != null)
    }

    @Test
    fun withoutANameCreateAsksForTheNameFirst() = runTest(dispatcher) {
        name.value = null
        val vm = RoomEntryViewModel(fake.repository(random = CodesRandom("MPRT")), name)

        vm.create()
        advanceUntilIdle()

        assertTrue(vm.state.value.askName)
        assertTrue(fake.writes.isEmpty())
        vm.onAskedName()

        name.value = "Bia"
        advanceUntilIdle()

        assertEquals("MPRT", vm.state.value.created)
    }
}
