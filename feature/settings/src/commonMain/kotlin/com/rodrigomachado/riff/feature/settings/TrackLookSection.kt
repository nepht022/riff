package com.rodrigomachado.riff.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.rodrigomachado.riff.core.model.FretStyle
import com.rodrigomachado.riff.core.model.HighwayArt
import com.rodrigomachado.riff.core.model.HighwayBackground
import com.rodrigomachado.riff.core.model.MenuSound
import com.rodrigomachado.riff.core.model.NoteStyle
import com.rodrigomachado.riff.core.model.TrackLook
import com.rodrigomachado.riff.core.ui.component.focusHighlight
import com.rodrigomachado.riff.core.ui.component.withMenuSound
import com.rodrigomachado.riff.core.ui.stage.FretsPreview
import com.rodrigomachado.riff.core.ui.stage.HighwayPreview
import com.rodrigomachado.riff.core.ui.stage.NotesPreview
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing
import com.rodrigomachado.riff.feature.settings.resources.Res
import com.rodrigomachado.riff.feature.settings.resources.settings_art_bolts
import com.rodrigomachado.riff.feature.settings.resources.settings_art_guitars
import com.rodrigomachado.riff.feature.settings.resources.settings_art_none
import com.rodrigomachado.riff.feature.settings.resources.settings_art_picks
import com.rodrigomachado.riff.feature.settings.resources.settings_art_stars
import com.rodrigomachado.riff.feature.settings.resources.settings_art_tattoo
import com.rodrigomachado.riff.feature.settings.resources.settings_art_winged_skull
import com.rodrigomachado.riff.feature.settings.resources.settings_bg_amplifier
import com.rodrigomachado.riff.feature.settings.resources.settings_bg_diamond_plate
import com.rodrigomachado.riff.feature.settings.resources.settings_bg_flames
import com.rodrigomachado.riff.feature.settings.resources.settings_bg_fretboard
import com.rodrigomachado.riff.feature.settings.resources.settings_bg_leather
import com.rodrigomachado.riff.feature.settings.resources.settings_bg_neon
import com.rodrigomachado.riff.feature.settings.resources.settings_bg_stage
import com.rodrigomachado.riff.feature.settings.resources.settings_fret_knobs
import com.rodrigomachado.riff.feature.settings.resources.settings_fret_pedals
import com.rodrigomachado.riff.feature.settings.resources.settings_fret_speakers
import com.rodrigomachado.riff.feature.settings.resources.settings_fret_style
import com.rodrigomachado.riff.feature.settings.resources.settings_highway_art
import com.rodrigomachado.riff.feature.settings.resources.settings_highway_background
import com.rodrigomachado.riff.feature.settings.resources.settings_look_close
import com.rodrigomachado.riff.feature.settings.resources.settings_note_style
import com.rodrigomachado.riff.feature.settings.resources.settings_style_bolts
import com.rodrigomachado.riff.feature.settings.resources.settings_style_discs
import com.rodrigomachado.riff.feature.settings.resources.settings_style_flames
import com.rodrigomachado.riff.feature.settings.resources.settings_style_picks
import com.rodrigomachado.riff.feature.settings.resources.settings_style_skulls
import com.rodrigomachado.riff.feature.settings.resources.settings_style_stars
import org.jetbrains.compose.resources.stringResource

internal enum class LookChoice { BACKGROUND, ART, NOTES, FRETS }

/**
 * Uma escolha do visual: muda só o campo escolhido, aplicada sobre o valor gravado (duas escolhas rápidas, antes de
 * a primeira voltar da gravação, não desfazem uma à outra).
 */
typealias TrackLookChange = (TrackLook) -> TrackLook

/** As quatro escolhas do visual da pista; cada uma abre a lista com miniaturas desenhadas pelo app. */
@Composable
internal fun TrackLookSection(look: TrackLook, onLook: (TrackLookChange) -> Unit) {
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
                TextButton(
                    onClick = withMenuSound(MenuSound.BACK, close),
                    modifier = Modifier.focusHighlight(CircleShape),
                ) {
                    Text(stringResource(Res.string.settings_look_close))
                }
            },
        )
    }
}

