# Riff: calibração e ajuste fino por música — Plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Medir o atraso entre som e toque do jogador (metrônomo + toques) e aplicá-lo em todas as partidas, e permitir um ajuste fino por música no menu de pausa, salvo por faixa do Spotify ou por mapa da biblioteca.

**Architecture:** `:feature:calibration` toca um metrônomo numa `AudioSink` (a mesma saída das faixas separadas), estima o instante em que cada clique sai no alto-falante (`ClickTimeline`) e compara com os toques (`CalibrationSession`, Kotlin puro). O `SongClock` passa a aceitar mudança de ajuste no meio da partida; o ajuste por música fica em `track_matches.trackOffsetMs` (Spotify) e numa tabela nova `chart_offsets` (biblioteca).

**Tech Stack:** Kotlin, coroutines, Compose, Hilt, Room 2.8 (AutoMigration, room-testing), DataStore, Android AudioTrack, JUnit 4/5 + Robolectric + Roborazzi.

**Spec:** `docs/superpowers/specs/2026-10-09-calibracao-design.md` (complementa `2026-10-05-riff-design.md` §2 item 4, §4, §11 passo 7).

## Global Constraints

- minSdk 26; compileSdk 37; targetSdk 36; Kotlin 2.4.20; AGP 9.4.1; Gradle via `./scripts/gw` (Windows a partir do WSL).
- Um atraso só (sem perfil por saída de som). Ajuste fino no menu de pausa. Medição tocando junto com o clique.
- Metrônomo: 100 BPM (600 ms), 4 cliques de entrada que não contam, 20 cliques medidos; clique = 20 ms de senoide de 1,5 kHz com decaimento rápido, sintetizado em código.
- Toques a mais de 250 ms da mediana são descartados; menos de 12 válidos → "instável". Cada clique aceita no máximo um toque (o mais próximo).
- Sinal: `SongClock` soma o ajuste à posição; atraso medido `L` → `audioOffsetMs = −L`. Na partida vale `audioOffsetMs + ajuste da música`.
- Limites: atraso geral e ajuste por música em −500..+500 ms. Retoque na calibração: ±5 ms. Ajuste na pausa: ±10 ms.
- Ajuste por música: Spotify em `track_matches.trackOffsetMs` (ajustar salva o casamento automático); biblioteca em `chart_offsets(chartId PK, offsetMs)`, `ON DELETE CASCADE`, banco 1 → 2 por `AutoMigration`.
- Instantes no mesmo relógio: `SystemClock.uptimeMillis` / `PointerInputChange.uptimeMillis`.
- Strings pt-BR padrão e en. Detekt e Lint sem erros; Robolectric `sdk=35`.

## Review Focus

1. **Reimportar a biblioteca não pode apagar os ajustes dos mapas que continuam lá** (o import usa `@Upsert`, sem apagar e recriar o mapa). Teste: `ChartOffsetDaoTest.reimportingAChartKeepsItsOffset` (Task 2).
2. **"Jogar de novo" no Spotify depois de ajustar na pausa usa o ajuste novo**, não o valor que veio na rota. Teste: `GameplayViewModelTest.spotifyOffsetIsReadFromTheSavedMatchNotFromTheRoute` (Task 4).
3. **Jogador que toca rápido demais (dois toques no mesmo clique) ou esquece alguns cliques** ainda recebe um resultado coerente. Testes: `CalibrationSessionTest.twoTapsOnTheSameClickKeepTheClosest` e `missedClicksDoNotBreakTheMeasurement` (Task 5).
4. **App vai para segundo plano no meio da medição**: a medição para, o som para e a tela volta ao início. Teste: `CalibrationViewModelTest.cancellingStopsTheMeasurementAndGoesBackToTheStart` (Task 6).
5. **Botão de ajuste apertado além do limite**: o valor para em ±500 ms e a sessão não é deslocada além disso. Teste: `GameplayViewModelTest.songOffsetStopsAtTheLimits` (Task 4).

---

## Estrutura de arquivos

| Arquivo | Responsabilidade |
|---|---|
| `core/game/.../SongClock.kt` | `offsetMs` mutável |
| `feature/gameplay/.../GameSession.kt` | `adjustOffset(deltaMs)` |
| `core/database/.../Entities.kt`, `ChartOffsetDao.kt`, `RiffDatabase.kt`, `DatabaseModule.kt` | Tabela `chart_offsets`, versão 2 |
| `core/data/.../ChartOffsetRepository.kt` | Ajuste por mapa |
| `core/data/.../TrackMatchRepository.kt` | `offsetFor`, `saveOffset` |
| `feature/gameplay/.../GameplayViewModel.kt`, `GameplayScreen.kt` | Ajuste na pausa |
| `feature/calibration/.../CalibrationSession.kt` | Cálculo do atraso (puro) |
| `feature/calibration/.../ClickTrack.kt` | Metrônomo em PCM e linha do tempo dos cliques (puro) |
| `feature/calibration/.../ClickTrackPlayer.kt` | Toca o metrônomo numa `AudioSink` e devolve os instantes |
| `feature/calibration/.../CalibrationViewModel.kt`, `CalibrationScreen.kt`, `CalibrationRoute.kt` | Tela |
| `feature/settings/.../SettingsScreen.kt` | Linha "Calibrar atraso do áudio" |
| `core/audio/.../AudioModule.kt` | Binding de `AudioSinkFactory` |
| `app/.../RiffNavHost.kt` | Rota da calibração |

---

### Task 1: Ajuste do relógio no meio da partida

**Files:**
- Modify: `core/game/src/main/kotlin/com/rodrigomachado/riff/core/game/SongClock.kt`
- Modify: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/GameSession.kt`
- Test: `core/game/src/test/kotlin/com/rodrigomachado/riff/core/game/SongClockTest.kt`
- Test: `feature/gameplay/src/test/kotlin/com/rodrigomachado/riff/feature/gameplay/GameSessionTest.kt`

**Interfaces:**
- Produces: `SongClock(timeSource, offsetMs: Long = 0)` com `var offsetMs: Long`; `GameSession.adjustOffset(deltaMs: Long)`.

- [ ] **Step 1: Escrever os testes que falham**

Em `SongClockTest.kt`, acrescente:
```kotlin
    @Test
    fun changingTheOffsetMovesThePositionAtOnce() {
        time.now = 5_000
        clock.sync(positionMs = 1_000, isPaused = true, capturedAtMs = 5_000)

        clock.offsetMs = -40

        assertEquals(960L, clock.positionMs())
    }
