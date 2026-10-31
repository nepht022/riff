# Riff no Windows — Etapa 2a: Biblioteca no PC — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** No PC, "Sozinho" abre a Biblioteca: escolher uma pasta do PC, importar os mapas (pastas e `.sng`), ver a lista, filtrar e excluir; banco e preferências funcionam no celular e no PC. O celular fica igual.

**Architecture:** `:core:datastore`, `:core:database`, `:core:data` e `:feature:library` viram Kotlin Multiplatform (Android + `desktop`). O código sem Android vai para `commonMain`; o que usa SAF, `Context` e APIs do Android fica em `androidMain`; o PC ganha implementações com `java.io` em `desktopMain`. O **Hilt continua no celular**: as classes comuns mantêm `@Inject constructor` (javax.inject é JVM, visível no código comum de alvos Android + JVM), os módulos Hilt ficam em `androidMain`, e cada ViewModel comum ganha uma subclasse `@HiltViewModel` fina em `androidMain`. No PC, um `DesktopGraph` monta as dependências à mão e a navegação usa o Navigation Compose multiplataforma com as mesmas rotas `@Serializable`. A separação por IA (etapa 4) entra no PC como "indisponível" (implementações vazias).

**Tech Stack:** Room KMP 2.8.5 (KSP por alvo; no PC `androidx.sqlite:sqlite-bundled`), DataStore Preferences KMP 1.2.1 (okio), lifecycle-viewmodel e navigation-compose multiplataforma da JetBrains (versões que acompanham a CMP 1.12.2), Compose Multiplatform 1.12.2.

**Spec:** `docs/superpowers/specs/2026-10-31-riff-windows-design.md` (§6, etapa 2; divisão em 2a–2d combinada com o usuário em 2026-10-31).

## Global Constraints

- "O Android continua funcionando igual: nenhuma diferença para quem joga no celular (testes e screenshots do Android continuam passando sem mudança visual)." Screenshots do Android exatas; regravar só com diferença invisível, comparação lado a lado e nota no commit.
- Anti-objetivo: duplicar telas, regras ou textos entre `androidMain` e `desktopMain`.
- A biblioteca do PC "começa do zero": nada é importado do celular nem do Clone Hero.
- Dados do celular intactos: o banco do Android continua no mesmo arquivo e no mesmo esquema (v13); nenhuma migração nova nesta etapa.
- Português padrão no PC (`DesktopLocale.applyDefault()`).
- Padrões: TDD, detekt, `./scripts/gw`, `git grep`. Lição da etapa 1: depois de trocar só uma referência de screenshot, `--rerun` na tarefa de teste; screenshots do PC só comparam com `verifyRoborazziJvm`.
- Suíte completa: `./scripts/gw test testDebugUnitTest testAndroidHostTest desktopTest detekt lint verifyRoborazziDebug verifyRoborazziAndroidHostTest verifyRoborazziJvm :app:assembleDebug :app:assembleRelease --continue`.

## Review Focus

1. **Atualizar o celular por cima da 0.5.0** (banco v13 existente, preferências existentes): nada muda — mesmo arquivo, mesmo esquema, mesmas chaves. Coberto pelo `MigrationTest`/`RiffPreferencesTest` existentes, que passam a rodar no módulo KMP, e pelo aceite no celular (Task 6).
2. **Pasta do PC com mapas quebrados, pastas sem mapa, `.sng` corrompido, nomes com acento/espaço:** a importação conta os ignorados e não para (como no celular). Teste na Task 3 (`FileFolderSourceTest` com acento e espaço) e na Task 5 (importação ponta a ponta com uma pasta quebrada).
3. **Pasta escolhida que some (pen drive removido, pasta apagada):** a Biblioteca avisa como no celular (`FolderAccessLostException`), sem travar. Teste na Task 3.
4. **Excluir um mapa no PC apaga só a pasta dele (ou o `.sng`), nunca a raiz da biblioteca** (regra do celular, `LibraryFolders`). Teste na Task 3.
5. **Erros do banco no PC** (o código que hoje captura `android.database.SQLException`/`SQLiteConstraintException` precisa capturar o equivalente do driver do PC): a fila de separação e o que mais capturar esses erros se comportam igual. Teste na Task 2/3 onde a captura existir.

---

