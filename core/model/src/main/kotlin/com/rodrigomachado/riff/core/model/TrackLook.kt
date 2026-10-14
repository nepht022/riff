package com.rodrigomachado.riff.core.model

/** Superfície da pista (temas de rock desenhados em código). STAGE é a pista de sempre. */
enum class HighwayBackground { STAGE, AMPLIFIER, LEATHER, FRETBOARD, FLAMES, NEON, DIAMOND_PLATE }

/** Desenho claro estampado na pista, por baixo das notas. */
enum class HighwayArt { NONE, WINGED_SKULL, BOLTS, STARS, GUITARS, PICKS, TATTOO }

/** Forma das notas que descem (as cores das trilhas não mudam). */
enum class NoteStyle { DISCS, PICKS, SKULLS, STARS, BOLTS, FLAMES }

/** Forma dos cinco botões da linha de acerto. */
enum class FretStyle { DISCS, PEDALS, KNOBS, SPEAKERS, PICKS }

/** O visual escolhido da pista; [DEFAULT] é o de sempre. */
data class TrackLook(
    val background: HighwayBackground,
    val art: HighwayArt,
    val notes: NoteStyle,
    val frets: FretStyle,
) {
    companion object {
        val DEFAULT = TrackLook(HighwayBackground.STAGE, HighwayArt.NONE, NoteStyle.DISCS, FretStyle.DISCS)
    }
}
