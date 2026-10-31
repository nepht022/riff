# Riff no Windows — Etapa 1: base comum — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `:core:ui` e `:feature:home` passam a ser Kotlin Multiplatform (Android + desktop), o Android fica igual para o jogador, e um app `:desktop` abre a tela inicial do Riff numa janela do Windows.

**Architecture:** Um plugin de convenção novo (`riff.kmp.library.compose`) monta módulos KMP com o alvo Android do AGP 9 (`com.android.kotlin.multiplatform.library`) e um alvo JVM `desktop`, com Compose Multiplatform. Textos e fontes de `:core:ui` e `:feature:home` vão para os recursos do Compose Multiplatform (`composeResources`), usados pelas duas plataformas. O que ainda depende de `android.graphics` (os desenhos da pista em `core/ui/.../stage/`) fica em `androidMain` até a etapa 2. `:core:model`, `:core:chart` e `:core:game` já são JVM puros e são usados como estão.

**Tech Stack:** Kotlin 2.4.20, AGP 9.4.1, Compose Multiplatform 1.12.2 (`org.jetbrains.compose`; casa com o Compose do BOM 2026.09.00, ui 1.12.1), lifecycle multiplataforma da JetBrains (`org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose`, versão indicada nas notas da CMP 1.12.2), Robolectric + Roborazzi (Android) e Roborazzi desktop (`roborazzi-compose-desktop`) no PC.

**Spec:** `docs/superpowers/specs/2026-10-31-riff-windows-design.md` (§6, etapa 1: "Base comum").

## Global Constraints

- "O Android continua funcionando igual: nenhuma diferença para quem joga no celular (testes e screenshots do Android continuam passando sem mudança visual)."
- "Se a migração mudar pixels de forma invisível (antialiasing), as referências podem ser regravadas com comparação lado a lado e nota no commit; diferença visível é defeito."
- Anti-objetivo: "Duplicar o jogo: duas cópias das regras, da pista ou das telas, uma para cada plataforma." Nenhum texto, fonte ou tela copiado entre `androidMain` e `desktopMain`.
- Português é o idioma padrão, mesmo num sistema em inglês (decisão do usuário de 2026-10-16); English continua escolhível no Android.
- Padrões do repositório: TDD, detekt (LongMethod 60, TooManyFunctions 11, MaxLineLength 120), `./scripts/gw`, `git grep`.
- Suíte completa do Android continua sendo o gate: `./scripts/gw test testDebugUnitTest detekt lint verifyRoborazziDebug :app:assembleDebug :app:assembleRelease --continue --console=plain`, com os nomes de tarefa dos módulos KMP acrescentados (Task 1 descobre e registra quais são).

## Review Focus

1. **Idioma do app em English (escolha nas Configurações, inclusive no caminho do Android antes do 13 com `attachBaseContext`):** os textos que vieram de `composeResources` (dificuldades, separação, tela inicial) aparecem em inglês. Teste na Task 2.
2. **Build release minificado (R8):** fontes e textos do `composeResources` carregam no APK release (sem eles a tela inicial mostra fonte padrão ou quebra). Conferência na Task 3 (APK release instalado no celular do usuário, ou `aapt`/listagem do APK mostrando `composeResources`).
3. **Fonte grande do Android (fontScale 2):** as telas comuns continuam iguais às referências (`LargeFontScreenshotTest` e o teste da tela inicial deitada). Já coberto pelos testes existentes, que passam a rodar no módulo KMP.
4. **PC com Windows em inglês (Locale da JVM `en-US`):** a janela mostra "Sozinho", não "Solo". Teste na Task 4.
5. **Janela pequena no PC (800×500):** o Ranking continua alcançável (a tela rola). Teste na Task 4.

---

### Task 1: Plugin KMP e `:core:ui` multiplataforma sem mover código

Prova as ferramentas antes de mover qualquer código: o módulo vira KMP com **todo** o código e os testes no lado Android.