```

Em `GameSessionTest.kt`, acrescente:
```kotlin
    @Test
    fun adjustingTheOffsetShiftsTheSongTimeImmediately() {
        player.positionMs = 1_000
        val session = session(note(10_000))
        session.pause()

        session.adjustOffset(-10)
        session.adjustOffset(-10)

        assertEquals(980L, session.tick())
    }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :core:game:test :feature:gameplay:testDebugUnitTest --console=plain`
Expected: FAIL na compilação (`offsetMs` é `val` privado; `adjustOffset` não existe).

- [ ] **Step 3: Implementar**

Em `SongClock.kt`, troque `class SongClock(private val timeSource: TimeSource, private val offsetMs: Long = 0) {` por:
```kotlin
class SongClock(private val timeSource: TimeSource, offsetMs: Long = 0) {
    /** Ajuste somado à posição; pode mudar no meio da partida (ajuste fino na pausa). */
    var offsetMs: Long = offsetMs
```
(o restante da classe continua usando `offsetMs`).

Em `GameSession.kt`, depois de `fun resume()`, acrescente:
```kotlin
    /** Ajuste fino de sincronia desta música, aplicado na hora. */
    fun adjustOffset(deltaMs: Long) {
        clock.offsetMs += deltaMs
        songTimeMs = clock.positionMs()
    }
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw detekt -PdetektAutoCorrect --console=plain; ./scripts/gw detekt :core:game:test :feature:gameplay:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(game): ajuste de sincronia no meio da partida" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Tabela `chart_offsets` e banco na versão 2

**Files:**
- Modify: `core/database/src/main/kotlin/com/rodrigomachado/riff/core/database/Entities.kt`
- Create: `core/database/src/main/kotlin/com/rodrigomachado/riff/core/database/ChartOffsetDao.kt`
- Modify: `core/database/src/main/kotlin/com/rodrigomachado/riff/core/database/RiffDatabase.kt`, `DatabaseModule.kt`
- Modify: `core/database/build.gradle.kts`, `gradle/libs.versions.toml`
- Create (gerado pelo build): `core/database/schemas/com.rodrigomachado.riff.core.database.RiffDatabase/2.json`
- Test: `core/database/src/test/kotlin/com/rodrigomachado/riff/core/database/ChartOffsetDaoTest.kt`
- Test: `core/database/src/test/kotlin/com/rodrigomachado/riff/core/database/MigrationTest.kt`

**Interfaces:**
- Produces: `ChartOffsetEntity(chartId: String, offsetMs: Int)`; `ChartOffsetDao { suspend fun get(chartId: String): Int?; suspend fun upsert(offset: ChartOffsetEntity) }`; `RiffDatabase.chartOffsetDao()`; binding Hilt de `ChartOffsetDao`.

- [ ] **Step 1: Escrever os testes que falham**

Em `gradle/libs.versions.toml`, `[libraries]`: `androidx-room-testing = { module = "androidx.room:room-testing", version.ref = "room" }`.
Em `core/database/build.gradle.kts`, nas dependências: `testImplementation(libs.androidx.room.testing)`; e no bloco `android { }`:
```kotlin
    // Os esquemas exportados viram assets dos testes, para o teste de migração.
    sourceSets["test"].assets.srcDir("$projectDir/schemas")
```

`ChartOffsetDaoTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChartOffsetDaoTest {
    private lateinit var db: RiffDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RiffDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun chart(id: String, scanId: Long = 1) =
        ChartEntity(id, "Música $id", "Banda", null, null, null, "content://$id", "CHART", scanId)

    @Test
    fun savesAndReadsTheOffsetOfAChart() = runTest {
        db.chartDao().replace(chart("a"), emptyList())

        db.chartOffsetDao().upsert(ChartOffsetEntity("a", -30))
        db.chartOffsetDao().upsert(ChartOffsetEntity("a", 20))

        assertEquals(20, db.chartOffsetDao().get("a"))
        assertNull(db.chartOffsetDao().get("b"))
    }

    @Test
    fun removingAChartRemovesItsOffset() = runTest {
        db.chartDao().replace(chart("a", scanId = 1), emptyList())
        db.chartOffsetDao().upsert(ChartOffsetEntity("a", 20))

        db.chartDao().deleteNotSeenIn(2)

        assertNull(db.chartOffsetDao().get("a"))
    }

    @Test
    fun reimportingAChartKeepsItsOffset() = runTest {
        db.chartDao().replace(chart("a", scanId = 1), emptyList())
        db.chartOffsetDao().upsert(ChartOffsetEntity("a", 20))

        db.chartDao().replace(chart("a", scanId = 2), emptyList())
        db.chartDao().deleteNotSeenIn(2)

        assertEquals(20, db.chartOffsetDao().get("a"))
    }
}
```

`MigrationTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), RiffDatabase::class.java)

    @Test
    fun version1BecomesVersion2KeepingTheChartsAndAddingOffsets() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO charts (id, title, artist, album, charter, durationMs, folderUri, format, lastSeenScanId) " +
                    "VALUES ('a', 'Música', 'Banda', NULL, NULL, NULL, 'content://a', 'CHART', 1)",
            )
        }

        helper.runMigrationsAndValidate(DB, 2, true).use { db ->
            db.execSQL("INSERT INTO chart_offsets (chartId, offsetMs) VALUES ('a', 15)")
            db.query("SELECT offsetMs FROM chart_offsets WHERE chartId = 'a'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(15, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :core:database:testDebugUnitTest --console=plain`
Expected: FAIL na compilação (`ChartOffsetEntity`, `chartOffsetDao` não existem).

- [ ] **Step 3: Implementar**

Em `Entities.kt`, acrescente:
```kotlin
/** Ajuste fino de sincronia de um mapa da biblioteca (ms somados ao relógio). Sai junto com o mapa. */
@Entity(
    tableName = "chart_offsets",
    foreignKeys = [ForeignKey(ChartEntity::class, ["id"], ["chartId"], onDelete = ForeignKey.CASCADE)],
)
data class ChartOffsetEntity(@PrimaryKey val chartId: String, val offsetMs: Int)
```

`ChartOffsetDao.kt`:
```kotlin
package com.rodrigomachado.riff.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ChartOffsetDao {
    @Query("SELECT offsetMs FROM chart_offsets WHERE chartId = :chartId")
    suspend fun get(chartId: String): Int?

    @Upsert
    suspend fun upsert(offset: ChartOffsetEntity)
}
```

`RiffDatabase.kt` inteiro:
```kotlin
package com.rodrigomachado.riff.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChartEntity::class,
        ChartDifficultyEntity::class,
        TrackMatchEntity::class,
        ScoreEntity::class,
        ChartOffsetEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class RiffDatabase : RoomDatabase() {
    abstract fun chartDao(): ChartDao

    abstract fun trackMatchDao(): TrackMatchDao

    abstract fun scoreDao(): ScoreDao

    abstract fun chartOffsetDao(): ChartOffsetDao
}
```

Em `DatabaseModule.kt`, acrescente:
```kotlin
    @Provides
    fun chartOffsetDao(database: RiffDatabase): ChartOffsetDao = database.chartOffsetDao()
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw detekt -PdetektAutoCorrect --console=plain; ./scripts/gw detekt :core:database:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`; `ChartOffsetDaoTest` 3/3; `MigrationTest` 1/1; `schemas/.../2.json` criado. Se o `MigrationTestHelper` não achar o `1.json` como asset, confira o caminho do `srcDir` (a pasta tem o nome completo da classe do banco) e registre no ledger.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(database): ajuste de sincronia por mapa (chart_offsets) e banco na versão 2" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Repositórios de ajuste por música

**Files:**
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/ChartOffsetRepository.kt`
- Modify: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/TrackMatchRepository.kt`, `DataModule.kt`
- Modify (fakes): `core/data/src/test/kotlin/com/rodrigomachado/riff/core/data/SpotifyNowPlayingRepositoryTest.kt`, `feature/library/src/test/kotlin/com/rodrigomachado/riff/feature/library/Fakes.kt`, `feature/nowplaying/src/test/kotlin/com/rodrigomachado/riff/feature/nowplaying/Fakes.kt`
- Test: `core/data/src/test/kotlin/com/rodrigomachado/riff/core/data/RoomChartOffsetRepositoryTest.kt`, `RoomTrackMatchRepositoryTest.kt`

**Interfaces:**
- Consumes: `ChartOffsetDao`, `ChartOffsetEntity` (Task 2).
- Produces:
  ```kotlin
  const val MAX_SYNC_OFFSET_MS = 500  // em com.rodrigomachado.riff.core.data
  interface ChartOffsetRepository { suspend fun get(chartId: String): Int; suspend fun save(chartId: String, offsetMs: Int) }
  // TrackMatchRepository ganha:
  suspend fun offsetFor(spotifyTrackUri: String, chartId: String): Int?
  suspend fun saveOffset(spotifyTrackUri: String, chartId: String, offsetMs: Int)
  ```
  Binding `ChartOffsetRepository → RoomChartOffsetRepository`.

- [ ] **Step 1: Escrever os testes que falham**

`RoomChartOffsetRepositoryTest.kt`:
```kotlin
package com.rodrigomachado.riff.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rodrigomachado.riff.core.database.ChartEntity
import com.rodrigomachado.riff.core.database.RiffDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomChartOffsetRepositoryTest {
    private lateinit var db: RiffDatabase
    private lateinit var offsets: RoomChartOffsetRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RiffDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        offsets = RoomChartOffsetRepository(db.chartOffsetDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aChartWithoutAdjustmentHasZero() = runTest {
        assertEquals(0, offsets.get("a"))
    }

    @Test
    fun theAdjustmentIsSavedWithinTheLimits() = runTest {
        db.chartDao().replace(ChartEntity("a", "M", "B", null, null, null, "content://a", "CHART", 1), emptyList())

        offsets.save("a", 30)
        assertEquals(30, offsets.get("a"))

        offsets.save("a", 900)
        assertEquals(MAX_SYNC_OFFSET_MS, offsets.get("a"))
    }
}
```

Em `RoomTrackMatchRepositoryTest.kt`, acrescente:
```kotlin
    @Test
    fun adjustingAnAutomaticMatchSavesItWithTheOffset() = runTest {
        val queen = chart("a", "Bohemian Rhapsody", "Queen")
        charts.saveScanned(queen, 1)
        assertEquals(null, matches.offsetFor("spotify:track:x", "a"))

        matches.saveOffset("spotify:track:x", "a", -20)

        assertEquals(-20, matches.offsetFor("spotify:track:x", "a"))
        assertEquals(MatchStatus.Matched(queen, -20), matches.matchFor(track("Bohemian Rhapsody", "Queen")))
    }

    @Test
    fun theOffsetOfASavedMatchIsUpdatedAndLimited() = runTest {
        charts.saveScanned(chart("a", "Música", "Banda"), 1)
        matches.save("spotify:track:x", "a", confidence = 1f, isManual = true)

        matches.saveOffset("spotify:track:x", "a", -800)

        assertEquals(-MAX_SYNC_OFFSET_MS, matches.offsetFor("spotify:track:x", "a"))
        assertEquals(true, db.trackMatchDao().get("spotify:track:x")?.isManual)
    }
```

Atualize os fakes de `TrackMatchRepository` para compilar, acrescentando a cada um:
```kotlin
    override suspend fun offsetFor(spotifyTrackUri: String, chartId: String): Int? = null

    override suspend fun saveOffset(spotifyTrackUri: String, chartId: String, offsetMs: Int) = Unit
```
(`FakeMatches` em `SpotifyNowPlayingRepositoryTest.kt`, `FakeTrackMatchRepository` nos `Fakes.kt` da biblioteca e de "Tocando agora").

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :core:data:testDebugUnitTest --console=plain`
Expected: FAIL na compilação (`RoomChartOffsetRepository`, `offsetFor`, `saveOffset`, `MAX_SYNC_OFFSET_MS`).

- [ ] **Step 3: Implementar**

`ChartOffsetRepository.kt`:
```kotlin
package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.database.ChartOffsetDao
import com.rodrigomachado.riff.core.database.ChartOffsetEntity
import javax.inject.Inject

/** Limite do ajuste fino de sincronia (e do atraso geral), em ms para cada lado. */
const val MAX_SYNC_OFFSET_MS = 500

/** Ajuste fino de sincronia por mapa da biblioteca. */
interface ChartOffsetRepository {
    /** 0 quando o mapa nunca foi ajustado. */
    suspend fun get(chartId: String): Int

    suspend fun save(chartId: String, offsetMs: Int)
}

class RoomChartOffsetRepository @Inject constructor(private val dao: ChartOffsetDao) : ChartOffsetRepository {
    override suspend fun get(chartId: String): Int = dao.get(chartId)?.limited() ?: 0

    override suspend fun save(chartId: String, offsetMs: Int) = dao.upsert(ChartOffsetEntity(chartId, offsetMs.limited()))
}

internal fun Int.limited(): Int = coerceIn(-MAX_SYNC_OFFSET_MS, MAX_SYNC_OFFSET_MS)
```

Em `TrackMatchRepository.kt`, na interface:
```kotlin
    /** Ajuste salvo desta faixa com este mapa; `null` se não houver casamento salvo com ele. */
    suspend fun offsetFor(spotifyTrackUri: String, chartId: String): Int?

    /** Grava o ajuste fino; um casamento automático passa a ficar salvo (com o mesmo mapa). */
    suspend fun saveOffset(spotifyTrackUri: String, chartId: String, offsetMs: Int)
```
E no `RoomTrackMatchRepository`:
```kotlin
    override suspend fun offsetFor(spotifyTrackUri: String, chartId: String): Int? =
        dao.get(spotifyTrackUri)?.takeIf { it.chartId == chartId }?.trackOffsetMs?.limited()

    override suspend fun saveOffset(spotifyTrackUri: String, chartId: String, offsetMs: Int) {
        val saved = dao.get(spotifyTrackUri)?.takeIf { it.chartId == chartId }
        val updated = saved?.copy(trackOffsetMs = offsetMs.limited())
            ?: TrackMatchEntity(spotifyTrackUri, chartId, confidence = 1f, isManual = false, trackOffsetMs = offsetMs.limited())
        dao.upsert(updated)
        version.update { it + 1 }
    }
```

Em `DataModule.kt` (`DataBindsModule`):
```kotlin
    @Binds
    abstract fun chartOffsetRepository(impl: RoomChartOffsetRepository): ChartOffsetRepository
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw detekt -PdetektAutoCorrect --console=plain; ./scripts/gw detekt :core:data:testDebugUnitTest :feature:library:testDebugUnitTest :feature:nowplaying:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`; `RoomChartOffsetRepositoryTest` 2/2; `RoomTrackMatchRepositoryTest` 8/8.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(data): ajuste fino salvo por mapa e por faixa do Spotify" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Ajuste fino no menu de pausa

**Files:**
- Modify: `feature/gameplay/src/main/kotlin/com/rodrigomachado/riff/feature/gameplay/GameplayViewModel.kt`, `GameplayScreen.kt`
- Modify: `feature/gameplay/src/main/res/values/strings.xml`, `values-en/strings.xml`
- Test: `feature/gameplay/src/test/kotlin/com/rodrigomachado/riff/feature/gameplay/Fakes.kt`, `GameplayViewModelTest.kt`, `GameStageBackTest.kt`

**Interfaces:**
- Consumes: `GameSession.adjustOffset` (Task 1); `ChartOffsetRepository`, `TrackMatchRepository.offsetFor/saveOffset`, `MAX_SYNC_OFFSET_MS` (Task 3).
- Produces: `GameplayUiState.Ready(..., songOffsetMs: Int = 0)`; `GameplayViewModel.adjustSongOffset(deltaMs: Int)`; construtor do VM com `chartOffsets: ChartOffsetRepository, trackMatches: TrackMatchRepository` depois de `scores`; `GameStage(..., onAdjustOffset: (Int) -> Unit = {})`; strings `gameplay_sync` ("Sincronia desta música: %1$s ms"), `gameplay_sync_earlier` ("−10 ms"), `gameplay_sync_later` ("+10 ms").

- [ ] **Step 1: Escrever os testes que falham**

Em `Fakes.kt` do gameplay, acrescente:
```kotlin
class FakeChartOffsetRepository : com.rodrigomachado.riff.core.data.ChartOffsetRepository {
    val offsets = mutableMapOf<String, Int>()

    override suspend fun get(chartId: String): Int = offsets[chartId] ?: 0

    override suspend fun save(chartId: String, offsetMs: Int) {
        offsets[chartId] = offsetMs
    }
}

class FakeTrackMatchRepository : com.rodrigomachado.riff.core.data.TrackMatchRepository {
    val offsets = mutableMapOf<Pair<String, String>, Int>()
    override val changes = kotlinx.coroutines.flow.MutableStateFlow(0)

    override suspend fun matchFor(track: com.rodrigomachado.riff.core.model.PlaybackState) =
        com.rodrigomachado.riff.core.model.MatchStatus.NoMatch

    override suspend fun save(spotifyTrackUri: String, chartId: String, confidence: Float, isManual: Boolean) = Unit

    override suspend fun offsetFor(spotifyTrackUri: String, chartId: String): Int? = offsets[spotifyTrackUri to chartId]

    override suspend fun saveOffset(spotifyTrackUri: String, chartId: String, offsetMs: Int) {
        offsets[spotifyTrackUri to chartId] = offsetMs
    }
}
```

Em `GameplayViewModelTest.kt`:
- Acrescente `private val chartOffsets = FakeChartOffsetRepository()` e `private val trackMatches = FakeTrackMatchRepository()`.
- No helper `viewModel`, depois de `scores,` acrescente `chartOffsets,` e `trackMatches,`.
- Acrescente:
```kotlin
    @Test
    fun theLibraryChartAdjustmentIsUsedAndSaved() = runTest {
        chartOffsets.offsets["a"] = 20
        loader.result = { playable() }
        val viewModel = viewModel()
        val ready = viewModel.uiState.value as GameplayUiState.Ready
        assertEquals(20, ready.songOffsetMs)
        player.positionMs = 1_000
        time.now = GameSession.SYNC_INTERVAL_MS
        assertEquals(980L, ready.session.tick()) // 1000 − 40 (geral) + 20 (mapa)

        viewModel.pause()
        viewModel.adjustSongOffset(+10)

        assertEquals(30, chartOffsets.offsets["a"])
        assertEquals(30, (viewModel.uiState.value as GameplayUiState.Ready).songOffsetMs)
        assertEquals(990L, ready.session.tick())
    }

    @Test
    fun spotifyOffsetIsReadFromTheSavedMatchNotFromTheRoute() = runTest {
        trackMatches.offsets["spotify:track:x" to "a"] = -30
        loader.result = { playable() }

        val viewModel = viewModel(spotifyTrackUri = "spotify:track:x", trackOffsetMs = 25)
        viewModel.adjustSongOffset(-10)

        assertEquals(-40, (viewModel.uiState.value as GameplayUiState.Ready).songOffsetMs)
        assertEquals(-40, trackMatches.offsets["spotify:track:x" to "a"])
    }

    @Test
    fun songOffsetStopsAtTheLimits() = runTest {
        chartOffsets.offsets["a"] = 495
        loader.result = { playable() }
        val viewModel = viewModel()
        val ready = viewModel.uiState.value as GameplayUiState.Ready
        player.positionMs = 1_000
        time.now = GameSession.SYNC_INTERVAL_MS
        val before = ready.session.tick()

        viewModel.adjustSongOffset(+10)
        viewModel.adjustSongOffset(+10)

        assertEquals(500, chartOffsets.offsets["a"])
        assertEquals(before + 5, ready.session.tick())
    }
```

Em `GameStageBackTest.kt` (já monta o `GameStage` pausado), acrescente:
```kotlin
    @Test
    fun thePauseMenuAdjustsTheSyncOfThisSong() {
        val adjustments = mutableListOf<Int>()
        val session = GameSession(listOf(note(60_000)), FakeSongPlayer(), FakeSfxPlayer(), FakeTime(), audioOffsetMs = 0)
        composeRule.setContent {
            RiffTheme {
                GameStage(
                    GameplayUiState.Ready("Música", "Banda", Difficulty.EXPERT, session, Settings.DEFAULT, isPaused = true, songOffsetMs = 20),
                    reducedMotion = true,
                    onPause = {},
                    onResume = {},
                    onExit = {},
                    onSessionFinished = {},
                    onAdjustOffset = { adjustments += it },
                    animate = false,
                )
            }
        }

        composeRule.onNodeWithText("Sincronia desta música: +20 ms").assertExists()
        composeRule.onNodeWithText("−10 ms").performClick()
        composeRule.onNodeWithText("+10 ms").performClick()

        assertEquals(listOf(-10, 10), adjustments)
    }
```
(Confira os imports e o `@Config(qualifiers = "pt-rBR")` do arquivo; acrescente-os se faltarem.)

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :feature:gameplay:testDebugUnitTest --console=plain`
Expected: FAIL na compilação (construtor do VM, `songOffsetMs`, `adjustSongOffset`, `onAdjustOffset`).

- [ ] **Step 3: Implementar**

`GameplayViewModel.kt`:
- `Ready` ganha `val songOffsetMs: Int = 0,` depois de `withoutSeparateGuitar`.
- Construtor: depois de `private val scores: ScoreRepository,` acrescente `private val chartOffsets: ChartOffsetRepository,` e `private val trackMatches: TrackMatchRepository,` (imports `com.rodrigomachado.riff.core.data.ChartOffsetRepository`, `...TrackMatchRepository`, `...MAX_SYNC_OFFSET_MS`).
- Campo: `private var songOffsetMs = 0`.
- Em `load()`, troque `val offset = settings.audioOffsetMs.toLong() + trackOffsetMs` por:
```kotlin
        songOffsetMs = spotifyTrackUri?.let { trackMatches.offsetFor(it, chartId) ?: trackOffsetMs }
            ?: chartOffsets.get(chartId)
        val offset = settings.audioOffsetMs.toLong() + songOffsetMs
```
  e passe `songOffsetMs = songOffsetMs` ao criar o `Ready` (argumento nomeado depois de `withoutSeparateGuitar`).
- Novo método:
```kotlin
    /** Ajuste fino na pausa: vale ao continuar e fica salvo para esta música (Spotify) ou este mapa. */
    fun adjustSongOffset(deltaMs: Int) {
        val ready = state.value as? GameplayUiState.Ready ?: return
        val updated = (songOffsetMs + deltaMs).coerceIn(-MAX_SYNC_OFFSET_MS, MAX_SYNC_OFFSET_MS)
        if (updated == songOffsetMs) return
        ready.session.adjustOffset((updated - songOffsetMs).toLong())
        songOffsetMs = updated
        state.update { ready.copy(songOffsetMs = updated) }
        viewModelScope.launch {
            val uri = spotifyTrackUri
            if (uri != null) trackMatches.saveOffset(uri, chartId, updated) else chartOffsets.save(chartId, updated)
        }
    }
```

`GameplayScreen.kt`:
- `GameplayScreen` passa `onAdjustOffset = viewModel::adjustSongOffset` ao `GameStage`.
- `GameStage` ganha `onAdjustOffset: (Int) -> Unit = {},` depois de `onPlaybackChanged`, e troca `if (state.isPaused) PauseOverlay(onResume, onExit)` por `if (state.isPaused) PauseOverlay(state.songOffsetMs, onAdjustOffset, onResume, onExit)`.
- `PauseOverlay` inteiro:
```kotlin
@Composable
private fun PauseOverlay(songOffsetMs: Int, onAdjustOffset: (Int) -> Unit, onResume: () -> Unit, onExit: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(RiffColors.StageBlack.copy(alpha = SCRIM_ALPHA)),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PosterTitle(stringResource(R.string.gameplay_paused))
        PosterButton(stringResource(R.string.gameplay_resume), onClick = onResume)
        SyncAdjuster(songOffsetMs, onAdjustOffset)
        TextButton(onClick = onExit) { Text(stringResource(R.string.gameplay_exit), color = RiffColors.Paper) }
    }
}

/** Ajuste fino de sincronia desta música, de 10 em 10 ms. */
@Composable
private fun SyncAdjuster(songOffsetMs: Int, onAdjustOffset: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.gameplay_sync, signed(songOffsetMs)),
            style = MaterialTheme.typography.bodyLarge,
            color = RiffColors.Paper,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            TextButton(onClick = { onAdjustOffset(-SYNC_STEP_MS) }) {
                Text(stringResource(R.string.gameplay_sync_earlier), color = RiffColors.Paper)
            }
            TextButton(onClick = { onAdjustOffset(SYNC_STEP_MS) }) {
                Text(stringResource(R.string.gameplay_sync_later), color = RiffColors.Paper)
            }
        }
    }
}

private fun signed(value: Int): String = if (value > 0) "+$value" else value.toString()
```
- Constante privada no arquivo: `private const val SYNC_STEP_MS = 10`.

Strings (pt):
```xml
    <string name="gameplay_sync">Sincronia desta música: %1$s ms</string>
    <string name="gameplay_sync_earlier">−10 ms</string>
    <string name="gameplay_sync_later">+10 ms</string>
```
(en):
```xml
    <string name="gameplay_sync">Sync for this song: %1$s ms</string>
    <string name="gameplay_sync_earlier">−10 ms</string>
    <string name="gameplay_sync_later">+10 ms</string>
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw detekt -PdetektAutoCorrect --console=plain; ./scripts/gw detekt lintDebug :feature:gameplay:testDebugUnitTest :feature:gameplay:verifyRoborazziDebug :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`; testes novos passando; `Gameplay_stage.png` sem mudança (a partida não está pausada no screenshot).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(gameplay): ajuste fino de sincronia no menu de pausa, salvo por música" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Módulo `:feature:calibration` — metrônomo e cálculo do atraso

**Files:**
- Modify: `settings.gradle.kts` (`include(":feature:calibration")`)
- Create: `feature/calibration/build.gradle.kts`, `src/main/AndroidManifest.xml`, `src/test/resources/robolectric.properties` (`sdk=35`)
- Create: `feature/calibration/src/main/kotlin/com/rodrigomachado/riff/feature/calibration/CalibrationSession.kt`
- Create: `feature/calibration/src/main/kotlin/com/rodrigomachado/riff/feature/calibration/ClickTrack.kt`
- Test: `feature/calibration/src/test/kotlin/com/rodrigomachado/riff/feature/calibration/CalibrationSessionTest.kt`, `ClickTrackTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  sealed interface CalibrationResult { data class Measured(val latencyMs: Int) : CalibrationResult; data object Unstable : CalibrationResult }
  object CalibrationSession { const val LEAD_IN = 4; const val MEASURED = 20; const val MIN_VALID = 12; const val OUTLIER_MS = 250L
      fun result(clickTimesMs: List<Long>, tapTimesMs: List<Long>): CalibrationResult }
  object ClickTrack { const val INTERVAL_MS = 600; const val START_DELAY_MS = 500; val CLICK_COUNT: Int
      fun clickFrames(sampleRate: Int): LongArray; fun render(sampleRate: Int): FloatArray /* estéreo */ }
  class ClickTimeline(sampleRate: Int, clickFrames: LongArray) { fun observe(nowMs: Long, playedFrames: Long); fun clickTimesMs(): List<Long> }
  ```

- [ ] **Step 1: Módulo**

`feature/calibration/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.riff.android.feature)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.rodrigomachado.riff.feature.calibration"
}

dependencies {
    // A saída de áudio que informa quando cada som sai no alto-falante.
    implementation(project(":core:audio"))
}
```
Manifest: `<?xml version="1.0" encoding="utf-8"?>` + `<manifest />`. Copie `robolectric.properties` de `feature/gameplay/src/test/resources/`. Em `settings.gradle.kts`, `include(":feature:calibration")` depois de `include(":feature:settings")`.

- [ ] **Step 2: Escrever os testes que falham**

`CalibrationSessionTest.kt`:
```kotlin
package com.rodrigomachado.riff.feature.calibration

import org.junit.Assert.assertEquals
import org.junit.Test

class CalibrationSessionTest {
    private val clicks = List(CalibrationSession.LEAD_IN + CalibrationSession.MEASURED) { 1_000L + it * 600L }
    private val measured = clicks.drop(CalibrationSession.LEAD_IN)

    @Test
    fun tapsFortyMillisecondsLateMeasureForty() {
        val taps = measured.map { it + 40 }

        assertEquals(CalibrationResult.Measured(40), CalibrationSession.result(clicks, taps))
    }

    @Test
    fun tapsOutOfRhythmAreDiscarded() {
        val taps = measured.mapIndexed { i, click -> if (i < 3) click + 290 else click + 35 }

        assertEquals(CalibrationResult.Measured(35), CalibrationSession.result(clicks, taps))
    }

    @Test
    fun fewerThanTwelveGoodTapsIsUnstable() {
        val taps = measured.take(11).map { it + 40 }

        assertEquals(CalibrationResult.Unstable, CalibrationSession.result(clicks, taps))
    }

    @Test
    fun missedClicksDoNotBreakTheMeasurement() {
        val taps = measured.filterIndexed { i, _ -> i % 4 != 0 }.map { it + 50 }

        assertEquals(CalibrationResult.Measured(50), CalibrationSession.result(clicks, taps))
    }

    @Test
    fun twoTapsOnTheSameClickKeepTheClosest() {
        val taps = measured.map { it + 40 } + measured.take(5).map { it + 200 }

        assertEquals(CalibrationResult.Measured(40), CalibrationSession.result(clicks, taps.sorted()))
    }

    @Test
    fun tapsOnTheLeadInClicksDoNotCount() {
        val taps = clicks.take(CalibrationSession.LEAD_IN).map { it - 100 } + measured.map { it + 40 }

        assertEquals(CalibrationResult.Measured(40), CalibrationSession.result(clicks, taps))
    }

    @Test
    fun tappingBeforeTheClickGivesANegativeLatency() {
        val taps = measured.map { it - 15 }

        assertEquals(CalibrationResult.Measured(-15), CalibrationSession.result(clicks, taps))
    }
}
```

`ClickTrackTest.kt`:
```kotlin
package com.rodrigomachado.riff.feature.calibration

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClickTrackTest {
    @Test
    fun twentyFourClicksSixHundredMillisecondsApartAfterAShortPause() {
        val frames = ClickTrack.clickFrames(sampleRate = 1_000)

        assertEquals(24, frames.size)
        assertEquals(500L, frames[0])
        assertEquals(1_100L, frames[1])
        assertEquals(500L + 23 * 600L, frames.last())
    }

    @Test
    fun theRenderedTrackHasSoundOnlyAtTheClicks() {
        val rate = 44_100
        val pcm = ClickTrack.render(rate)
        val first = ClickTrack.clickFrames(rate)[0].toInt()

        assertEquals(0f, pcm[(first - 10) * 2], 0f)
        assertTrue((0 until 200).any { kotlin.math.abs(pcm[(first + it) * 2]) > 0.1f })
        assertTrue(pcm.all { it in -1f..1f })
    }

    @Test
    fun clickInstantsComeFromWhereTheOutputWas() {
        val timeline = ClickTimeline(sampleRate = 1_000, clickFrames = longArrayOf(500, 1_100))

        timeline.observe(nowMs = 10_000, playedFrames = 200) // o clique 0 sai em 10.300
        timeline.observe(nowMs = 10_400, playedFrames = 600) // o clique 1 sai em 10.900
        timeline.observe(nowMs = 10_402, playedFrames = 600) // saída parada (buffer): leitura ignorada

        assertArrayEquals(longArrayOf(10_300, 10_900), timeline.clickTimesMs().toLongArray())
    }

    @Test
    fun withoutObservationsThereAreNoInstants() {
        assertEquals(emptyList<Long>(), ClickTimeline(1_000, longArrayOf(500)).clickTimesMs())
    }
}
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `./scripts/gw :feature:calibration:testDebugUnitTest --console=plain`
Expected: FAIL na compilação (`CalibrationSession`, `ClickTrack`, `ClickTimeline`).

- [ ] **Step 4: Implementar**

`CalibrationSession.kt`:
```kotlin
package com.rodrigomachado.riff.feature.calibration

import kotlin.math.abs
import kotlin.math.roundToInt

sealed interface CalibrationResult {
    /** Atraso médio do toque em relação ao clique (positivo: o jogador toca depois do clique). */
    data class Measured(val latencyMs: Int) : CalibrationResult

    data object Unstable : CalibrationResult
}

/** Compara os instantes dos cliques (no alto-falante) com os toques do jogador. */
object CalibrationSession {
    const val LEAD_IN = 4
    const val MEASURED = 20
    const val MIN_VALID = 12
    const val OUTLIER_MS = 250L

    fun result(clickTimesMs: List<Long>, tapTimesMs: List<Long>): CalibrationResult {
        if (clickTimesMs.isEmpty()) return CalibrationResult.Unstable
        // Cada clique fica só com o toque mais próximo; toques nos cliques de entrada não contam.
        val best = mutableMapOf<Int, Long>()
        for (tap in tapTimesMs) {
            val click = clickTimesMs.indices.minBy { abs(tap - clickTimesMs[it]) }
            if (click < LEAD_IN) continue
            val diff = tap - clickTimesMs[click]
            if (best[click]?.let { abs(it) <= abs(diff) } != true) best[click] = diff
        }
        val diffs = best.values.sorted()
        if (diffs.size < MIN_VALID) return CalibrationResult.Unstable
        val center = median(diffs)
        val kept = diffs.filter { abs(it - center) <= OUTLIER_MS }
        return if (kept.size < MIN_VALID) CalibrationResult.Unstable else CalibrationResult.Measured(median(kept).roundToInt())
    }

    private fun median(sorted: List<Long>): Double {
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle].toDouble() else (sorted[middle - 1] + sorted[middle]) / 2.0
    }
}
```
Atenção ao teste `tapsOutOfRhythmAreDiscarded`: com 3 toques a +290 e 17 a +35, a mediana é 35 e os três ficam a 255 ms dela (> 250), então são descartados.

`ClickTrack.kt`:
```kotlin
package com.rodrigomachado.riff.feature.calibration

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToLong
import kotlin.math.sin

/** Metrônomo da calibração: 4 cliques de entrada e 20 medidos, a 100 BPM, sintetizados aqui. */
object ClickTrack {
    const val INTERVAL_MS = 600
    const val START_DELAY_MS = 500
    val CLICK_COUNT = CalibrationSession.LEAD_IN + CalibrationSession.MEASURED
    private const val CLICK_MS = 20
    private const val CLICK_HZ = 1_500.0
    private const val DECAY_MS = 4.0
    private const val LEVEL = 0.6f
    private const val TAIL_MS = 500

    /** Quadro (desde o início da saída) em que cada clique começa. */
    fun clickFrames(sampleRate: Int): LongArray = LongArray(CLICK_COUNT) {
        ((START_DELAY_MS + it * INTERVAL_MS) * sampleRate / 1_000.0).roundToLong()
    }

    /** O metrônomo inteiro em PCM float estéreo intercalado. */
    fun render(sampleRate: Int): FloatArray {
        val frames = clickFrames(sampleRate)
        val total = (frames.last() + (TAIL_MS * sampleRate / 1_000)).toInt()
        val pcm = FloatArray(total * 2)
        val length = CLICK_MS * sampleRate / 1_000
        for (start in frames) {
            for (i in 0 until length) {
                val t = i.toDouble() / sampleRate
                val sample = (sin(2 * PI * CLICK_HZ * t) * exp(-t * 1_000 / DECAY_MS)).toFloat() * LEVEL
                val frame = (start + i).toInt()
                pcm[frame * 2] = sample
                pcm[frame * 2 + 1] = sample
            }
        }
        return pcm
    }
}

/**
 * Converte os quadros dos cliques em instantes do relógio do sistema, a partir de leituras de "agora, a saída
 * já tocou N quadros". Cada leitura com a saída andando dá uma estimativa; o instante é a média delas.
 */
class ClickTimeline(private val sampleRate: Int, private val clickFrames: LongArray) {
    private val sums = DoubleArray(clickFrames.size)
    private var samples = 0
    private var lastPlayed = -1L

    fun observe(nowMs: Long, playedFrames: Long) {
        // Saída parada (começo, buffer cheio): a leitura não diz nada novo sobre o relógio.
        if (playedFrames <= 0 || playedFrames == lastPlayed) return
        lastPlayed = playedFrames
        for (i in clickFrames.indices) sums[i] += nowMs + (clickFrames[i] - playedFrames) * 1_000.0 / sampleRate
        samples++
    }

    fun clickTimesMs(): List<Long> = if (samples == 0) emptyList() else sums.map { (it / samples).roundToLong() }
}
```

- [ ] **Step 5: Rodar e ver passar**

Run: `./scripts/gw detekt -PdetektAutoCorrect --console=plain; ./scripts/gw detekt :feature:calibration:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`; `CalibrationSessionTest` 7/7; `ClickTrackTest` 4/4.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(calibration): metrônomo e cálculo do atraso" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Medição de verdade e estado da tela

**Files:**
- Create: `feature/calibration/src/main/kotlin/com/rodrigomachado/riff/feature/calibration/ClickTrackPlayer.kt`
- Create: `feature/calibration/src/main/kotlin/com/rodrigomachado/riff/feature/calibration/CalibrationViewModel.kt`
- Create: `feature/calibration/src/main/kotlin/com/rodrigomachado/riff/feature/calibration/CalibrationModule.kt`
- Modify: `core/audio/src/main/kotlin/com/rodrigomachado/riff/core/audio/AudioModule.kt`
- Test: `feature/calibration/src/test/kotlin/com/rodrigomachado/riff/feature/calibration/CalibrationViewModelTest.kt`

**Interfaces:**
- Consumes: `CalibrationSession`, `ClickTrack`, `ClickTimeline` (Task 5); `AudioSink`, `AudioSinkFactory`, `AudioTrackSink` (`:core:audio`); `SettingsRepository.update` e `MAX_SYNC_OFFSET_MS` (`:core:data`).
- Produces:
  ```kotlin
  fun interface ClickTrackPlayer { /** Toca o metrônomo e devolve o instante de cada clique; cancelável. */ suspend fun play(): List<Long> }
  sealed interface CalibrationUiState { Instructions; Measuring(taps: Int); Result(latencyMs: Int); Unstable; AudioFailed }
  class CalibrationViewModel(settings: SettingsRepository, player: ClickTrackPlayer) { val uiState; fun start(); fun onTap(uptimeMs: Long); fun nudge(deltaMs: Int); fun save(onSaved: () -> Unit); fun cancel() }
  ```
  Binding Hilt `AudioSinkFactory` em `AudioModule` e `ClickTrackPlayer → AudioClickTrackPlayer` em `CalibrationModule`.

- [ ] **Step 1: Escrever o teste que falha**

`CalibrationViewModelTest.kt`:
```kotlin
package com.rodrigomachado.riff.feature.calibration

import com.rodrigomachado.riff.core.data.SettingsRepository
import com.rodrigomachado.riff.core.model.Settings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalibrationViewModelTest {
    private class FakeSettings : SettingsRepository {
        override val settings = MutableStateFlow(Settings.DEFAULT)

        override suspend fun update(transform: (Settings) -> Settings) {
            settings.value = transform(settings.value)
        }
    }

    private val settings = FakeSettings()
    private val clicks = List(ClickTrack.CLICK_COUNT) { 1_000L + it * 600L }
    private var finish = CompletableDeferred<List<Long>>()
    private var plays = 0
    private val player = ClickTrackPlayer {
        plays++
        finish.await()
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun tapAll(viewModel: CalibrationViewModel, lateMs: Long) =
        clicks.drop(CalibrationSession.LEAD_IN).forEach { viewModel.onTap(it + lateMs) }

    @Test
    fun aMeasurementShowsTheLatencyAndSavingStoresItAsANegativeOffset() = runTest {
        val viewModel = CalibrationViewModel(settings, player)
        assertEquals(CalibrationUiState.Instructions, viewModel.uiState.value)

        viewModel.start()
        tapAll(viewModel, lateMs = 42)
        assertEquals(CalibrationUiState.Measuring(taps = 20), viewModel.uiState.value)
        finish.complete(clicks)

        assertEquals(CalibrationUiState.Result(42), viewModel.uiState.value)
        viewModel.nudge(+5)
        viewModel.nudge(+5)
        assertEquals(CalibrationUiState.Result(52), viewModel.uiState.value)
        var saved = false
        viewModel.save { saved = true }
        assertEquals(-52, settings.settings.value.audioOffsetMs)
        assertTrue(saved)
    }

    @Test
    fun anUnsteadyMeasurementAsksToRepeat() = runTest {
        val viewModel = CalibrationViewModel(settings, player)

        viewModel.start()
        clicks.drop(CalibrationSession.LEAD_IN).take(5).forEach { viewModel.onTap(it + 40) }
        finish.complete(clicks)

        assertEquals(CalibrationUiState.Unstable, viewModel.uiState.value)
        assertEquals(Settings.DEFAULT.audioOffsetMs, settings.settings.value.audioOffsetMs)
    }

    @Test
    fun theSavedValueStaysWithinTheLimits() = runTest {
        val viewModel = CalibrationViewModel(settings, player)
        viewModel.start()
        tapAll(viewModel, lateMs = 498)
        finish.complete(clicks)

        viewModel.nudge(+5)
        viewModel.save {}

        assertEquals(-500, settings.settings.value.audioOffsetMs)
    }

    @Test
    fun cancellingStopsTheMeasurementAndGoesBackToTheStart() = runTest {
        val viewModel = CalibrationViewModel(settings, player)
        viewModel.start()
        viewModel.onTap(3_000)

        viewModel.cancel()
        finish.complete(clicks) // o metrônomo já foi cancelado: nada acontece

        assertEquals(CalibrationUiState.Instructions, viewModel.uiState.value)
        finish = CompletableDeferred()
        viewModel.start()
        assertEquals(CalibrationUiState.Measuring(taps = 0), viewModel.uiState.value)
        assertEquals(2, plays)
    }

    @Test
    fun anOutputThatDoesNotOpenIsExplained() = runTest {
        val viewModel = CalibrationViewModel(settings) { throw java.io.IOException("saída indisponível") }

        viewModel.start()

        assertEquals(CalibrationUiState.AudioFailed, viewModel.uiState.value)
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :feature:calibration:testDebugUnitTest --console=plain`
Expected: FAIL na compilação (`ClickTrackPlayer`, `CalibrationViewModel`, `CalibrationUiState`).

- [ ] **Step 3: Implementar**

`ClickTrackPlayer.kt`:
```kotlin
package com.rodrigomachado.riff.feature.calibration

import android.os.SystemClock
import com.rodrigomachado.riff.core.audio.AudioSinkFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Toca o metrônomo e devolve o instante (relógio `uptimeMillis`) em que cada clique saiu no alto-falante. */
fun interface ClickTrackPlayer {
    suspend fun play(): List<Long>
}

class AudioClickTrackPlayer @Inject constructor(private val sinks: AudioSinkFactory) : ClickTrackPlayer {
    override suspend fun play(): List<Long> = withContext(Dispatchers.IO) {
        val pcm = ClickTrack.render(SAMPLE_RATE)
        val timeline = ClickTimeline(SAMPLE_RATE, ClickTrack.clickFrames(SAMPLE_RATE))
        val sink = sinks.create(SAMPLE_RATE)
        try {
            sink.start()
            coroutineScope {
                val watcher = launch {
                    while (isActive) {
                        timeline.observe(SystemClock.uptimeMillis(), sink.playedFrames)
                        delay(OBSERVE_EVERY_MS)
                    }
                }
                var offset = 0
                val total = pcm.size / 2
                while (offset < total && isActive) {
                    val count = minOf(CHUNK_FRAMES, total - offset)
                    offset += maxOf(0, sink.write(pcm, offset, count))
                }
                while (isActive && sink.playedFrames < total) delay(OBSERVE_EVERY_MS)
                watcher.cancel()
            }
            timeline.clickTimesMs()
        } finally {
            sink.stop()
            sink.release()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val CHUNK_FRAMES = 1_024
        const val OBSERVE_EVERY_MS = 20L
    }
}
```

`CalibrationViewModel.kt`:
```kotlin
package com.rodrigomachado.riff.feature.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rodrigomachado.riff.core.data.MAX_SYNC_OFFSET_MS
import com.rodrigomachado.riff.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CalibrationUiState {
    data object Instructions : CalibrationUiState

    data class Measuring(val taps: Int) : CalibrationUiState

    data class Result(val latencyMs: Int) : CalibrationUiState

    data object Unstable : CalibrationUiState

    data object AudioFailed : CalibrationUiState
}

@HiltViewModel
class CalibrationViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val player: ClickTrackPlayer,
) : ViewModel() {
    private val state = MutableStateFlow<CalibrationUiState>(CalibrationUiState.Instructions)
    val uiState: StateFlow<CalibrationUiState> = state.asStateFlow()

    private val taps = mutableListOf<Long>()
    private var measuring: Job? = null

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // qualquer falha da saída vira a mensagem na tela
    fun start() {
        measuring?.cancel()
        taps.clear()
        state.value = CalibrationUiState.Measuring(taps = 0)
        measuring = viewModelScope.launch {
            state.value = try {
                when (val result = CalibrationSession.result(player.play(), taps.toList())) {
                    is CalibrationResult.Measured -> CalibrationUiState.Result(result.latencyMs.limited())
                    CalibrationResult.Unstable -> CalibrationUiState.Unstable
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CalibrationUiState.AudioFailed
            }
        }
    }

    /** Instante do toque no relógio `uptimeMillis` (o do evento de toque). */
    fun onTap(uptimeMs: Long) {
        val current = state.value as? CalibrationUiState.Measuring ?: return
        taps += uptimeMs
        state.value = current.copy(taps = taps.size)
    }

    /** Retoque à mão antes de salvar. */
    fun nudge(deltaMs: Int) {
        val current = state.value as? CalibrationUiState.Result ?: return
        state.value = CalibrationUiState.Result((current.latencyMs + deltaMs).limited())
    }

    fun save(onSaved: () -> Unit) {
        val current = state.value as? CalibrationUiState.Result ?: return
        viewModelScope.launch {
            settings.update { it.copy(audioOffsetMs = -current.latencyMs) }
            onSaved()
        }
    }

    /** O app saiu da tela no meio da medição: para o metrônomo e volta ao começo. */
    fun cancel() {
        if (state.value !is CalibrationUiState.Measuring) return
        measuring?.cancel()
        measuring = null
        taps.clear()
        state.value = CalibrationUiState.Instructions
    }

    private fun Int.limited(): Int = coerceIn(-MAX_SYNC_OFFSET_MS, MAX_SYNC_OFFSET_MS)
}
```

`CalibrationModule.kt`:
```kotlin
package com.rodrigomachado.riff.feature.calibration

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
abstract class CalibrationModule {
    @Binds
    abstract fun clickTrackPlayer(impl: AudioClickTrackPlayer): ClickTrackPlayer
}
```

Em `AudioModule.kt`, acrescente:
```kotlin
    @Provides
    fun audioSinks(): AudioSinkFactory = AudioSinkFactory(::AudioTrackSink)
```
e use `audioSinks()` no lugar de `::AudioTrackSink` no `stemPlayers` se preferir (opcional; não muda comportamento).

- [ ] **Step 4: Rodar e ver passar**

Run: `./scripts/gw detekt -PdetektAutoCorrect --console=plain; ./scripts/gw detekt :feature:calibration:testDebugUnitTest :core:audio:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`; `CalibrationViewModelTest` 5/5.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(calibration): medição com o metrônomo na saída de áudio e estado da tela" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Tela de calibração, linha nas Configurações e navegação

**Files:**
- Create: `feature/calibration/src/main/kotlin/com/rodrigomachado/riff/feature/calibration/CalibrationRoute.kt`, `CalibrationScreen.kt`
- Create: `feature/calibration/src/main/res/values/strings.xml`, `values-en/strings.xml`
- Modify: `feature/settings/src/main/kotlin/com/rodrigomachado/riff/feature/settings/SettingsScreen.kt`, `res/values*/strings.xml`
- Modify: `app/build.gradle.kts`, `app/src/main/kotlin/com/rodrigomachado/riff/RiffNavHost.kt`
- Test: `feature/calibration/src/test/kotlin/com/rodrigomachado/riff/feature/calibration/CalibrationContentTest.kt`, `CalibrationScreenshotTest.kt`
- Test: `feature/settings/src/test/kotlin/com/rodrigomachado/riff/feature/settings/SettingsContentTest.kt`, `SettingsScreenshotTest.kt`

**Interfaces:**
- Consumes: `CalibrationViewModel`, `CalibrationUiState` (Task 6).
- Produces: `@Serializable data object CalibrationRoute`; `CalibrationScreen(onDone: () -> Unit, modifier, viewModel)`; `CalibrationContent(state, onStart, onTap: (Long) -> Unit, onNudge: (Int) -> Unit, onSave, onDone, modifier)`; `SettingsContent(..., onCalibrate: () -> Unit, ...)` (obrigatório, depois de `onPreviewMiss`); `SettingsScreen(onBack, onCalibrate, ...)`.

- [ ] **Step 1: Escrever os testes que falham**

`CalibrationContentTest.kt`:
```kotlin
package com.rodrigomachado.riff.feature.calibration

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class CalibrationContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(state: CalibrationUiState) = composeRule.setContent {
        RiffTheme {
            CalibrationContent(
                state = state,
                onStart = { events += "start" },
                onTap = { events += "tap" },
                onNudge = { events += "nudge:$it" },
                onSave = { events += "save" },
                onDone = { events += "done" },
            )
        }
    }

    @Test
    fun theInstructionsExplainWhatToDo() {
        show(CalibrationUiState.Instructions)

        composeRule.onNodeWithText("Toque na tela junto com cada clique.").assertExists()
        composeRule.onNodeWithText("Começar").performClick()

        assertEquals(listOf("start"), events)
    }

    @Test
    fun whileMeasuringTheWholeScreenTakesTaps() {
        show(CalibrationUiState.Measuring(taps = 3))

        composeRule.onNodeWithText("3 toques").assertExists()
        composeRule.onNodeWithTag(CalibrationTestTags.TAP_AREA).performTouchInput { down(center) }

        assertEquals(listOf("tap"), events)
    }

    @Test
    fun theResultCanBeNudgedSavedOrRepeated() {
        show(CalibrationUiState.Result(42))

        composeRule.onNodeWithText("Atraso medido: 42 ms").assertExists()
        composeRule.onNodeWithText("−5 ms").performClick()
        composeRule.onNodeWithText("+5 ms").performClick()
        composeRule.onNodeWithText("Salvar").performClick()
        composeRule.onNodeWithText("Repetir").performClick()

        assertEquals(listOf("nudge:-5", "nudge:5", "save", "start"), events)
    }

    @Test
    fun anUnsteadyMeasurementAsksToTryAgain() {
        show(CalibrationUiState.Unstable)

        composeRule.onNodeWithText("Medição instável. Tente de novo, tocando junto com o clique.").assertExists()
        composeRule.onNodeWithText("Repetir").performClick()

        assertEquals(listOf("start"), events)
    }

    @Test
    fun anOutputThatDoesNotOpenIsExplained() {
        show(CalibrationUiState.AudioFailed)

        composeRule.onNodeWithText("Não deu para tocar o metrônomo.").assertExists()
    }
}
```

`CalibrationScreenshotTest.kt`:
```kotlin
package com.rodrigomachado.riff.feature.calibration

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.rodrigomachado.riff.core.ui.component.StageBackground
import com.rodrigomachado.riff.core.ui.theme.RiffTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rBR-w360dp-h640dp-xhdpi")
class CalibrationScreenshotTest {
    @Test
    fun instructions() = snapshot("Calibration_instructions", CalibrationUiState.Instructions)

    @Test
    fun result() = snapshot("Calibration_result", CalibrationUiState.Result(42))

    private fun snapshot(name: String, state: CalibrationUiState) {
        captureRoboImage("src/test/screenshots/$name.png") {
            RiffTheme {
                StageBackground(Modifier.fillMaxSize()) {
                    CalibrationContent(state, onStart = {}, onTap = {}, onNudge = {}, onSave = {}, onDone = {})
                }
            }
        }
    }
}
```

Em `SettingsContentTest.kt` e `SettingsScreenshotTest.kt`, passe `onCalibrate = {}` em toda chamada de `SettingsContent` (depois de `onPreviewMiss`) e acrescente a `SettingsContentTest`:
```kotlin
    @Test
    fun calibrationShowsTheCurrentDelayAndOpens() {
        var opened = false
        composeRule.setContent {
            RiffTheme {
                SettingsContent(
                    settings = Settings.DEFAULT.copy(audioOffsetMs = -42),
                    onMasterVolume = {},
                    onHitVolume = {},
                    onMissVolume = {},
                    onNoteSpeed = {},
                    onLeftHanded = {},
                    onPreviewHit = {},
                    onPreviewMiss = {},
                    onCalibrate = { opened = true },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Calibrar atraso do áudio").performScrollTo().performClick()

        composeRule.onNodeWithText("42 ms").assertExists()
        assertEquals(true, opened)
    }

    @Test
    fun withoutCalibrationItSaysSo() {
        show()

        composeRule.onNodeWithText("Não calibrado").performScrollTo().assertExists()
    }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./scripts/gw :feature:calibration:testDebugUnitTest :feature:settings:testDebugUnitTest --console=plain`
Expected: FAIL na compilação (`CalibrationContent`, `CalibrationTestTags`, `onCalibrate`).

- [ ] **Step 3: Implementar**

`CalibrationRoute.kt`:
```kotlin
package com.rodrigomachado.riff.feature.calibration

import kotlinx.serialization.Serializable

/** Calibração do atraso do áudio (metrônomo + toques). */
@Serializable
data object CalibrationRoute
```

`CalibrationScreen.kt`:
```kotlin
package com.rodrigomachado.riff.feature.calibration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rodrigomachado.riff.core.ui.component.PosterButton
import com.rodrigomachado.riff.core.ui.component.PosterTitle
import com.rodrigomachado.riff.core.ui.theme.RiffColors
import com.rodrigomachado.riff.core.ui.theme.Spacing

object CalibrationTestTags {
    const val TAP_AREA = "calibration_tap_area"
}

@Composable
fun CalibrationScreen(onDone: () -> Unit, modifier: Modifier = Modifier, viewModel: CalibrationViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.cancel() }
    CalibrationContent(
        state = state,
        onStart = viewModel::start,
        onTap = viewModel::onTap,
        onNudge = viewModel::nudge,
        onSave = { viewModel.save(onDone) },
        onDone = onDone,
        modifier = modifier,
    )
}

@Composable
fun CalibrationContent(
    state: CalibrationUiState,
    onStart: () -> Unit,
    onTap: (Long) -> Unit,
    onNudge: (Int) -> Unit,
    onSave: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(horizontal = Spacing.md)) {
        TextButton(onClick = onDone) { Text(stringResource(R.string.calibration_back), color = RiffColors.Paper) }
        PosterTitle(stringResource(R.string.calibration_title), style = MaterialTheme.typography.displaySmall)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (state) {
                CalibrationUiState.Instructions -> Message(
                    stringResource(R.string.calibration_instructions),
                    stringResource(R.string.calibration_start),
                    onStart,
                )
                is CalibrationUiState.Measuring -> TapArea(state.taps, onTap)
                is CalibrationUiState.Result -> ResultPanel(state.latencyMs, onNudge, onSave, onStart)
                CalibrationUiState.Unstable -> Message(
                    stringResource(R.string.calibration_unstable),
                    stringResource(R.string.calibration_repeat),
                    onStart,
                )
                CalibrationUiState.AudioFailed -> Message(
                    stringResource(R.string.calibration_audio_failed),
                    stringResource(R.string.calibration_retry),
                    onStart,
                )
            }
        }
    }
}

@Composable
private fun Message(text: String, action: String, onAction: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = RiffColors.Paper)
        PosterButton(action, onClick = onAction)
    }
}

/** A tela inteira recebe os toques; o instante é o do evento, no relógio `uptimeMillis`. */
@Composable
private fun TapArea(taps: Int, onTap: (Long) -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .testTag(CalibrationTestTags.TAP_AREA)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.filter { it.pressed && !it.previousPressed }.forEach {
                            onTap(it.uptimeMillis)
                            it.consume()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(stringResource(R.string.calibration_measuring), style = MaterialTheme.typography.titleMedium, color = RiffColors.Paper)
            Text(
                pluralStringResource(R.plurals.calibration_taps, taps, taps),
                style = MaterialTheme.typography.displaySmall,
                color = RiffColors.Paper,
            )
        }
    }
}

@Composable
private fun ResultPanel(latencyMs: Int, onNudge: (Int) -> Unit, onSave: () -> Unit, onRepeat: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Text(
            stringResource(R.string.calibration_result, latencyMs),
            style = MaterialTheme.typography.headlineSmall,
            color = RiffColors.Paper,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            TextButton(onClick = { onNudge(-NUDGE_MS) }) { Text(stringResource(R.string.calibration_less), color = RiffColors.Paper) }
            TextButton(onClick = { onNudge(NUDGE_MS) }) { Text(stringResource(R.string.calibration_more), color = RiffColors.Paper) }
        }
        PosterButton(stringResource(R.string.calibration_save), onClick = onSave)
        TextButton(onClick = onRepeat) { Text(stringResource(R.string.calibration_repeat), color = RiffColors.Paper) }
    }
}

