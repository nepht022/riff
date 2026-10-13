# Riff: filtros na pesquisa e jogo sem guitarra separada (Projeto A)

Data: 2026-10-13 · Status: aprovado em conversa, aguardando revisão da especificação escrita
Complementa: `2026-10-12-pesquisa-encore-design.md` (pesquisa e download do Encore) e `2026-10-08`/`2026-10-09` (guitarra separada, sons)

## 1. Objetivo

1. Na pesquisa do Encore, achar mais fácil os mapas que valem para o jogo: **originais** e **com guitarra separada**.
2. Jogar bem os mapas que **não** têm a guitarra separada: a música toca inteira, como no modo Spotify.

Contexto: a maioria dos mapas da comunidade vem com o áudio num arquivo só (`song.opus`); sem `guitar.*` não há guitarra para cortar no erro. O Smells Like Teen Spirit da biblioteca tem guitarra separada porque a separamos à mão em 2026-10-08.

Decisões do usuário (2026-10-13):

- O trabalho foi dividido em dois projetos. Este é o **A**. O **B** (separar a guitarra com IA no celular, a partir do teste de viabilidade de 2026-10-12: demucs.cpp, 9–10 min e ~5 GB por música no S25, mesma qualidade do PC) vem depois, com especificação própria, e traz a pergunta "Separar a guitarra com IA?" antes de jogar uma música sem guitarra separada.
- Filtro "Só com guitarra separada": com ele ligado, o app **busca páginas seguintes sozinho** até um limite.
- Alternância **Originais | Versões**: versões são covers, remixes, ao vivo, medleys (pelo título).
- Sem guitarra separada, **todos** os mapas (pasta, `.sng`, baixados) tocam inteiros, **sem som de acerto**, só com o som de erro. O som de acerto sai do app.

**Mudança (2026-10-13, depois da implementação):** o usuário pediu o som de acerto de volta. A remoção (§3, último item) foi revertida: sem guitarra separada, no modo Biblioteca, o acerto volta a tocar o som curto e o controle volta às Configurações; no Spotify o acerto segue sem som.

Suposições aceitas: a alternância começa em Originais; os filtros valem também na pesquisa aberta pelo Tocando agora.

## 2. Filtros na pesquisa

### Controles

- Abaixo do campo de busca: um seletor de duas posições **Originais | Versões** (começa em Originais) e um interruptor **Só com guitarra separada** (começa desligado). Valem para a tela aberta (não ficam salvos).
- Mudar um controle reaplica os filtros aos resultados já carregados e, se precisar, busca mais (abaixo); não refaz a busca do zero.
- Sons do menu: os dois controles soam como navegar.

### Originais e versões

- Um título é **versão** quando contém, como palavra inteira e sem diferenciar maiúsculas, um destes termos: `cover`, `remix`, `live`, `ao vivo`, `medley`, `mashup`, `acoustic`. "Remastered", "Remaster" e "(2011 Remaster)" contam como originais.
- Exemplos reais do Encore: "Enter Sandman (Metallica cover)" → versão; "Fade to Black (Live at Tushino)" → versão; "Black Album Medley" → versão; "Battery" → original; "Smells Like Teen Spirit" → original.
- Só o título (`name`) é olhado; um mapa sem esses termos conta como original (o filtro não é perfeito).

### Busca automática

- Os filtros são aplicados aos resultados que chegam do Encore (depois do filtro de rips oficiais). Se uma página fica sem nenhum resultado visível depois dos filtros e ainda há mais (`hasMore`), o app busca a próxima sozinho.
- Com **Só com guitarra separada** ligado: cada resultado que passa no filtro de originais/versões é conferido pelo começo do pacote (como a marca de hoje, no máximo 3 ao mesmo tempo); aparecem só os confirmados, à medida que chegam. O app continua buscando páginas até **10 mapas com guitarra separada** ou **100 resultados conferidos** desde a última busca ou "Procurar mais".
  - Enquanto procura: "Procurando mapas com guitarra separada… 37 de 100".
  - Sem nenhum: "Nenhum mapa com guitarra separada nos primeiros 100 resultados." e **Procurar mais** (mais 100).
  - Resultados que não deu para conferir (erro de rede no começo do arquivo) não aparecem com o filtro ligado.