**Files:**
- Create: `build-logic/convention/src/main/kotlin/KmpLibraryComposeConventionPlugin.kt`
- Modify: `build-logic/convention/build.gradle.kts` (registrar `riff.kmp.library.compose`; `compileOnly` do plugin do Compose Multiplatform e do plugin KMP do AGP)
- Modify: `gradle/libs.versions.toml` (versão `composeMultiplatform = "1.12.2"`, plugin `org.jetbrains.compose`, `roborazzi-compose-desktop`, lifecycle da JetBrains)
- Modify: `build.gradle.kts` (raiz: `apply false` dos plugins novos; `detekt.source` passa a incluir `src/commonMain/kotlin`, `src/androidMain/kotlin`, `src/desktopMain/kotlin`, `src/androidHostTest/kotlin`, `src/desktopTest/kotlin`, `src/commonTest/kotlin` de cada subprojeto)
- Modify: `core/ui/build.gradle.kts`
- Move: `core/ui/src/main/kotlin` → `core/ui/src/androidMain/kotlin`; `core/ui/src/main/res` → `core/ui/src/androidMain/res`; `core/ui/src/test/{kotlin,resources,screenshots}` → `core/ui/src/androidHostTest/...` (screenshots: onde o Roborazzi do alvo KMP as procura; manter os PNGs byte a byte)

**Interfaces:**
- Produces: plugin `riff.kmp.library.compose` que, aplicado a um módulo, configura `kotlin { androidLibrary { namespace; compileSdk = RiffSdk.COMPILE; minSdk = RiffSdk.MIN; withHostTest { isIncludeAndroidResources = true } }; jvm("desktop") }`, JVM 17, compose compiler, Compose Multiplatform (`compose.runtime`, `foundation`, `material3`, `ui`, `components.resources` em `commonMain`), e nas dependências de teste Android as mesmas do `AndroidFeatureConventionPlugin` (junit4, robolectric, roborazzi, roborazzi-compose, ui-test-junit4, ui-test-manifest). O `maven.repo.local` do Robolectric (`riff.robolectric.mavenRepoLocal`) vale também nas tarefas de teste do módulo.
- Produces: a lista exata das tarefas Gradle que substituem `testDebugUnitTest`, `verifyRoborazziDebug` e `lint` num módulo KMP (anotar no ledger e usar nas tasks seguintes e na Task 5).

- [ ] **Step 1: Registrar a linha de base** — rodar `./scripts/gw :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug --console=plain` e anotar no ledger o número de testes (21 classes, 15 screenshots).
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Criar o plugin e converter `:core:ui`** com o código todo em `androidMain`/`androidHostTest` (nenhuma linha de código muda; só caminhos e build).

- [ ] **Step 3: Rodar os testes e as screenshots do módulo convertido**
Run: as tarefas equivalentes de host test e de verificação do Roborazzi do alvo Android KMP.
Expected: o mesmo número de testes da linha de base passando e as 15 screenshots verificadas **sem regravar**.

  Se o Robolectric/Roborazzi não funcionar com `com.android.kotlin.multiplatform.library`, a alternativa é `com.android.library` + `org.jetbrains.kotlin.multiplatform` com `android.builtInKotlin=false`/`android.newDsl=false` só nesses módulos, se o AGP 9.4 ainda aceitar. Registrar como `Ruling:` no ledger, com o custo: deixa de funcionar no AGP 10. Se nenhuma das duas rodar os testes de tela, **parar e perguntar** (o plano estaria quebrado).