private const val NUDGE_MS = 5
```

`feature/calibration/src/main/res/values/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="calibration_title">Calibração</string>
    <string name="calibration_back">Voltar</string>
    <string name="calibration_instructions">Toque na tela junto com cada clique.</string>
    <string name="calibration_start">Começar</string>
    <string name="calibration_measuring">Toque junto com cada clique</string>
    <plurals name="calibration_taps">
        <item quantity="one">%1$d toque</item>
        <item quantity="other">%1$d toques</item>
    </plurals>
    <string name="calibration_result">Atraso medido: %1$d ms</string>
    <string name="calibration_less">−5 ms</string>
    <string name="calibration_more">+5 ms</string>
    <string name="calibration_save">Salvar</string>
    <string name="calibration_repeat">Repetir</string>
    <string name="calibration_unstable">Medição instável. Tente de novo, tocando junto com o clique.</string>
    <string name="calibration_audio_failed">Não deu para tocar o metrônomo.</string>
    <string name="calibration_retry">Tentar de novo</string>
</resources>
```
`values-en/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="calibration_title">Calibration</string>
    <string name="calibration_back">Back</string>
    <string name="calibration_instructions">Tap the screen with each click.</string>
    <string name="calibration_start">Start</string>
    <string name="calibration_measuring">Tap with each click</string>
    <plurals name="calibration_taps">
        <item quantity="one">%1$d tap</item>
        <item quantity="other">%1$d taps</item>
    </plurals>
    <string name="calibration_result">Measured delay: %1$d ms</string>
    <string name="calibration_less">−5 ms</string>
    <string name="calibration_more">+5 ms</string>
    <string name="calibration_save">Save</string>
    <string name="calibration_repeat">Repeat</string>
    <string name="calibration_unstable">Unsteady measurement. Try again, tapping with the click.</string>
    <string name="calibration_audio_failed">Couldn\'t play the metronome.</string>
    <string name="calibration_retry">Try again</string>