### Task 1: `:core:datastore` multiplataforma (piloto: `@Inject` e Hilt com código comum)

**Files:**
- Create: `build-logic/convention/src/main/kotlin/KmpLibraryConventionPlugin.kt` (`riff.kmp.library`: o mesmo do `riff.kmp.library.compose` sem Compose; o `.compose` passa a aplicar este e somar o Compose)
- Modify: `core/datastore/build.gradle.kts`; mover `src/main/kotlin` → `src/commonMain/kotlin` (`RiffPreferences.kt`) e `src/androidMain/kotlin` (`DataStoreModule.kt`, Hilt); testes → `src/androidHostTest`
- Create: `core/datastore/src/desktopMain/kotlin/.../DesktopDataStore.kt`
- Test: `core/datastore/src/desktopTest/kotlin/.../DesktopDataStoreTest.kt`

**Interfaces:**
- Produces: plugin `riff.kmp.library`; Hilt em módulo KMP: aplicar o plugin do Hilt/KSP com `kspAndroid` (anotar no ledger o arranjo que funcionou — as Tasks 2–4 seguem igual).
- Produces: `fun riffDataStore(file: java.io.File): DataStore<Preferences>` (desktopMain) usando `PreferenceDataStoreFactory.createWithPath`; `RiffPreferences` inalterado na API.

- [ ] **Step 1: Teste do PC (RED: não compila)**

```kotlin
class DesktopDataStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun theSettingsSurviveReopening() = runTest {
        val file = File(folder.root, "riff_settings.preferences_pb")
        RiffPreferences(riffDataStore(file)).updateSettings { it.copy(missVolume = 0.25f) }
        assertEquals(0.25f, RiffPreferences(riffDataStore(file)).settings.first().missVolume)
    }
}
```
(Usar um campo real de `Settings` e a API real de `RiffPreferences`; o teste valida gravar e ler de novo. Duas instâncias do DataStore no mesmo arquivo ao mesmo tempo são proibidas: fechar o escopo da primeira antes de abrir a segunda.)

- [ ] **Step 2: Rodar** — `./scripts/gw :core:datastore:desktopTest` → FAIL (módulo não é KMP).
- [ ] **Step 3: Converter o módulo e criar `riffDataStore`.** Pilotar aqui o `@Inject` em `commonMain` (dependência `javax.inject` no `commonMain`) e o Hilt em `androidMain`. Se o código comum não enxergar `javax.inject`, alternativa: as classes comuns ficam sem anotação e os módulos Hilt em `androidMain` as constroem com `@Provides` (registrar `Ruling:` e seguir igual nas tasks seguintes).
- [ ] **Step 4: Rodar** — `:core:datastore:desktopTest :core:datastore:testAndroidHostTest :app:assembleDebug` → PASS; os testes Android existentes do módulo iguais.
- [ ] **Step 5: Commit** — `build: core:datastore multiplataforma`

### Task 2: `:core:database` multiplataforma (Room KMP)

**Files:**
- Modify: `build-logic/convention/src/main/kotlin/AndroidRoomConventionPlugin.kt` → versão KMP (`riff.kmp.room`: plugin do Room, `room { schemaDirectory("$projectDir/schemas") }`, `room-compiler` em `kspAndroid` e `kspDesktop`, `room-runtime` no `commonMain`, `sqlite-bundled` no `desktopMain`)
- Move: entidades, DAOs e `RiffDatabase` → `commonMain`; `DatabaseModule.kt` (Hilt) → `androidMain`; testes (inclusive `MigrationTest`) → `androidHostTest`, mantendo os esquemas como assets do teste.
- Modify: `RiffDatabase.kt` — `@ConstructedBy(RiffDatabaseConstructor::class)` e `expect object RiffDatabaseConstructor : RoomDatabaseConstructor<RiffDatabase>` (o Room gera os `actual`).
- Create: `core/database/src/desktopMain/kotlin/.../DesktopDatabase.kt` — `fun riffDatabase(file: File): RiffDatabase` com `Room.databaseBuilder<RiffDatabase>(name = file.absolutePath).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()`
- Test: `core/database/src/desktopTest/kotlin/.../DesktopDatabaseTest.kt`

