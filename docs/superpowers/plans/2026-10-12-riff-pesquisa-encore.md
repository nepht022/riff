# Pesquisa e download do Encore (.sng) — Plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pesquisar mapas no Encore de dentro do app (Biblioteca e Tocando agora), baixá-los como `.sng` para "Baixados pelo Riff" e ler `.sng` como se fosse uma pasta de mapa.

**Architecture:** O `.sng` vira uma "pasta virtual": um `SngFolderSource` por cima do `SafFolderSource` lista os arquivos de dentro do pacote (URIs `…#sng` e `…#sng:<nome>`) e lê cada um desmascarado; importação e carregamento da partida quase não mudam. O áudio de dentro do pacote toca por uma `MediaDataSource`. Um módulo `:core:encore` fala com a API (pesquisa, começo do arquivo, download); `:core:data` grava na pasta por SAF e importa o pacote baixado; `:feature:search` é a tela.

**Tech Stack:** Kotlin, Compose, Hilt, Room (v3 → v4), `HttpURLConnection` + `org.json` (sem biblioteca nova), SAF (`DocumentsContract`), `MediaDataSource`, JUnit 5 em `:core:chart`, JUnit 4 + Robolectric no resto.

**Spec:** `docs/superpowers/specs/2026-10-12-pesquisa-encore-design.md`

## Global Constraints

- Gradle só por `./scripts/gw`; antes de suítes longas, `./scripts/gw --stop`.
- Detekt: MaxLineLength 120, LongMethod 60, ReturnCount 4, ComplexCondition 3, TooManyFunctions 11; autocorreção com `./scripts/gw detekt -PdetektAutoCorrect`.
- Suíte completa: `./scripts/gw test testDebugUnitTest detekt lint verifyRoborazziDebug :app:assembleDebug :app:assembleRelease --console=plain`, depois `scripts/conferir_r8_spotify.sh`.
- Commit só encadeado na verificação (`… && git commit`), mensagem terminando em `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Textos de interface em pt-BR com tradução en; identidade "Pôster de turnê" (`PosterButton`, `PosterTitle`, `RiffColors`, `Spacing`); sons do menu com `sound =`/`withMenuSound`.
- Nunca usar assets do Guitar Hero; rips de jogos oficiais escondidos na pesquisa.
- Download guarda o **pacote completo** (`.sng`) em "Baixados pelo Riff" dentro da pasta da biblioteca. Nunca extrair áudio do Spotify.
- API do Encore: `POST https://api.enchor.us/search` com `"source": "api"` (o servidor só aceita `website`, `bridge` ou `api`); arquivos em `https://files.enchor.us/<md5>[_novideo].sng`. Nada copiado do Bridge (GPL-3).
- README atualizado no mesmo branch.

## Review Focus

1. Reimportar a biblioteca depois de baixar: o mapa baixado continua marcado "Já na biblioteca" (o md5 do Encore não pode ser apagado pela importação) — teste no Task 5.
2. Subpasta ilegível durante a importação: os mapas `.sng` dela continuam na biblioteca (o `keepFolder` por prefixo precisa casar com o URI do pacote) — teste no Task 3.
3. Arquivo `.sng` truncado ou forjado (tamanhos que apontam para fora do arquivo, contagens gigantes): erro de mapa inválido, nunca `OutOfMemoryError` ou leitura fora do arquivo — teste no Task 1.
4. Sair da tela durante o download: o `.part` é apagado e nada entra na biblioteca — teste no Task 7.
5. Voltar 0,5 s com áudio de dentro do pacote (faixas separadas): o `MediaExtractor` reposiciona lendo de qualquer posição — teste da `SngMediaDataSource` em posições arbitrárias no Task 4.

---

### Task 1: Ler `.sng` (`:core:chart`)

**Files:**
- Create: `core/chart/src/main/kotlin/com/rodrigomachado/riff/core/chart/sng/RandomRead.kt`
- Create: `core/chart/src/main/kotlin/com/rodrigomachado/riff/core/chart/sng/SngPackage.kt`
- Create: `core/chart/src/main/kotlin/com/rodrigomachado/riff/core/chart/sng/SngRef.kt`
- Create: `core/chart/src/main/kotlin/com/rodrigomachado/riff/core/chart/ChartAudioFiles.kt`
- Create: `core/chart/src/testFixtures/kotlin/com/rodrigomachado/riff/core/chart/sng/SngWriter.kt`
- Modify: `core/chart/build.gradle.kts` (plugin `java-test-fixtures`)
- Test: `core/chart/src/test/kotlin/com/rodrigomachado/riff/core/chart/sng/SngPackageTest.kt`, `.../ChartAudioFilesTest.kt`, `.../sng/SngRefTest.kt`

**Interfaces:**
- Produces:
  - `interface RandomRead : Closeable { val size: Long; fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int }`
  - `class ByteArrayRandomRead(bytes: ByteArray) : RandomRead`
  - `fun interface RandomReadOpener { fun open(uri: String): RandomRead }` (IOException)
  - `class InvalidSngException(message: String) : IOException`
  - `class SngPackage private constructor(...) : Closeable` com `companion fun open(source: RandomRead): SngPackage`, `val metadata: Map<String, String>`, `val files: List<SngFile>`, `fun read(name: String, maxBytes: Long): ByteArray`, `fun open(name: String): RandomRead`, `fun songInfo(): SongInfo`
  - `data class SngFile(val name: String, val size: Long, val offset: Long)`
  - `object SngIndex { fun parse(head: ByteArray): Result }` com `sealed interface Result { data class Complete(val metadata: Map<String,String>, val files: List<SngFile>); data class NeedsMore(val bytes: Int) }`
  - `object SngRef { fun packageUri(fileUri: String): String; fun entryUri(fileUri: String, name: String): String; fun packageFileOf(uri: String): String?; fun entryOf(uri: String): Pair<String, String>?; fun isPackageName(name: String): Boolean }`
  - `object ChartAudioFiles { val EXTENSIONS: List<String>; val BACKING_NAMES: List<String>; fun hasSeparateGuitar(fileNames: Collection<String>): Boolean; fun find(names: Collection<String>, base: String): String? }`
  - testFixtures: `object SngWriter { fun write(metadata: Map<String, String>, files: Map<String, ByteArray>, mask: ByteArray = ByteArray(16) { (it * 37 + 11).toByte() }): ByteArray }`

- [ ] **Step 1: test fixtures e testes que falham**

`core/chart/build.gradle.kts` ganha `` `java-test-fixtures` `` em `plugins`.

`SngWriter.kt` (testFixtures):

```kotlin
package com.rodrigomachado.riff.core.chart.sng

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Monta um `.sng` (SngFileFormat, little-endian) para os testes. */
object SngWriter {
    fun write(
        metadata: Map<String, String>,
        files: Map<String, ByteArray>,
        mask: ByteArray = ByteArray(16) { (it * 37 + 11).toByte() },
    ): ByteArray {
        val meta = ByteArrayOutputStream()
        metadata.forEach { (key, value) ->
            val k = key.toByteArray(); val v = value.toByteArray()
            meta.write(int32(k.size)); meta.write(k); meta.write(int32(v.size)); meta.write(v)
        }
        val metaSection = uint64(meta.size().toLong() + 8) + uint64(metadata.size.toLong()) + meta.toByteArray()
        val names = files.keys.map { it.toByteArray() }
        val indexBody = names.sumOf { 1 + it.size + 16 }
        val headerSize = 6 + 4 + 16
        val indexSection = 16 + indexBody
        var offset = (headerSize + metaSection.size + indexSection + 8).toLong()
        val index = ByteArrayOutputStream()
        files.values.forEachIndexed { i, content ->
            index.write(names[i].size); index.write(names[i])
            index.write(uint64(content.size.toLong())); index.write(uint64(offset))
            offset += content.size
        }
        val data = ByteArrayOutputStream()
        files.values.forEach { content -> data.write(masked(content, mask)) }
        return "SNGPKG".toByteArray() + int32(1) + mask + metaSection +
            uint64(index.size().toLong() + 8) + uint64(files.size.toLong()) + index.toByteArray() +
            uint64(data.size().toLong()) + data.toByteArray()
    }

    fun masked(content: ByteArray, mask: ByteArray) =
        ByteArray(content.size) { i -> (content[i].toInt() xor mask[i % 16].toInt() xor (i and 0xFF)).toByte() }

    private fun int32(v: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
    private fun uint64(v: Long) = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array()
}
```

`SngPackageTest.kt`:

```kotlin
package com.rodrigomachado.riff.core.chart.sng

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SngPackageTest {
    private val notes = "[Song]\n{\n}\n".toByteArray()
    private val audio = ByteArray(1_000) { (it % 251).toByte() }
    private val bytes = SngWriter.write(
        mapOf("name" to "Música", "artist" to "Banda", "delay" to "120", "song_length" to "200000"),
        mapOf("notes.chart" to notes, "song.opus" to audio),
    )

    @Test
    fun readsTheIndexTheMetadataAndUnmasksEveryFile() {
        SngPackage.open(ByteArrayRandomRead(bytes)).use { sng ->
            assertEquals(listOf("notes.chart", "song.opus"), sng.files.map { it.name })
            assertArrayEquals(notes, sng.read("notes.chart", maxBytes = 1_000_000))
            assertArrayEquals(audio, sng.read("song.opus", maxBytes = 1_000_000))
            assertEquals("Música", sng.songInfo().title)
            assertEquals(120L, sng.songInfo().offsetMs)
        }
    }

    @Test
    fun aFileOpensForReadingAtAnyPosition() {
        SngPackage.open(ByteArrayRandomRead(bytes)).use { sng ->
            val file = sng.open("song.opus")
            val buffer = ByteArray(10)
            assertEquals(10, file.read(500, buffer, 0, 10))
            assertArrayEquals(audio.copyOfRange(500, 510), buffer)
            assertEquals(1_000L, file.size)
            assertEquals(-1, file.read(1_000, buffer, 0, 10))
        }
    }

    @Test
    fun aWrongHeaderIsAnInvalidPackage() {
        assertThrows<InvalidSngException> { SngPackage.open(ByteArrayRandomRead("NOTSNG0000".toByteArray())) }
    }

    @Test
    fun aTruncatedPackageIsInvalidNotACrash() {
        assertThrows<InvalidSngException> { SngPackage.open(ByteArrayRandomRead(bytes.copyOf(bytes.size - 10))) }
    }

    @Test
    fun hugeCountsAreRejectedBeforeAllocating() {
        val forged = bytes.copyOf().also { // metadataCount (posição 34) com um valor absurdo
            java.nio.ByteBuffer.wrap(it, 34, 8).order(java.nio.ByteOrder.LITTLE_ENDIAN).putLong(Long.MAX_VALUE)
        }
        assertThrows<InvalidSngException> { SngPackage.open(ByteArrayRandomRead(forged)) }
    }

    @Test
    fun aFileLargerThanTheLimitIsRefused() {
        SngPackage.open(ByteArrayRandomRead(bytes)).use { sng ->
            assertThrows<java.io.IOException> { sng.read("song.opus", maxBytes = 10) }
        }
    }

    @Test
    fun theIndexComesFromJustTheBeginningOfTheFile() {
        val result = SngIndex.parse(bytes.copyOf(200))
        assertTrue(result is SngIndex.Result.Complete)
        assertEquals(listOf("notes.chart", "song.opus"), (result as SngIndex.Result.Complete).files.map { it.name })
        assertTrue(SngIndex.parse(bytes.copyOf(30)) is SngIndex.Result.NeedsMore)
    }
}
```

`SngRefTest.kt`:

```kotlin
package com.rodrigomachado.riff.core.chart.sng

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SngRefTest {
    private val file = "content://p/tree/primary%3AC/document/primary%3AC%2FSong.sng"

    @Test
    fun packageAndEntryUrisRoundTrip() {
        assertEquals(file, SngRef.packageFileOf(SngRef.packageUri(file)))
        assertEquals(file to "song.opus", SngRef.entryOf(SngRef.entryUri(file, "song.opus")))
        assertNull(SngRef.entryOf(file))
        assertNull(SngRef.packageFileOf("content://p/tree/x/document/y"))
    }

    @Test
    fun thePackageUriKeepsTheFileUriAsPrefix() {
        // keepFolder (subpasta ilegível) casa por prefixo do URI da pasta.
        assertEquals(true, SngRef.packageUri(file).startsWith(file))
    }
}
```