</resources>
```

`SettingsScreen.kt`:
- `SettingsScreen` ganha `onCalibrate: () -> Unit` depois de `onBack` e passa `onCalibrate = onCalibrate` ao `SettingsContent`.
- `SettingsContent` ganha `onCalibrate: () -> Unit,` depois de `onPreviewMiss` e, depois de `LeftHandedRow(...)`:
```kotlin
        CalibrationRow(settings.audioOffsetMs, onCalibrate)
```
- Novo composable:
```kotlin
@Composable
private fun CalibrationRow(audioOffsetMs: Int, onCalibrate: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onCalibrate).padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.settings_calibrate),
            style = MaterialTheme.typography.titleMedium,
            color = RiffColors.Paper,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (audioOffsetMs == 0) stringResource(R.string.settings_not_calibrated) else "${-audioOffsetMs} ms",
            style = MaterialTheme.typography.titleMedium,
            color = RiffColors.PaperDim,
        )
    }
}
```
(import `androidx.compose.foundation.clickable`.)
- Strings: pt `settings_calibrate` "Calibrar atraso do áudio", `settings_not_calibrated` "Não calibrado"; en "Calibrate audio delay", "Not calibrated".

`app/build.gradle.kts`: `implementation(project(":feature:calibration"))`.
`RiffNavHost.kt`:
```kotlin
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onCalibrate = dropUnlessResumed { navController.navigate(CalibrationRoute) },
            )
        }
        composable<CalibrationRoute> {
            CalibrationScreen(onDone = dropUnlessResumed { navController.popBackStack() })
        }