**Interfaces:**
- Consumes: plugin `riff.kmp.library` (Task 1).
- Produces: `riffDatabase(file: File): RiffDatabase` (desktop); `RiffDatabase` e DAOs com a mesma API no `commonMain`. O Android continua montando o banco como hoje (mesmo nome de arquivo, mesmas migrações, sem driver novo).

- [ ] **Step 1: Teste do PC (RED)**

```kotlin
class DesktopDatabaseTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun aChartSavedOnThePcIsReadBackAfterReopening() = runTest {
        val file = File(folder.root, "riff.db")
        riffDatabase(file).run { chartDao().upsert(/* um ChartEntity mínimo com id "a" */); close() }
        val again = riffDatabase(file)
        assertEquals(listOf("a"), again.chartDao().observeAll().first().map { it.chart.id })
        again.close()
    }
}
```
(Usar os nomes reais do `ChartDao` e de `ChartWithDifficulties`; o que importa é gravar, fechar, reabrir e ler.)

- [ ] **Step 2: Rodar** → FAIL.
- [ ] **Step 3: Converter.** Funções de DAO que o Room KMP não aceitar no código comum (bloqueantes) e capturas de `android.database.*` viram o equivalente comum (`androidx.sqlite.SQLiteException`); registrar cada mudança como `Ruling:`.
- [ ] **Step 4: Rodar** — `:core:database:desktopTest :core:database:testAndroidHostTest` (inclui `MigrationTest` 12→13 e anteriores) `:app:assembleDebug` → PASS. O esquema `13.json` não muda (`git diff --stat core/database/schemas` vazio).
- [ ] **Step 5: Commit** — `build: core:database multiplataforma (Room KMP)`

### Task 3: `:core:data` multiplataforma e a pasta do PC

**Files:**
- Move para `commonMain`: interfaces e implementações sem Android (repositórios Room/DataStore, `folder/FolderSource.kt`, `SngFolderSource`, `importer/*`, `library/ChartDeleter.kt`, `game/*` sem Android, `ranking/*`, `download/*`, `EncoreDownloads`, `AiSeparations.kt` (interfaces e o que for comum)).
- Fica em `androidMain`: `SafFolderSource`, `SafFolderAccess`, `SafFolderWriter`, `ContentRandomReadOpener`, `SystemAppLanguageStore`, `LegacyAppLanguage`, `DataModule.kt` (Hilt), `BuildConfig.RANKING_URL` (o ranking no PC é da etapa 4).
- Create em `desktopMain` (`folder/`): `FileFolderSource : FolderSource`, `FileFolderAccess : FolderAccess`, `FileFolderWriter : FolderWriter`, `FileRandomReadOpener : RandomReadOpener` — `java.io.File`/`RandomAccessFile`; a "uri" de uma pasta ou arquivo no PC é o caminho absoluto.
- Create em `desktopMain`: `NoAiSeparations` — `AiSeparationRepository` e `SeparationScheduler` vazios (nada separado, nada na fila, agendar não faz nada) até a etapa 4.
- Test: `core/data/src/desktopTest/kotlin/.../folder/FileFolderSourceTest.kt`, `FileFolderWriterTest.kt`
- Modify: `build.gradle.kts` raiz (Kover com `:core:data` KMP: relatório do alvo Android ou desktop, mínimo 70% mantido)

**Interfaces:**
- Consumes: Tasks 1–2.
- Produces: `FileFolderSource()`, `FileFolderAccess()`, `FileFolderWriter()`, `FileRandomReadOpener()`, `NoAiSeparations` (implementa `AiSeparationRepository` e `SeparationScheduler`).

- [ ] **Step 1: Testes do PC (RED)** — em pasta temporária com: `Música Á/` (`notes.chart`, `song.ini`, `song.ogg` — usar os fixtures de `testFixtures(project(":core:chart"))`), `Pacote B.sng` (fixture `.sng` do `core:chart`), `vazia/`, `quebrada/` (`notes.chart` inválido).

