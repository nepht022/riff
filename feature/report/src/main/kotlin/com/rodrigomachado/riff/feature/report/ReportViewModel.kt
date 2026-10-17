package com.rodrigomachado.riff.feature.report

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class ReportStatus { EDITING, SENDING, SENT, FAILED, WAIT, NOT_CONFIGURED }

data class ReportUiState(
    val kind: ReportKind = ReportKind.PROBLEM,
    val category: ReportCategory? = null,
    val text: String = "",
    val contact: String = "",
    val includeDevice: Boolean = true,
    val device: DeviceInfo,
    val status: ReportStatus = ReportStatus.EDITING,
) {
    /** Email opcional: vazio vale; preenchido, precisa parecer um email. */
    val contactInvalid: Boolean get() = contact.isNotBlank() && !EMAIL.matches(contact.trim())

    /** Problema e Sugestão pedem uma categoria; Outro não tem. */
    val needsCategory: Boolean get() = category == null && ReportCategory.forKind(kind).isNotEmpty()

    val canSend: Boolean
        get() = text.isNotBlank() && !contactInvalid && !needsCategory &&
            status != ReportStatus.SENDING && status != ReportStatus.NOT_CONFIGURED

    private companion object {
        val EMAIL = Regex("""[^@\s]+@[^@\s]+\.[^@\s]+""")
    }
}

/**
 * Depois de um relato enviado, o próximo espera [WAIT_MS] (enquanto o app estiver aberto): segura spam acidental.
 * O limite de verdade é a cota diária do Google no script.
 */
@Singleton
class ReportCooldown(private val now: () -> Long) {
    @Inject constructor() : this(SystemClock::elapsedRealtime)

    private var lastSent: Long? = null

    val canSend: Boolean get() = lastSent?.let { now() - it >= WAIT_MS } ?: true

    fun sent() {
        lastSent = now()
    }

    companion object {
        const val WAIT_MS = 5 * 60_000L
    }
}

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val sender: ReportSender,
    private val cooldown: ReportCooldown,
    deviceInfo: DeviceInfoSource,
) : ViewModel() {
    // O que foi escrito fica no estado salvo: volta mesmo se o Android fechar o Riff em segundo plano.
    private val state = MutableStateFlow(
        ReportUiState(
            kind = saved.get<String>(KIND)?.let { name -> ReportKind.entries.firstOrNull { it.name == name } }
                ?: ReportKind.PROBLEM,
            category = saved.get<String>(
                CATEGORY,
            )?.let { name -> ReportCategory.entries.firstOrNull { it.name == name } },
            text = saved[TEXT] ?: "",
            contact = saved[CONTACT] ?: "",
            includeDevice = saved[INCLUDE_DEVICE] ?: true,
            device = deviceInfo.read(),
            status = if (sender.isConfigured) ReportStatus.EDITING else ReportStatus.NOT_CONFIGURED,
        ),
    )
    val uiState: StateFlow<ReportUiState> = state.asStateFlow()

    fun setKind(kind: ReportKind) {
        saved[KIND] = kind.name
        // A categoria de um tipo não serve para outro.
        if (state.value.category?.kind != kind) saved[CATEGORY] = null
        state.update { it.copy(kind = kind, category = it.category?.takeIf { category -> category.kind == kind }) }
    }

    fun setCategory(category: ReportCategory) {
        saved[CATEGORY] = category.name
        state.update { it.copy(category = category, kind = category.kind) }
    }

    fun setText(text: String) {
        val cut = text.take(MAX_TEXT)
        saved[TEXT] = cut
        state.update { it.copy(text = cut) }
    }

    fun setContact(contact: String) {
        val cut = contact.take(MAX_CONTACT)
        saved[CONTACT] = cut
        state.update { it.copy(contact = cut) }
    }

    fun setIncludeDevice(include: Boolean) {
        saved[INCLUDE_DEVICE] = include
        state.update { it.copy(includeDevice = include) }
    }

    // Qualquer falha (sem internet, rede bloqueada pelo aparelho) vira a mensagem na tela; o texto fica.
    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    fun send() {
        val current = state.value
        if (!current.canSend) return
        if (!cooldown.canSend) {
            state.update { it.copy(status = ReportStatus.WAIT) }
            return
        }
        state.update { it.copy(status = ReportStatus.SENDING) }
        val report = BugReport(
            kind = current.kind,
            text = current.text.trim(),
            contact = current.contact.trim().ifBlank { null },
            device = current.device.takeIf { current.includeDevice },
            category = current.category,
        )
        viewModelScope.launch {
            val sent = try {
                sender.send(report)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (sent) cooldown.sent()
            state.update { it.copy(status = if (sent) ReportStatus.SENT else ReportStatus.FAILED) }
        }
    }

    companion object {
        const val MAX_TEXT = 4_000
        const val MAX_CONTACT = 200
        private const val KIND = "kind"
        private const val CATEGORY = "category"
        private const val TEXT = "text"
        private const val CONTACT = "contact"
        private const val INCLUDE_DEVICE = "includeDevice"
    }
}