```
(imports `com.rodrigomachado.riff.feature.calibration.CalibrationRoute` e `CalibrationScreen`.)

- [ ] **Step 4: Rodar, gravar screenshots e conferir**

Run: `./scripts/gw :feature:calibration:recordRoborazziDebug :feature:settings:recordRoborazziDebug --console=plain` e leia `Calibration_instructions.png`, `Calibration_result.png` e `Settings.png` (linha "Calibrar atraso do áudio" no fim, sem texto cortado).
Run: `./scripts/gw detekt -PdetektAutoCorrect --console=plain; ./scripts/gw detekt lintDebug :feature:calibration:testDebugUnitTest :feature:calibration:verifyRoborazziDebug :feature:settings:testDebugUnitTest :feature:settings:verifyRoborazziDebug :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(calibration): tela de calibração a partir das Configurações" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Teste no aparelho e documentação

**Files:**
- Modify: `docs/superpowers/specs/2026-10-05-riff-design.md`, `README.md`

- [ ] **Step 1: No aparelho (precisa do usuário)**

Instale o APK e peça ao usuário: Configurações → "Calibrar atraso do áudio" → Começar, tocar junto com os cliques, conferir o resultado, Salvar. Depois uma partida (biblioteca e Spotify), pausar, usar −10/+10 e continuar. Registre no ledger o atraso medido, se as notas ficaram mais certas e nenhum `FATAL EXCEPTION` no `logcat`.