`ChartAudioFilesTest.kt`:

```kotlin
package com.rodrigomachado.riff.core.chart

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChartAudioFilesTest {
    @Test
    fun separateGuitarNeedsGuitarAndAnotherTrack() {
        assertEquals(true, ChartAudioFiles.hasSeparateGuitar(listOf("notes.chart", "guitar.ogg", "song.opus")))
        assertEquals(false, ChartAudioFiles.hasSeparateGuitar(listOf("notes.chart", "song.opus")))
        assertEquals(false, ChartAudioFiles.hasSeparateGuitar(listOf("guitar.ogg")))
        assertEquals("Song.OGG", ChartAudioFiles.find(listOf("x.txt", "Song.OGG"), "song"))
    }
}
```

- [ ] **Step 2: rodar e ver falhar**

Run: `./scripts/gw :core:chart:test --console=plain`
Expected: FAIL na compilação (`SngPackage`, `SngIndex`, `SngRef`, `ChartAudioFiles`, `ByteArrayRandomRead` não existem).

- [ ] **Step 3: implementar**

`RandomRead.kt`:

```kotlin
package com.rodrigomachado.riff.core.chart.sng

import java.io.Closeable
import java.io.IOException

/** Leitura em qualquer posição (arquivo do SAF, bytes em memória ou um arquivo de dentro do `.sng`). */
interface RandomRead : Closeable {
    val size: Long

    /** Lê até [length] bytes a partir de [position]; -1 no fim. */
    fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int

    override fun close() = Unit
}

fun interface RandomReadOpener {
    @Throws(IOException::class)
    fun open(uri: String): RandomRead
}

class ByteArrayRandomRead(private val bytes: ByteArray) : RandomRead {
    override val size: Long get() = bytes.size.toLong()

    override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= bytes.size) return -1
        val count = minOf(length.toLong(), bytes.size - position).toInt()
        System.arraycopy(bytes, position.toInt(), buffer, offset, count)
        return count
    }
}

/** Lê exatamente [length] bytes ou lança [InvalidSngException] (arquivo truncado). */
internal fun RandomRead.readFully(position: Long, length: Int): ByteArray {
    val out = ByteArray(length)
    var done = 0
    while (done < length) {
        val n = read(position + done, out, done, length - done)
        if (n <= 0) throw InvalidSngException("arquivo truncado")
        done += n
    }
    return out
}
```

`SngPackage.kt`:

```kotlin
package com.rodrigomachado.riff.core.chart.sng

import com.rodrigomachado.riff.core.chart.SongIniParser
import com.rodrigomachado.riff.core.chart.SongInfo
import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class InvalidSngException(message: String) : IOException(message)

data class SngFile(val name: String, val size: Long, val offset: Long)

/**
 * Pacote `.sng` (SngFileFormat): cabeçalho "SNGPKG", metadados (as chaves do song.ini), índice e os arquivos
 * mascarados. Cada byte `i` de um arquivo é `byte XOR máscara[i % 16] XOR (i AND 0xFF)`.
 */
class SngPackage private constructor(
    private val source: RandomRead,
    private val mask: ByteArray,
    val metadata: Map<String, String>,
    val files: List<SngFile>,
) : Closeable {

    fun read(name: String, maxBytes: Long): ByteArray {
        val file = file(name)
        if (file.size > maxBytes) throw IOException("$name grande demais (${file.size} bytes)")
        val bytes = source.readFully(file.offset, file.size.toInt())
        unmask(bytes, 0L)
        return bytes
    }

    fun open(name: String): RandomRead {
        val file = file(name)
        return object : RandomRead {
            override val size: Long get() = file.size

            override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
                if (position >= file.size) return -1
                val count = minOf(length.toLong(), file.size - position).toInt()
                val n = source.read(file.offset + position, buffer, offset, count)
                if (n > 0) unmask(buffer, position, offset, n)
                return n
            }
        }
    }

    /** Os metadados no formato do song.ini, pelas mesmas regras do [SongIniParser]. */
    fun songInfo(): SongInfo =
        SongIniParser.parse("[song]\n" + metadata.entries.joinToString("\n") { "${it.key} = ${it.value}" })

    private fun file(name: String) = files.firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?: throw IOException("$name não está no pacote")

    private fun unmask(bytes: ByteArray, start: Long, offset: Int = 0, length: Int = bytes.size) {
        for (k in 0 until length) {
            val i = start + k
            bytes[offset + k] = (bytes[offset + k].toInt() xor mask[(i % MASK_SIZE).toInt()].toInt() xor
                (i and 0xFF).toInt()).toByte()
        }
    }

    override fun close() = source.close()

    companion object {
        internal const val MASK_SIZE = 16

        /** @throws InvalidSngException cabeçalho errado, arquivo truncado ou tamanhos fora do arquivo. */
        fun open(source: RandomRead): SngPackage {
            val headSize = minOf(source.size, SngIndex.MAX_INDEX_BYTES.toLong()).toInt()
            val head = source.readFully(0, headSize)
            val index = when (val parsed = SngIndex.parse(head)) {
                is SngIndex.Result.Complete -> parsed
                is SngIndex.Result.NeedsMore -> throw InvalidSngException("índice maior que o arquivo")
            }
            index.files.forEach { if (it.offset + it.size > source.size) throw InvalidSngException("${it.name} fora") }
            return SngPackage(source, head.copyOfRange(10, 26), index.metadata, index.files)
        }
    }
}

/** Lê cabeçalho, metadados e índice a partir só do começo do arquivo (o resto são os dados). */
object SngIndex {
    const val MAX_INDEX_BYTES = 256 * 1024
    private const val MAX_FILES = 64
    private const val MAX_METADATA = 4_096

    sealed interface Result {
        data class Complete(val metadata: Map<String, String>, val files: List<SngFile>) : Result
        data class NeedsMore(val bytes: Int) : Result
    }

    fun parse(head: ByteArray): Result {
        if (head.size < 26) return Result.NeedsMore(26)
        if (String(head, 0, 6) != "SNGPKG") throw InvalidSngException("não é um .sng")
        val buf = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
        return try {
            buf.position(26)
            val metaLen = buf.long
            val metaCount = buf.long
            if (metaCount !in 0..MAX_METADATA || metaLen !in 8..MAX_INDEX_BYTES.toLong()) {
                throw InvalidSngException("metadados inválidos")
            }
            val metadata = LinkedHashMap<String, String>()
            repeat(metaCount.toInt()) { metadata[string(buf, buf.int)] = string(buf, buf.int) }
            buf.long // tamanho do índice
            val fileCount = buf.long
            if (fileCount !in 0..MAX_FILES) throw InvalidSngException("arquivos demais: $fileCount")
            val files = List(fileCount.toInt()) {
                val name = string(buf, buf.get().toInt() and 0xFF)
                val size = buf.long
                val offset = buf.long
                if (size < 0 || offset < 0 || size > Int.MAX_VALUE) throw InvalidSngException("$name inválido")
                SngFile(name, size, offset)
            }
            Result.Complete(metadata, files)
        } catch (e: java.nio.BufferUnderflowException) {
            if (head.size >= MAX_INDEX_BYTES) throw InvalidSngException("índice grande demais")
            Result.NeedsMore(minOf(head.size * 2, MAX_INDEX_BYTES))
        }
    }

    private fun string(buf: ByteBuffer, length: Int): String {
        if (length < 0 || length > buf.remaining()) throw java.nio.BufferUnderflowException()
        val bytes = ByteArray(length).also { buf.get(it) }
        return String(bytes, Charsets.UTF_8)
    }
}
```

`SngRef.kt`:

```kotlin
package com.rodrigomachado.riff.core.chart.sng

/**
 * URIs de um `.sng` tratado como pasta: `<arquivo>#sng` (a pasta) e `<arquivo>#sng:<nome>` (um arquivo de
 * dentro). O URI do arquivo vem antes, então a regra de prefixo das subpastas continua valendo.
 */
object SngRef {
    private const val MARK = "#sng"

    fun packageUri(fileUri: String) = fileUri + MARK

    fun entryUri(fileUri: String, name: String) = "$fileUri$MARK:$name"

    fun packageFileOf(uri: String): String? = if (uri.endsWith(MARK)) uri.removeSuffix(MARK) else null

    fun entryOf(uri: String): Pair<String, String>? {
        val at = uri.lastIndexOf("$MARK:")
        return if (at < 0) null else uri.substring(0, at) to uri.substring(at + MARK.length + 1)
    }

    fun isPackageName(name: String) = name.endsWith(".sng", ignoreCase = true)
}
```

`ChartAudioFiles.kt` (as listas saem do `FolderChartTrackLoader`, que passa a usá-las no Task 3):

```kotlin
package com.rodrigomachado.riff.core.chart

/** Nomes dos arquivos de áudio do Clone Hero e a regra da guitarra separada. */
object ChartAudioFiles {
    val EXTENSIONS = listOf("ogg", "opus", "mp3", "wav")

    /** As faixas do Clone Hero, menos a guitarra; `song` primeiro. */
    val BACKING_NAMES = listOf(
        "song", "rhythm", "bass", "drums", "drums_1", "drums_2", "drums_3", "drums_4",
        "vocals", "vocals_1", "vocals_2", "keys", "crowd",
    )

    /** O nome de [names] que é `<base>.<extensão de áudio>`, sem diferenciar maiúsculas. */
    fun find(names: Collection<String>, base: String): String? = EXTENSIONS.firstNotNullOfOrNull { extension ->
        names.firstOrNull { it.equals("$base.$extension", ignoreCase = true) }
    }

    fun hasSeparateGuitar(names: Collection<String>): Boolean =
        find(names, "guitar") != null && BACKING_NAMES.any { find(names, it) != null }
}
```

- [ ] **Step 4: rodar e ver passar**

Run: `./scripts/gw :core:chart:test detekt --console=plain`
Expected: PASS (todos os testes de `:core:chart`); detekt sem achados (formatar com `-PdetektAutoCorrect` se preciso).

- [ ] **Step 5: commit**

```bash
./scripts/gw :core:chart:test detekt -q && git add core/chart && git commit -m "feat: leitor de .sng (índice, metadados, arquivos desmascarados) e regra comum da guitarra separada

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Tabela do md5 do Encore (`:core:database`, `:core:data`)

**Files:**
- Modify: `core/database/src/main/kotlin/com/rodrigomachado/riff/core/database/Entities.kt` (nova entidade)
- Create: `core/database/src/main/kotlin/com/rodrigomachado/riff/core/database/EncoreChartDao.kt`
- Modify: `core/database/src/main/kotlin/com/rodrigomachado/riff/core/database/RiffDatabase.kt` (versão 4, `AutoMigration(3, 4)`, entidade e DAO)
- Modify: `core/database/src/main/kotlin/com/rodrigomachado/riff/core/database/DatabaseModule.kt`
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/EncoreDownloads.kt`
- Modify: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/DataModule.kt`
- Test: `core/database/src/test/.../MigrationTest.kt` (caso 3 → 4), `core/data/src/test/.../RoomEncoreDownloadsTest.kt`

**Interfaces:**
- Produces:
  - `@Entity(tableName = "encore_charts") data class EncoreChartEntity(@PrimaryKey val chartId: String, val md5: String)` com FK para `charts(id)` `ON DELETE CASCADE`.
  - `EncoreChartDao { @Upsert suspend fun upsert(e: EncoreChartEntity); @Query("SELECT md5 FROM encore_charts") fun observeMd5s(): Flow<List<String>>; @Query("SELECT chartId FROM encore_charts WHERE md5 = :md5") suspend fun chartIdOf(md5: String): String? }`
  - `interface EncoreDownloads { val md5s: Flow<Set<String>>; suspend fun remember(chartId: String, md5: String); suspend fun chartIdOf(md5: String): String? }`; `RoomEncoreDownloads`.

