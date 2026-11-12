# Design novo (celular e PC) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** aplicar no app os protótipos aprovados pelo usuário em 2026-11-11: tela inicial, Biblioteca, Resultado e sala no celular; tela inicial e Configurações no PC; capas, "Voltar" com seta, nota do Resultado e barra de teclas do PC.

**Architecture:** as telas são comuns (Compose Multiplatform); a mudança é de layout, com poucos dados novos (última partida, recordes recentes, recorde anterior, capa por mapa). Peças comuns novas no `:core:ui`; a leitura da capa sai do `:feature:gameplay` para um lugar comum. Onde celular e PC divergem, o layout escolhe pela largura (como as telas já fazem) ou por `expect`/plataforma.

**Tech Stack:** Kotlin 2.4.20, Compose Multiplatform 1.12.2, Room, Roborazzi (celular `recordRoborazziAndroidHostTest`, PC `recordRoborazziDesktop`/`Jvm`).

**Spec (fonte visual):** os protótipos em `https://claude.ai/artifact/Kg1yZJFeXnQULyU8uifv2Z` (privado do usuário; o implementador não abre) — descritos abaixo em "Telas", que é a referência para quem implementa. Identidade de hoje: "Pôster de turnê" (`core/ui`: `RiffColors`, `PosterButton`, `PosterTitle`, fontes New Rocker e Oswald).

## Global Constraints

- Mesma identidade visual: fundo `RiffColors` escuro, papel, vermelho, tinta; New Rocker nos títulos/botões-cartaz, Oswald no texto; sombra dura preta; sem gradientes.
- Toque ≥ 44 dp; contraste do texto ≥ 4,5:1; leitores de tela com descrições (como hoje).
- PC: tudo continua navegável só por teclado e só por controle (foco visível, `menuKeys`), nada piora na navegação de hoje.
- Nada de funcionalidade nova além do descrito (nada de rede nova; a capa vem da imagem do mapa que o app já lê).
- Todas as screenshots que mudarem são regravadas e conferidas com Read; nenhum teste de comportamento existente pode quebrar (ajuste só o que testa texto/posição que o design mudou de propósito).
- Detekt (120 colunas), comentários em português, strings pt/en.

## Decisões

