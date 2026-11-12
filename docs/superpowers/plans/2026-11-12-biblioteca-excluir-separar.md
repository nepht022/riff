# Biblioteca: X em cada música e barra de seleção embaixo — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:test-driven-development. Steps use checkbox (`- [ ]`) syntax.

**Goal:** opção D aprovada pelo usuário em 2026-11-12 (`C:\Users\Rodrigo Machado\Pictures\Riff comparar\excluir-separar.html`): a lista da Biblioteca de hoje com o X de excluir sempre à vista em cada cartão; "Selecionar" continua no ⋮; no modo Selecionar, as ações saem do topo e vão para uma barra fixa embaixo.

**Architecture:** só UI em `feature/library` (commonMain: `LibraryDeletion.kt`, `LibraryScreen.kt`); o ViewModel e os callbacks (`DeletionCallbacks`) já têm tudo (`onDelete` de um mapa com confirmação, `onToggleAll`, `onDeleteSelected`, `onSeparateSelected`, `onCancelSelecting`).

## Global Constraints
- "+ Mapas", topo, filtro, chips, cartão (opção A do cartão), menu ⋮ (Reimportar, Trocar pasta, Selecionar): sem mudança.
- Identidade "Pôster de turnê"; toque ≥ 44 dp; contraste ≥ 4,5:1; descrições para leitor de tela; strings pt/en.
- PC: tudo alcançável só por teclado e só por controle (foco visível, `menuKeys`); nada piora.
- Detekt 120 colunas, comentários em português. Screenshots que mudarem regravadas e conferidas com Read.

## Decisões
1. **Fora da seleção:** o X vermelho no canto de cada cartão (o `DeleteX` de hoje), sempre à vista; tocar abre a confirmação de hoje ("Excluir “Título”?"). Tocar no cartão continua jogando. Mapa protegido (`canDelete` falso: solto na raiz / em separação) sem X.
2. **No PC:** o X é alcançável por teclado/controle (ex.: → a partir do cartão, ou Tab), sem atrapalhar ↑/↓ entre os cartões; documentar o caminho escolhido no teste.
3. **Na seleção:** cada cartão com a caixa de marcar à esquerda (como hoje), **sem X**. Tocar no cartão marca/desmarca.
4. **Barra fixa embaixo** (fora da lista, sempre visível durante a seleção, fundo escuro com borda superior vermelha): linha de cima "N marcadas" (plural pt/en) à esquerda e "Marcar todas"/"Desmarcar todas" (o `SelectAll`, só os mapas à mostra) à direita; linha de baixo `Separar (n)` (PosterButton papel/claro, desabilitado com 0 separáveis) e `Excluir (n)` (PosterButton vermelho, desabilitado com 0) lado a lado e grandes, e **Cancelar** pequeno (TextButton) no fim da linha. A lista ganha espaço embaixo para o último cartão não ficar atrás da barra.
5. A `SelectionActions` de cima da lista sai.
6. Fonte grande (acessibilidade): a barra não pode cortar texto; se não couber em uma linha, quebra.

## Testes (TDD: FAIL → implementar → PASS)
- `xShowsOnEveryCardOutsideSelecting` (e não em mapa protegido); `xAsksToDeleteThatChart`.
- `selectingHidesTheXAndShowsTheBottomBar`; `bottomBarCountsAndActs` (Separar/Excluir/Cancelar/Marcar todas chamam os callbacks; desabilitados com 0).
- PC: `pcKeyboardReachesTheXAndTheBottomBar` (foco no X e nos botões da barra só por teclado).
- Screenshots: `Library_list`, `Library_selecting`, `LargeFont_library` (e `Library_menu`/`Library_ai_*` se mudarem) regravadas no celular e no PC.
- Commit `feat: Biblioteca com X em cada música e barra de seleção embaixo`.

## Task 2: docs e teste do usuário
- `docs/pendencias.md` (seção nova), README se citar o Selecionar/X. Teste do usuário: celular (APK release por cima) e PC.
