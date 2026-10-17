package com.rodrigomachado.riff.feature.report

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val text: String = "",
    val contact: String = "",
    val includeDevice: Boolean = true,
    val device: DeviceInfo,
    val status: ReportStatus = ReportStatus.EDITING,
) {
    /** Email opcional: vazio vale; preenchido, precisa parecer um email. */
    val contactInvalid: Boolean get() = contact.isNotBlank() && !EMAIL.matches(contact.trim())

    val canSend: Boolean
        get() = text.isNotBlank() && !contactInvalid &&
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
    private val sender: ReportSender,
    private val cooldown: ReportCooldown,
    deviceInfo: DeviceInfoSource,
) : ViewModel() {
    private val state = MutableStateFlow(
        ReportUiState(
            device = deviceInfo.read(),
            status = if (sender.isConfigured) ReportStatus.EDITING else ReportStatus.NOT_CONFIGURED,
        ),
    )
    val uiState: StateFlow<ReportUiState> = state.asStateFlow()

    fun setKind(kind: ReportKind) = state.update { it.copy(kind = kind) }

    fun setText(text: String) = state.update { it.copy(text = text.take(MAX_TEXT)) }

    fun setContact(contact: String) = state.update { it.copy(contact = contact.take(MAX_CONTACT)) }

    fun setIncludeDevice(include: Boolean) = state.update { it.copy(includeDevice = include) }

    @Suppress("SwallowedException") // a falha vira a mensagem na tela; o texto fica para tentar de novo
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
        )
        viewModelScope.launch {
            val sent = try {
                sender.send(report)
                true
            } catch (e: ReportFailedException) {
                false
            }
            if (sent) cooldown.sent()
            state.update { it.copy(status = if (sent) ReportStatus.SENT else ReportStatus.FAILED) }
        }
    }

    companion object {
        const val MAX_TEXT = 4_000
        const val MAX_CONTACT = 200
    }
}
