package com.rodrigomachado.riff.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "charts")
data class ChartEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val charter: String?,
    val durationMs: Long?,
    val folderUri: String,
    val format: String,
    /** Importação em que o mapa foi visto por último; os não vistos na mais recente são apagados. */
    val lastSeenScanId: Long,
)

@Entity(
    tableName = "chart_difficulties",
    primaryKeys = ["chartId", "difficulty"],
    foreignKeys = [ForeignKey(ChartEntity::class, ["id"], ["chartId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("chartId")],
)
data class ChartDifficultyEntity(val chartId: String, val difficulty: String)

data class ChartWithDifficulties(
    @Embedded val chart: ChartEntity,
    @Relation(parentColumn = "id", entityColumn = "chartId") val difficulties: List<ChartDifficultyEntity>,
)

@Entity(
    tableName = "track_matches",
    foreignKeys = [ForeignKey(ChartEntity::class, ["id"], ["chartId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("chartId")],
)
data class TrackMatchEntity(
    @PrimaryKey val spotifyTrackUri: String,
    val chartId: String,
    val confidence: Float,
    val isManual: Boolean,
    val trackOffsetMs: Int,
    /** O jogador (ou a detecção automática) já escolheu o ajuste; 0 escolhido vale, 0 não escolhido não. */
    @ColumnInfo(defaultValue = "0") val offsetChosen: Boolean = false,
)

@Entity(
    tableName = "scores",
    foreignKeys = [ForeignKey(ChartEntity::class, ["id"], ["chartId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("chartId", "difficulty")],
)
data class ScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chartId: String,
    val difficulty: String,
    val score: Long,
    val accuracy: Float,
    val maxCombo: Int,
    val perfect: Int,
    val good: Int,
    val miss: Int,
    val playedAtEpochMs: Long,
)

/** Ajuste fino de sincronia de um mapa da biblioteca (ms somados ao relógio). Sai junto com o mapa. */
@Entity(
    tableName = "chart_offsets",
    foreignKeys = [ForeignKey(ChartEntity::class, ["id"], ["chartId"], onDelete = ForeignKey.CASCADE)],
)
data class ChartOffsetEntity(@PrimaryKey val chartId: String, val offsetMs: Int)

/** Mapas baixados do Encore (md5 do pacote), à parte da tabela de mapas: a importação regrava aquela linha. */
@Entity(
    tableName = "encore_charts",
    foreignKeys = [ForeignKey(ChartEntity::class, ["id"], ["chartId"], onDelete = ForeignKey.CASCADE)],
)
data class EncoreChartEntity(@PrimaryKey val chartId: String, val md5: String)

/** Separação da guitarra pela IA: SEPARATED (arquivos em filesDir/separacoes) ou DECLINED ("Jogar sem separar"). */
@Entity(
    tableName = "ai_separations",
    foreignKeys = [ForeignKey(ChartEntity::class, ["id"], ["chartId"], onDelete = ForeignKey.CASCADE)],
)
data class AiSeparationEntity(
    @PrimaryKey val chartId: String,
    val state: String,
    val bytes: Long,
    val createdAtEpochMs: Long,
)
