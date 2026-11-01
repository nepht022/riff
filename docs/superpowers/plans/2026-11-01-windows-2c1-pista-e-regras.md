# Riff no Windows — Etapa 2c-1: pista e regras da palheta — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** O motor do jogo julga a palheta (acorde inteiro numa batida, HOPO e tap sem palhetar, palhetada errada quebra o combo) e a pista desenhada do Riff (todos os fundos, estampas, gemas e chamas) roda no PC. O celular fica igual, pixel a pixel.

**Architecture:** `Note` ganha `isTap` (hoje o tap vira `isHopo` no `NoteAssembler`). O `GameEngine` ganha `onStrum` e `onFret` ao lado do `onPress`/`onRelease` de hoje (o toque do celular não muda). A pista (`core/ui/.../stage/`) vai para `commonMain`; só a camada que "assa" texturas e aplica a perspectiva fica por plataforma: no Android, o código de hoje (Bitmap, BlurMaskFilter, BitmapShader, `setPolyToPoly`); no PC, o mesmo com Skia (o que o Compose do PC usa por baixo). Formas (caveira, raio, estrelas, estampas) passam a ser `Path` do Compose em comum, convertidas para a plataforma na hora de assar.

**Tech Stack:** Kotlin, Compose Multiplatform 1.12.2, Skia do Compose desktop (`org.jetbrains.skia`).

**Spec:** `docs/superpowers/specs/2026-10-31-riff-windows-design.md` (§4 "Como se joga no PC"; §6 etapa 2; divisão da 2c em 2c-1/2c-2 combinada com o usuário em 2026-11-01).

## Global Constraints

- O celular fica igual: os 6 screenshots da pista (`Stage*.png`) e os 4 da partida (`Gameplay_*.png`) não mudam; o toque segue pelo `onPress`/`onRelease`.
- Regras da palheta (spec §4): "a nota conta quando a palheta é apertada com as cores certas seguradas, dentro da janela de acerto. Acordes: todas as cores do acorde seguradas; **um acorde de N cores vale N notas** em pontos, combo e precisão. Palheta sem nota, ou com cor errada, é erro (como no Clone Hero). HOPO/tap: … preferindo o comportamento do Clone Hero."
- Comportamento do Clone Hero adotado (escolha minha, ver Task 2): nota simples aceita cores mais baixas seguradas junto ("ancorar"); acorde exige exatamente as cores dele; HOPO vale sem palhetar só se a nota anterior foi acertada; tap vale sempre; palhetada logo depois de acertar um HOPO sem palheta (até a janela de "perfeito", 50 ms) não conta como erro.
- Nada do Guitar Hero (visual): só o que o Riff já desenha.
- Suíte completa: `./scripts/gw test testDebugUnitTest testAndroidHostTest desktopTest detekt lint lintMultiplatform koverVerify verifyRoborazziDebug verifyRoborazziAndroidHostTest verifyRoborazziJvm :app:assembleDebug :app:assembleRelease --continue`.

## Review Focus

1. **Pista do celular depois da mudança de `Path` do Android para `Path` do Compose:** idêntica (os 10 screenshots). Coberto pelos testes de tela existentes.
2. **Acorde com uma cor a mais ou a menos segurada:** é erro (quebra o combo), e o acorde pode ainda ser acertado dentro da janela. Teste na Task 2.
3. **Palheta dupla rápida** (↑ e ↓ quase juntos, ou palheta logo depois de um HOPO acertado): não vira erro falso. Teste na Task 2.
4. **Mapa com tap e HOPO no mesmo trecho; mapa antigo sem marcas:** tap vale sem palheta mesmo depois de um erro; HOPO só depois de acerto; sem marcas, tudo pede palheta. Teste na Task 2.
5. **Fundos da pista com imagem do mapa e cada estilo de estampa no PC:** desenham sem erro e parecidos com o celular (screenshot do PC conferido a olho). Task 4.

---

### Task 1: `Note.isTap` separado de `isHopo`

**Files:**
- Modify: `core/model/.../Note.kt` — `data class Note(timeMs, lane, sustainMs = 0, isHopo = false, isTap = false)`.
- Modify: `core/chart/.../NoteAssembler.kt` (linha ~40: hoje `isHopo = chord.isTap || resolveHopo(...)`) → `isHopo = !chord.isTap && resolveHopo(...)`, `isTap = chord.isTap`.
- Modify: `feature/gameplay/.../StageRenderer.kt` (uso de `isHopo` no visual) → `isHopo || isTap` (o desenho não muda).
- Test: `core/chart/src/test/.../NoteAssemblerTest.kt` (ou o teste do parser que já cobre tap): casos novos.

