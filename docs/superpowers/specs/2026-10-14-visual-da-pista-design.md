# Riff: visual da pista (fundos, desenhos, notas, botões) e imagem do mapa

Data: 2026-10-14 · Status: aprovado em conversa, aguardando revisão da especificação escrita
Complementa: `2026-10-07-identidade-visual-design.md` (pista, gemas e chama desenhadas em código) e `2026-10-09` (polimento: a pista não aloca por quadro)

## 1. Objetivo

Deixar o jogador escolher o visual da partida, no espírito das opções de pista dos jogos de guitarra, com temas de rock. Mostrar a imagem do mapa atrás da pista.

Restrições:

- Nada do Guitar Hero nem de projetos que redesenham seus assets: tudo é desenhado em código pelo Riff (sem imagens novas no APK).
- A partida continua a 120 fps no Galaxy S25 com qualquer combinação, medida com `scripts/desempenho.sh medir`.
- O visual de hoje continua sendo o de fábrica.

## 2. Decisões do usuário (2026-10-13/14)

- Vídeo de fundo: descartado (o app continua baixando a versão sem vídeo).
- **Imagem do mapa** atrás da pista: opção nas Configurações, **ligada de fábrica**.
- O interruptor "Perguntar de novo" passa a se chamar **"Perguntar sempre se separa"** e fica **ligado de fábrica**.
- Quatro escolhas visuais independentes, todas com estas opções (prévias em `.superpowers/pistas/`, aprovadas com "gostei de tudo"):
  - **Fundo da pista** (superfície): Palco (atual), Amplificador, Couro e rebites, Braço de guitarra, Chamas, Neon, Chapa de metal. O usuário foi avisado de que o Braço de guitarra lembra a pista do Guitar Hero e manteve.
  - **Design da pista** (estampa clara impressa na superfície, por baixo das notas): Sem desenho (atual), Caveira alada, Raios, Estrelas, Guitarras, Palhetas (com o raio do ícone do Riff), Tatuagem (coração com adaga).
  - **Design das notas**: Discos (atual), Palhetas, Caveiras, Estrelas, Raios, Chamas.
  - **Design dos botões** (os 5 da linha de acerto): Discos (atual), Pedais de efeito, Knobs de amplificador, Alto-falantes, Palhetas.
- Cores das trilhas (verde, vermelho, amarelo, azul, laranja) não mudam em nenhum design.

## 3. O que o usuário vê

### Configurações, seção Jogo

- **Fundo da pista**, **Design da pista**, **Design das notas**, **Design dos botões**: uma linha cada, com o nome escolhido à direita (como "Calibrar atraso do áudio"). Tocar abre uma lista com uma miniatura de cada opção (a miniatura é a própria pista/nota/botão desenhada pelo app) e o nome; a escolhida fica marcada. Escolher fecha e salva. Sons: abrir e escolher soam como navegar.
- **Imagem do mapa** (interruptor, ligado de fábrica): "Mostra a capa ou a imagem de fundo do mapa atrás da pista."

### Configurações, seção Guitarra separada por IA

- **Perguntar sempre se separa** (interruptor, ligado de fábrica), com a explicação de hoje.

### Partida

- A pista usa o fundo e a estampa escolhidos; os dois **rolam junto com as notas** (andam na mesma velocidade, como a pista se movendo).
- Notas e botões usam os designs escolhidos, achatados pela perspectiva como os discos de hoje. Notas longas, HOPO (centro branco) e a chama do acerto continuam valendo em todos.
- Com **Imagem do mapa** ligada e um mapa que tenha `background.*` (preferida) ou `album.*` (capa), a imagem cobre a tela atrás da pista, escurecida (para não brigar com as notas), cortada para preencher. Sem imagem, fica o fundo de palco de hoje. Vale na Biblioteca e no Tocando agora (pelo mapa casado).

## 4. Arquitetura

- **`:core:model`**: `enum class HighwayBackground`, `HighwayArt`, `NoteStyle`, `FretStyle` (com o padrão = visual atual) e os campos em `Settings`: `highwayBackground`, `highwayArt`, `noteStyle`, `fretStyle`, `showChartImage = true`, `askSeparationAlways = true`.
- **`:core:datastore`**: chaves novas; as enums são gravadas pelo nome (um nome desconhecido volta ao padrão). A preferência de perguntar sempre ganha **chave nova** (`ask_separation_always_v2`): a antiga já estava gravada como desligada para quem salvou qualquer ajuste, e a decisão é ligar para todos uma vez.
- **`:core:ui` / `stage`**:
  - `HighwayTexture`: gera, no começo da partida (fora do laço de quadros), um bitmap que se repete na profundidade com o fundo e a estampa escolhidos, desenhados em código (ruído, linhas, formas). Determinístico (semente fixa).
  - A superfície da pista passa a ser desenhada com um `BitmapShader` (repetição vertical) e uma matriz de perspectiva (`Matrix.setPolyToPoly` do retângulo da textura para o trapézio da `HighwayGeometry`), com o deslocamento da rolagem na matriz. Matriz e `Paint` reaproveitados: nenhuma alocação por quadro.
  - Linhas das trilhas, trastes e bordas continuam por cima, com a cor da borda vindo do fundo escolhido.
  - `drawGem` e `drawFretButton` escolhem a forma pelo estilo; as formas em "tamanho 1" ficam prontas no `StageCache`, como os pincéis de hoje.
- **`:core:data`**: `ChartArtFinder` acha `background.*` e depois `album.*` (`jpg`, `jpeg`, `png`) na pasta ou no `.sng`; `PlayableChart` ganha `artUri: String?`.
- **`:feature:gameplay`**: o ViewModel decodifica a imagem (reduzida ao tamanho da tela, uma vez) quando a opção está ligada; a tela a desenha atrás de tudo com um véu escuro.
- **`:feature:settings`**: as quatro linhas com a lista de escolha, o interruptor da imagem e o novo nome do interruptor de perguntar.

## 5. Erros e casos de borda

- Imagem que não decodifica (corrompida, formato estranho): fica o fundo de hoje, sem aviso.
- Imagem enorme: decodificada já reduzida (`inSampleSize`) para não estourar a memória.
- Preferência gravada com um nome de opção que não existe mais: volta ao padrão.
- Telas pequenas/grandes: a textura se ajusta à geometria; a estampa mantém a proporção.

## 6. Testes

- `Settings`/preferências: padrões (visual atual, imagem ligada, perguntar sempre ligado), gravação e leitura de cada enum, nome desconhecido volta ao padrão, a chave antiga de perguntar é ignorada.
- `ChartArtFinder`: prefere `background.*`, cai para `album.*`, ignora maiúsculas, funciona em `.sng`; nenhum.
- `HighwayTexture`: gera o tamanho pedido para cada fundo e estampa sem erro; determinística (mesma semente, mesmos pixels).
- Screenshots (Roborazzi): uma imagem por categoria com todas as opções lado a lado (pista com cada fundo, pista com cada estampa, fileira de notas e de botões de cada design) e a partida com imagem do mapa.
- Configurações: as quatro linhas abrem a lista, escolher chama o ViewModel e mostra o nome; os interruptores.
- Gameplay: com a opção ligada e um mapa com imagem, o estado traz a imagem; desligada, não.
- No aparelho: cada fundo/design na partida, `scripts/desempenho.sh medir` com a combinação mais pesada (Chamas + Caveira alada + Caveiras + Alto-falantes + imagem do mapa): 120 fps, poucos quadros atrasados.