- [ ] **Step 1: testes que falham**

Em `RoomEncoreDownloadsTest` (Robolectric, banco em memória como `RoomChartOffsetRepositoryTest`):

```kotlin
@Test
fun aDownloadedChartStaysMarkedAfterTheLibraryIsRescanned() = runTest {
    val chart = ChartEntity("a", "M", "B", null, null, null, "content://a#sng", "CHART", 1)
    db.chartDao().replace(chart, emptyList())
    downloads.remember("a", "a07d209baccd2d58042d81b869a0077e")

    db.chartDao().replace(chart.copy(lastSeenScanId = 2), emptyList()) // Reimportar regrava a linha do mapa

    assertEquals(setOf("a07d209baccd2d58042d81b869a0077e"), downloads.md5s.first())
    assertEquals("a", downloads.chartIdOf("a07d209baccd2d58042d81b869a0077e"))
}

@Test
fun removingTheChartForgetsTheDownload() = runTest {
    db.chartDao().replace(ChartEntity("a", "M", "B", null, null, null, "u", "CHART", 1), emptyList())
    downloads.remember("a", "m5")
    db.chartDao().deleteNotSeenIn(2)
    assertEquals(emptySet<String>(), downloads.md5s.first())
}
```

No teste de migração existente, um caso novo `migrate3To4` com `helper.runMigrationsAndValidate(DB, 4, true)` depois de criar a versão 3.

- [ ] **Step 2: ver falhar**

Run: `./scripts/gw :core:database:testDebugUnitTest :core:data:testDebugUnitTest --console=plain`
Expected: FAIL na compilação (`EncoreChartEntity`, `RoomEncoreDownloads`).

- [ ] **Step 3: implementar**

Entidade e DAO:

```kotlin
/** Mapas baixados do Encore (md5 do pacote), à parte da tabela de mapas: a importação regrava aquela linha. */
@Entity(
    tableName = "encore_charts",
    foreignKeys = [ForeignKey(ChartEntity::class, ["id"], ["chartId"], onDelete = ForeignKey.CASCADE)],
)
data class EncoreChartEntity(@PrimaryKey val chartId: String, val md5: String)
```

```kotlin
@Dao
interface EncoreChartDao {
    @Upsert
    suspend fun upsert(entity: EncoreChartEntity)

    @Query("SELECT md5 FROM encore_charts")
    fun observeMd5s(): Flow<List<String>>

    @Query("SELECT chartId FROM encore_charts WHERE md5 = :md5")
    suspend fun chartIdOf(md5: String): String?
}
```

`RiffDatabase`: `version = 4`, `AutoMigration(from = 3, to = 4)` na lista, `EncoreChartEntity::class` em `entities`, `abstract fun encoreChartDao(): EncoreChartDao`. `DatabaseModule`: `@Provides fun encoreChartDao(database: RiffDatabase): EncoreChartDao = database.encoreChartDao()`. Rodar `./scripts/gw :core:database:assembleDebug` para gerar `core/database/schemas/.../4.json` (vai no commit).

`EncoreDownloads.kt`:

```kotlin
package com.rodrigomachado.riff.core.data

import com.rodrigomachado.riff.core.database.EncoreChartDao
import com.rodrigomachado.riff.core.database.EncoreChartEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Quais pacotes do Encore já estão na biblioteca (para a pesquisa mostrar "Já na biblioteca"). */
interface EncoreDownloads {
    val md5s: Flow<Set<String>>

    suspend fun remember(chartId: String, md5: String)

    /** O mapa da biblioteca baixado deste pacote (para o Jogar da pesquisa). */
    suspend fun chartIdOf(md5: String): String?
}

class RoomEncoreDownloads @Inject constructor(private val dao: EncoreChartDao) : EncoreDownloads {
    override val md5s: Flow<Set<String>> = dao.observeMd5s().map { it.toSet() }

    override suspend fun remember(chartId: String, md5: String) = dao.upsert(EncoreChartEntity(chartId, md5))

    override suspend fun chartIdOf(md5: String): String? = dao.chartIdOf(md5)
}
```

`DataBindsModule`: `@Binds abstract fun encoreDownloads(impl: RoomEncoreDownloads): EncoreDownloads`.

- [ ] **Step 4: ver passar**

Run: `./scripts/gw :core:database:testDebugUnitTest :core:data:testDebugUnitTest detekt --console=plain`
Expected: PASS.

- [ ] **Step 5: commit**

```bash
./scripts/gw :core:database:testDebugUnitTest :core:data:testDebugUnitTest detekt -q && git add core/database core/data && git commit -m "feat: banco v4 guarda o md5 do Encore de cada mapa baixado

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: `.sng` na biblioteca (`:core:data`)

**Files:**
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/folder/SngFolderSource.kt`
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/folder/ContentRandomRead.kt`
- Modify: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/importer/ChartImporter.kt` (`visit` reconhece `.sng`; título sem `.sng`)
- Modify: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/game/ChartTrackLoader.kt` (usa `ChartAudioFiles`)
- Modify: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/DataModule.kt` (`FolderSource` = `SngFolderSource(SafFolderSource, opener)`; `RandomReadOpener` = `ContentRandomReadOpener`)
- Modify: `core/data/build.gradle.kts` (`testImplementation(testFixtures(project(":core:chart")))`)
- Test: `core/data/src/test/.../folder/SngFolderSourceTest.kt`, `.../importer/FolderChartImporterTest.kt` (casos novos), `.../game/FolderChartTrackLoaderTest.kt` (caso novo)

**Interfaces:**
- Consumes: `SngPackage`, `SngRef`, `RandomRead`, `RandomReadOpener`, `ChartAudioFiles` (Task 1).
- Produces:
  - `class SngFolderSource(private val delegate: FolderSource, private val opener: RandomReadOpener) : FolderSource` — `listChildren(SngRef.packageUri(f))` lista os arquivos do pacote como `FolderEntry(SngRef.entryUri(f, name), name, false, size)`; `read(entry)` de um `entryUri` lê do pacote; o resto vai para [delegate].
  - `class ContentRandomReadOpener @Inject constructor(@ApplicationContext context: Context) : RandomReadOpener`
  - A pasta de um mapa `.sng` na biblioteca é `SngRef.packageUri(<uri do arquivo>)`; o áudio dele, `SngRef.entryUri(<uri>, "song.opus")`.

- [ ] **Step 1: testes que falham**

O `FolderChartImporterTest` usa uma árvore em memória (`FakeFolderSource`); os testes novos montam o `.sng` com `SngWriter` e usam `SngFolderSource(fake, opener = { uri -> ByteArrayRandomRead(fake.bytes(uri)) })`. Casos:

```kotlin
@Test
fun aSngFileIsImportedLikeASongFolder() = runTest {
    tree.file("root/Nirvana - Teen Spirit.sng", SngWriter.write(
        mapOf("name" to "Smells Like Teen Spirit", "artist" to "Nirvana"),
        mapOf("notes.chart" to validChart(), "song.opus" to ByteArray(10)),
    ))

    val report = importer().import("root")

    assertEquals(1, report.importedCount)
    val chart = repository.charts.single()
    assertEquals("Smells Like Teen Spirit", chart.title)
    assertEquals(SngRef.packageUri("root/Nirvana - Teen Spirit.sng"), chart.folderUri)
}

@Test
fun aCorruptedSngGoesToTheReportAsAnInvalidChart() = runTest {
    tree.file("root/quebrado.sng", "isto não é um sng".toByteArray())

    val report = importer().import("root")

    assertEquals(ImportFailureKind.INVALID_CHART, report.failures.single().kind)
}

@Test
fun anUnreadableSubfolderKeepsItsSngCharts() = runTest {
    // Revisão §2: keepFolder por prefixo precisa casar com o URI do pacote dentro da subpasta.
    tree.file("root/sub/a.sng", SngWriter.write(mapOf("name" to "A"), mapOf("notes.chart" to validChart())))
    importer().import("root")
    tree.failListing("root/sub")

    importer().import("root")

    assertEquals(listOf("A"), repository.charts.map { it.title })
}
```

(`validChart()` é o mapa mínimo que os testes do importador já usam; `failListing` faz o `listChildren` da subpasta lançar `IOException` — se o fake não tiver, acrescentar.)

`FolderChartTrackLoaderTest`, caso novo: mapa salvo com `folderUri = SngRef.packageUri("x.sng")` e pacote com `notes.chart`, `guitar.ogg` e `song.opus` → `audio == ChartAudio.Stems(SngRef.entryUri("x.sng","guitar.ogg"), listOf(SngRef.entryUri("x.sng","song.opus")), fallbackUri = SngRef.entryUri("x.sng","song.opus"))`.

`SngFolderSourceTest`: listar o pacote dá os nomes e tamanhos; `read` de uma entrada devolve o conteúdo desmascarado; `read` acima de `maxBytes` lança `FileTooLargeException`; URIs comuns vão para o delegate.

- [ ] **Step 2: ver falhar**

Run: `./scripts/gw :core:data:testDebugUnitTest --console=plain`
Expected: FAIL (compilação de `SngFolderSource`; o importador ignora `.sng`).

- [ ] **Step 3: implementar**

`SngFolderSource.kt`:

```kotlin
package com.rodrigomachado.riff.core.data.folder

import com.rodrigomachado.riff.core.chart.sng.InvalidSngException
import com.rodrigomachado.riff.core.chart.sng.RandomReadOpener
import com.rodrigomachado.riff.core.chart.sng.SngPackage
import com.rodrigomachado.riff.core.chart.sng.SngRef

/**
 * Um `.sng` lido como se fosse uma pasta: os arquivos de dentro aparecem como filhos, já desmascarados. Assim a
 * importação e o carregamento da partida tratam pacote e pasta do mesmo jeito.
 */
class SngFolderSource(private val delegate: FolderSource, private val opener: RandomReadOpener) : FolderSource {
    override suspend fun listChildren(folderUri: String): List<FolderEntry> {
        val file = SngRef.packageFileOf(folderUri) ?: return delegate.listChildren(folderUri)
        return open(file).use { sng ->
            sng.files.map { FolderEntry(SngRef.entryUri(file, it.name), it.name, isDirectory = false, it.size) }
        }
    }

    override suspend fun read(file: FolderEntry, maxBytes: Long): ByteArray {
        val (packageFile, name) = SngRef.entryOf(file.uri) ?: return delegate.read(file, maxBytes)
        return open(packageFile).use { sng ->
            val size = sng.files.firstOrNull { it.name.equals(name, ignoreCase = true) }?.size ?: 0
            if (size > maxBytes) throw FileTooLargeException(name, maxBytes)
            sng.read(name, maxBytes)
        }
    }

    private fun open(file: String): SngPackage = SngPackage.open(opener.open(file))
}
```

(`InvalidSngException` é `IOException`; no importador ela precisa virar **mapa inválido**, então `importSong` ganha `catch (e: InvalidSngException) { keep(folder, ImportFailureKind.INVALID_CHART, e.message) }` antes do `catch (e: IOException)`.)

`ContentRandomRead.kt`:

```kotlin
package com.rodrigomachado.riff.core.data.folder

import android.content.Context
import android.net.Uri
import com.rodrigomachado.riff.core.chart.sng.RandomRead
import com.rodrigomachado.riff.core.chart.sng.RandomReadOpener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import javax.inject.Inject

/** Arquivo do SAF lido por posição (o `.sng` é lido aos pedaços, nunca inteiro na memória). */
class ContentRandomReadOpener @Inject constructor(@ApplicationContext private val context: Context) :
    RandomReadOpener {
    override fun open(uri: String): RandomRead {
        val descriptor = context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
            ?: throw IOException("não foi possível abrir $uri")
        val stream = FileInputStream(descriptor.fileDescriptor)
        val channel = stream.channel
        return object : RandomRead {
            override val size: Long = channel.size()

            override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int =
                channel.read(ByteBuffer.wrap(buffer, offset, length), position)

            override fun close() {
                stream.close()
                descriptor.close()
            }
        }
    }
}
```

