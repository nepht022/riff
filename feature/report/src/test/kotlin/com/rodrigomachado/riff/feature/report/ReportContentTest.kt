package com.rodrigomachado.riff.feature.report

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.ui.component.LocalMenuSound
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class ReportContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val device = DeviceInfo("1.4", "16", "samsung SM-S931B", "pt-BR")
    private val events = mutableListOf<String>()
    private val sounds = mutableListOf<MenuSound>()

    /** O estado acompanha o que é digitado, como na tela de verdade (o ViewModel devolve o texto novo). */
    private fun show(initial: ReportUiState) = composeRule.setContent {
        var state by remember { mutableStateOf(initial) }
        CompositionLocalProvider(LocalMenuSound provides { sounds += it }) {
            RiffTheme {
                ReportContent(
                    state = state,
                    onKind = { events += "kind:$it" },
                    onText = {
                        events += "text:$it"
                        state = state.copy(text = it)
                    },
                    onContact = {
                        events += "contact:$it"
                        state = state.copy(contact = it)
                    },
                    onIncludeDevice = { events += "device:$it" },
                    onSend = { events += "send" },
                    onBack = { events += "back" },
                )
            }
        }
    }

    @Test
    fun theFormShowsEveryFieldAndWhatGoesAlong() {
        show(ReportUiState(device = device))

        composeRule.onNodeWithText("Reportar problema").assertExists()
        composeRule.onNodeWithText("Problema").assertExists()
        composeRule.onNodeWithText("Sugestão").assertExists()
        composeRule.onNodeWithText("Outro").assertExists()
        composeRule.onNodeWithText("O que aconteceu?").assertExists()
        composeRule.onNodeWithText("Seu email (opcional)").performScrollTo().assertExists()
        composeRule.onNodeWithTag(ReportTestTags.INCLUDE_DEVICE).performScrollTo().assertIsOn()
        composeRule.onNodeWithText("Riff 1.4 · Android 16 · samsung SM-S931B · pt-BR").performScrollTo().assertExists()
    }

    @Test
    fun whatIsTypedAndChosenGoesToTheScreenState() {
        show(ReportUiState(device = device))

        composeRule.onNodeWithText("Sugestão").performClick()
        composeRule.onNodeWithTag(ReportTestTags.TEXT).performTextInput("Trava")
        composeRule.onNodeWithTag(ReportTestTags.CONTACT).performScrollTo().performTextInput("eu@x.com")
        composeRule.onNodeWithTag(ReportTestTags.INCLUDE_DEVICE).performScrollTo().performClick()

        assertEquals(listOf("kind:SUGGESTION", "text:Trava", "contact:eu@x.com", "device:false"), events)
    }

    @Test
    fun sendIsOffUntilThereIsAText() {
        show(ReportUiState(device = device))

        composeRule.onNodeWithText("Enviar").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun sendingSoundsLikeConfirming() {
        show(ReportUiState(text = "Trava", device = device))

        composeRule.onNodeWithText("Enviar").performScrollTo().assertIsEnabled().performClick()

        assertEquals(listOf("send"), events)
        assertEquals(listOf(MenuSound.CONFIRM), sounds)
    }

    @Test
    fun anInvalidEmailIsPointedOut() {
        show(ReportUiState(text = "Trava", contact = "eu@", device = device))

        composeRule.onNodeWithText("Confira o email.").performScrollTo().assertExists()
    }

    @Test
    fun afterSendingItThanksAndGoesBack() {
        show(ReportUiState(text = "Trava", device = device, status = ReportStatus.SENT))

        composeRule.onNodeWithText("Obrigado! Recebemos seu relato.").assertExists()
        composeRule.onNodeWithTag(ReportTestTags.DONE).performClick()

        assertEquals(listOf("back"), events)
    }

    @Test
    fun aFailureAsksToTryAgain() {
        show(ReportUiState(text = "Trava", device = device, status = ReportStatus.FAILED))

        composeRule.onNodeWithText("Não deu para enviar. Confira a internet e tente de novo.").performScrollTo()
            .assertExists()
    }

    @Test
    fun rightAfterAReportItAsksToWait() {
        show(ReportUiState(text = "Trava", device = device, status = ReportStatus.WAIT))

        composeRule.onNodeWithText("Você acabou de enviar um relato. Espere alguns minutos para enviar outro.")
            .performScrollTo().assertExists()
    }

    @Test
    fun withoutConfigurationItSaysSoAndCannotSend() {
        show(ReportUiState(text = "Trava", device = device, status = ReportStatus.NOT_CONFIGURED))

        composeRule.onNodeWithText("O envio de relatos não está configurado nesta versão do Riff.").performScrollTo()
            .assertExists()
        composeRule.onNodeWithText("Enviar").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun withTheDeviceInfoOffTheSwitchShowsIt() {
        show(ReportUiState(device = device, includeDevice = false))

        composeRule.onNodeWithTag(ReportTestTags.INCLUDE_DEVICE).performScrollTo().assertIsOff()
    }

    @Test
    fun backLeaves() {
        show(ReportUiState(device = device))

        composeRule.onNodeWithText("Voltar").performClick()

        assertEquals(listOf("back"), events)
    }
}
