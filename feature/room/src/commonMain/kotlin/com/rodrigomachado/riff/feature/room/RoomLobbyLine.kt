package com.rodrigomachado.riff.feature.room

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.rodrigomachado.riff.core.room.SongStatus
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.feature.room.resources.Res
import com.rodrigomachado.riff.feature.room.resources.room_and
import com.rodrigomachado.riff.feature.room.resources.room_be_ready
import com.rodrigomachado.riff.feature.room.resources.room_need_two
import com.rodrigomachado.riff.feature.room.resources.room_ready_count
import com.rodrigomachado.riff.feature.room.resources.room_start_with_ready
import com.rodrigomachado.riff.feature.room.resources.room_starting
import com.rodrigomachado.riff.feature.room.resources.room_waiting_download
import com.rodrigomachado.riff.feature.room.resources.room_waiting_ready
import com.rodrigomachado.riff.feature.room.resources.room_waiting_song
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Um pedaço da linha do que falta na sala (o texto sai das strings, na tela). */
internal sealed interface LobbyNeed {
    /** "Começando…": a linha é só isso. */
    data object Starting : LobbyNeed

    /** "2 prontos". */
    data class ReadyCount(val count: Int) : LobbyNeed

    /** "esperando Bruno_88 terminar de baixar" (outros jogadores baixando a música do Encore). */
    data class Downloading(val names: List<String>) : LobbyNeed

    /** "esperando Ana ficar pronta" (outros com a música, ou ainda conferindo, sem o Pronto). */
    data class NotReady(val names: List<String>) : LobbyNeed

    /** Sem música, para quem não é o dono: "esperando Ana escolher a música". */
    data class Song(val owner: String) : LobbyNeed

    /** Os avisos do Começar do dono: ficar pronto, ou os 2 prontos. */
    data class Hint(val hint: StartHint) : LobbyNeed

    /** Passou dos 30 s: o Começar do dono já vale "com quem está pronto". */
    data object WithWhoIsReady : LobbyNeed
}

/**
 * O que falta para a rodada começar, na ordem da linha: quantos estão prontos, por quem a sala espera (primeiro quem
 * baixa a música, senão quem não está pronto; eu mesmo não entro: o meu lado aparece no meio da tela) e os avisos do
 * dono. Quem sumiu ou está sem a música (sem o Encore) não é esperado.
 */
internal fun RoomLobbyUiState.Ready.whatIsMissing(): List<LobbyNeed> {
    if (starting) return listOf(LobbyNeed.Starting)
    val others = room.players.filter { !it.gone && it.uid != room.me }
    return buildList {
        if (room.song == null) {
            val owner = room.players.firstOrNull { it.uid == room.owner }
            if (!room.iAmOwner && owner != null) add(LobbyNeed.Song(owner.name))
        } else {
            add(LobbyNeed.ReadyCount(readyCount))
            val downloading = others.filter { it.song == SongStatus.DOWNLOADING }
            val notReady = others.filter {
                it.song != SongStatus.DOWNLOADING && it.song != SongStatus.MISSING && !it.isReady()
            }
            when {
                downloading.isNotEmpty() -> add(LobbyNeed.Downloading(downloading.map { it.name }))
                notReady.isNotEmpty() -> add(LobbyNeed.NotReady(notReady.map { it.name }))
            }
        }
        startHint?.let { add(LobbyNeed.Hint(it)) }
        if (withWhoIsReady) add(LobbyNeed.WithWhoIsReady)
    }
}

/**
 * A linha do que falta, numa frase só ("2 prontos · esperando Bruno_88 terminar de baixar"); vazia, nada. Muda
 * sozinha (alguém fica pronto, termina de baixar): o leitor de tela anuncia, sem interromper.
 */
@Composable
internal fun MissingLine(state: RoomLobbyUiState.Ready, modifier: Modifier = Modifier) {
    val parts = state.whatIsMissing().map { it.text() }
    if (parts.isEmpty()) return
    Text(
        parts.joinToString(" · ").replaceFirstChar { it.uppercase() },
        style = MaterialTheme.typography.bodyLarge,
        color = RiffColors.Paper,
        textAlign = TextAlign.Center,
        modifier = modifier.testTag(RoomTestTags.MISSING).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun LobbyNeed.text(): String = when (this) {
    LobbyNeed.Starting -> stringResource(Res.string.room_starting)

    is LobbyNeed.ReadyCount -> pluralStringResource(Res.plurals.room_ready_count, count, count)

    is LobbyNeed.Downloading -> pluralStringResource(Res.plurals.room_waiting_download, names.size, names(names))

    is LobbyNeed.NotReady -> pluralStringResource(Res.plurals.room_waiting_ready, names.size, names(names))

    is LobbyNeed.Song -> stringResource(Res.string.room_waiting_song, owner)

    is LobbyNeed.Hint -> when (hint) {
        StartHint.BE_READY -> stringResource(Res.string.room_be_ready)
        StartHint.NEED_TWO -> stringResource(Res.string.room_need_two)
    }

    LobbyNeed.WithWhoIsReady -> stringResource(Res.string.room_start_with_ready)
}

/** "Ana", "Ana e Bia", "Ana, Bia e Caio". */
@Composable
private fun names(names: List<String>): String = if (names.size == 1) {
    names.single()
} else {
    stringResource(Res.string.room_and, names.dropLast(1).joinToString(", "), names.last())
}