1. **Nota do Resultado:** S ≥ 98% de precisão, A ≥ 90%, B ≥ 80%, C abaixo (proposta aceita pelo usuário; limites numa constante `ResultGrade`).
2. **Capa:** `ChartArtImages` (hoje em `feature/gameplay/.../ChartArt.kt`) passa para um módulo comum que Biblioteca, Resultado, sala e tela inicial usam (ex.: `:core:ui` para o componente `ChartThumb(chartId, title, size)` e a interface; a implementação continua onde está a leitura de arquivo). Sem imagem: quadrado com as iniciais do título (até 2 letras) sobre uma cor escolhida pelo hash do `chartId` entre vermelho, tinta e marrom do tema. Cache em memória por `chartId` (LRU pequeno, ~64) para a lista não reler a imagem ao rolar.
3. **"Voltar":** componente comum com seta + "Voltar" (44 dp de altura), em todas as telas que hoje usam o texto "Voltar" no topo (e "Sair da sala" na sala).
4. **Barra de teclas (só PC):** rodapé fixo com as teclas da tela atual ("← → Mover", "Enter / ✕ Escolher", "Esc / ○ Voltar"…); cada tela informa as suas; mostra os símbolos do controle quando há um ligado, só teclado quando não.
5. **Tela inicial do celular:** Sozinho (vermelho) e Sala (papel, "com amigos") maiores; "Continuar" (a última partida: capa, título, artista, dificuldade, recorde; tocar abre a partida dessa música e dificuldade; some sem partidas); "Últimos recordes" (até 3: título e pontos); Ranking embaixo. Dados: `ScoreRepository` ganha `last(): ScoreRecord?` e `recentBests(limit: Int): List<ScoreRecord>` (por `playedAtEpochMs`).
6. **Tela inicial do PC:** três quadros iguais lado a lado — Sozinho, Sala, Ranking — com uma linha de apoio cada ("Biblioteca e Encore", "Até 4, celular e PC", a tabela do modo atual); "Reportar" e "Configurações" com texto; barra de teclas.
7. **Biblioteca:** topo = Voltar · ⋮ (menu: Reimportar, Trocar pasta, Selecionar); título + "+ Mapas" (abre a pesquisa no Encore); campo de filtro (o filtro local de hoje); chips "Todas · N", "Guitarra separada", "Com recorde"; cartões com capa, título, artista · duração, "Recorde N · Dificuldade" ou "Ainda sem recorde"; selo "IA" quando a guitarra é separada; os X de apagar só no modo Selecionar. O aviso da importação (mapas importados/pastas com erro) continua, no mesmo lugar.
8. **Resultado:** capa + título + artista · dificuldade; carimbo da nota (Decisão 1) inclinado; pontos grandes; "Novo recorde" e "antes: N" (o recorde anterior, quando havia); barra de precisão; 4 blocos (Maior combo, Perfeitos, Bons, Erros); linha do ranking (o status de hoje); "Voltar" e "Jogar de novo" lado a lado (na partida de sala: "Ver a sala" e "Voltar à sala", como hoje).
9. **Sala (celular e PC):** topo = "Sair da sala" com seta · código pequeno ("SALA KQZX") com botão compartilhar (celular: `Intent.ACTION_SEND` com o texto "Entra na minha sala do Riff: KQZX"; PC: copia o código para a área de transferência e mostra "Código copiado"); jogadores logo abaixo ("Jogadores · N de 4"); música com capa e "Trocar" (dono); "Sua dificuldade" em 4 botões; uma linha do que falta ("2 prontos · esperando Bruno_88 terminar de baixar"); "Não estou pronto"/"Pronto" e "Começar" lado a lado embaixo.
10. **Configurações do PC:** abas numa coluna à esquerda (selecionada em vermelho; "Licenças e privacidade" no pé da coluna), opções à direita em blocos maiores; barra de teclas. O celular mantém as abas no topo.

## Review Focus

1. Lista grande da Biblioteca (centenas de mapas): rolar sem engasgar com as capas → `thumbnailsAreCachedAndLoadedOffTheMainThread` (Task 1).
2. Mapa sem imagem ou com imagem corrompida: iniciais, sem erro → `missingOrBrokenArtShowsInitials` (Task 1).
3. Fonte grande do sistema (acessibilidade) no celular: as telas rolam, nada cortado → screenshots `LargeFont_*` das telas mudadas (cada task).
4. PC só com teclado/controle: todos os botões novos alcançáveis (menu ⋮, chips, compartilhar, abas da coluna) → testes de foco no `:desktop` (Tasks 3, 5, 6).
5. Primeira vez (sem partidas, sem recordes): tela inicial sem "Continuar"/"Últimos recordes", sem buracos → `homeWithoutMatchesHidesContinue` (Task 2).

---

### Task 1: peças comuns — capa, Voltar, nota, barra de teclas

**Files:** Create em `core/ui/src/commonMain/.../ui/`: `ChartThumb.kt`, `BackRow.kt`, `ResultGrade.kt`, `KeyHintBar.kt`; move `ChartArtImages` para comum (e ajuste `:feature:gameplay`, app e `DesktopGraph`); Test em `core/ui` (screenshots e unidade).
- Produces: `fun interface ChartArtImages { suspend fun load(chartId: String): ImageBitmap? }` (no novo lugar), `@Composable fun ChartThumb(chartId: String, title: String, size: Dp, images: ChartArtImages?)`, `@Composable fun BackRow(text: String = "Voltar", onBack: () -> Unit)`, `enum class ResultGrade { S, A, B, C; companion object { fun of(accuracy: Float): ResultGrade } }`, `@Composable fun KeyHintBar(hints: List<KeyHint>)`, `data class KeyHint(val keys: String, val pad: String?, val label: String)`.
- [ ] Testes: `gradeLimits` (0,98→S, 0,9799→A, 0,90→A, 0,80→B, 0,7999→C), `missingOrBrokenArtShowsInitials`, `initialsColorIsStablePerChart`, `thumbnailsAreCachedAndLoadedOffTheMainThread`, screenshots de cada peça. → FAIL → implementar → PASS → commit `feat: peças do design novo (capa, Voltar, nota, barra de teclas)`.