```kotlin
@Test fun listsFoldersAndSngWithAccentsAndSpaces()        // FileFolderSource lista "Música Á" e "Pacote B.sng" (via SngFolderSource)
@Test fun importingCountsTheBrokenOnesAndGoesOn()         // FolderChartImporter com FileFolderSource: 2 importados, 1 ignorado ("quebrada"), "vazia" sem mapa
@Test fun aFolderThatDisappearedRaisesAccessLost()        // apagar a raiz depois de escolhida → FolderAccessLostException ao listar
@Test fun deletingAChartRemovesOnlyItsFolder()            // FolderChartDeleter + FileFolderWriter: apaga "Música Á", mantém a raiz e o resto
@Test fun theLibraryRootIsNeverDeleted()                  // mapa solto direto na raiz: o deleter recusa, como no celular (LibraryFolders)
```
(Nomes e assinaturas reais do importador, do deleter e do `ImportReport`; os valores esperados são os acima.)

- [ ] **Step 2: Rodar** — `./scripts/gw :core:data:desktopTest` → FAIL.
- [ ] **Step 3: Converter e implementar.**
- [ ] **Step 4: Rodar** — `:core:data:desktopTest :core:data:testAndroidHostTest koverVerify :app:assembleDebug` → PASS.
- [ ] **Step 5: Commit** — `feat: core:data multiplataforma e a pasta de mapas do PC`

### Task 4: `:feature:library` multiplataforma

**Files:**
- Modify: `build-logic/.../AndroidFeatureConventionPlugin.kt` → versão KMP `riff.kmp.feature` (aplica `riff.kmp.library.compose` + serialização; `commonMain` com `:core:ui`, `:core:model`, `:core:data`, lifecycle-viewmodel-compose e navigation-compose multiplataforma; `androidMain` com Hilt e `hilt-lifecycle-viewmodel-compose`)
- Move: tela, partes, filtro, exclusão, pergunta da separação e `LibraryViewModel` → `commonMain`; strings → `composeResources`; testes e screenshots → `androidHostTest` (screenshots continuam em `src/test/screenshots`).
- Create: `feature/library/src/androidMain/.../HiltLibraryViewModel.kt` — `@HiltViewModel class HiltLibraryViewModel @Inject constructor(<mesmos parâmetros>) : LibraryViewModel(<...>)`; `LibraryViewModel` vira `open`.
- Create: `expect fun rememberFolderPicker(onPicked: (String) -> Unit): () -> Unit` em `commonMain`; `actual` Android = o `OpenDocumentTree` de hoje (com a permissão persistente); `actual` desktop = `JFileChooser` só de pastas, rodando fora da thread da UI.
- Modify: `app/.../RiffNavHost.kt` — a rota da Biblioteca passa `hiltViewModel<HiltLibraryViewModel>()` (o parâmetro padrão `hiltViewModel()` sai da tela comum).

**Interfaces:**
- Consumes: Task 3 (`ChartRepository`, `LibraryFolderStore`, `ChartImporter`, `FolderAccess`, `AiSeparationRepository`, `SeparationScheduler`, `ChartAudioProbe`, `SettingsRepository`, `ChartDeleter`).
- Produces: `LibraryScreen(viewModel: LibraryViewModel, ...callbacks de hoje)` em `commonMain`; `rememberFolderPicker`.

- [ ] **Step 1: Teste de idioma da Biblioteca (base, no Android)** — no `LibraryContentTest` (ou o teste de tela existente), caso em inglês com `Locale.setDefault(Locale.ENGLISH)` + qualifiers `en` (molde do `HomeContentTest.inEnglishTheTilesAreInEnglish`): o botão de escolher pasta aparece com o texto de `values-en`. Rodar → PASS (base com `R.string`).
- [ ] **Step 2: Converter o módulo** (Files acima).
- [ ] **Step 3: Rodar** — `:feature:library:testAndroidHostTest --rerun :feature:library:verifyRoborazziAndroidHostTest :feature:library:compileKotlinDesktop :app:testDebugUnitTest :app:assembleDebug` → PASS, screenshots sem regravar, teste em inglês passando.
- [ ] **Step 4: Commit** — `refactor: Biblioteca multiplataforma`

### Task 5: O PC abre a Biblioteca