- [ ] **Step 4: O app usa o módulo convertido**
Run: `./scripts/gw :app:assembleDebug :app:testDebugUnitTest :app:verifyRoborazziDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit** — `build: core:ui vira módulo multiplataforma (código ainda só no Android)`

### Task 2: `:core:ui` comum: tema, componentes, textos e fontes

**Files:**
- Move para `core/ui/src/commonMain/kotlin`: `theme/*`, `component/*`, `navigation/DropUnlessResumed.kt` (com o lifecycle da JetBrains).
- Fica em `core/ui/src/androidMain/kotlin`: `stage/*` (usa `android.graphics`; vai para comum na etapa 2).
- Create: `core/ui/src/commonMain/composeResources/{values,values-en}/strings.xml` e `difficulties.xml` (conteúdo atual de `res/values*`, com os mesmos nomes de chave); `core/ui/src/commonMain/composeResources/font/new_rocker_regular.ttf` e `oswald_variable.ttf` (movidos, não copiados).
- Delete: `core/ui/src/androidMain/res/values*` e `res/font` (depois de nada mais usar `R` em `:core:ui`).
- Modify: `theme/Type.kt`: `NewRocker`, `Oswald` e a tipografia passam a ser montados em composição a partir de `Res.font.*` (a API de fonte do `composeResources` é `@Composable`). O `RiffTheme` usa `riffTypography()`. Pacote do `Res`: `com.rodrigomachado.riff.core.ui.resources`, interno ao módulo.
- Modify: `feature/licenses/.../AndroidLicenseSource.kt` só se ele importar `NewRocker`/`Oswald` como símbolo (se for só texto, nada muda).
- Modify: `component/DifficultyLabels.kt` — `difficultyLabel(index): Int` (`@StringRes`) dá lugar a `@Composable fun difficultyName(index: Int): String`; os usos `stringResource(difficultyLabel(x))` em `feature/library/.../LibraryParts.kt` (2), `feature/ranking/.../RankingBoard.kt`, `feature/results/.../ResultsScreen.kt` e `feature/search/.../SearchScreen.kt` (2) viram `difficultyName(x)`.
- Test: `core/ui/src/androidHostTest/.../theme/AppLanguageTest.kt` (novo)

**Interfaces:**
- Consumes: plugin e nomes de tarefa da Task 1.
- Produces (em `commonMain`, mesmos nomes e assinaturas de hoje): `RiffTheme`, `RiffColors`, `Spacing`, `PosterButton`, `PosterTitle`, `StageBackground`, `SeparationIndicator`, `SeparationControls`, `LocalSeparation`, `withMenuSound`, `MenuSound` (este já vem de `:core:model`), `EmptyState`, `ErrorState`, `StatusBadge`, `TrackCard`, `DifficultyDialog`. Nova: `@Composable fun difficultyName(index: Int): String` (substitui `difficultyLabel`). Nova: `@Composable fun riffTypography(): Typography`. `NewRocker`/`Oswald` viram `@Composable fun newRocker(): FontFamily` e `@Composable fun oswald(): FontFamily` (os usos internos mudam junto).

- [ ] **Step 1: Escrever o teste de idioma do app (RED se as strings não vierem da configuração da tela)**

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class AppLanguageTest {
    @get:Rule val composeRule = createComposeRule()

    /** O idioma escolhido no Riff chega à tela pela configuração (attachBaseContext / LocaleManager), não pelo sistema. */
    @Test
    fun theDifficultyLabelsFollowTheAppLanguage() {
        composeRule.setContent {
            val english = Configuration(LocalConfiguration.current).apply { setLocale(Locale.ENGLISH) }
            CompositionLocalProvider(LocalConfiguration provides english) {
                RiffTheme { Text(difficultyName(0)) }
            }
        }
        composeRule.onNodeWithText("Easy").assertExists() // pt: "Fácil"; en: "Easy" (difficulties.xml)
    }
}
```

- [ ] **Step 2: Rodar o teste** — FAIL na compilação (`difficultyName` não existe). Criar `difficultyName` ainda sobre `R.string` (`stringResource(difficultyLabel(index))`) e trocar os 6 usos nos módulos de tela; rodar de novo → PASS (base, com o idioma vindo da configuração do Android). Depois de mover para `composeResources` (Step 3), ele é o que prova que o idioma segue a configuração. Se falhar após o Step 3, o `composeResources` está lendo o idioma do sistema: corrigir passando o ambiente de recursos a partir da `LocalConfiguration` (não mexer no teste).

- [ ] **Step 3: Mover tema, componentes, textos e fontes para `commonMain`/`composeResources`.**

- [ ] **Step 4: Rodar todos os testes e screenshots do módulo e do app**
Run: tarefas de host test e Roborazzi de `:core:ui` + `./scripts/gw :app:testDebugUnitTest :app:verifyRoborazziDebug :app:assembleDebug --console=plain`
Expected: tudo verde, screenshots **sem regravar**. Se alguma diferir só em antialiasing invisível, regravar com nota no commit e o par antes/depois anexado ao ledger (Global Constraints); diferença visível é defeito a corrigir.

- [ ] **Step 5: `:core:ui` compila para o desktop**
Run: `./scripts/gw :core:ui:compileKotlinDesktop --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit** — `refactor: tema, componentes, textos e fontes do core:ui em comum (Android e PC)`