- A marca "Guitarra separada" fica **guardada por mapa** durante a tela (md5 → sim/não): uma segunda busca que traz o mesmo mapa já mostra a marca, sem conferir de novo. (Resolve o ponto menor de 2026-10-12 sobre a marca que sumia.)
- Uma busca nova cancela a procura em andamento.

## 3. Jogar sem guitarra separada

- Mapa sem faixas separadas no modo Biblioteca: a música toca inteira; **acertar não faz som**; errar faz o som de erro curto de sempre (as seis gravações de guitarra CC0). Igual ao modo Spotify.
- Vale para pastas, `.sng` e mapas baixados. Com guitarra separada nada muda (a guitarra corta no erro e volta no acerto).
- O **som de acerto sai do app**: `SfxPlayer.hit`, os arquivos `sfx_hit_*.wav` e o `scripts/gerar_sfx.py` deixam de ser usados no jogo; nas Configurações somem o controle **Som de acerto** e a prévia dele; `Settings.hitVolume` e a preferência `hit_volume` saem (o valor guardado é ignorado). **Volume geral** e **Som de erro** ficam. O metrônomo da calibração continua com a sua nota (gerada à parte em `ClickTrack`).

## 4. Arquitetura

- **`:core:encore`:** `VersionTitles.isVersion(title: String): Boolean` (termos da §2).
- **`:feature:search`:** `SearchViewModel` ganha `filter` (originais/versões, só com guitarra separada), aplica os filtros, guarda `separateGuitar` por md5, e a busca automática (§2); `SearchScreen` ganha a linha de controles e as mensagens de procura.
- **`:feature:gameplay`:** `GameSession` sem `hitSounds` (nunca toca acerto); `GameplayViewModel` só passa o volume de erro.
- **`:core:audio`:** `SfxPlayer.hit` e `setVolumes(hit, miss)` viram `setMissVolume(miss)`; `SoundPoolSfxPlayer` deixa de carregar os sons de acerto; `res/raw/sfx_hit_*.wav` saem.
- **`:core:model` / `:core:datastore` / `:feature:settings`:** sai `hitVolume` (modelo, preferência, tela e ViewModel).
- **README:** seção da pesquisa (filtros) e de Jogar/Configurações (sem som de acerto; sem guitarra separada a música toca inteira).

## 5. Erros e casos de borda

- Encore fora do ar durante a busca automática: para de procurar e mostra o erro de sempre ("Não deu para falar com o Encore."), mantendo os já encontrados.
- O limite de 100 conta resultados conferidos (os que passaram no filtro de originais/versões e tiveram o começo do arquivo lido), não páginas.
- Trocar Originais/Versões com o filtro de guitarra ligado: reaplica e retoma a procura com a mesma contagem.

## 6. Testes

- `VersionTitles`: os exemplos reais da §2, palavra inteira ("Livewire" não é versão; "Live at" é), maiúsculas.
- `SearchViewModel`: Originais esconde versões e Versões só mostra versões; página vazia depois dos filtros busca a próxima; filtro de guitarra busca até 10 encontrados e até 100 conferidos (transporte falso com várias páginas e pacotes com e sem `guitar.*`); "Procurar mais"; marca guardada entre buscas; busca nova cancela a procura.
- Tela: controles com o som de navegar; mensagens "Procurando…" e "Nenhum…"; screenshot da pesquisa com os controles.
- `GameSession`/`GameplayViewModel`: mapa sem guitarra separada não chama acerto e chama erro.
- Configurações: sem o controle de acerto; screenshot regravado.
- Preferências: um `hit_volume` antigo guardado não quebra a leitura.
- No aparelho: pesquisar "metallica" (Originais e Versões), ligar "Só com guitarra separada", jogar um mapa sem guitarra separada (sem som de acerto).