`ChartImporter.visit` — depois do teste de `NOTES_FILES` e antes de descer nas subpastas:

```kotlin
for (child in children.filter { !it.isDirectory && SngRef.isPackageName(it.name) }) {
    val packageFolder = FolderEntry(SngRef.packageUri(child.uri), child.name.removeSuffix(".sng"), true, child.sizeBytes)
    val files = listOrKeep(packageFolder) ?: continue
    importSong(packageFolder, files)
}
```

e `load()` usa `folder.name` como está (já sem `.sng`). `listOrKeep` de um pacote inválido lança `InvalidSngException` (`IOException`) → hoje vira `UNREADABLE`; trocar para tratar `InvalidSngException` como `INVALID_CHART` em `listOrKeep` também (o teste `aCorruptedSngGoesToTheReportAsAnInvalidChart` cobre).

`FolderChartTrackLoader.audioOf` passa a usar `ChartAudioFiles.find(files.map { it.name }, base)` para achar o nome e devolve o `uri` da entrada correspondente; as listas locais (`EXTENSIONS`, `BACKING_NAMES`) saem.

`DataModule`: tirar `@Binds folderSource`; em `DataProvidesModule`:

```kotlin
@Provides
fun folderSource(saf: SafFolderSource, opener: RandomReadOpener): FolderSource = SngFolderSource(saf, opener)
```

e `@Binds abstract fun randomReadOpener(impl: ContentRandomReadOpener): RandomReadOpener` no `DataBindsModule`.

- [ ] **Step 4: ver passar**

Run: `./scripts/gw :core:data:testDebugUnitTest detekt --console=plain`
Expected: PASS, incluindo os testes antigos do importador e do loader.

- [ ] **Step 5: commit**

```bash
./scripts/gw :core:data:testDebugUnitTest detekt -q && git add core/data && git commit -m "feat: a biblioteca lê .sng como uma pasta de mapa

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Tocar áudio de dentro do `.sng` (`:core:audio`)

**Files:**
- Create: `core/audio/src/main/kotlin/com/rodrigomachado/riff/core/audio/SngMediaDataSource.kt`
- Modify: `core/audio/src/main/kotlin/com/rodrigomachado/riff/core/audio/MediaSongPlayer.kt` (`load`)
- Modify: `core/audio/src/main/kotlin/com/rodrigomachado/riff/core/audio/MediaCodecPcmSource.kt` (`open`/`startDecoding`)
- Modify: `core/audio/src/main/kotlin/com/rodrigomachado/riff/core/audio/AudioModule.kt` (passa o `RandomReadOpener`)
- Modify: `core/audio/build.gradle.kts` (`implementation(project(":core:chart"))`, `testImplementation(testFixtures(project(":core:chart")))`)
- Test: `core/audio/src/test/.../SngMediaDataSourceTest.kt`

**Interfaces:**
- Consumes: `SngRef.entryOf`, `SngPackage.open(...).open(name)`, `RandomReadOpener` (binding do Task 3).
- Produces: `class SngMediaDataSource(file: RandomRead, owner: Closeable) : MediaDataSource`; `fun audioDataSource(uri: String, opener: RandomReadOpener): MediaDataSource?` (null para URI comum).

- [ ] **Step 1: teste que falha** (Robolectric)

```kotlin
@RunWith(RobolectricTestRunner::class)
class SngMediaDataSourceTest {
    private val audio = ByteArray(5_000) { (it * 7 % 256).toByte() }
    private val sng = SngWriter.write(mapOf("name" to "M"), mapOf("notes.chart" to ByteArray(3), "song.opus" to audio))

    @Test
    fun theAudioInsideThePackageIsReadAtAnyPositionLikeAFile() {
        val source = checkNotNull(audioDataSource(SngRef.entryUri("x.sng", "song.opus")) { ByteArrayRandomRead(sng) })
        val buffer = ByteArray(100)

        assertEquals(5_000L, source.size)
        // O extrator lê fora de ordem (reposicionar ao voltar 0,5 s).
        assertEquals(100, source.readAt(4_000, buffer, 0, 100))
        assertArrayEquals(audio.copyOfRange(4_000, 4_100), buffer)
        assertEquals(100, source.readAt(10, buffer, 0, 100))
        assertArrayEquals(audio.copyOfRange(10, 110), buffer)
        assertEquals(-1, source.readAt(5_000, buffer, 0, 100))
        source.close()
    }

    @Test
    fun aPlainUriHasNoPackageDataSource() {
        assertNull(audioDataSource("content://x/song.ogg") { error("não deveria abrir") })
    }
}
```

- [ ] **Step 2: ver falhar**

Run: `./scripts/gw :core:audio:testDebugUnitTest --tests '*SngMediaDataSourceTest' --console=plain`
Expected: FAIL (compilação).

- [ ] **Step 3: implementar**

```kotlin
package com.rodrigomachado.riff.core.audio

import android.media.MediaDataSource
import com.rodrigomachado.riff.core.chart.sng.RandomRead
import com.rodrigomachado.riff.core.chart.sng.RandomReadOpener
import com.rodrigomachado.riff.core.chart.sng.SngPackage
import com.rodrigomachado.riff.core.chart.sng.SngRef
import java.io.Closeable

/** Um arquivo de dentro do `.sng`, desmascarado, para o `MediaPlayer` e o `MediaExtractor`. */
class SngMediaDataSource(private val file: RandomRead, private val owner: Closeable) : MediaDataSource() {
    override fun getSize(): Long = file.size

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int =
        if (size == 0) 0 else file.read(position, buffer, offset, size)

    override fun close() = owner.close()
}

/** A fonte de dados do áudio [uri] se ele estiver dentro de um `.sng`; null para um arquivo comum. */
fun audioDataSource(uri: String, opener: RandomReadOpener): MediaDataSource? {
    val (packageFile, name) = SngRef.entryOf(uri) ?: return null
    val sng = SngPackage.open(opener.open(packageFile))
    return SngMediaDataSource(sng.open(name), sng)
}
```

`MediaSongPlayer` ganha `private val opener: RandomReadOpener` no construtor e, em `load(uri)`:

```kotlin
val packaged = audioDataSource(uri, opener)
if (packaged != null) player.setDataSource(packaged) else player.setDataSource(context, Uri.parse(uri))
```

`MediaCodecPcmSource.open(context, uri, opener)` e `startDecoding`: `audioDataSource(uri, opener)?.let(extractor::setDataSource) ?: extractor.setDataSource(context, Uri.parse(uri), null)`. `AudioModule`: os provedores de `SongPlayerFactory`, `StemSongPlayerFactory` e `MusicStartFinder` recebem `opener: RandomReadOpener` e o repassam. Os testes existentes que constroem `MediaSongPlayer(context, player)` passam `opener = { error("sem pacote") }` (parâmetro com default não serve: o Hilt precisa dele; manter o parâmetro sem default e ajustar os testes).

- [ ] **Step 4: ver passar**

Run: `./scripts/gw :core:audio:testDebugUnitTest detekt --console=plain`
Expected: PASS.

- [ ] **Step 5: commit**

```bash
./scripts/gw :core:audio:testDebugUnitTest detekt -q && git add core/audio && git commit -m "feat: o áudio de dentro do .sng toca no player de arquivo e no de faixas separadas

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Cliente do Encore (`:core:encore`, módulo novo)

**Files:**
- Create: `core/encore/build.gradle.kts`, `core/encore/src/main/AndroidManifest.xml` (vazio, só `<manifest/>`)
- Modify: `settings.gradle.kts` (`include(":core:encore")`)
- Create: `core/encore/src/main/kotlin/com/rodrigomachado/riff/core/encore/EncoreClient.kt`
- Create: `core/encore/src/main/kotlin/com/rodrigomachado/riff/core/encore/HttpTransport.kt`
- Create: `core/encore/src/main/kotlin/com/rodrigomachado/riff/core/encore/OfficialCharts.kt`
- Create: `core/encore/src/main/kotlin/com/rodrigomachado/riff/core/encore/EncoreModule.kt`
- Create: `core/encore/src/test/resources/encore/search_teen_spirit.json` (resposta real enxuta: 2 mapas da comunidade com o mesmo nome, 1 Harmonix `rb2dlc`, 1 Neversoft `gh5` — gerada em 2026-10-12, campos `name, artist, charter, icon, diff_guitar, md5, hasVideoBackground, song_length, notesData.noteCounts`)
- Test: `core/encore/src/test/kotlin/com/rodrigomachado/riff/core/encore/EncoreClientTest.kt`

**Interfaces:**
- Consumes: `SngIndex`, `ChartAudioFiles` (Task 1).
- Produces:
  - `data class EncoreChart(val md5: String, val name: String, val artist: String, val charter: String?, val durationMs: Long?, val guitarDifficulties: Set<Difficulty>, val hasVideoBackground: Boolean)` com `val downloadUrl: String`
  - `data class EncorePage(val charts: List<EncoreChart>, val found: Int, val page: Int)` com `val hasMore: Boolean`
  - `class EncoreClient(transport: HttpTransport)` com `suspend fun search(query: String, page: Int): EncorePage`, `suspend fun hasSeparateGuitar(chart: EncoreChart): Boolean?`, `suspend fun download(chart: EncoreChart, into: OutputStream, onProgress: (Float) -> Unit)`
  - `class EncoreUnavailableException(cause: Throwable?) : IOException`, `class EncoreFormatException(message: String) : IOException`
  - `interface HttpTransport { suspend fun post(url: String, json: String): HttpResponse; suspend fun get(url: String, range: LongRange? = null): HttpResponse }`, `class HttpResponse(val code: Int, val length: Long?, val body: InputStream) : Closeable`

- [ ] **Step 1: testes que falham** (Robolectric, para o `org.json`)

```kotlin
@RunWith(RobolectricTestRunner::class)
class EncoreClientTest {
    private val fixture = javaClass.getResource("/encore/search_teen_spirit.json")!!.readText()
    private val transport = FakeTransport()
    private val client = EncoreClient(transport)

    @Test
    fun searchSendsTheQueryAsAnApiClientAndKeepsOnlyCommunityCharts() = runTest {
        transport.post = HttpResponse(201, null, fixture.byteInputStream())

        val page = client.search("smells like teen spirit", page = 1)

        val sent = JSONObject(transport.lastJson!!)
        assertEquals("smells like teen spirit", sent.getString("search"))
        assertEquals("api", sent.getString("source"))
        assertEquals("guitar", sent.getString("instrument"))
        // Harmonix (rb2dlc) e Neversoft (gh5) saem; os dois da comunidade ficam.
        assertEquals(listOf("a07d209baccd2d58042d81b869a0077e", "80dc4fff35cc77abec7edeb59de1580c"), page.charts.map { it.md5 })
        assertEquals(setOf(Difficulty.EXPERT), page.charts.first().guitarDifficulties)
        assertEquals("https://files.enchor.us/a07d209baccd2d58042d81b869a0077e.sng", page.charts.first().downloadUrl)
    }

    @Test
    fun theSameChartListedTwiceAppearsOnce() = runTest {
        val twice = JSONObject(fixture).also { o -> o.getJSONArray("data").put(o.getJSONArray("data").get(0)) }
        transport.post = HttpResponse(201, null, twice.toString().byteInputStream())

        assertEquals(2, client.search("x", 1).charts.size)
    }

    @Test
    fun aChangedResponseIsReportedAsAFormatChange() = runTest {
        transport.post = HttpResponse(201, null, """{"results": []}""".byteInputStream())
        assertThrows(EncoreFormatException::class.java) { runBlocking { client.search("x", 1) } }
    }

    @Test
    fun aServerErrorOrNoNetworkIsUnavailable() = runTest {
        transport.post = HttpResponse(503, null, "".byteInputStream())
        assertThrows(EncoreUnavailableException::class.java) { runBlocking { client.search("x", 1) } }
        transport.failure = java.net.UnknownHostException("api.enchor.us")
        assertThrows(EncoreUnavailableException::class.java) { runBlocking { client.search("x", 1) } }
    }

    @Test
    fun theSeparateGuitarMarkComesFromTheBeginningOfThePackage() = runTest {
        val sng = SngWriter.write(mapOf("name" to "M"), mapOf("notes.chart" to ByteArray(10), "guitar.ogg" to ByteArray(10), "song.ogg" to ByteArray(10)))
        transport.get = { _, range -> HttpResponse(206, null, sng.copyOf((range!!.last + 1).toInt().coerceAtMost(sng.size)).inputStream()) }

        assertEquals(true, client.hasSeparateGuitar(chart(md5 = "a07d209baccd2d58042d81b869a0077e")))
        assertEquals(0L..16_383L, transport.lastRange)
    }

    @Test
    fun theVideoFreePackageIsDownloadedWhenTheChartHasAVideo() = runTest {
        transport.get = { _, _ -> HttpResponse(200, 4, "abcd".byteInputStream()) }
        val out = java.io.ByteArrayOutputStream()
        val progress = mutableListOf<Float>()

        client.download(chart(md5 = "a07d209baccd2d58042d81b869a0077e", video = true), out) { progress += it }

        assertEquals("https://files.enchor.us/a07d209baccd2d58042d81b869a0077e_novideo.sng", transport.lastUrl)
        assertEquals("abcd", out.toString())
        assertEquals(1f, progress.last())
    }
}
```

