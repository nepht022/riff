package com.rodrigomachado.riff.feature.report

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {
    private val device = DeviceInfo("1.4", "16", "samsung SM-S931B", "pt-BR")
    private val sender = FakeReportSender()
    private var now = 1_000_000L
    private val cooldown = ReportCooldown { now }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        ReportViewModel(saved, sender, cooldown) { device }.also { it.setCategory(ReportCategory.CRASH) }

    @Test
    fun aFilledReportIsSentWithTheDeviceInfoAndThanks() = runTest {
        val vm = viewModel()
        vm.setKind(ReportKind.SUGGESTION)
        vm.setCategory(ReportCategory.SONGS)
        vm.setText("  Mais músicas  ")
        vm.setContact(" eu@exemplo.com ")

        vm.send()

        assertEquals(
            BugReport(ReportKind.SUGGESTION, "Mais músicas", "eu@exemplo.com", device, ReportCategory.SONGS),
            sender.sent.single(),
        )
        assertEquals(ReportStatus.SENT, vm.uiState.value.status)
    }

    @Test
    fun withTheDeviceInfoOffNoneIsSent() = runTest {
        val vm = viewModel()
        vm.setText("Trava")
        vm.setIncludeDevice(false)

        vm.send()

        assertNull(sender.sent.single().device)
        assertNull(sender.sent.single().contact)
    }

    @Test
    fun aFailureKeepsWhatWasWritten() = runTest {
        sender.failure = ReportFailedException("sem internet")
        val vm = viewModel()
        vm.setText("Trava")

        vm.send()

        assertEquals(ReportStatus.FAILED, vm.uiState.value.status)
        assertEquals("Trava", vm.uiState.value.text)
        assertTrue(vm.uiState.value.canSend) // pode tentar de novo na hora
    }

    @Test
    fun anotherReportRightAfterSendingHasToWait() = runTest {
        viewModel().apply {
            setText("Primeiro")
            send()
        }
        now += 60_000
        val vm = viewModel()
        vm.setText("Segundo")

        vm.send()

        assertEquals(1, sender.sent.size)
        assertEquals(ReportStatus.WAIT, vm.uiState.value.status)

        now += ReportCooldown.WAIT_MS
        vm.send()
        assertEquals(2, sender.sent.size)
    }

    @Test
    fun anEmptyTextOrAnInvalidEmailCannotBeSent() = runTest {
        val vm = viewModel()
        assertFalse(vm.uiState.value.canSend)

        vm.setText("Trava")
        vm.setContact("nao-e-email")
        assertFalse(vm.uiState.value.canSend)
        assertTrue(vm.uiState.value.contactInvalid)

        vm.setContact("")
        assertTrue(vm.uiState.value.canSend)
        vm.send()
        assertEquals(1, sender.sent.size)
    }

    @Test
    fun whileSendingTheButtonDoesNotSendTwice() = runTest {
        val gate = CompletableDeferred<Unit>()
        sender.gate = gate
        val vm = viewModel()
        vm.setText("Trava")

        vm.send()
        vm.send()
        assertEquals(ReportStatus.SENDING, vm.uiState.value.status)
        gate.complete(Unit)

        assertEquals(1, sender.sent.size)
    }

    @Test
    fun aVeryLongTextIsCut() = runTest {
        val vm = viewModel()

        vm.setText("a".repeat(ReportViewModel.MAX_TEXT + 50))

        assertEquals(ReportViewModel.MAX_TEXT, vm.uiState.value.text.length)
    }

    @Test
    fun withoutAnAddressInTheBuildTheScreenSaysSo() = runTest {
        sender.isConfigured = false

        assertEquals(ReportStatus.NOT_CONFIGURED, viewModel().uiState.value.status)
    }

    /** O Android pode fechar o Riff em segundo plano enquanto a pessoa escreve: o texto volta com a tela. */
    @Test
    fun whatWasWrittenSurvivesTheAppBeingClosedInTheBackground() = runTest {
        val saved = SavedStateHandle()
        viewModel(saved).apply {
            setKind(ReportKind.OTHER)
            setText("Relato comprido")
            setContact("eu@exemplo.com")
            setIncludeDevice(false)
        }

        val restored = ReportViewModel(saved, sender, cooldown) { device }.uiState.value

        assertEquals(ReportKind.OTHER, restored.kind)
        assertEquals("Relato comprido", restored.text)
        assertEquals("eu@exemplo.com", restored.contact)
        assertFalse(restored.includeDevice)
    }

    @Test
    fun anUnexpectedErrorWhileSendingDoesNotCloseTheApp() = runTest {
        sender.failure = SecurityException("rede bloqueada pelo aparelho")
        val vm = viewModel()
        vm.setText("Trava")

        vm.send()

        assertEquals(ReportStatus.FAILED, vm.uiState.value.status)
    }

    @Test
    fun problemsAndSuggestionsNeedACategoryButOtherDoesNot() = runTest {
        val vm = ReportViewModel(SavedStateHandle(), sender, cooldown) { device }
        vm.setText("Trava")
        assertFalse(vm.uiState.value.canSend) // Problema sem categoria

        vm.setCategory(ReportCategory.SPOTIFY)
        assertTrue(vm.uiState.value.canSend)

        vm.setKind(ReportKind.SUGGESTION) // a categoria do problema não serve para sugestão
        assertNull(vm.uiState.value.category)
        assertFalse(vm.uiState.value.canSend)

        vm.setKind(ReportKind.OTHER)
        assertTrue(vm.uiState.value.canSend)
        vm.send()
        assertNull(sender.sent.single().category)
    }

    @Test
    fun theCategorySurvivesTheAppBeingClosed() = runTest {
        val saved = SavedStateHandle()
        viewModel(saved).setCategory(ReportCategory.ENCORE)

        assertEquals(ReportCategory.ENCORE, ReportViewModel(saved, sender, cooldown) { device }.uiState.value.category)
    }

    @Test
    fun theKindOfTheCategoryIsSavedWithIt() = runTest {
        val saved = SavedStateHandle()
        viewModel(saved).setCategory(ReportCategory.GAMEPLAY) // uma sugestão, escolhida direto

        val restored = ReportViewModel(saved, sender, cooldown) { device }.uiState.value
        assertEquals(ReportKind.SUGGESTION, restored.kind)
        assertEquals(ReportCategory.GAMEPLAY, restored.category)
    }

    @Test
    fun aCategoryOfAnotherKindIsNotRestored() = runTest {
        val saved = SavedStateHandle(
            mapOf("kind" to ReportKind.PROBLEM.name, "category" to ReportCategory.GAMEPLAY.name),
        )

        assertNull(ReportViewModel(saved, sender, cooldown) { device }.uiState.value.category)
    }

    /** "Não deu para enviar" e "Espere" valiam para o que foi tentado: editando, somem. */
    @Test
    fun editingClearsTheFailureAndTheWait() = runTest {
        sender.failure = ReportFailedException("sem internet")
        val vm = viewModel()
        vm.setText("Trava")
        vm.send()

        vm.setText("Trava ao abrir")

        assertEquals(ReportStatus.EDITING, vm.uiState.value.status)
    }

    @Test
    fun editingAlsoClearsTheWait() = runTest {
        viewModel().apply {
            setText("Primeiro")
            send()
        }
        val vm = viewModel()
        vm.setText("Segundo")
        vm.send()
        assertEquals(ReportStatus.WAIT, vm.uiState.value.status)

        vm.setContact("eu@exemplo.com")

        assertEquals(ReportStatus.EDITING, vm.uiState.value.status)
    }

    @Test
    fun eachKindHasItsOwnCategories() {
        assertEquals(9, ReportCategory.forKind(ReportKind.PROBLEM).size)
        assertEquals(6, ReportCategory.forKind(ReportKind.SUGGESTION).size)
        assertTrue(ReportCategory.forKind(ReportKind.OTHER).isEmpty())
    }

    @Test
    fun otherCanHaveAFreeCategory() = runTest {
        val vm = viewModel()
        vm.setKind(ReportKind.OTHER)
        vm.setCustomCategory("  Elogio  ")
        vm.setText("Gostei muito")

        vm.send()

        assertEquals("Elogio", sender.sent.single().customCategory)
        assertNull(sender.sent.single().category)
    }

    @Test
    fun theFreeCategoryIsOptionalAndOnlyForOther() = runTest {
        val vm = viewModel()
        vm.setCustomCategory("Elogio")
        vm.setText("Trava")
        vm.send() // ainda em Problema: a categoria livre não vai

        assertNull(sender.sent.single().customCategory)
    }

    @Test
    fun theFreeCategorySurvivesTheAppBeingClosed() = runTest {
        val saved = SavedStateHandle()
        viewModel(saved).setCustomCategory("Elogio")

        assertEquals("Elogio", ReportViewModel(saved, sender, cooldown) { device }.uiState.value.customCategory)
    }
}

class FakeReportSender : ReportSender {
    override var isConfigured = true
    var failure: Exception? = null
    var gate: CompletableDeferred<Unit>? = null
    val sent = mutableListOf<BugReport>()

    override suspend fun send(report: BugReport) {
        gate?.await()
        failure?.let { throw it }
        sent += report
    }
}
