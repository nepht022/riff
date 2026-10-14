package com.rodrigomachado.riff.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.FretStyle
import com.rodrigomachado.riff.core.model.HighwayArt
import com.rodrigomachado.riff.core.model.HighwayBackground
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.NoteStyle
import com.rodrigomachado.riff.core.model.TrackLook
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.stage.FretsPreview
import com.rodrigomachado.riff.core.ui.stage.HighwayPreview
import com.rodrigomachado.riff.core.ui.stage.NotesPreview
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

internal enum class LookChoice { BACKGROUND, ART, NOTES, FRETS }

/** As quatro escolhas do visual da pista; cada uma abre a lista com miniaturas desenhadas pelo app. */
@Composable
internal fun TrackLookSection(look: TrackLook, onLook: (TrackLook) -> Unit) {
    var open by rememberSaveable { mutableStateOf<LookChoice?>(null) }
    for (choice in LookChoice.entries) {
        ChoiceRow(choiceTitle(choice), currentName(choice, look)) { open = choice }
    }
    open?.let { choice ->
        val close = { open = null }
        AlertDialog(
            onDismissRequest = close,
            title = { Text(choiceTitle(choice)) },
            text = {
                TrackLookChoices(choice, look) {
                    onLook(it)
                    close()
                }
            },
            confirmButton = {
                TextButton(onClick = withMenuSound(MenuSound.BACK, close)) {
                    Text(stringResource(R.string.settings_look_close))
                }
            },
        )
    }
}

/** A lista de uma escolha: cada opção com a miniatura e o nome; tocar devolve o visual com ela. */
@Composable
internal fun TrackLookChoices(choice: LookChoice, look: TrackLook, onPick: (TrackLook) -> Unit) {
    val track = Modifier.size(TRACK_WIDTH, TRACK_HEIGHT)
    val row = Modifier.size(ROW_WIDTH, ROW_HEIGHT)
    when (choice) {
        LookChoice.BACKGROUND -> ChoiceList(
            HighwayBackground.entries,
            look.background,
            { backgroundName(it) },
            { HighwayPreview(look.copy(background = it), track) },
        ) { onPick(look.copy(background = it)) }

        LookChoice.ART -> ChoiceList(
            HighwayArt.entries,
            look.art,
            { artName(it) },
            { HighwayPreview(look.copy(art = it), track) },
        ) { onPick(look.copy(art = it)) }

        LookChoice.NOTES -> ChoiceList(NoteStyle.entries, look.notes, { noteName(it) }, { NotesPreview(it, row) }) {
            onPick(look.copy(notes = it))
        }

        LookChoice.FRETS -> ChoiceList(FretStyle.entries, look.frets, { fretName(it) }, { FretsPreview(it, row) }) {
            onPick(look.copy(frets = it))
        }
    }
}

@Composable
private fun choiceTitle(choice: LookChoice): String = stringResource(
    when (choice) {
        LookChoice.BACKGROUND -> R.string.settings_highway_background
        LookChoice.ART -> R.string.settings_highway_art
        LookChoice.NOTES -> R.string.settings_note_style
        LookChoice.FRETS -> R.string.settings_fret_style
    },
)

@Composable
private fun currentName(choice: LookChoice, look: TrackLook): String = when (choice) {
    LookChoice.BACKGROUND -> backgroundName(look.background)
    LookChoice.ART -> artName(look.art)
    LookChoice.NOTES -> noteName(look.notes)
    LookChoice.FRETS -> fretName(look.frets)
}

@Composable
private fun ChoiceRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(
            onClick = withMenuSound(MenuSound.NAVIGATE, onClick),
        ).padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = RiffColors.Paper,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.titleMedium, color = RiffColors.PaperDim)
    }
}

/** Poucas opções (até 7): uma coluna rolável, com todas montadas. */
@Composable
private fun <T> ChoiceList(
    options: List<T>,
    selected: T,
    name: @Composable (T) -> String,
    preview: @Composable (T) -> Unit,
    onChoose: (T) -> Unit,
) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        for (option in options) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = withMenuSound(MenuSound.NAVIGATE) { onChoose(option) }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                RadioButton(
                    selected = option == selected,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = RiffColors.TourRed),
                )
                preview(option)
                Text(name(option), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun backgroundName(kind: HighwayBackground): String = stringResource(
    when (kind) {
        HighwayBackground.STAGE -> R.string.settings_bg_stage
        HighwayBackground.AMPLIFIER -> R.string.settings_bg_amplifier
        HighwayBackground.LEATHER -> R.string.settings_bg_leather
        HighwayBackground.FRETBOARD -> R.string.settings_bg_fretboard
        HighwayBackground.FLAMES -> R.string.settings_bg_flames
        HighwayBackground.NEON -> R.string.settings_bg_neon
        HighwayBackground.DIAMOND_PLATE -> R.string.settings_bg_diamond_plate
    },
)

@Composable
private fun artName(kind: HighwayArt): String = stringResource(
    when (kind) {
        HighwayArt.NONE -> R.string.settings_art_none
        HighwayArt.WINGED_SKULL -> R.string.settings_art_winged_skull
        HighwayArt.BOLTS -> R.string.settings_art_bolts
        HighwayArt.STARS -> R.string.settings_art_stars
        HighwayArt.GUITARS -> R.string.settings_art_guitars
        HighwayArt.PICKS -> R.string.settings_art_picks
        HighwayArt.TATTOO -> R.string.settings_art_tattoo
    },
)

@Composable
private fun noteName(style: NoteStyle): String = stringResource(
    when (style) {
        NoteStyle.DISCS -> R.string.settings_style_discs
        NoteStyle.PICKS -> R.string.settings_style_picks
        NoteStyle.SKULLS -> R.string.settings_style_skulls
        NoteStyle.STARS -> R.string.settings_style_stars
        NoteStyle.BOLTS -> R.string.settings_style_bolts
        NoteStyle.FLAMES -> R.string.settings_style_flames
    },
)

@Composable
private fun fretName(style: FretStyle): String = stringResource(
    when (style) {
        FretStyle.DISCS -> R.string.settings_style_discs
        FretStyle.PEDALS -> R.string.settings_fret_pedals
        FretStyle.KNOBS -> R.string.settings_fret_knobs
        FretStyle.SPEAKERS -> R.string.settings_fret_speakers
        FretStyle.PICKS -> R.string.settings_style_picks
    },
)

private val TRACK_WIDTH = 56.dp
private val TRACK_HEIGHT = 96.dp
private val ROW_WIDTH = 120.dp
private val ROW_HEIGHT = 40.dp