(`FakeTransport` no próprio arquivo de teste: `var post: HttpResponse?`, `var get: (String, LongRange?) -> HttpResponse`, `var failure: IOException?`, `lastJson`, `lastUrl`, `lastRange`. `chart(md5, video)` monta um `EncoreChart` com os demais campos fixos.)

- [ ] **Step 2: ver falhar**

Run: `./scripts/gw :core:encore:testDebugUnitTest --console=plain`
Expected: FAIL (módulo e classes não existem).

- [ ] **Step 3: implementar**

`core/encore/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.riff.android.library)
    alias(libs.plugins.riff.android.hilt)
}

android {
    namespace = "com.rodrigomachado.riff.core.encore"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:chart"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(testFixtures(project(":core:chart")))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

`HttpTransport.kt`:

```kotlin
package com.rodrigomachado.riff.core.encore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class HttpResponse(val code: Int, val length: Long?, val body: InputStream) : Closeable {
    override fun close() = body.close()
}

interface HttpTransport {
    suspend fun post(url: String, json: String): HttpResponse

    suspend fun get(url: String, range: LongRange? = null): HttpResponse
}

/** HTTP do próprio Android (sem biblioteca nova). */
class UrlConnectionTransport : HttpTransport {
    override suspend fun post(url: String, json: String) = withContext(Dispatchers.IO) {
        val connection = connect(url).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.use { it.write(json.toByteArray()) }
        response(connection)
    }

    override suspend fun get(url: String, range: LongRange?) = withContext(Dispatchers.IO) {
        val connection = connect(url)
        range?.let { connection.setRequestProperty("Range", "bytes=${it.first}-${it.last}") }
        response(connection)
    }

    private fun connect(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
    }

    private fun response(connection: HttpURLConnection): HttpResponse {
        val code = connection.responseCode
        val body = if (code >= 400) connection.errorStream ?: "".byteInputStream() else connection.inputStream
        return HttpResponse(code, connection.contentLengthLong.takeIf { it >= 0 }, body)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 20_000
    }
}
```

`OfficialCharts.kt`:

```kotlin
package com.rodrigomachado.riff.core.encore

/**
 * Rips de jogos oficiais (Harmonix, Neversoft…): o usuário não os usa. Pelo estúdio no charter e pelo ícone do
 * setlist (vistos em respostas reais da API em 2026-10-12: rb1, rb2dlc, gh5, ghm).
 */
internal object OfficialCharts {
    private val STUDIOS = listOf(
        "harmonix", "neversoft", "redoctane", "vicarious visions", "freestylegames", "budcat", "beenox",
    )
    private val ICONS = setOf(
        "gh1", "gh2", "gh80s", "gh3", "ghot", "ghotd", "ghwt", "ghm", "gh5", "ghvh", "ghsh", "ghwor", "bh", "ghl",
        "rb1", "rb2", "rb3", "rb1dlc", "rb2dlc", "rb3dlc", "rbdlc", "rbacdc", "rbbeatles", "rbtb", "rbgd", "lrb", "rbn",
    )

    fun isOfficial(charter: String?, icon: String?): Boolean =
        icon?.lowercase() in ICONS || STUDIOS.any { charter?.lowercase()?.contains(it) == true }
}
```

`EncoreClient.kt`:

```kotlin
package com.rodrigomachado.riff.core.encore

import com.rodrigomachado.riff.core.chart.ChartAudioFiles
import com.rodrigomachado.riff.core.chart.sng.SngIndex
import com.rodrigomachado.riff.core.model.Difficulty
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream

data class EncoreChart(
    val md5: String,
    val name: String,
    val artist: String,
    val charter: String?,
    val durationMs: Long?,
    val guitarDifficulties: Set<Difficulty>,
    val hasVideoBackground: Boolean,
) {
    /** Sem o vídeo de fundo (o Riff não usa) quando o mapa tem um. */
    val downloadUrl: String get() = "$FILES/$md5${if (hasVideoBackground) "_novideo" else ""}.sng"
}

data class EncorePage(val charts: List<EncoreChart>, val found: Int, val page: Int) {
    val hasMore: Boolean get() = page * PER_PAGE < found
}

class EncoreUnavailableException(cause: Throwable?) : IOException("Encore indisponível", cause)

class EncoreFormatException(message: String) : IOException(message)

/** Pesquisa e downloads do Encore (enchor.us). A API não é documentada: o formato mudou → [EncoreFormatException]. */
class EncoreClient(private val transport: HttpTransport) {
    suspend fun search(query: String, page: Int): EncorePage {
        val body = JSONObject()
            .put("search", query).put("per_page", PER_PAGE).put("page", page).put("instrument", "guitar")
            .put("difficulty", JSONObject.NULL).put("drumType", JSONObject.NULL).put("drumsReviewed", false)
            .put("sort", JSONObject.NULL).put("source", "api")
        val text = call { transport.post("$API/search", body.toString()) }.use { response ->
            if (response.code !in 200..299) throw EncoreUnavailableException(IOException("HTTP ${response.code}"))
            response.body.readBytes().decodeToString()
        }
        return parse(text, page)
    }