### Task 3: `:feature:home` multiplataforma

**Files:**
- Modify: `feature/home/build.gradle.kts` (aplica `riff.kmp.library.compose` + serialização; `commonMain` depende de `:core:ui`, `:core:model`, `kotlinx-serialization-core`)
- Move: `feature/home/src/main/kotlin/*` → `src/commonMain/kotlin`; `res/values*/strings.xml` → `src/commonMain/composeResources/values*/strings.xml`; testes e screenshots → `src/androidHostTest`
- Modify: `HomeScreen.kt` (de `stringResource(R.string.x)` para `stringResource(Res.string.x)`)

**Interfaces:**
- Consumes: Task 2 (componentes comuns).
- Produces: `HomeRoute`, `HomeScreen(onSolo, onRanking, onSettings, onReport, modifier)` e `HomeTestTags` em `commonMain`, com as mesmas assinaturas (o `RiffNavHost` do app não muda).

- [ ] **Step 1: Adicionar ao `HomeContentTest` o caso em inglês (RED depois da mudança se o idioma não seguir a configuração)**

```kotlin
@Test
@Config(qualifiers = "en")
fun inEnglishTheTilesAreInEnglish() {
    show()
    composeRule.onNodeWithText("Solo").assertExists() // valor de values-en/strings.xml (home_solo)
}
```
(Conferir o valor real de `home_solo` em `values-en`.)

- [ ] **Step 2: Rodar** — passa com o código atual (base).

- [ ] **Step 3: Converter o módulo** (Files acima).

- [ ] **Step 4: Rodar os testes do módulo e do app**
Expected: os 6 testes do `HomeContentTest` e as 2 screenshots (`Home.png`, `Home_queue.png`) sem regravar; `:app` compila e testa.

- [ ] **Step 5: Release no celular (Review Focus 2)** — `./scripts/gw :app:assembleRelease`; confirmar que o APK contém os recursos do Compose (`unzip -l app/build/outputs/apk/release/app-release.apk | grep -c composeResources` > 0). Se o celular estiver no adb, instalar (`install -r --user 0`) e conferir com `uiautomator dump` que "Sozinho" e "★ Ranking" aparecem; senão, registrar no ledger que a conferência visual fica para o aceite do usuário.

- [ ] **Step 6: Commit** — `refactor: tela inicial multiplataforma`

### Task 4: App `:desktop` que abre a tela inicial

**Files:**
- Create: `desktop/build.gradle.kts` (Kotlin JVM + Compose Multiplatform `compose.desktop.currentOs`; `compose.desktop.application { mainClass = "com.rodrigomachado.riff.desktop.MainKt" }`; dependências `:core:ui`, `:feature:home`; testes com `compose.desktop.uiTestJUnit4`, junit4, `roborazzi-compose-desktop`)
- Modify: `settings.gradle.kts` (`include(":desktop")`)
- Create: `desktop/src/main/kotlin/com/rodrigomachado/riff/desktop/Main.kt`
- Create: `desktop/src/main/kotlin/com/rodrigomachado/riff/desktop/RiffDesktopApp.kt`
- Test: `desktop/src/test/kotlin/com/rodrigomachado/riff/desktop/RiffDesktopAppTest.kt`, screenshot `desktop/src/test/screenshots/Home_desktop.png`