- [ ] **Step 1: Testes (RED)** — nota com marca de tap no `.chart` (flag 6) e no `.mid` (marcador de tap) sai `isTap = true, isHopo = false`; nota HOPO natural sai `isHopo = true, isTap = false`; nota forçada a palhetada sai as duas falsas.
- [ ] **Step 2: Rodar** — `./scripts/gw :core:chart:test` → FAIL (`isTap` inexistente).
- [ ] **Step 3: Implementar.**
- [ ] **Step 4: Rodar** — `:core:chart:test :core:game:test :feature:gameplay:testDebugUnitTest :feature:gameplay:verifyRoborazziDebug` → PASS, screenshots iguais.
- [ ] **Step 5: Commit** — `feat: notas de tap separadas das de HOPO`

### Task 2: Palheta no motor do jogo

**Files:**
- Modify: `core/game/.../GameEngine.kt`
- Test: `core/game/src/test/.../GameEngineStrumTest.kt`

**Interfaces:**
- Produces:
  - `fun onStrum(held: Set<Int>, timeMs: Long): List<Judgment>?` — julga o próximo acorde pendente dentro da janela (as notas com o menor `timeMs` ainda sem julgamento, entre `timeMs − goodMs` e `timeMs + goodMs`). Certo quando: nota simples → a maior cor segurada é a da nota; acorde → as cores seguradas são exatamente as do acorde. Certo: cada nota é julgada (PERFECT/GOOD pela distância), soma pontos e combo como N acertos (com o multiplicador de antes de cada um) e abre os sustains. Errado ou sem nota na janela: zera o combo (como o toque sem nota de hoje) e devolve `null` — exceto dentro de `windows.perfectMs` depois de um acerto por `onFret`, quando não faz nada e devolve lista vazia.
  - `fun onFret(lane: Int, held: Set<Int>, timeMs: Long): List<Judgment>?` — ao apertar uma cor com a palheta ligada: se o próximo acorde pendente na janela é tap, ou HOPO com `combo > 0`, e as cores batem (mesma regra), acerta sem palhetar; senão, nada (sem penalidade) e `null`.
  - `onRelease(lane, timeMs)` continua encerrando o sustain da cor.

- [ ] **Step 1: Testes (RED)**

```kotlin
@Test fun aStrumWithTheRightFretHitsTheNote()             // nota verde em 1000; held={0}, strum em 1010 → [PERFECT], combo 1
@Test fun anchoringLowerFretsIsAllowedOnSingleNotes()     // nota amarela (2); held={0,2} → acerta
@Test fun aHigherFretHeldMissesTheSingleNote()            // nota vermelha (1); held={1,3} → null, combo 0, nota ainda pendente
@Test fun aChordNeedsExactlyItsFrets()                    // acorde {0,2} em 1000: held={0} → null; held={0,1,2} → null; held={0,2} → [PERFECT, PERFECT]
@Test fun aChordCountsAsItsNotes()                        // acorde de 3 cores acertado: combo 3, score = 3 × 100, accuracy 1
@Test fun strummingWithNothingToHitBreaksTheCombo()       // combo 5, strum sem nota na janela → null, combo 0
@Test fun aHopoAfterAHitNeedsNoStrum()                    // nota em 1000 (acertada) e HOPO em 1200: onFret(1, {1}, 1200) → [PERFECT]
@Test fun aHopoAfterAMissNeedsAStrum()                    // primeira nota perdida, HOPO depois: onFret → null; onStrum → acerta
@Test fun aTapNeedsNoStrumEvenAfterAMiss()                // nota tap depois de um erro: onFret acerta
@Test fun aStrumRightAfterAFretHitIsNotAnOverstrum()      // HOPO acertado por onFret em 1200; strum em 1230 → lista vazia, combo mantido
@Test fun withoutMarksEveryNoteNeedsAStrum()              // notas sem isHopo/isTap: onFret nunca acerta
@Test fun releasingTheFretEndsItsSustain()                // nota com sustain acertada por strum; onRelease(lane) para os pontos de sustain
```

- [ ] **Step 2: Rodar** — `./scripts/gw :core:game:test` → FAIL.
- [ ] **Step 3: Implementar** (reaproveitar o registro de acerto do `onPress` numa função privada; `onPress` não muda de comportamento: os 22 testes de hoje passam).
- [ ] **Step 4: Rodar** — `:core:game:test koverVerify` → PASS.
- [ ] **Step 5: Commit** — `feat: palheta no motor do jogo (acorde, HOPO e tap)`

### Task 3: A pista em código comum