- [ ] **Step 2: Documentação**

- Spec principal, §2 item 4, acrescente: "Implementado em 2026-10-09: metrônomo a 100 BPM (4 + 20 cliques), atraso = mediana dos toques válidos, salvo como `audioOffsetMs = −atraso`; ajuste fino de ±10 ms no menu de pausa, salvo por faixa do Spotify ou por mapa (`chart_offsets`). Detalhes em `2026-10-09-calibracao-design.md`."
- Spec principal, §11 passo 7: "Calibração e Configurações implementadas em 2026-10-09."
- README: na tabela de módulos, `| \`:feature:calibration\` | Calibração do atraso do áudio: metrônomo, toques e resultado |`; Status: `Fundação, identidade visual, biblioteca, gameplay, integração com o Spotify, configurações e calibração prontos. Próximo plano: polimento (Baseline Profile e README final).`

- [ ] **Step 3: Verificação e commit**

Run: `./scripts/gw detekt lintDebug test koverVerify :core:ui:verifyRoborazziDebug :feature:library:verifyRoborazziDebug :feature:gameplay:verifyRoborazziDebug :feature:results:verifyRoborazziDebug :feature:nowplaying:verifyRoborazziDebug :feature:settings:verifyRoborazziDebug :feature:calibration:verifyRoborazziDebug assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`.

```bash
git add -A
git commit -m "docs: calibração na especificação e no README" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