**Interfaces:**
- Consumes: `HomeScreen`, `RiffTheme`, `StageBackground` (Tasks 2–3).
- Produces: `@Composable fun RiffDesktopApp()` (tema + fundo + tela inicial; os botões ainda não levam a lugar nenhum nesta etapa) e `fun main()` que fixa o idioma padrão português antes da primeira composição e abre `Window(title = "Riff", state = rememberWindowState(width = 1280.dp, height = 800.dp))` com tamanho mínimo 800×500.
- Produces: `object DesktopLocale { fun applyDefault() }` — define `Locale.setDefault(Locale.forLanguageTag("pt-BR"))` (escolha do idioma no PC é da etapa 2).

- [ ] **Step 1: Testes (RED: o módulo não existe)**

```kotlin
class RiffDesktopAppTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun opensOnTheHomeScreenInPortugueseEvenOnAnEnglishWindows() {
        Locale.setDefault(Locale.US)
        DesktopLocale.applyDefault()
        composeRule.setContent { RiffDesktopApp() }
        composeRule.onNodeWithText("Sozinho").assertExists()
        composeRule.onNodeWithContentDescription("Sala, em breve").assertExists()
    }

    @Test
    fun aSmallWindowStillReachesTheRanking() {
        DesktopLocale.applyDefault()
        composeRule.setContent { Box(Modifier.size(800.dp, 500.dp)) { RiffDesktopApp() } }
        composeRule.onNodeWithTag(HomeTestTags.RANKING).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun homeScreenshot() {
        DesktopLocale.applyDefault()
        composeRule.setContent { Box(Modifier.size(1280.dp, 800.dp)) { RiffDesktopApp() } }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/Home_desktop.png")
    }
}
```

- [ ] **Step 2: Rodar** — `./scripts/gw :desktop:test --console=plain` → FAIL (projeto inexistente).

- [ ] **Step 3: Criar o módulo e o app.**

- [ ] **Step 4: Rodar** — `./scripts/gw :desktop:test -Proborazzi.test.record=true` uma vez para gravar `Home_desktop.png`; olhar a imagem (letreiro na New Rocker, quadros Sozinho/Sala, ★ Ranking); depois `./scripts/gw :desktop:test` → PASS. Se a fonte variável Oswald não aplicar o peso no desktop, registrar e corrigir em `commonMain` sem quebrar o Android.

- [ ] **Step 5: Abrir a janela no Windows** — `./scripts/gw :desktop:run` (roda no Windows pelo `scripts/gw`). Expected: janela "Riff" com a tela inicial. Pedir ao usuário que confira (aceite da etapa).

- [ ] **Step 6: Commit** — `feat: app de PC abre a tela inicial do Riff`

### Task 5: CI, detekt e documentação

**Files:**
- Modify: `.github/workflows/ci.yml` (acrescentar as tarefas dos módulos KMP registradas na Task 1 e `:desktop:test`; o Linux do CI roda os testes do Compose desktop sem tela)
- Modify: `README.md` (seção curta "Riff no PC (em construção)": `./scripts/gw :desktop:run`; módulos KMP na tabela de módulos)
- Modify: `docs/pendencias.md` (se algo ficar para depois)

- [ ] **Step 1: Suíte completa com as tarefas novas**
Run: a suíte do Global Constraints + `:desktop:test` + tarefas KMP da Task 1.
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Conferir no Linux** (`~/riff-linux`, mesmo comando do CI) que as screenshots do desktop e do Android passam iguais.
Expected: sem diferenças. Se o desktop desenhar diferente no Linux (fontes do sistema não entram; as nossas vêm do `composeResources`), investigar antes de aceitar tolerância.

- [ ] **Step 3: Commit** — `ci: testa também os módulos multiplataforma e o app de PC`