**Files:**
- Move para `core/ui/src/commonMain/.../stage/`: `Flame.kt`, `HighwayGeometry.kt`, `StageDrawing.kt`, `StyledPieces.kt`, `Hud.kt` (o locale do placar vira `Locale.current` do Compose ou expect/actual), `StagePreviews.kt` (se o `@Preview` atrapalhar no comum, fica no Android).
- Modify → comum: `RockShapes.kt` e `HighwayStamps.kt` passam a montar `androidx.compose.ui.graphics.Path` (mesmas coordenadas; `Matrix` do Compose para as transformações; "furos" com `BlendMode.Clear`).
- Create: a fronteira por plataforma, só com o que `StageDrawing`/`StyledPieces`/`feature:gameplay`/`feature:settings` chamam hoje de `StageCache`, `HighwayTexture` e `HighwaySurface` (levantar a lista exata no começo da task e registrar no ledger). `expect` em `commonMain`; `actual` Android = o código de hoje movido sem mudança de comportamento (convertendo as `Path` do Compose com `asAndroidPath()`); `actual` PC = Skia: `Bitmap`/`Canvas` do Skia para assar os ladrilhos, `MaskFilter.makeBlur` para o desfoque (mesmos raios: 18, 4, 8), `Shader` de imagem com repetição, e a perspectiva do trapézio por uma homografia de 4 pontos calculada em comum (`fun quadToQuad(src: FloatArray, dst: FloatArray): FloatArray` 3×3), usada no PC e testada contra o `setPolyToPoly` do Android.
- Test: `core/ui/src/commonTest` ou `desktopTest`: `QuadToQuadTest` (os 4 cantos vão aos 4 cantos de destino; o centro de um retângulo vai ao cruzamento das diagonais do trapézio); `androidHostTest`: um teste comparando `quadToQuad` com `Matrix.setPolyToPoly` do Android para o trapézio da pista (diferença < 1e-3).

**Interfaces:**
- Produces: a mesma API pública de hoje do `stage/` (usada por `feature:gameplay`, `feature:settings` e `app`), agora em `commonMain`; `quadToQuad`.

- [ ] **Step 1: Teste da homografia (RED)** e linha de base: anotar os testes da pista (`FlameTest`, `HighwayGeometryTest`, `HighwayTextureTest`, `HudTest`, `StageCacheTest`, `StageDrawingTest`, `StageScreenshotTest`).
- [ ] **Step 2: Mover e separar a fronteira.**
- [ ] **Step 3: Rodar** — `:core:ui:testAndroidHostTest --rerun :core:ui:verifyRoborazziAndroidHostTest :feature:gameplay:testDebugUnitTest :feature:gameplay:verifyRoborazziDebug :feature:settings:verifyRoborazziDebug :app:assembleDebug :core:ui:compileKotlinDesktop` → PASS, screenshots **sem regravar** (diferença invisível só com comparação lado a lado e nota no commit).
- [ ] **Step 4: Commit** — `refactor: a pista em código comum (texturas por plataforma)`

### Task 4: A pista desenhada no PC

**Files:**
- Test: `core/ui/src/desktopTest/kotlin/.../stage/DesktopStageScreenshotTest.kt` — as mesmas cenas do `StageScreenshotTest` do Android (pista padrão com notas, sustain e chama; os 7 fundos; cada estampa; imagem do mapa atrás com o véu) gravadas em `core/ui/src/test/screenshots/desktop/`, com a tolerância de 1% do PC (suavização de texto/bordas entre Windows e Linux).

- [ ] **Step 1: Teste (RED)** — renderiza as cenas no PC; FAIL enquanto o `actual` do PC não existir (ou, se a Task 3 já o trouxe, sem referências gravadas).
- [ ] **Step 2: Implementar o que faltar no `actual` do PC** até desenhar todas as cenas sem erro.
- [ ] **Step 3: Gravar** (`recordRoborazziDesktop`) e **olhar cada imagem lado a lado com a do celular**; anotar no ledger diferenças visíveis e corrigir as que não forem só de suavização (o desfoque do Skia e o do Android podem diferir um pouco: aceitar se o efeito for o mesmo a olho).
- [ ] **Step 4: Rodar** — `:core:ui:desktopTest --rerun :core:ui:verifyRoborazziDesktop` → PASS (Windows); depois no Linux (`~/riff-linux`) → PASS.
- [ ] **Step 5: Commit** — `feat: a pista do Riff desenhada no PC`

### Task 5: Fechamento

- [ ] **Step 1:** Suíte completa → BUILD SUCCESSFUL; Linux (comando do CI) → verde.
- [ ] **Step 2:** README (módulos: `core:ui` inteiro multiplataforma; regras da palheta) e `docs/pendencias.md` se algo ficar. Commit.
