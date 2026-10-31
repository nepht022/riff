# Riff no Windows

Data: 2026-10-31 · Status: rascunho para o usuário aprovar (perguntas do revisor respondidas em 2026-10-31)
Segundo de três projetos, nesta ordem (decisão do usuário): 1. tirar o Spotify (feito, 0.5.0) → **2. Riff no Windows** → 3. multiplayer por código de sala (até 4, placar ao vivo, Firebase gratuito). Este documento cobre só o 2.

## 1. Intenção

O usuário quer jogar o Riff **sentado no PC, de teclado ou de controle de PlayStation**, e depois (projeto 3) **online com os amigos**. O Riff do Windows é o mesmo jogo do celular (tudo menos o Spotify, que saiu na 0.5.0), feito a partir da **mesma base de código**: uma correção ou mudança no jogo vale para os dois.

Referência dele: já jogou Clone Hero no PC e "achou ok no teclado".

## 2. Decisões do usuário

- Plataforma: Windows, com **Compose Multiplatform Desktop**, na mesma base de código do Android (decidido na conversa de 2026-10-30).
- Controles: **teclado** ou **controle**, com **teclas configuráveis**. O controle do usuário é **genérico, ligado por cabo** (resposta de 2026-10-31; na conversa ele disse "de PlayStation" — qualquer controle USB comum precisa funcionar).
- **Tocar com palheta** (como no Guitar Hero/Clone Hero: uma mão segura as cores, a outra aperta a palheta no tempo de cada nota) e a opção **"Tocar sem palheta"** (só apertar a cor na hora certa, como no toque do celular).
- Teclas de fábrica parecidas com as do Clone Hero.
- Biblioteca **começa do zero** no PC: o usuário escolhe uma pasta de mapas no PC ou baixa pela pesquisa do Encore. Nada vem do Clone Hero dele nem do celular.
- Tudo do Riff de hoje, menos o Spotify. Aceitos pelo usuário ("está certo, pode seguir") os palpites:
  - ranking online no mesmo Apps Script e na mesma planilha, mas com **três tabelas separadas** (resposta do usuário, opção "c"): **celular**, **PC com palheta** e **PC sem palheta**. O placar de cada mapa mostra uma tabela por vez, com a troca na tela Ranking;
  - **o mesmo nome no celular e no PC** (resposta do usuário): o nome deixa de ser preso a um só aparelho. Como ligar (escolha minha): no aparelho que já tem o nome, as Configurações mostram um código curto e temporário ("Usar meu nome em outro aparelho"); no outro, ao pedir o nome, há "Já tenho um nome" para digitar o código. O script passa a aceitar várias chaves de aparelho por nome; liberar o nome solta todas;
  - dados (recordes, separações, ajustes) **não** passam entre celular e PC: cada um tem os seus;
  - guitarra separada por IA também no PC;
  - mesma tela inicial (Sozinho, Sala "em breve", Ranking), adaptada à tela deitada do PC e navegável por teclado e controle;
  - instalação por um instalador baixado do GitHub; o aviso do Windows sobre app desconhecido fica para depois.

## 3. Objetivos, não objetivos e anti-objetivos

**Objetivos**
- No Windows: importar mapas de uma pasta, pesquisar e baixar no Encore, jogar com teclado ou controle (com e sem palheta), resultado e recorde, ranking online, guitarra separada por IA, calibração, Configurações, Reportar, Licenças.
- Teclas e botões configuráveis nas Configurações, com teclas de fábrica.
- Todas as telas usáveis só com teclado ou só com controle (sem mouse), e também com mouse.
- O Android continua funcionando igual: nenhuma diferença para quem joga no celular (testes e screenshots do Android continuam passando sem mudança visual).
- Base pronta para o projeto 3: o jogo do PC e o do celular contam pontos do mesmo jeito.

**Não objetivos**
- Multiplayer, Sala funcionando (projeto 3).
- Separar por IA com o Riff fechado (escolha minha): no PC a fila fica salva e roda só com o Riff aberto, continuando na próxima abertura; sem bandeja do sistema nem serviço à parte. "Separar só carregando" só aparece num notebook.
- Guitarra de plástico (controle de Guitar Hero/Rock Band) — pode entrar depois, se o mapa de botões já permitir.
- macOS, Linux, loja da Microsoft, assinatura de código.
- Levar dados entre celular e PC.
- Toque na tela do PC (notebook com tela sensível): não é pedido.

**Anti-objetivos**
- Duplicar o jogo: duas cópias das regras, da pista ou das telas, uma para cada plataforma.
- Atraso de entrada perceptível no teclado ou no controle (o jogo é de ritmo).
- Quebrar ou mudar o Riff do celular.

## 4. Como se joga no PC

