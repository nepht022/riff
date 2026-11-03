package com.rodrigomachado.riff.core.model

/**
 * Preferências do jogador. [audioOffsetMs] negativo compensa atraso de áudio (fone Bluetooth).
 * Volumes vão de 0 (desligado) a 1: [masterVolume] multiplica todos os sons do app; [hitVolume] e [missVolume]
 * são os sons de acerto e de erro. [menuSounds] liga os sons de guitarra dos botões do menu (no volume geral).
 */
data class Settings(
    val audioOffsetMs: Int,
    val noteSpeed: Float,
    val leftHanded: Boolean,
    val masterVolume: Float,
    val hitVolume: Float,
    val missVolume: Float,
    val menuSounds: Boolean = true,
    /** Perguntar se quer separar a guitarra com IA mesmo nas músicas em que já escolheu "Sem separar". */
    val askSeparationAlways: Boolean = true,
    /** A fila de separações da guitarra por IA espera o celular estar na tomada. */
    val separateOnlyCharging: Boolean = false,
    val highwayBackground: HighwayBackground = HighwayBackground.STAGE,
    val highwayArt: HighwayArt = HighwayArt.NONE,
    val noteStyle: NoteStyle = NoteStyle.DISCS,
    val fretStyle: FretStyle = FretStyle.DISCS,
    /** Mostrar a capa ou a imagem de fundo do mapa atrás da pista. */
    val showChartImage: Boolean = true,
    /** Teclas, botões do controle e palheta da partida no PC (o celular não usa). */
    val controls: Controls = Controls(),
) {
    init {
        require(noteSpeed in MIN_NOTE_SPEED..MAX_NOTE_SPEED) { "noteSpeed fora de $MIN_NOTE_SPEED..$MAX_NOTE_SPEED" }
        require(masterVolume in 0f..1f) { "masterVolume fora de 0..1: $masterVolume" }
        require(hitVolume in 0f..1f) { "hitVolume fora de 0..1: $hitVolume" }
        require(missVolume in 0f..1f) { "missVolume fora de 0..1: $missVolume" }
    }

    val effectiveHitVolume: Float get() = masterVolume * hitVolume

    val effectiveMissVolume: Float get() = masterVolume * missVolume

    val trackLook: TrackLook get() = TrackLook(highwayBackground, highwayArt, noteStyle, fretStyle)

    companion object {
        const val MIN_NOTE_SPEED = 0.5f
        const val MAX_NOTE_SPEED = 2f

        val DEFAULT = Settings(
            audioOffsetMs = 0,
            noteSpeed = 1f,
            leftHanded = false,
            masterVolume = 1f,
            hitVolume = 0.6f,
            missVolume = 0.6f,
        )
    }
}
