package com.rodrigomachado.riff.feature.report

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
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
import org.junit.Assert.assertTrue
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

    private val device = DeviceInfo.android("1.4", "16", "samsung SM-S931B", "pt-BR")
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
                    onCategory = { events += "category:$it" },
                    onCustomCategory = {
                        events += "custom:$it"
                        state = state.copy(customCategory = it)
                    },
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
        composeRule.onNode(hasText("Incluir informações do aparelho") and isToggleable()).performScrollTo().assertIsOn()
        composeRule.onNodeWithText("Riff 1.4 · Android 16 · samsung SM-S931B · pt-BR").performScrollTo().assertExists()
    }

    /** Antes de enviar, a pessoa sabe para onde o relato vai e para que serve o email (LGPD). */
    @Test
    fun itSaysWhereTheReportGoesAndWhatTheEmailIsFor() {
        show(ReportUiState(device = device))

        composeRule.onNodeWithText(
            "O relato vai para o desenvolvedor do Riff. O email é opcional e só serve para ele responder você.",
        ).performScrollTo().assertExists()
    }

    @Test
    fun whatIsTypedAndChosenGoesToTheScreenState() {
        show(ReportUiState(device = device))

        composeRule.onNodeWithText("Sugestão").performClick()
        composeRule.onNodeWithTag(ReportTestTags.TEXT).performTextInput("Trava")
        composeRule.onNodeWithTag(ReportTestTags.CONTACT).performScrollTo().performTextInput("eu@x.com")
        composeRule.onNodeWithText("Incluir informações do aparelho").performScrollTo().performClick()

        assertEquals(listOf("kind:SUGGESTION", "text:Trava", "contact:eu@x.com", "device:false"), events)
    }

    @Test
    fun sendIsOffUntilThereIsAText() {
        show(ReportUiState(device = device))

        composeRule.onNodeWithText("Enviar").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun sendingSoundsLikeConfirming() {
        show(ReportUiState(text = "Trava", category = ReportCategory.CRASH, device = device))

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
        // O som de voltar é da navegação, ao sair da tela; aqui ele tocaria duas vezes.
        assertEquals(emptyList<MenuSound>(), sounds)
    }

    @Test
    fun aFailureAsksToTryAgain() {
        show(ReportUiState(text = "Trava", device = device, status = ReportStatus.FAILED))

        composeRule.onNodeWithText("Não deu para enviar. Confira a internet e tente de novo.").performScrollTo()
            .assertExists()
    }

    /** O leitor de tela anuncia a falha e o "Obrigado" sem a pessoa procurar. */
    @Test
    fun theMessagesAreAnnouncedByTalkBack() {
        show(ReportUiState(text = "Trava", device = device, status = ReportStatus.FAILED))

        composeRule.onNode(
            androidx.compose.ui.test.hasText("Não deu para enviar", substring = true).and(
                androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(
                    androidx.compose.ui.semantics.SemanticsProperties.LiveRegion,
                ),
            ),
        ).assertExists()
    }

    @Test
    fun theThanksIsAnnouncedByTalkBack() {
        show(ReportUiState(text = "Trava", device = device, status = ReportStatus.SENT))

        composeRule.onNode(
            androidx.compose.ui.test.hasText("Obrigado", substring = true).and(
                androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(
                    androidx.compose.ui.semantics.SemanticsProperties.LiveRegion,
                ),
            ),
        ).assertExists()
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

        composeRule.onNode(
            hasText("Incluir informações do aparelho") and isToggleable(),
        ).performScrollTo().assertIsOff()
    }

    @Test
    fun backLeaves() {
        show(ReportUiState(device = device))

        composeRule.onNodeWithText("Voltar").performClick()

        assertEquals(listOf("back"), events)
        // O som de voltar é da navegação, ao sair da tela; aqui ele tocaria duas vezes.
        assertEquals(emptyList<MenuSound>(), sounds)
    }

    /** O TalkBack lê o nome do interruptor, e tocar na linha inteira liga e desliga. */
    @Test
    fun theWholeDeviceRowTogglesAndIsNamed() {
        show(ReportUiState(device = device))

        composeRule.onNodeWithText("Incluir informações do aparelho").performScrollTo().performClick()

        assertEquals(listOf("device:false"), events)
        composeRule.onNode(hasText("Incluir informações do aparelho") and isToggleable()).assertIsOn()
    }

    @Test
    fun theTextAsksForWhatFitsTheKind() {
        show(ReportUiState(kind = ReportKind.SUGGESTION, device = device))
        composeRule.onNodeWithText("Dê uma sugestão de melhoria!").assertExists()
    }

    @Test
    fun otherHasNoCategoryAndAsksForAMessage() {
        show(ReportUiState(kind = ReportKind.OTHER, device = device))

        composeRule.onNodeWithText("Escreva sua mensagem").assertExists()
        composeRule.onNodeWithText("Escolha uma categoria").assertDoesNotExist()
    }

    @Test
    fun aCategoryIsChosenFromTheList() {
        show(ReportUiState(device = device))

        composeRule.onNodeWithText("Escolha uma categoria").performClick()
        composeRule.onNodeWithText("Pesquisa e download do Encore").performScrollTo().performClick()

        assertEquals(listOf("category:ENCORE"), events)
        composeRule.onNodeWithText("Guitarra separada por IA").assertDoesNotExist() // a lista fecha
    }

    @Test
    fun theChosenCategoryIsShown() {
        show(ReportUiState(category = ReportCategory.LOOK, kind = ReportKind.SUGGESTION, device = device))

        composeRule.onNodeWithText("Visual da pista").assertExists()
    }

    @Test
    fun otherHasAFreeCategoryBelowTheMessage() {
        show(ReportUiState(kind = ReportKind.OTHER, device = device))

        val message = composeRule.onNodeWithTag(ReportTestTags.TEXT).getUnclippedBoundsInRoot()
        val category = composeRule.onNodeWithTag(ReportTestTags.CUSTOM_CATEGORY).performScrollTo()
            .getUnclippedBoundsInRoot()
        composeRule.onNodeWithText("Categoria (opcional)").assertExists()
        assertTrue(category.top > message.bottom)

        composeRule.onNodeWithTag(ReportTestTags.CUSTOM_CATEGORY).performTextInput("Elogio")
        assertEquals(listOf("custom:Elogio"), events)
    }

    @Test
    fun problemHasNoFreeCategory() {
        show(ReportUiState(device = device))

        composeRule.onNodeWithTag(ReportTestTags.CUSTOM_CATEGORY).assertDoesNotExist()
    }

    /** O TalkBack diz qual categoria está marcada na lista. */
    @Test
    fun theListSaysWhichCategoryIsChosen() {
        show(ReportUiState(category = ReportCategory.ENCORE, device = device))

        composeRule.onNodeWithText("Pesquisa e download do Encore").performClick()

        composeRule.onNode(hasText("Pesquisa e download do Encore") and isSelectable()).assertIsSelected()
        composeRule.onNode(hasText("Outro problema") and isSelectable()).performScrollTo().assertIsNotSelected()
    }
}