### Task 2: tela inicial (celular e PC)

**Files:** `feature/home` (layout por largura: celular Decisão 5, PC Decisão 6), `core/data` + `core/database` (`ScoreDao` com as consultas de `last`/`recentBests`; sem migração se só consulta), app e `DesktopGraph`; Test `HomeContentTest`, screenshots `Home`, `Home_desktop`, `Home_continue`, `LargeFont_home`.
- [ ] Testes: `continueOpensTheLastMatch`, `homeWithoutMatchesHidesContinue`, `recentBestsShowUpToThree`, `pcShowsThreeTilesAndKeyHints`, screenshots. → FAIL → implementar → PASS → commit `feat: tela inicial nova`.

### Task 3: Biblioteca

**Files:** `feature/library` (topo, menu ⋮, "+ Mapas", chips, cartões com capa e recorde, X só no Selecionar), dados de recorde por mapa (o que a lista já tem ou uma consulta do maior recorde por mapa), app e PC; Test `LibraryContentTest`, `LibraryPickTest`, screenshots, foco no PC.
- [ ] Testes: `menuHasReimportChangeFolderAndSelect`, `deleteOnlyInSelectMode`, `chipsFilterSeparateGuitarAndWithRecord`, `cardShowsRecordOrNoRecordYet`, `pcKeyboardReachesMenuChipsAndCards`, screenshots (lista, vazia, selecionando, fonte grande, PC). → FAIL → implementar → PASS → commit `feat: Biblioteca nova`.

### Task 4: Resultado

**Files:** `feature/results` (Decisão 8; o recorde anterior: o melhor antes desta partida), app e PC; Test `ResultsContentTest`, screenshots (`Results_new_best`, `Results_ranking_*`, sala, fonte grande, PC).
- [ ] Testes: `gradeStampFromAccuracy`, `previousBestShownOnNewRecord`, `buttonsSideBySide`, `roomResultKeepsSeeRoomAndBackToRoom`, screenshots. → FAIL → implementar → PASS → commit `feat: Resultado novo`.

### Task 5: sala

**Files:** `feature/room` (`RoomLobbyScreen` reorganizada, Decisão 9; compartilhar por `expect`: Android `Intent.ACTION_SEND`, PC área de transferência), app e PC; Test `RoomScreensTest`, screenshots da sala (todas as de hoje regravadas), `DesktopRoomFlowTest` (foco no compartilhar).
- [ ] Testes: `shareSendsTheCodeText` (celular, com a intenção capturada), `pcShareCopiesTheCode`, `whatIsMissingLine` ("2 prontos · esperando X terminar de baixar"), screenshots. → FAIL → implementar → PASS → commit `feat: sala nova`.

### Task 6: Configurações do PC e barra de teclas em todas as telas do PC

**Files:** `feature/settings` (layout de coluna à esquerda quando largo), `desktop/.../RiffDesktopApp.kt` (barra de teclas por tela), screenshots `Settings_wide*`; Test de foco nas abas da coluna.
- [ ] Testes: `wideSettingsHasTabsColumn`, `everyPcScreenShowsItsKeys`, `padHintsOnlyWithAPad`, screenshots. → FAIL → implementar → PASS → commit `feat: Configurações e barra de teclas no PC`.

### Task 7: documentação e teste do usuário

- [ ] `README.md` (Screenshots/descrição das telas, se mudou algo dito lá), `docs/pendencias.md` ("Design novo"); commit `docs: design novo`.
- [ ] Teste do usuário: celular (APK release por cima) e PC — as seis telas, a capa nas listas, compartilhar o código, a barra de teclas com e sem controle.