**Files:**
- Create: `desktop/src/main/kotlin/.../DesktopPaths.kt` — `object DesktopPaths { val dataDir: File }` = `%APPDATA%\Riff` no Windows (`System.getenv("APPDATA")`), senão `~/.riff`; cria a pasta.
- Create: `desktop/src/main/kotlin/.../DesktopGraph.kt` — `class DesktopGraph(dataDir: File)` monta `riffDatabase(File(dataDir, "riff.db"))`, `RiffPreferences(riffDataStore(File(dataDir, "riff_settings.preferences_pb")))`, os repositórios Room/DataStore, `SngFolderSource(FileFolderSource(), FileRandomReadOpener())`, `FileFolderAccess()`, `FolderChartImporter(...)`, `FolderChartDeleter(...)`, `FolderChartAudioProbe(...)`, `NoAiSeparations`; `fun libraryViewModel(): LibraryViewModel`.
- Modify: `RiffDesktopApp.kt` — `NavHost` multiplataforma com `HomeRoute` e `LibraryRoute`; "Sozinho" navega para a Biblioteca; Voltar volta; `RiffDesktopApp(graph: DesktopGraph)`.
- Modify: `Main.kt` (cria o `DesktopGraph(DesktopPaths.dataDir)`), `RiffDesktopAppTest.kt` (usa um grafo em pasta temporária).
- Test: `desktop/src/test/kotlin/.../DesktopLibraryTest.kt`

**Interfaces:**
- Consumes: Tasks 1–4.
- Produces: `DesktopGraph`, `DesktopPaths`, `RiffDesktopApp(graph)`.

- [ ] **Step 1: Teste ponta a ponta (RED)**

```kotlin
class DesktopLibraryTest {
    @get:Rule val composeRule = createComposeRule()
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun soloOpensTheLibraryAndImportsAPcFolder() = runTest {
        val charts = folder.newFolder("mapas") // com um mapa válido (fixture) e uma pasta quebrada
        val graph = DesktopGraph(folder.newFolder("dados"))
        graph.libraryFolderStore.setFolder(charts.absolutePath) // a pasta escolhida, sem o seletor
        DesktopLocale.applyDefault()
        composeRule.setContent { RiffDesktopApp(graph) }

        composeRule.onNodeWithTag(HomeTestTags.SOLO).performClick()
        // importar pelo botão "Reimportar" (ou o import automático da escolha), esperar a lista
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText("<título do fixture>").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("<título do fixture>").assertExists()
    }
}
```
(Nomes reais do `LibraryFolderStore` e dos textos/etiquetas da Biblioteca; o fluxo é: tela inicial → Sozinho → Biblioteca com a pasta → importar → título na lista; e o relatório conta a pasta quebrada como ignorada.)

- [ ] **Step 2: Rodar** — `./scripts/gw :desktop:test` → FAIL.
- [ ] **Step 3: Implementar.**
- [ ] **Step 4: Rodar** — `:desktop:test --rerun :desktop:verifyRoborazziJvm` → PASS (`Home_desktop.png` igual).
- [ ] **Step 5: No Windows** — `./scripts/gw :desktop:run`: Sozinho → Escolher pasta (JFileChooser) → importar. A biblioteca do PC começa vazia; para o aceite, pedir ao usuário uma pasta de mapas (ou, com a autorização dele, copiar alguns mapas do celular via adb para uma pasta do PC).
- [ ] **Step 6: Commit** — `feat: no PC, Sozinho abre a Biblioteca`

### Task 6: Celular, CI e documentação

- [ ] **Step 1: Suíte completa** (Global Constraints) → BUILD SUCCESSFUL.
- [ ] **Step 2: Linux** (`~/riff-linux`, comando do CI) → verde.
- [ ] **Step 3: Celular** — se o celular estiver no adb: instalar o release por cima (mesma 0.5.0), abrir, conferir pelo `uiautomator dump` que a Biblioteca lista os mesmos mapas de antes (o total de mapas do banco igual ao de antes da instalação, lido com o app de debug se preciso); senão, registrar e deixar para o aceite do usuário.
- [ ] **Step 4: CI** — `.github/workflows/ci.yml`: trocar a lista fixa `:core:ui:lint :feature:home:lint` pelos módulos KMP desta etapa também (ou uma tarefa agregadora no build raiz que rode o `lint` de todo módulo KMP — escolher uma e registrar).
- [ ] **Step 5: README** — tabela de módulos (quais são multiplataforma), seção "Riff no PC" (onde ficam os dados: `%APPDATA%\Riff`), `docs/pendencias.md` se algo ficar.
- [ ] **Step 6: Commit** — `docs: Biblioteca no PC no README e no CI`