    /** Pelo começo do pacote (o índice); null se não deu para saber. */
    suspend fun hasSeparateGuitar(chart: EncoreChart): Boolean? = try {
        call { transport.get(chart.downloadUrl, 0L until HEAD_BYTES) }.use { response ->
            if (response.code !in 200..299) return null
            when (val index = SngIndex.parse(response.body.readBytes())) {
                is SngIndex.Result.Complete -> ChartAudioFiles.hasSeparateGuitar(index.files.map { it.name })
                is SngIndex.Result.NeedsMore -> null
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        null
    }

    suspend fun download(chart: EncoreChart, into: OutputStream, onProgress: (Float) -> Unit) {
        call { transport.get(chart.downloadUrl) }.use { response ->
            if (response.code !in 200..299) throw EncoreUnavailableException(IOException("HTTP ${response.code}"))
            val buffer = ByteArray(BUFFER_BYTES)
            var total = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = response.body.read(buffer)
                if (read < 0) break
                into.write(buffer, 0, read)
                total += read
                response.length?.let { onProgress((total.toFloat() / it).coerceAtMost(1f)) }
            }
            onProgress(1f)
        }
    }

    private suspend fun call(request: suspend () -> HttpResponse): HttpResponse = try {
        request()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        throw EncoreUnavailableException(e)
    }

    private fun parse(text: String, page: Int): EncorePage = try {
        val root = JSONObject(text)
        val data = root.getJSONArray("data")
        val charts = (0 until data.length()).map { data.getJSONObject(it) }
            .filterNot { OfficialCharts.isOfficial(it.optStringOrNull("charter"), it.optStringOrNull("icon")) }
            .filter { !it.isNull("diff_guitar") }
            .map(::chartOf)
            .distinctBy { it.md5 }
        EncorePage(charts, root.getInt("found"), page)
    } catch (e: JSONException) {
        throw EncoreFormatException("resposta inesperada do Encore: ${e.message}")
    }

    private fun chartOf(json: JSONObject): EncoreChart {
        val counts = json.optJSONObject("notesData")?.optJSONArray("noteCounts")
        val difficulties = (0 until (counts?.length() ?: 0)).map { counts!!.getJSONObject(it) }
            .filter { it.optString("instrument") == "guitar" }
            .mapNotNull { count -> Difficulty.entries.firstOrNull { it.name.equals(count.optString("difficulty"), true) } }
            .toSet()
        return EncoreChart(
            md5 = json.getString("md5"),
            name = json.optStringOrNull("name") ?: "?",
            artist = json.optStringOrNull("artist").orEmpty(),
            charter = json.optStringOrNull("charter"),
            durationMs = json.optLong("song_length").takeIf { it > 0 },
            guitarDifficulties = difficulties,
            hasVideoBackground = json.optBoolean("hasVideoBackground"),
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}

internal const val PER_PAGE = 25
private const val API = "https://api.enchor.us"
private const val FILES = "https://files.enchor.us"
private const val HEAD_BYTES = 16_384L
private const val BUFFER_BYTES = 64 * 1024
```

(Conferir no código: `Difficulty.entries` tem `EASY, MEDIUM, HARD, EXPERT` — se o nome divergir de "expert/hard/medium/easy", mapear explicitamente.)

`EncoreModule.kt`:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object EncoreModule {
    @Provides
    fun encoreClient(): EncoreClient = EncoreClient(UrlConnectionTransport())
}
```

`settings.gradle.kts`: `include(":core:encore")`. Copiar o fixture de `.superpowers/encore/search_teen_spirit.json` (resposta real enxuta, gerada no planejamento) para `core/encore/src/test/resources/encore/search_teen_spirit.json`.

- [ ] **Step 4: ver passar**

Run: `./scripts/gw :core:encore:testDebugUnitTest detekt --console=plain`
Expected: PASS.

- [ ] **Step 5: commit**

```bash
./scripts/gw :core:encore:testDebugUnitTest detekt -q && git add settings.gradle.kts core/encore && git commit -m "feat: cliente do Encore (pesquisa sem rips oficiais, marca de guitarra separada e download)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Gravar na pasta e importar um pacote (`:core:data`)

**Files:**
- Modify: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/folder/FolderSource.kt` (`FolderAccess` e `FolderWriter`)
- Modify: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/folder/SafFolderSource.kt` (`SafFolderAccess` com escrita; `SafFolderWriter`)
- Modify: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/importer/ChartImporter.kt` (`importPackage`)
- Modify: `feature/library/src/main/kotlin/com/rodrigomachado/riff/feature/library/LibraryViewModel.kt` (`persistAccess`)
- Test: `core/data/src/test/.../importer/FolderChartImporterTest.kt` (caso novo), fakes de `FolderAccess` nos testes que a usam

**Interfaces:**
- Produces:
  - `interface FolderAccess { fun persistAccess(folderUri: String); fun canWrite(folderUri: String): Boolean }` (substitui `persistReadAccess`; pede leitura **e** escrita)
  - `interface FolderWriter { suspend fun createFile(parentUri: String, subfolder: String, name: String): String; fun openOutput(fileUri: String): OutputStream; suspend fun rename(fileUri: String, newName: String): String; suspend fun delete(fileUri: String) }` — `createFile` cria a subpasta se faltar e devolve o URI do arquivo; nome repetido ganha ` (2)`, ` (3)`…
  - `ChartImporter.importPackage(fileUri: String, name: String): Chart` (importa um `.sng` sem varrer a pasta; lança `InvalidSngException`/`ChartParseException`)

- [ ] **Step 1: teste que falha**

```kotlin
@Test
fun aSinglePackageIsImportedWithoutTouchingTheRestOfTheLibrary() = runTest {
    tree.file("root/a/notes.chart", validChart())
    importer().import("root")
    tree.file("root/Baixados pelo Riff/Teen.sng", SngWriter.write(mapOf("name" to "Teen"), mapOf("notes.chart" to validChart("x"))))

    val chart = importer().importPackage("root/Baixados pelo Riff/Teen.sng", "Teen.sng")

    assertEquals("Teen", chart.title)
    assertEquals(2, repository.charts.size) // o mapa da pasta "a" continua
}
```

(`validChart("x")` muda o conteúdo para outro id; se o helper não tiver o parâmetro, acrescentar.)

- [ ] **Step 2: ver falhar**

Run: `./scripts/gw :core:data:testDebugUnitTest --tests '*FolderChartImporterTest' --console=plain`
Expected: FAIL (`importPackage` não existe).

- [ ] **Step 3: implementar**

No `FolderChartImporter`:

```kotlin
override suspend fun importPackage(fileUri: String, name: String): Chart = withContext(dispatcher) {
    val folder = FolderEntry(SngRef.packageUri(fileUri), name.removeSuffix(".sng"), isDirectory = true, sizeBytes = 0)
    val chart = load(folder, source.listChildren(folder.uri))
    // O id da importação mais recente: a próxima limpeza (Reimportar) não apaga o mapa recém-baixado.
    repository.saveScanned(chart, now())
    chart
}
```

Atenção: o `lastSeenScanId = now()` de um download é maior que o da última importação; o `deleteNotSeenIn(scanId)` só roda no fim de uma importação completa, que vê o arquivo de novo e regrava — sem perda.

`SafFolderAccess`:

```kotlin
override fun persistAccess(folderUri: String) {
    val chosen = Uri.parse(folderUri)
    val resolver = context.contentResolver
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    try {
        resolver.takePersistableUriPermission(chosen, flags)
    } catch (e: SecurityException) {
        resolver.takePersistableUriPermission(chosen, Intent.FLAG_GRANT_READ_URI_PERMISSION) // provedor só leitura
    }
    resolver.persistedUriPermissions.filter { it.uri != chosen }
        .forEach { resolver.releasePersistableUriPermission(it.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
}

override fun canWrite(folderUri: String): Boolean =
    context.contentResolver.persistedUriPermissions.any { it.uri == Uri.parse(folderUri) && it.isWritePermission }
```

`SafFolderWriter` (`@Inject constructor(@ApplicationContext context)`), com `DocumentsContract`:

```kotlin
override suspend fun createFile(parentUri: String, subfolder: String, name: String): String = withContext(Dispatchers.IO) {
    val tree = Uri.parse(parentUri)
    val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    val folder = childNamed(tree, parent, subfolder)
        ?: DocumentsContract.createDocument(resolver, parent, Document.MIME_TYPE_DIR, subfolder)
        ?: throw IOException("não deu para criar $subfolder")
    val free = freeName(tree, folder, name)
    (DocumentsContract.createDocument(resolver, folder, "application/octet-stream", free)
        ?: throw IOException("não deu para criar $free")).toString()
}

override fun openOutput(fileUri: String): OutputStream =
    resolver.openOutputStream(Uri.parse(fileUri), "w") ?: throw IOException("não deu para gravar $fileUri")

override suspend fun rename(fileUri: String, newName: String): String = withContext(Dispatchers.IO) {
    (DocumentsContract.renameDocument(resolver, Uri.parse(fileUri), newName)
        ?: throw IOException("não deu para renomear")).toString()
}

override suspend fun delete(fileUri: String) {
    withContext(Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(resolver, Uri.parse(fileUri)) } }
}
```

```kotlin
private val resolver get() = context.contentResolver

/** Filhos de [folder] (documento dentro da árvore [tree]): nome → URI. */
private fun children(tree: Uri, folder: Uri): Map<String, Uri> {
    val id = DocumentsContract.getDocumentId(folder)
    val query = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
    val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME)
    return resolver.query(query, projection, null, null, null)?.use { cursor ->
        buildMap {
            while (cursor.moveToNext()) {
                put(cursor.getString(1).orEmpty(), DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)))
            }
        }
    } ?: throw FolderAccessLostException("sem acesso a $folder")
}

private fun childNamed(tree: Uri, folder: Uri, name: String): Uri? = children(tree, folder)[name]

/** "Nome.sng.part" livre: com o nome final ocupado, "Nome (2).sng.part", "Nome (3).sng.part"… */
private fun freeName(tree: Uri, folder: Uri, name: String): String {
    val taken = children(tree, folder).keys.map { it.removeSuffix(".part") }.toSet()
    val final = name.removeSuffix(".part")
    val base = final.substringBeforeLast('.')
    val extension = final.substringAfterLast('.', "")
    val free = generateSequence(1) { it + 1 }
        .map { n -> if (n == 1) final else "$base ($n).$extension" }
        .first { it !in taken }
    return "$free.part"
}
```

O `.part` é o nome final + `.part`; o `ChartDownloader` renomeia para o nome livre sem o sufixo (`createFile` devolve o URI; o nome livre escolhido é o `DISPLAY_NAME` dele menos `.part`).

`LibraryViewModel.onFolderPicked` chama `folderAccess.persistAccess(uri)`. Os fakes de `FolderAccess` dos testes ganham `canWrite`. `DataBindsModule`: `@Binds abstract fun folderWriter(impl: SafFolderWriter): FolderWriter`.

- [ ] **Step 4: ver passar**

Run: `./scripts/gw :core:data:testDebugUnitTest :feature:library:testDebugUnitTest detekt --console=plain`
Expected: PASS.

- [ ] **Step 5: commit**

```bash
./scripts/gw :core:data:testDebugUnitTest :feature:library:testDebugUnitTest detekt -q && git add core/data feature/library && git commit -m "feat: escrita na pasta da biblioteca por SAF e importação de um pacote só

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Baixar para a biblioteca (`:core:data`)

**Files:**
- Create: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/download/ChartDownloader.kt`
- Modify: `core/data/build.gradle.kts` (`implementation(project(":core:encore"))`)
- Modify: `core/data/src/main/kotlin/com/rodrigomachado/riff/core/data/DataModule.kt`
- Test: `core/data/src/test/.../download/ChartDownloaderTest.kt`

**Interfaces:**
- Consumes: `EncoreClient.download`, `EncoreChart` (Task 5); `FolderWriter`, `ChartImporter.importPackage` (Task 6); `EncoreDownloads.remember` (Task 2); `LibraryFolderStore.rootUri`; `TrackMatchRepository.save(trackUri, chartId, 1f, isManual = true)`; `SngPackage` (Task 1).
- Produces:
  - `interface ChartDownloader { suspend fun download(chart: EncoreChart, matchTrackUri: String?, onProgress: (Float) -> Unit): Chart }`
  - `class ChartDownloadException(val reason: Reason, cause: Throwable?) : Exception` com `enum Reason { NO_FOLDER, NO_WRITE_ACCESS, NETWORK, BROKEN_FILE, STORAGE }`
  - `const val DOWNLOADS_FOLDER = "Baixados pelo Riff"`

- [ ] **Step 1: testes que falham**

Fakes: `FakeWriter` (arquivos num mapa, registra `deleted`), `FakeEncore` (`download` escreve bytes dados ou lança), importador real sobre a árvore em memória + `SngFolderSource`.

```kotlin
@Test
fun aDownloadedPackageEntersTheLibraryAndIsRemembered() = runTest {
    val chart = downloader(bytes = validSng("Teen")).download(encoreChart("m5"), matchTrackUri = null) {}

    assertEquals("Teen", chart.title)
    assertEquals(listOf("root/Baixados pelo Riff/Nirvana - Teen.sng"), writer.files.keys.toList())
    assertEquals(setOf("m5"), downloads.md5s.first())
}

@Test
fun aDownloadFromTheSpotifyScreenIsMatchedWithTheTrack() = runTest {
    val chart = downloader(bytes = validSng("Teen")).download(encoreChart("m5"), matchTrackUri = "spotify:track:x") {}

    assertEquals(Triple("spotify:track:x", chart.id, true), matches.saved.single())
}

@Test
fun aBrokenDownloadLeavesNothingBehind() = runTest {
    val error = assertThrows(ChartDownloadException::class.java) {
        runBlocking { downloader(bytes = "lixo".toByteArray()).download(encoreChart("m5"), null) {} }
    }
    assertEquals(ChartDownloadException.Reason.BROKEN_FILE, error.reason)
    assertEquals(emptyMap<String, ByteArray>(), writer.files)
}

@Test
fun leavingDuringTheDownloadDeletesThePartialFile() = runTest {
    val job = launch { downloader(hang = true).download(encoreChart("m5"), null) {} }
    runCurrent()
    job.cancelAndJoin()

    assertEquals(emptyMap<String, ByteArray>(), writer.files) // Revisão §4
}

@Test
fun withoutWriteAccessNothingIsDownloaded() = runTest {
    access.writable = false
    val error = assertThrows(ChartDownloadException::class.java) {
        runBlocking { downloader(bytes = validSng("Teen")).download(encoreChart("m5"), null) {} }
    }
    assertEquals(ChartDownloadException.Reason.NO_WRITE_ACCESS, error.reason)
}
```

- [ ] **Step 2: ver falhar**

Run: `./scripts/gw :core:data:testDebugUnitTest --tests '*ChartDownloaderTest' --console=plain`
Expected: FAIL (compilação).

- [ ] **Step 3: implementar**

```kotlin
package com.rodrigomachado.riff.core.data.download

/** Baixa um mapa do Encore para "Baixados pelo Riff" e o põe na biblioteca (e casado com a faixa, se veio dela). */
class EncoreChartDownloader @Inject constructor(
    private val encore: EncoreClient,
    private val folders: LibraryFolderStore,
    private val access: FolderAccess,
    private val writer: FolderWriter,
    private val importer: ChartImporter,
    private val downloads: EncoreDownloads,
    private val matches: TrackMatchRepository,
    private val opener: RandomReadOpener,
) : ChartDownloader {
    override suspend fun download(chart: EncoreChart, matchTrackUri: String?, onProgress: (Float) -> Unit): Chart {
        val root = folders.rootUri.first() ?: throw ChartDownloadException(Reason.NO_FOLDER, null)
        if (!access.canWrite(root)) throw ChartDownloadException(Reason.NO_WRITE_ACCESS, null)
        val finalName = fileName(chart)
        val part = failing(Reason.STORAGE) { writer.createFile(root, DOWNLOADS_FOLDER, "$finalName.part") }
        try {
            failing(Reason.NETWORK) { writer.openOutput(part).use { encore.download(chart, it, onProgress) } }
            failing(Reason.BROKEN_FILE) { SngPackage.open(opener.open(part)).close() }
            val file = failing(Reason.STORAGE) { writer.rename(part, finalName) }
            val imported = failing(Reason.BROKEN_FILE) { importer.importPackage(file, finalName) }
            downloads.remember(imported.id, chart.md5)
            matchTrackUri?.let { matches.save(it, imported.id, confidence = 1f, isManual = true) }
            return imported
        } catch (e: Throwable) {
            withContext(NonCancellable) { writer.delete(part) }
            throw e
        }
    }

    private inline fun <T> failing(reason: Reason, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ChartDownloadException) {
        throw e
    } catch (e: Exception) {
        throw ChartDownloadException(reason, e)
    }

    /** "Artista - Nome.sng", sem os caracteres que as pastas não aceitam. */
    private fun fileName(chart: EncoreChart): String =
        "${chart.artist} - ${chart.name}".replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(MAX_NAME) + ".sng"

    private companion object {
        const val MAX_NAME = 120
    }
}
```

(Ajustar os `inline` com lambdas suspensas: `failing` vira `private suspend inline fun`; se o detekt reclamar de `TooGenericExceptionCaught`, anotar com o motivo "cada etapa vira um motivo na tela". O `rename` apaga o `.part` original no provedor; se o arquivo já foi renomeado e a importação falhar, apagar o renomeado: guardar o URI atual numa `var current = part` e apagar `current` no `catch`.)

`DataBindsModule`: `@Binds abstract fun chartDownloader(impl: EncoreChartDownloader): ChartDownloader`.

- [ ] **Step 4: ver passar**

Run: `./scripts/gw :core:data:testDebugUnitTest detekt --console=plain`
Expected: PASS.

- [ ] **Step 5: commit**

```bash
./scripts/gw :core:data:testDebugUnitTest detekt -q && git add core/data && git commit -m "feat: baixar um mapa do Encore para Baixados pelo Riff e pôr na biblioteca

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Tela de pesquisa (`:feature:search`, módulo novo)

**Files:**
- Create: `feature/search/build.gradle.kts`, `feature/search/src/main/AndroidManifest.xml`
- Modify: `settings.gradle.kts` (`include(":feature:search")`)
- Create: `feature/search/src/main/kotlin/com/rodrigomachado/riff/feature/search/SearchRoute.kt`
- Create: `feature/search/src/main/kotlin/com/rodrigomachado/riff/feature/search/SearchViewModel.kt`
- Create: `feature/search/src/main/kotlin/com/rodrigomachado/riff/feature/search/SearchScreen.kt`
- Create: `feature/search/src/main/res/values/strings.xml`, `values-en/strings.xml`
- Test: `feature/search/src/test/.../SearchViewModelTest.kt`, `SearchContentTest.kt`, `SearchScreenshotTest.kt`

**Interfaces:**
- Consumes: `EncoreClient`, `EncorePage`, `EncoreChart`, `EncoreUnavailableException`, `EncoreFormatException` (Task 5); `ChartDownloader`, `ChartDownloadException` (Task 7); `EncoreDownloads.md5s` (Task 2); `TrackMatcher`/`TrackQuery` de `:core:chart` para ordenar quando vem do Spotify; `FolderAccess.canWrite` + `LibraryFolderStore`.
- Produces:
  - `@Serializable data class SearchRoute(val query: String? = null, val matchTrackUri: String? = null, val matchTrackTitle: String? = null, val matchTrackArtist: String? = null, val matchTrackDurationMs: Long? = null)`
  - `SearchScreen(onBack: () -> Unit, onPlay: (chartId: String, difficulty: Difficulty) -> Unit, onMatched: () -> Unit, modifier)`
  - `SearchUiState` (abaixo)

```kotlin
data class SearchUiState(
    val query: String = "",
    val results: List<ResultItem> = emptyList(),
    val isSearching: Boolean = false,
    val hasMore: Boolean = false,
    val error: SearchError? = null,
    val canWrite: Boolean = true,
    val matched: Boolean = false,
)

data class ResultItem(val chart: EncoreChart, val state: ItemState, val separateGuitar: Boolean?)

sealed interface ItemState {
    data object Available : ItemState
    data class Downloading(val progress: Float) : ItemState
    data class Ready(val chartId: String) : ItemState // "Já na biblioteca" / Jogar
    data class Failed(val reason: ChartDownloadException.Reason) : ItemState
}

enum class SearchError { UNAVAILABLE, FORMAT_CHANGED, NOTHING_FOUND }
```

- [ ] **Step 1: testes que falham**

`SearchViewModelTest` (fakes de `EncoreClient` via um `HttpTransport` falso ou, mais simples, uma interface `EncoreSearch` — usar o `EncoreClient` real com `FakeTransport` e o fixture copiado para `feature/search/src/test/resources`):

```kotlin
@Test
fun fromTheSpotifyScreenItSearchesRightAwayAndPutsTheClosestFirst() = runTest {
    val vm = viewModel(SearchRoute(query = "Nirvana Smells Like Teen Spirit", matchTrackUri = "spotify:track:x",
        matchTrackTitle = "Smells Like Teen Spirit", matchTrackArtist = "Nirvana", matchTrackDurationMs = 301_000))
    advanceUntilIdle()

    assertEquals("Nirvana Smells Like Teen Spirit", vm.uiState.value.query)
    assertEquals("Smells Like Teen Spirit", vm.uiState.value.results.first().chart.name)
}

@Test
fun downloadingShowsProgressAndThenReady() = runTest {
    downloader.progressSteps = listOf(0.5f)
    val vm = viewModel(SearchRoute(query = "teen spirit"))
    advanceUntilIdle()
    val chart = vm.uiState.value.results.first().chart
    downloader.gate = CompletableDeferred()

    vm.download(chart)
    runCurrent()
    assertEquals(ItemState.Downloading(0.5f), vm.uiState.value.results.first().state)

    downloader.gate!!.complete(Unit)
    advanceUntilIdle()
    assertEquals(ItemState.Ready("baixado-${chart.md5}"), vm.uiState.value.results.first().state)
}

@Test
fun aChartAlreadyInTheLibraryIsReadyWithoutDownloading() = runTest {
    downloads.owned["a07d209baccd2d58042d81b869a0077e"] = "ja-tenho"
    val vm = viewModel(SearchRoute(query = "teen spirit"))
    advanceUntilIdle()

    assertEquals(ItemState.Ready("ja-tenho"), vm.uiState.value.results.first().state)
    assertEquals(0, downloader.calls)
}

@Test
fun downloadingFromSpotifyMatchesAndReturns() = runTest {
    val vm = viewModel(SearchRoute(query = "teen spirit", matchTrackUri = "spotify:track:x", matchTrackTitle = "Smells Like Teen Spirit"))
    advanceUntilIdle()

    vm.download(vm.uiState.value.results.first().chart)
    advanceUntilIdle()

    assertEquals("spotify:track:x", downloader.lastMatchTrackUri)
    assertEquals(true, vm.uiState.value.matched)
}

@Test
fun withoutWriteAccessResultsShowButDownloadExplains() = runTest {
    access.writable = false
    val vm = viewModel(SearchRoute(query = "teen spirit"))
    advanceUntilIdle()

    assertEquals(false, vm.uiState.value.canWrite)
    assertEquals(2, vm.uiState.value.results.size)
    vm.download(vm.uiState.value.results.first().chart)
    assertEquals(0, downloader.calls)

    access.writable = true
    vm.onWriteAccessGranted("root")
    assertEquals(true, vm.uiState.value.canWrite)
}

@Test
fun networkFailureAndAChangedApiHaveTheirOwnMessages() = runTest {
    transport.failure = java.net.UnknownHostException("api.enchor.us")
    val offline = viewModel(SearchRoute(query = "x"))
    advanceUntilIdle()
    assertEquals(SearchError.UNAVAILABLE, offline.uiState.value.error)

    transport.failure = null
    transport.postBody = """{"results": []}"""
    val changed = viewModel(SearchRoute(query = "x"))
    advanceUntilIdle()
    assertEquals(SearchError.FORMAT_CHANGED, changed.uiState.value.error)
}

@Test
fun loadMoreAppendsTheNextPage() = runTest {
    transport.postBody = fixture.replace("\"found\": 8", "\"found\": 60")
    val vm = viewModel(SearchRoute(query = "teen spirit"))
    advanceUntilIdle()
    assertEquals(true, vm.uiState.value.hasMore)

    vm.loadMore()
    advanceUntilIdle()

    assertEquals(2, JSONObject(transport.lastJson!!).getInt("page"))
    assertEquals(4, vm.uiState.value.results.size) // a mesma página de novo, só para o teste
}

@Test
fun theSeparateGuitarMarkArrivesAfterTheResults() = runTest {
    transport.rangeBody = SngWriter.write(mapOf("name" to "M"), mapOf("guitar.ogg" to ByteArray(4), "song.ogg" to ByteArray(4)))
    val vm = viewModel(SearchRoute(query = "teen spirit"))
    advanceUntilIdle()

    assertEquals(true, vm.uiState.value.results.first().separateGuitar)
}
Fakes (no próprio arquivo de teste): `FakeTransport` (`postBody` = fixture, `failure`, `rangeBody`, `lastJson`), `FakeDownloader : ChartDownloader` (`progressSteps`, `gate: CompletableDeferred<Unit>?`, `calls`, `lastMatchTrackUri`; devolve um `Chart` com id `"baixado-<md5>"`), `FakeEncoreDownloads` (`owned: MutableMap<md5, chartId>`; `md5s` = `flowOf(owned.keys)`), `FakeFolderAccess` (`writable`). `viewModel(route)` monta o `SavedStateHandle` com os campos da rota e `EncoreClient(transport)`; `Dispatchers.setMain(UnconfinedTestDispatcher())` no `@Before`. "Ready" sem download: `downloads.chartIdOf(md5)` (Task 2).

`SearchContentTest`: campo + **Pesquisar** chama a busca; item **Baixar** com som CONFIRM; item **Já na biblioteca** mostra **Jogar**; marca "Guitarra separada"; mensagens de erro; aviso sem escrita com o botão **Permitir gravar na pasta**.

- [ ] **Step 2: ver falhar**

Run: `./scripts/gw :feature:search:testDebugUnitTest --console=plain`
Expected: FAIL (módulo não existe).

- [ ] **Step 3: implementar**

`feature/search/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.riff.android.feature)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.rodrigomachado.riff.feature.search"
}

dependencies {
    implementation(project(":core:encore"))
    implementation(project(":core:chart"))
    implementation(libs.androidx.activity.compose) // seletor de pasta para pedir a escrita
    testImplementation(testFixtures(project(":core:chart")))
}
```

`SearchViewModel` (`@HiltViewModel`, recebe `SavedStateHandle`, `EncoreClient`, `ChartDownloader`, `EncoreDownloads`, `FolderAccess`, `LibraryFolderStore`):

```kotlin
fun onQueryChange(text: String) = state.update { it.copy(query = text) }

fun search() {
    val query = state.value.query.trim().ifEmpty { return }
    page = 1
    load(query, append = false)
}

fun loadMore() = load(state.value.query.trim(), append = true)

private fun load(query: String, append: Boolean) {
    searching?.cancel()
    searching = viewModelScope.launch {
        state.update { it.copy(isSearching = true, error = null) }
        try {
            val result = encore.search(query, if (append) page + 1 else 1).also { page = it.page }
            val owned = downloads.md5s.first()
            val items = ordered(result.charts).map { chart ->  // readyState é suspend: usar map dentro da corrotina
                ResultItem(chart, owned.readyState(chart), separateGuitar = null)
            }
            state.update {
                it.copy(
                    results = if (append) it.results + items else items,
                    hasMore = result.hasMore,
                    isSearching = false,
                    error = if (!append && items.isEmpty()) SearchError.NOTHING_FOUND else null,
                )
            }
            items.forEach { checkSeparateGuitar(it.chart) }
        } catch (e: EncoreFormatException) {
            Log.w(TAG, "Resposta do Encore mudou", e)
            state.update { it.copy(isSearching = false, error = SearchError.FORMAT_CHANGED) }
        } catch (e: EncoreUnavailableException) {
            state.update { it.copy(isSearching = false, error = SearchError.UNAVAILABLE) }
        }
    }
}

private val guitarChecks = Semaphore(MAX_PEEKS)
private val checked = mutableSetOf<String>()

/** No máximo 3 ao mesmo tempo; cada md5 uma vez por tela. A marca chega depois dos resultados. */
private fun checkSeparateGuitar(chart: EncoreChart) {
    if (!checked.add(chart.md5)) return
    viewModelScope.launch {
        val separate = guitarChecks.withPermit { encore.hasSeparateGuitar(chart) } ?: return@launch
        state.update { s ->
            s.copy(results = s.results.map { if (it.chart.md5 == chart.md5) it.copy(separateGuitar = separate) else it })
        }
    }
}

private fun setItem(md5: String, item: ItemState) = state.update { s ->
    s.copy(results = s.results.map { if (it.chart.md5 == md5) it.copy(state = item) else it })
}

private suspend fun Set<String>.readyState(chart: EncoreChart): ItemState =
    if (chart.md5 in this) downloads.chartIdOf(chart.md5)?.let { ItemState.Ready(it) } ?: ItemState.Available
    else ItemState.Available

fun download(chart: EncoreChart) {
    if (!state.value.canWrite) return
    downloadsInFlight[chart.md5] = viewModelScope.launch {
        setItem(chart.md5, ItemState.Downloading(0f))
        try {
            val imported = downloader.download(chart, matchTrackUri) { p -> setItem(chart.md5, ItemState.Downloading(p)) }
            setItem(chart.md5, ItemState.Ready(imported.id))
            if (matchTrackUri != null) state.update { it.copy(matched = true) }
        } catch (e: ChartDownloadException) {
            setItem(chart.md5, ItemState.Failed(e.reason))
        }
    }
}

fun onWriteAccessGranted(uri: String) { folderAccess.persistAccess(uri); refreshWriteAccess() }
```

(A ordenação vinda do Spotify usa `TrackMatcher` com `TrackQuery(title, artist, durationMs)` sobre cada resultado convertido para o tipo que o matcher aceita — ver a assinatura em `core/chart/.../match/TrackMatcher.kt` e adaptar: ordenar por `confidence` decrescente. Sem faixa, a ordem do Encore.)

`SearchScreen`: `PosterTitle("Pesquisar")`, `TextButton` Voltar (sem som, a navegação soa), `OutlinedTextField` com `ImeAction.Search` + `PosterButton("Pesquisar", sound = NAVIGATE)`; `LazyColumn` com `TrackCard` por resultado (título, "Artista · charter", duração m:ss, dificuldades em selos, selo "Guitarra separada") e o botão do estado (`Baixar` CONFIRM / barra de progresso / `Jogar` CONFIRM / `Tentar de novo`); "Carregar mais" (NAVIGATE) no fim; sem escrita: cartão "Para baixar, o Riff precisa gravar na pasta da biblioteca" com **Permitir** abrindo `OpenDocumentTree` com a pasta atual (`ActivityResultContracts.OpenDocumentTree().createIntent` com `DocumentsContract.EXTRA_INITIAL_URI`); Jogar abre o `DifficultyDialog` com `chart.guitarDifficulties` (ou as da biblioteca). `MatchSavedReturn(state.matched, onMatched)` como na Biblioteca. Strings pt/en:

| chave | pt | en |
|---|---|---|
| search_title | Pesquisar | Search |
| search_hint | Música ou artista | Song or artist |
| search_action | Pesquisar | Search |
| search_download | Baixar | Download |
| search_play | Jogar | Play |
| search_in_library | Já na biblioteca | In your library |
| search_separate_guitar | Guitarra separada | Separate guitar |
| search_more | Carregar mais | Load more |
| search_unavailable | Não deu para falar com o Encore. | Could not reach Encore. |
| search_format_changed | A pesquisa do Encore mudou; o Riff precisa ser atualizado. | Encore search changed; Riff needs an update. |
| search_nothing | Nada encontrado para "%1$s". | Nothing found for "%1$s". |
| search_write_needed | Para baixar, o Riff precisa gravar na pasta da biblioteca. | To download, Riff needs to write to your library folder. |
| search_allow | Permitir | Allow |
| search_failed_network | O download falhou. | The download failed. |
| search_failed_broken | O arquivo baixado veio com defeito. | The downloaded file is broken. |
| search_failed_storage | Não deu para gravar na pasta. | Could not write to the folder. |
| search_retry | Tentar de novo | Try again |
| search_credit | Mapas do Encore (enchor.us), feitos pela comunidade. | Charts from Encore (enchor.us), made by the community. |

- [ ] **Step 4: ver passar**

Run: `./scripts/gw detekt -PdetektAutoCorrect -q; ./scripts/gw :feature:search:testDebugUnitTest :feature:search:recordRoborazziDebug detekt --console=plain`
Expected: PASS; screenshot `Search_results.png` gravado e conferido à mão (resultado da comunidade, selo "Guitarra separada", item baixando, item "Já na biblioteca").

- [ ] **Step 5: commit**

```bash
./scripts/gw :feature:search:testDebugUnitTest :feature:search:verifyRoborazziDebug detekt -q && git add settings.gradle.kts feature/search && git commit -m "feat: tela de pesquisa do Encore com download, progresso e Já na biblioteca

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Ligar a pesquisa à Biblioteca e ao Tocando agora

**Files:**
- Modify: `feature/library/src/main/kotlin/com/rodrigomachado/riff/feature/library/LibraryScreen.kt` (botão **Pesquisar**; `onSearch: (TrackToMatch?) -> Unit`)
- Modify: `feature/nowplaying/src/main/kotlin/com/rodrigomachado/riff/feature/nowplaying/NowPlayingScreen.kt` (**Pesquisar no Encore** em `NoMatch` e `Suggested`; `onSearch: (PlaybackState) -> Unit`)
- Modify: `feature/nowplaying/src/main/res/values*/strings.xml` (`nowplaying_search` = "Pesquisar no Encore" / "Search Encore"); `feature/library/src/main/res/values*/strings.xml` (`library_search` = "Pesquisar" / "Search")
- Modify: `app/src/main/kotlin/com/rodrigomachado/riff/RiffNavHost.kt` (rota `SearchRoute`; `onMatched` silencioso como na Biblioteca)
- Modify: `app/build.gradle.kts` (`implementation(project(":feature:search"))`), `app/src/main/AndroidManifest.xml` (`<uses-permission android:name="android.permission.INTERNET" />`)
- Test: `LibraryContentTest` e `NowPlayingContentTest` (casos novos), screenshots regravados

**Interfaces:**
- Consumes: `SearchRoute`, `SearchScreen` (Task 8).

- [ ] **Step 1: testes que falham**

```kotlin
// LibraryContentTest
@Test
fun searchOpensTheEncoreSearchWithANavigateSound() {
    var searched = false
    show(LibraryUiState(isLoading = false, hasFolder = true, charts = listOf(chart("a"))), onSearch = { searched = true })
    composeRule.onNodeWithText("Pesquisar").performClick()
    assertEquals(true, searched)
    assertEquals(listOf(MenuSound.NAVIGATE), sounds)
}

// NowPlayingContentTest
@Test
fun aTrackWithoutAChartOffersToSearchEncoreForIt() {
    var searchedFor: PlaybackState? = null
    show(NowPlayingUiState.Playing(playing(MatchStatus.NoMatch)), onSearch = { searchedFor = it })
    composeRule.onNodeWithText("Pesquisar no Encore").performClick()
    assertEquals("Bohemian Rhapsody - Remastered 2011", searchedFor?.title)
}
```

(O `show` dos dois testes ganha o parâmetro `onSearch`, com default vazio.)

- [ ] **Step 2: ver falhar**

Run: `./scripts/gw :feature:library:testDebugUnitTest :feature:nowplaying:testDebugUnitTest --console=plain`
Expected: FAIL (parâmetros e botões não existem).

- [ ] **Step 3: implementar**

- `LibraryContent` ganha `onSearch: () -> Unit` e um `PosterButton(stringResource(R.string.library_search), onClick = onSearch, sound = MenuSound.NAVIGATE)` na mesma `Row` de **Reimportar** (`ChartList`) e no estado "Nenhum mapa encontrado". `LibraryScreen(onSearch: (TrackToMatch?) -> Unit)` passa `{ onSearch(match) }`.
- `NowPlayingContent` ganha `onSearch: (PlaybackState) -> Unit = {}`; em `NoMatch` e `Suggested`, `TextButton(onClick = withMenuSound(MenuSound.NAVIGATE) { onSearch(track) })` com `nowplaying_search`. Se o `Playing` passar de 60 linhas, extrair `SearchEncore(track, onSearch)` como foi feito com `ChooseOther`.
- `RiffNavHost`:

```kotlin
composable<SearchRoute> {
    SearchScreen(
        onBack = dropUnlessResumed { navController.popBackStack() },
        onPlay = dropUnlessResumed { chartId, difficulty -> navController.navigate(GameplayRoute(chartId, difficulty.name)) },
        // Baixar já soou como confirmação: a volta automática ao Tocando agora fica em silêncio.
        onMatched = dropUnlessResumed {
            back.silenceNextReturn()
            navController.popBackStack(NowPlayingRoute, inclusive = false)
        },
    )
}
```

No `NowPlayingScreen` do NavHost: `onSearch = dropUnlessResumed { t -> navController.navigate(SearchRoute("${t.artist} ${t.title}", t.trackUri, t.title, t.artist, t.durationMs)) }`; na Biblioteca: `onSearch = dropUnlessResumed { match -> navController.navigate(SearchRoute(matchTrackUri = match?.trackUri, matchTrackTitle = match?.title)) }`. Se o `RiffNavHost` passar de 60 linhas, mover a rota para `searchDestinations(navController, back)` como `settingsDestinations`.
- Manifesto: `INTERNET`.

- [ ] **Step 4: ver passar**

Run: `./scripts/gw detekt -PdetektAutoCorrect -q; ./scripts/gw testDebugUnitTest detekt verifyRoborazziDebug --continue --console=plain`
Expected: os testes passam; o `verifyRoborazziDebug` acusa só as telas com o botão novo (Biblioteca e Tocando agora sem mapa) — conferir as imagens e regravar só esses módulos com `recordRoborazziDebug`.

- [ ] **Step 5: commit**

```bash
./scripts/gw testDebugUnitTest verifyRoborazziDebug detekt -q && git add app feature && git commit -m "feat: Pesquisar na Biblioteca e Pesquisar no Encore no Tocando agora

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: README, pendências e verificação no aparelho

**Files:**
- Modify: `README.md` (seção "Pesquisar e baixar mapas"; `.sng` em "Importar mapas" e na tabela de módulos com `:core:encore` e `:feature:search`; crédito ao Encore)
- Modify: `docs/pendencias.md` (tirar a ideia "ler `.sng`"; registrar: API do Encore não documentada; filtro de oficiais por lista)
- Modify: `docs/superpowers/specs/2026-10-12-pesquisa-encore-design.md` (Status: implementado)

- [ ] **Step 1: README**

Em "Importar mapas", item 4 vira: "Mapas `.sng` (o formato do Encore e do YARG) são lidos direto: é só colocar o arquivo na pasta." Seção nova depois de "Importar mapas":

```markdown
## Pesquisar e baixar mapas

- **Biblioteca → Pesquisar:** digite a música ou o artista. A lista mostra os mapas da comunidade no Encore (enchor.us), sem rips de jogos oficiais, com as dificuldades da guitarra e a marca **Guitarra separada** quando o pacote traz a guitarra à parte. **Baixar** grava o `.sng` em "Baixados pelo Riff", dentro da pasta da biblioteca, e o mapa já entra na lista.
- **Tocando agora → Pesquisar no Encore** (faixa sem mapa ou só com sugestão): a pesquisa já vem com o artista e o título; o mapa baixado fica casado com a faixa.
- Na primeira vez, o Riff pede para escolher a pasta de novo, agora com permissão de gravar nela.
- O pacote vem com o áudio da música (uso pessoal). A API do Encore não é oficial: se mudar, a pesquisa avisa que o Riff precisa ser atualizado.
```

Créditos: "- Mapas baixados pela pesquisa: Encore (enchor.us), feitos pela comunidade do Clone Hero."

- [ ] **Step 2: suíte completa e release**

Run: `./scripts/gw --stop; ./scripts/gw test testDebugUnitTest detekt lint verifyRoborazziDebug :app:assembleDebug :app:assembleRelease --console=plain && scripts/conferir_r8_spotify.sh`
Expected: BUILD SUCCESSFUL e "ok: ReleaseSpotifyLocator mantém o construtor". Se o R8 remover classes do `org.json` ou do `HttpURLConnection` (não deve: são da plataforma), registrar e ajustar `app/proguard-rules.pro`.

- [ ] **Step 3: no aparelho** (`adb install -r --user 0 …app-release.apk`)

1. Biblioteca → Pesquisar "smells like teen spirit": aparecem os da comunidade (metalli3212, highfine), sem Harmonix/Neversoft.
2. Permitir gravar (escolher a mesma pasta) → Baixar → progresso → Jogar → a música toca do `.sng`, com voltar 0,5 s funcionando.
3. Reimportar: o mapa continua e a pesquisa ainda mostra "Já na biblioteca".
4. Tocando agora com uma faixa sem mapa → Pesquisar no Encore → Baixar → volta com "Mapa encontrado".
5. Modo avião → Pesquisar → "Não deu para falar com o Encore."

- [ ] **Step 4: commit**

```bash
git add README.md docs && git commit -m "docs: README da pesquisa do Encore e leitura de .sng

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