- **Com palheta (padrão — escolha minha):** a nota conta quando a palheta é apertada com as cores certas seguradas, dentro da janela de acerto. Acordes: todas as cores do acorde seguradas; **um acorde de N cores vale N notas** em pontos, combo e precisão, como no celular (escolha minha: o mesmo mapa conta do mesmo jeito em todo lugar; no Clone Hero valeria 1). Palheta sem nota, ou com cor errada, é erro (como no Clone Hero). Detalhes de HOPO/tap do mapa: decisão do construtor, preferindo o comportamento do Clone Hero quando o mapa trouxer essas marcas.
- **Sem palheta:** apertar a cor na hora certa basta (o mesmo julgamento do toque no celular).
- **Teclas de fábrica (escolha minha, estilo Clone Hero):** cinco cores numa linha da mão esquerda e palheta nas setas ↑/↓ da mão direita; o construtor confere o padrão atual do Clone Hero e o segue. Pausa: Esc. Controle: botões de fábrica a definir pelo construtor (cores nos botões frontais e ombros, palheta no direcional), com teste no controle genérico USB do usuário.
- **Configurar:** nas Configurações, uma aba de controles: escolher "com/sem palheta", e para cada ação (cor 1–5, palheta, pausa) apertar a tecla ou o botão que quer. Voltar ao padrão.

## 5. Telas

- Mesmo visual "pôster de turnê", mesmas telas. Adaptadas a janela deitada e redimensionável (tela cheia opcional).
- Foco visível em todas as telas; setas/direcional movem, Enter/X confirma, Esc/○ volta.
- As maquetes da tela inicial no PC e da aba de controles serão mostradas ao usuário para escolher antes do plano da etapa que as constrói.

## 6. Etapas (escolha minha; cada uma com plano, revisão e merge próprios)

1. **Base comum:** os módulos que podem ser comuns passam a ser multiplataforma (regras, mapas, dados, telas), com o Android usando-os sem mudar nada para o jogador. Entrega: Android igual, suíte verde, e um app de PC que abre a tela inicial.
2. **Jogar no PC com teclado:** biblioteca de uma pasta do PC, áudio (inclusive faixas separadas e o corte da guitarra no erro), partida com e sem palheta, resultado, recorde, calibração, Configurações.
3. **Controle e teclas configuráveis:** controle de PlayStation, aba de controles, navegação de todas as telas sem mouse.
4. **O resto e o instalador:** pesquisa do Encore, guitarra separada por IA, ranking (três tabelas e o mesmo nome em vários aparelhos, mudando também o celular e o Apps Script — republicar o script é ação do usuário), Reportar, Licenças, instalador do Windows publicado no GitHub Releases (só quando o usuário pedir).

## 7. Restrições

- Mesmo repositório, mesmos padrões (TDD, detekt, testes de tela). O CI passa a compilar e testar também o PC.
- Nada do Guitar Hero (sons, imagens) — vale também para o PC.
- Endereços do ranking e do Reportar como hoje (fora do git, embutidos no build).
- O usuário compila no Windows (pasta em `/mnt/c`, `scripts/gw`).

## 8. Riscos

- **Tamanho:** cerca de metade dos arquivos usa APIs só do Android (Hilt, MediaCodec/AudioTrack, WorkManager, seletor de pastas, Robolectric nos testes). A etapa 1 é a mais arriscada para o celular.
- **Áudio no PC:** decodificar ogg/opus/mp3/wav e os `.sng` e tocar várias faixas com baixa latência exige bibliotecas que o Android dá de graça; a escolha precisa ser medida (latência e sincronia) cedo, na etapa 2.
- **Controle genérico no Windows:** pode aparecer como XInput ou como DirectInput, conforme o modelo; a biblioteca precisa ler os dois (ex.: SDL com a base de mapeamentos de controles). Testar com o controle do usuário no começo da etapa 3.
- **IA no PC:** o ONNX Runtime para PC teve problema com o Java do Android Studio (msvcp140); o instalador precisa levar um Java que funcione.
- **Aviso do Windows** ao instalar app sem assinatura: aceito por agora.

## 9. Testes e aceite

- Android: suíte atual verde, screenshots iguais. Se a migração mudar pixels de forma invisível (antialiasing), as referências podem ser regravadas com comparação lado a lado e nota no commit (escolha minha); diferença visível é defeito.
- PC: testes das regras novas (palheta, teclas configuradas), testes de tela no desktop, medição de latência entrada → julgamento e de sincronia do áudio.
- Aceite do usuário no PC dele em cada etapa: abrir, jogar com teclado (e depois com o controle), com e sem palheta.

## 10. Deixado para o construtor

- Como dividir os módulos em comum e por plataforma, e como substituir o Hilt onde for comum.
- Bibliotecas de áudio, controle e instalador (registrando a escolha e a medição).
- Botões de fábrica do controle; HOPO/tap.
- Onde o PC guarda dados e a pasta de mapas.