/** A lista de uma escolha: cada opção com a miniatura e o nome; tocar devolve o visual com ela. */
@Composable
internal fun TrackLookChoices(choice: LookChoice, look: TrackLook, onPick: (TrackLookChange) -> Unit) {
    val track = Modifier.size(TRACK_WIDTH, TRACK_HEIGHT)
    val row = Modifier.size(ROW_WIDTH, ROW_HEIGHT)
    when (choice) {
        LookChoice.BACKGROUND -> ChoiceList(
            HighwayBackground.entries,
            look.background,
            { backgroundName(it) },
            { HighwayPreview(look.copy(background = it), track) },
        ) { picked -> onPick { it.copy(background = picked) } }

        LookChoice.ART -> ChoiceList(
            HighwayArt.entries,
            look.art,
            { artName(it) },
            { HighwayPreview(look.copy(art = it), track) },
        ) { picked -> onPick { it.copy(art = picked) } }

        LookChoice.NOTES -> ChoiceList(
            NoteStyle.entries,
            look.notes,
            { noteName(it) },
            { NotesPreview(it, row) },
        ) { picked ->
            onPick { it.copy(notes = picked) }
        }

        LookChoice.FRETS -> ChoiceList(
            FretStyle.entries,
            look.frets,
            { fretName(it) },
            { FretsPreview(it, row) },
        ) { picked ->
            onPick { it.copy(frets = picked) }
        }
    }
}

@Composable
private fun choiceTitle(choice: LookChoice): String = stringResource(
    when (choice) {
        LookChoice.BACKGROUND -> Res.string.settings_highway_background
        LookChoice.ART -> Res.string.settings_highway_art
        LookChoice.NOTES -> Res.string.settings_note_style
        LookChoice.FRETS -> Res.string.settings_fret_style
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
internal fun ChoiceRow(title: String, value: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else DISABLED_ALPHA).focusHighlight().clickable(
            enabled = enabled,
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
                Modifier.fillMaxWidth().focusHighlight().clickable(
                    onClick = withMenuSound(MenuSound.NAVIGATE) { onChoose(option) },
                ),
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
        HighwayBackground.STAGE -> Res.string.settings_bg_stage
        HighwayBackground.AMPLIFIER -> Res.string.settings_bg_amplifier
        HighwayBackground.LEATHER -> Res.string.settings_bg_leather
        HighwayBackground.FRETBOARD -> Res.string.settings_bg_fretboard
        HighwayBackground.FLAMES -> Res.string.settings_bg_flames
        HighwayBackground.NEON -> Res.string.settings_bg_neon
        HighwayBackground.DIAMOND_PLATE -> Res.string.settings_bg_diamond_plate
    },
)

@Composable
private fun artName(kind: HighwayArt): String = stringResource(
    when (kind) {
        HighwayArt.NONE -> Res.string.settings_art_none
        HighwayArt.WINGED_SKULL -> Res.string.settings_art_winged_skull
        HighwayArt.BOLTS -> Res.string.settings_art_bolts
        HighwayArt.STARS -> Res.string.settings_art_stars
        HighwayArt.GUITARS -> Res.string.settings_art_guitars
        HighwayArt.PICKS -> Res.string.settings_art_picks
        HighwayArt.TATTOO -> Res.string.settings_art_tattoo
    },
)

@Composable
private fun noteName(style: NoteStyle): String = stringResource(
    when (style) {
        NoteStyle.DISCS -> Res.string.settings_style_discs
        NoteStyle.PICKS -> Res.string.settings_style_picks
        NoteStyle.SKULLS -> Res.string.settings_style_skulls
        NoteStyle.STARS -> Res.string.settings_style_stars
        NoteStyle.BOLTS -> Res.string.settings_style_bolts
        NoteStyle.FLAMES -> Res.string.settings_style_flames
    },
)

@Composable
private fun fretName(style: FretStyle): String = stringResource(
    when (style) {
        FretStyle.DISCS -> Res.string.settings_style_discs
        FretStyle.PEDALS -> Res.string.settings_fret_pedals
        FretStyle.KNOBS -> Res.string.settings_fret_knobs
        FretStyle.SPEAKERS -> Res.string.settings_fret_speakers
        FretStyle.PICKS -> Res.string.settings_style_picks
    },
)

private val TRACK_WIDTH = 56.dp
private val TRACK_HEIGHT = 96.dp
private val ROW_WIDTH = 120.dp
private val ROW_HEIGHT = 40.dp
