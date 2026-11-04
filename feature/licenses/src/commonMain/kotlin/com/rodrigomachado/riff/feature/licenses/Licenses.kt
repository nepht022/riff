package com.rodrigomachado.riff.feature.licenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Um item da tela: o que é, de quem, a licença e o texto dela (null quando não há texto para mostrar). */
data class LicenseEntry(val name: String, val author: String, val license: String, val text: String?)

/** [content]: o que não vem do Gradle (modelo, fontes, sons); [libraries]: as dependências. */
data class LicensesUiState(
    val content: List<LicenseEntry> = emptyList(),
    val libraries: List<LicenseEntry> = emptyList(),
)

interface LicenseSource {
    suspend fun content(): List<LicenseEntry>

    suspend fun libraries(): List<LicenseEntry>
}

/** Licenças e privacidade. No celular o Hilt cria o `HiltLicensesViewModel`; no PC o app monta a fonte. */
open class LicensesViewModel(private val source: LicenseSource) : ViewModel() {
    private val ui = MutableStateFlow(LicensesUiState())
    val state: StateFlow<LicensesUiState> = ui.asStateFlow()

    init {
        viewModelScope.launch { ui.value = LicensesUiState(source.content(), source.libraries()) }
    }
}
