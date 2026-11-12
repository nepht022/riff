<p align="center"><img src="docs/icone.png" width="160" alt="Ícone do Riff"></p>

# Riff

[![CI](https://github.com/nepht022/riff/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/nepht022/riff/actions/workflows/ci.yml)

Jogo de ritmo no estilo Guitar Hero para **Android e Windows**, com **multiplayer** de até 4 pessoas (celular e PC juntos). As notas vêm de mapas do Clone Hero que você importa ou baixa do Encore, e a música vem da pasta do mapa. Se a pasta traz a guitarra separada (ou a IA do Riff a separou), ela some quando você erra e volta quando acerta, como no Guitar Hero; sem ela, a música toca inteira e o acerto fica em silêncio.

Projeto pessoal, gratuito e sem anúncios; não está na Play Store nem na Microsoft Store.

## Baixar

Tudo fica em **[Releases](https://github.com/nepht022/riff/releases/latest)**:

- **Celular (Android 8 ou mais novo, ARM):** baixe o `Riff-<versão>.apk`, abra no celular e permita "instalar apps desconhecidos". Uma versão nova instala por cima e mantém recordes, mapas e ajustes.
- **PC (Windows 10/11, 64 bits):** baixe o `Riff-<versão>.msi` e abra. O instalador não tem assinatura digital, então o Windows avisa que o app é desconhecido: "Mais informações" → "Executar assim mesmo". Instala só para o seu usuário, sem pedir administrador, e o Java vem junto. Os dados ficam em `%APPDATA%\Riff` e continuam ao atualizar ou desinstalar.
- Os arquivos `fonte-*` de cada versão são o código-fonte do software livre que vai no instalador do PC (Java, FFmpeg, LAME); não precisa baixar para jogar.

## O que tem

- **Sozinho:** a Biblioteca com os seus mapas (capas, filtro, "Guitarra separada" e "Com recorde"), a partida e o Resultado com nota (S, A, B, C) e recorde.
- **Sala:** crie uma sala e mande o código de 4 letras; até 4 pessoas, no celular e no PC. O dono escolhe a música, cada um escolhe a dificuldade, a música começa junta em todos, com placar ao vivo e a classificação da sala no fim.
- **Ranking online:** três tabelas (Celular, PC com palheta, PC sem palheta) e o mesmo nome em vários aparelhos.
- **Pesquisa no Encore:** procura e baixa mapas da comunidade direto no app.
- **Guitarra separada por IA:** separa a guitarra de qualquer música no próprio aparelho, sem internet.
- **No PC:** teclado (como no Clone Hero) ou controle (base de PS3), teclas configuráveis e tudo navegável sem mouse.
- Visual da pista personalizável, calibração do atraso do áudio, Português e English.

Até a versão 0.4.0 havia também um modo que tocava junto com o Spotify; ele saiu na 0.5.0 (a 0.4.0 continua nos Releases).

**Documentação do projeto:**

- Especificação: `docs/superpowers/specs/2026-10-05-riff-design.md` (e as especificações de cada etapa na mesma pasta)
- Planos: `docs/superpowers/plans/`
- Pendências conhecidas: `docs/pendencias.md`

## Compilar e configurar (para desenvolver)

### Requisitos

- Android Studio (o JBR 21 é usado como JDK) e Android SDK com a plataforma 37
- Um celular com Android 8 ou mais novo (o desempenho foi medido num Galaxy S25)
- No WSL, o Gradle roda pelo `./scripts/gw`, que chama o `gradlew.bat` do Windows; no Linux e no macOS, use `./gradlew`

### Chave de release

O debug e o release são assinados com a mesma chave própria, fora do git: assim um substitui o outro no celular sem apagar os dados, e quem recebeu o APK consegue atualizar. A chave fica em `~/.android/riff-release.jks` (no Windows, `C:\Users\<você>\.android\`) e a senha no `local.properties`:

```
riff.keystore.password=<senha>
riff.key.alias=riff
```

Crie a chave uma vez com `keytool -genkeypair -keystore ~/.android/riff-release.jks -alias riff -keyalg RSA -keysize 4096 -validity 10000`. **Guarde o `.jks` e a senha fora do PC**: sem eles, nenhuma atualização instala por cima e todo mundo precisa desinstalar (perdendo recordes e ajustes). Sem a chave, o debug usa a chave de debug e o release **para com erro** (um APK com outra chave não instala por cima do de quem já tem o Riff). **Trocar a chave** obriga quem tem a versão antiga a desinstalar, perdendo recordes, ajustes e a chave do ranking (o nome fica preso até ser liberado na planilha); no próprio celular dá para salvar os dados antes com o app de debug (`run-as`, como em [Desempenho](#desempenho)) e devolvê-los depois.

### Enviar o APK para outras pessoas

1. Suba `versionCode` e `versionName` em `app/build.gradle.kts` (a versão nova só instala por cima de uma mais antiga).
2. `./scripts/gw :app:assembleRelease` e envie `app/build/outputs/apk/release/app-release.apk` (~190 MB, só celulares ARM, Android 8+). A pessoa precisa permitir "instalar apps desconhecidos" para o app que abrir o arquivo.
3. Funciona sem nada além do APK: biblioteca, pesquisa no Encore, guitarra separada por IA, ranking, Reportar e Sala (os endereços dos scripts e do Firebase vão dentro do APK).

**Compilando você mesmo:** os endereços do ranking, do Reportar e da Sala ficam no `local.properties` (fora do git) e não vêm com o código. Sem eles, o app funciona inteiro para jogar sozinho, e essas três partes avisam que não estão configuradas. Para ter as suas, siga [Reportar problemas](#reportar-problemas), [Ranking online](#ranking-online) e `docs/firebase.md`.


## Importar mapas

1. Na **Biblioteca**, toque em **Escolher pasta** e escolha a pasta que guarda os seus mapas. **Reimportar** lê a pasta de novo; **Trocar pasta** escolhe outra.
2. Cada música é uma subpasta no formato do Clone Hero:
   - `notes.chart` ou `notes.mid` (as notas) e `song.ini` (nome, artista, atraso);
   - o áudio: `song.ogg`, `.opus`, `.mp3` ou `.wav`.
3. **Guitarra separada:** se a subpasta tiver `guitar.*` e pelo menos uma faixa de acompanhamento (`song`, `rhythm`, `bass`, `drums`, `drums_1` a `drums_4`, `vocals`, `vocals_1`, `vocals_2`, `keys`, `crowd`), o Riff toca todas juntas e corta só a guitarra no erro.
4. Mapas `.sng` (o pacote do Encore e do YARG, um arquivo por música) são lidos direto: é só colocar o arquivo na pasta.
5. **Excluir:** o **X** vermelho no canto de cada música apaga a pasta dela (ou o `.sng`), junto com a guitarra separada pela IA e o recorde. No PC, com a música em foco, a seta **→** leva ao X. Para várias de uma vez: **⋮ → Selecionar**, marque as músicas e use a barra de baixo (**Excluir (n)**, **Separar (n)**, **Marcar todas** e **Cancelar**); Esc, ○ ou o voltar do celular também cancelam. Sempre pede confirmação e não dá para desfazer. Mapas soltos direto na pasta da biblioteca não têm o X (apagar a pasta deles apagaria a biblioteca inteira), e a escolha da música da Sala não tem X.
6. Não use mapas da Harmonix/Neversoft (rips de Rock Band ou Guitar Hero): são conteúdo protegido. Prefira mapas feitos pela comunidade.

## Pesquisar e baixar mapas

- **Biblioteca → Pesquisar:** digite a música ou o artista. A lista mostra os mapas do Encore (enchor.us), inclusive os oficiais de Rock Band e Guitar Hero, com as dificuldades da guitarra e a marca **Guitarra separada** quando o pacote traz a guitarra à parte. **Baixar** grava o `.sng` em "Baixados pelo Riff", dentro da pasta da biblioteca, e o mapa já entra na lista (**Jogar**). Os que você já tem aparecem como "Já na biblioteca".
- **Originais | Versões** escolhe entre as gravações originais e covers, remixes, ao vivo e medleys (pelo título). **Só com guitarra separada** procura nas páginas seguintes sozinho até achar 10 mapas ou conferir 100 resultados; **Procurar mais** confere outros 100.
- Na primeira vez, o Riff pede para escolher a pasta de novo, agora com permissão de gravar nela (**Permitir**).
- O pacote vem com o áudio da música (uso pessoal). A API do Encore não é oficial: se ela mudar, a pesquisa avisa que o Riff precisa ser atualizado.

## Guitarra separada por IA

- Na Biblioteca, ao jogar uma música sem a guitarra separada (a maioria dos mapas do Encore), o Riff pergunta **Separar a guitarra com IA?** depois da dificuldade. **Separar** começa a separação em segundo plano (com outra separando, vira **+ Fila**); **Sem separar** joga com a música inteira e fica guardado (só pergunta de novo com **Perguntar sempre se separa** ligado, o padrão); **Voltar** fecha sem jogar.
- A separação roda no próprio celular, sem internet: o modelo Demucs (htdemucs_6s) com o ONNX Runtime na CPU. No Galaxy S25, uma música de 3:30 leva cerca de 2 minutos e usa até ~2,8 GB de RAM enquanto roda (celulares com menos de 8 GB usam um modo mais econômico e mais lento).
- **Fila:** várias músicas são separadas uma depois da outra. Além do **+ Fila**, o **Selecionar** da Biblioteca tem **Separar (n)**, que põe na fila as marcadas que ainda não têm guitarra separada. A fila fica guardada: sobrevive ao Android fechar o app e ao celular reiniciar (uma separação interrompida recomeça do início).
- Enquanto separa, aparece um ícone (palheta com anel de progresso e, com fila, quantas faltam) no topo das telas; tocar nele mostra a porcentagem, o tempo restante, **Cancelar esta**, a fila com um **✕** para tirar cada música e **Cancelar todas**. A notificação do Android acompanha ("42% · mais 2 na fila") e avisa no fim de cada uma. Se o celular esquentar demais, a separação pausa e continua quando esfriar. Uma falha não para a fila; sem espaço no celular, a fila é esvaziada.
- Mapas que já vêm com a guitarra à parte mostram **GUITARRA SEPARADA** na Biblioteca e não perguntam nada (a marca é anotada na importação: para mapas antigos, toque em **Reimportar** uma vez).
- Pronta, a música ganha a marca **GUITARRA SEPARADA IA** e passa a jogar como as que já vêm com a guitarra à parte: a guitarra some quando você erra.
- Cada música separada ocupa ~7 MB (guitarra e resto em `.m4a`, no espaço do app). Nas **Configurações**, a aba **Guitarra IA** mostra o espaço em **Apagar separações** e tem o interruptor **Perguntar sempre se separa** (ligado de fábrica): ligado, toda música sem guitarra separada pergunta ao jogar, mesmo as em que você escolheu "Sem separar". **Separar só carregando** (desligado de fábrica) faz a fila esperar o celular estar na tomada, para deixar separando à noite; tirar da tomada interrompe a atual, que recomeça quando voltar.

## Reportar problemas

- Na tela inicial, **Reportar** (ao lado de Configurações) abre um formulário: tipo (Problema, Sugestão ou Outro), o que aconteceu, email para resposta (opcional) e **Incluir informações do aparelho** (versão do Riff, Android, modelo e idioma, mostrados na tela; ligado de fábrica). Depois de um envio, o próximo espera 5 minutos.
- O relato vai para um Google Apps Script na conta do desenvolvedor, que manda um email "[Riff] Problema: ..." para essa mesma conta (responder vai direto para quem relatou, se deixou o email). Nenhuma senha fica no app; o Google limita a ~100 emails por dia.
- **Configurar (uma vez):** publique `scripts/relatos_apps_script.gs` como app da Web (instruções no começo do arquivo) e coloque o endereço no `local.properties`: `report.url=https://script.google.com/macros/s/.../exec`. Sem ele, a tela avisa que o envio não está configurado.
- A tela diz, abaixo do email, que o relato vai para o desenvolvedor e que o email só serve para a resposta.
- Antes de distribuir o app (Play Store), declarar na ficha de segurança dos dados que ele envia o texto do relato, o email opcional e as informações do aparelho.

## Ranking online

- **Nome de jogador:** na primeira vez que o Riff abre, ele pede o seu nome no ranking (3 a 20 letras, números, espaço, `_` ou `-`), avisando que o nome e as melhores pontuações ficam visíveis para todos. O nome fica com este celular: ninguém mais pode usar ("Rodrigo" e "rodrigo" contam como o mesmo). **Agora não** adia até a próxima abertura. Para trocar: **Configurações → Geral → Nome no ranking** (o antigo fica livre; as suas pontuações acompanham o nome novo).
- **Envio automático:** ao acabar uma partida que seja a sua melhor naquele mapa e dificuldade em algum número, ela vai sozinha para o ranking. O Resultado mostra "Ranking: 3º de 27" e **Ver ranking**. Sem internet, a partida fica na fila e vai quando a conexão voltar. No primeiro nome, os seus melhores já guardados no celular vão uma vez, em lotes de até 50 por pedido.
- **Tela Ranking** (o cartaz da tela inicial): as músicas com pontuação (busca por música ou artista) e o placar de cada uma, por dificuldade, ordenado por **Pontos**, **Precisão**, **Combo**, **Perfeitos** ou **Tempo sem errar** (a maior sequência sem erro, em m:ss), do maior para o menor ou ao contrário, com busca por nome e a sua linha em destaque.
- **"Mesma música" = mesmo mapa** (as mesmas notas): mapas diferentes da mesma música têm rankings separados.
- **Onde fica:** um Google Apps Script com uma planilha na conta do dono do app (`scripts/ranking_apps_script.gs`; o passo a passo de publicar e de republicar — Implantar → Gerenciar implantações → editar → Nova versão, o mesmo URL — e o protocolo completo estão no começo do arquivo). O endereço vai no `local.properties` como `ranking.url=…/exec`; sem ele, o ranking fica desligado. Só o apelido e as pontuações ficam guardados; a chave de cada aparelho vai como hash. Testes do script sem o Google: `node scripts/ranking_apps_script.test.js`.
- **A planilha:** **Jogadores** tem uma linha por aparelho (nome, nome em minúsculas, hash da chave do aparelho, criado, atualizado e, no fim, `jogador` — o id do jogador; vazio nas linhas de antes = o próprio hash). **Pontuações** guarda o melhor de cada número por jogador, mapa, dificuldade e, na última coluna, `tabela` (`celular`, `pc_palheta` ou `pc_sem_palheta`; vazio nas linhas de antes = `celular`). **Removidos** guarda os aparelhos tirados do ranking. As colunas novas ficam no fim: a planilha de antes continua valendo sem migrar nada.
- **Três tabelas:** cada partida conta numa tabela (Celular, PC com palheta, PC sem palheta), com posições separadas. O app 0.5.0 (que não pede `tabelas`) continua vendo só a do celular.
- **O mesmo nome em vários aparelhos:** num aparelho com nome, o script dá um código de vínculo (6 letras e números sem os ambíguos, vale 10 minutos, uso único, um ativo por jogador; fica só no cache do Apps Script, nunca na planilha). No outro aparelho, o código põe o aparelho no mesmo jogador: as pontuações dos dois somam o melhor de cada número, e trocar o nome num troca em todos. Se o aparelho que vincula já tinha um nome só dele, esse nome fica livre e as pontuações dele saem (o app reenvia o histórico local no jogador novo); se o nome antigo era de vários aparelhos, só este sai dele e o resto fica com os outros.
- **Tirar alguém do ranking** (pedido pelo Reportar, nome ofensivo): rode `removerJogador("Nome")` no editor do script: apaga o jogador em todos os aparelhos dele e as pontuações, e os aparelhos esquecem o nome e não voltam sozinhos. Apagar só a linha do jogador à mão faz o aparelho reservar o mesmo nome de novo no próximo envio.
- **Jogador apagado da planilha:** no próximo envio, o celular reserva o mesmo nome de novo e manda a partida; se outra pessoa já tiver pego o nome, o app pede um nome novo (a partida espera na fila).
- **Limites do script** (o endereço vai dentro do APK): 60 pedidos de envio e 300 pontuações por hora por aparelho (um lote é um pedido), 5 reservas de nome por hora por aparelho (tentar um nome em uso conta; salvar o mesmo nome não), 100 nomes novos por hora no total, 5 códigos de vínculo e 5 tentativas de vínculo (certas ou erradas) por hora por aparelho, e, somando todos os aparelhos, 100 códigos de vínculo errados e 100 aparelhos sem nome vinculados por hora (a recusa por limite não gasta o código); chaves curtas ou vazias são recusadas. O app continua funcionando com uma versão antiga do script (manda uma pontuação por vez) até ela ser republicada.

## Sala (multiplayer)

**Como jogar:** na tela inicial, **Sala** → **Criar sala** mostra um código de 4 letras (compartilhe pelo botão ao lado dele) e os amigos entram em **Entrar com código**. Até 4 pessoas, no celular e no PC. O dono toca em **Escolher música**; quem não tem a música pode baixá-la do Encore (se ela for de lá). Cada um escolhe a própria dificuldade e marca **Pronto**, e o dono toca em **Começar** (com 2 ou mais prontos). A música começa junta em todos, com o placar da sala ao vivo; no fim, **Ver a sala** mostra o pódio. Precisa de internet.

**Como funciona por dentro** (especificação em `docs/superpowers/specs/2026-11-05-riff-multiplayer-design.md`). Na M1: **Sala** na tela inicial (celular e PC) → **Criar sala** (código de 4 letras, sem I, L e O) ou **Entrar com código**; a sala mostra ao vivo até 4 jogadores (nome do ranking, aparelho, dono, "sumiu" depois de 60 s sem sinal); sair passa o dono ao jogador mais antigo, e o último a sair apaga a sala. Na M2: o dono escolhe a música (**Escolher música** abre a Biblioteca); cada jogador vê se tem a música (do Encore: **Baixar do Encore**; sem ela, fica de fora da rodada), escolhe a própria dificuldade e marca **Pronto**; **Começar** liga com 2 ou mais prontos (ou 30 s depois do primeiro Pronto, com quem está pronto). A partida começa junta: cada aparelho carrega, o dono marca o começo 3 s depois de todos carregarem (ou 15 s, com quem carregou), e cada um converte a hora do servidor para o próprio relógio (medido pelo `vistoEm` lido de volta). Sem pausa na partida de sala (Esc/Start/Voltar perguntam "Sair da partida?" com a música seguindo); o Resultado é o de sempre, com "Voltar à sala". Na M3: **placar ao vivo** minimizado na partida de sala (até 4 linhas "posição · nome · pontos", a sua como "Você", as linhas deslizando quando alguém passa; "saiu" por último e "sem conexão" depois de 15 s sem pontos; celular em cima à esquerda, PC na coluna da esquerda), cada aparelho grava o próprio placar a cada 1 s fora da thread da tela e o final ao terminar (reenvia quando a internet volta); **Resultado da sala** ("Ver a sala" no Resultado: pódio e classificação com pontos, precisão e maior combo, ao vivo até todos terminarem). Começo mais junto: relógio pela mediana das 3 mais rápidas de 5 medidas, e o player aquecido antes do instante (no PC o primeiro som levava 270–660 ms; aquecido, ~80 ms) com a latência informada descontada. Log de sincronia: tag `RiffSync` (`adb logcat -s RiffSync`; no PC, na saída de erro do `./scripts/gw :desktop:run`).

- Servidor: Firebase gratuito (login anônimo + Realtime Database), pela API REST e o streaming SSE, sem SDK (o mesmo código no celular e no PC). Criar o projeto e publicar as regras: `docs/firebase.md`. Sem `firebase.apiKey`/`firebase.dbUrl` no `local.properties`, a Sala fica "em breve".
- Regras do banco em `firebase/database.rules.json`, testadas no emulador: `scripts/testar_regras_sala.sh` (ver `firebase/README.md`). O limite de 4 é garantido por vagas (`vagas/v1..v4`); a sala parada há mais de 2 h é apagada por quem sortear o mesmo código.
- Módulos: `:core:room` (cliente do Firebase e `RoomRepository`), `:core:room:testing` (Firebase falso dos testes), `:feature:room` (telas).

## Licenças e privacidade

**Configurações → Geral → Licenças e privacidade**: o que o app guarda e envia (ranking, Reportar, Encore), como pedir para sair do ranking e as licenças de tudo que vem de fora. A lista das bibliotecas é gerada no build pelo plugin AboutLibraries (`app/build.gradle.kts`); o que não vem do Gradle (modelo Demucs, demucs.onnx, fontes, sons do Freesound, mapas do Encore) está em `feature/licenses/.../AndroidLicenseSource.kt`, com os textos em `feature/licenses/src/main/assets/licencas/`. Ao adicionar algo de fora do Gradle, acrescente nessa lista.

## Jogar

- **Começar:** na tela inicial, **Sozinho** abre a Biblioteca; toque numa música e escolha a dificuldade. Toque nas trilhas na linha de acerto, com vários dedos se precisar; segure as notas longas.
- **Barra de progresso:** a linha fina no topo da partida mostra quanto da música já passou (até a última nota mais a folga do fim, quando vem o resultado).
- **Pausar:** botão **Pausar** ou o gesto de voltar. A partida também pausa sozinha numa ligação, quando outro app começa a tocar ou quando o fone é desconectado; continuar com a ligação ainda em andamento pausa de novo e a pausa explica que o áudio está ocupado. No menu de pausa, **Sincronia desta música** ajusta quando as notas chegam: **−1 s / +1 s** para diferenças grandes (por exemplo, um mapa com contagem de entrada) e **−10 ms / +10 ms** para o fino. O valor vale ao continuar e fica salvo para aquele mapa.
- **Calibração** (Configurações → Jogo → Calibrar atraso do áudio): toque junto com as batidas para medir o atraso do seu fone ou alto-falante. Útil com fone Bluetooth; com o alto-falante do celular costuma ser melhor deixar em 0 (**Zerar**).
- **Configurações**, em abas (abre sempre em **Geral**):
  - **Geral:** **Idioma** (Português, o padrão, ou English: troca na hora; no Android 13 ou mais novo também fica em Configurações do celular → Apps → Riff → Idioma) e sons do menu (liga e desliga).
  - **Sons:** volume geral, do acerto e do erro.
  - **Jogo:** velocidade das notas (0,5× a 2×), modo canhoto (trilhas espelhadas) e calibração.
  - **Visual da pista:** os quatro designs e a imagem do mapa (abaixo).
  - **Guitarra IA:** apagar separações, "Perguntar sempre se separa" e "Separar só carregando".
- **Visual da pista:** quatro escolhas nas Configurações, cada uma com miniaturas. **Fundo da pista**: Palco (o de sempre), Amplificador, Couro e rebites, Braço de guitarra, Chamas, Neon, Chapa de metal. **Design da pista** (desenho claro estampado na pista, rolando com a música): Sem desenho, Caveira alada, Raios, Estrelas, Guitarras, Palhetas, Tatuagem. **Design das notas**: Discos, Palhetas, Caveiras, Estrelas, Raios, Chamas. **Design dos botões**: Discos, Pedais de efeito, Knobs de amplificador, Alto-falantes, Palhetas. As cores das trilhas não mudam. Tudo é desenhado pelo app, em código.
- **Imagem do mapa** (ligada de fábrica): mostra atrás da pista, escurecida, a imagem de fundo do mapa (`background.jpg`/`.png`) ou, sem ela (ou grande demais, ou corrompida), a capa (`album.jpg`/`.png`).
- **Sons do menu:** os botões fora da partida tocam guitarra, no volume geral: abrir ou escolher algo dá um toque abafado, começar ou salvar dá um power chord (cada um sorteia entre duas gravações) e voltar, pelo botão ou pelo gesto do celular, arrasta a palheta na corda. Durante a partida os botões ficam em silêncio.

## Qualidade e testes (QA)

O Riff é feito com **TDD**: cada mudança começa por um teste que falha pelo motivo certo, depois vem o código mínimo que o faz passar, e no fim roda a suíte inteira. Números de 2026-11-12: **~1.680 testes** em Kotlin (285 arquivos, celular e PC), **110 screenshots** de referência (celular e PC), 48 testes do script do ranking, 3 do script do Reportar e 53 das regras do Firebase da Sala.

### Camadas de teste

| Camada | O que cobre | Onde |
|---|---|---|
| Unidade (JVM) | motor do jogo (julgamento, combo, pontuação, relógio), leitura de mapas (`.chart`, `.mid`, `.sng`, `song.ini`), fila de separações, ranking | `core/*/src/test` |
| Robolectric | ViewModels, repositórios, players de áudio, WorkManager da separação, Room | `core/*/src/test`, `feature/*/src/test` |
| Interface (Compose) | cada tela e cada estado (sem pasta, sem mapa, separando, pausa, áudio ocupado, erros), cliques, sons do menu, textos em pt e en | `feature/*/src/test/*ContentTest.kt` |
| Screenshots (Roborazzi) | o visual de cada tela comparado pixel a pixel com a referência, no celular (inclusive fonte grande e celular deitado) e no PC | `*/src/test/screenshots` (as do PC em `desktop/src/test/screenshots` e `core/ui/src/test/screenshots/desktop`) |
| PC (Compose Desktop) | o app do PC de ponta a ponta: navegação só por teclado e só por controle (foco, Esc, menus, X da Biblioteca), partida com teclado e palheta, controles configuráveis, fila da IA | `desktop/src/test`, `feature/*/src/desktopTest` |
| Sala (multiplayer) | sala, vagas, prontos, começo junto, relógio do servidor e placar ao vivo contra um Firebase falso (`:core:room:testing`); nenhum teste usa o Firebase de verdade | `core/room/src/commonTest`, `feature/room/src/androidHostTest` e `feature/room/src/test` |
| Regras do Firebase | as regras do banco da Sala no emulador do Firebase (quem pode criar, entrar, escrever o placar, apagar) | `scripts/testar_regras_sala.sh` (`firebase/rules.test.mjs`) |
| Instalador do PC | dentro do app empacotado: ONNX Runtime, modelo, FFmpeg, SQLite, GLFW, conexão segura e números em português | `./scripts/gw :desktop:verifyDistributable` |
| Migração do banco | cada versão do esquema do Room migra sem perder dados (casamentos, ajustes, recordes) | `core/database/src/test/.../MigrationTest.kt` |
| Golden da IA | a separação da guitarra (Demucs em ONNX) reproduz a saída de referência | `core/separation/src/test` |
| Script do ranking | o Apps Script roda em Node com uma planilha falsa que imita as conversões do Google Planilhas | `node scripts/ranking_apps_script.test.js` |
| Script do Reportar | o Apps Script dos relatos roda em Node com o email simulado (linha do aparelho do celular e do PC) | `node scripts/relatos_apps_script.test.js` |
| Aparelho | medição de sincronia (registro temporário `RiffSync` no `adb logcat`), quadros atrasados e abertura (Macrobenchmark + Baseline Profile) | `scripts/desempenho.sh`, `scripts/quadros_travados.py` |

### Portões de qualidade

- **Detekt + ktlint** (`config/detekt/detekt.yml`): limites de tamanho de função (60 linhas), de funções por classe e arquivo (11), de complexidade (14) e de linha (120 colunas).
- **Android Lint** com erros quebrando o build.
- **Kover:** cobertura mínima de **70%** em `:core:chart`, `:core:game` e `:core:data` (`koverVerify`).
- **Suíte completa antes de cada merge:**

  ```bash
  ./scripts/gw test testDebugUnitTest testAndroidHostTest desktopTest detekt lint lintMultiplatform koverVerify verifyRoborazziAndroidHostTest verifyRoborazziDesktop verifyRoborazziJvm :app:assembleDebug :app:assembleRelease --continue
  node scripts/ranking_apps_script.test.js
  node scripts/relatos_apps_script.test.js
  scripts/testar_regras_sala.sh        # regras do Firebase no emulador (Node e um JDK 11+ do Windows; RIFF_JDK)
  ```

- **CI** (GitHub Actions, `.github/workflows/ci.yml`): detekt, lint, testes do celular e do PC, screenshots do celular (as do PC são comparadas só no Windows; veja [Screenshots](#screenshots)), Kover e o APK de debug a cada push na `main` e em pull requests. O modelo da IA vem do Git LFS, com cache. Roda tudo até o fim (`--continue`) e lista todas as falhas de uma vez; quando falha, guarda as comparações das screenshots e os relatórios dos testes no artefato `falhas-ci`. Os scripts em Node, as regras do Firebase, a comparação das screenshots do PC e o instalador do PC rodam só na máquina de quem desenvolve (Windows). Depois de cada push na `main`, o resultado do CI é conferido (`gh run list`).
- **Idioma dos testes:** todos rodam em português do Brasil em qualquer máquina (`user.language`/`user.country` no `build.gradle.kts` da raiz): os do PC leem os textos do app no idioma da JVM, e o Linux do CI é em inglês. Os do celular escolhem o idioma pelo Robolectric (`@Config(qualifiers = "pt-rBR")`).
- **Antes de publicar uma versão:** `verifyDistributable` (as 7 conferências dentro do app empacotado) e a conferência de que nenhum segredo (`local.properties`, chave de assinatura, chaves do Firebase) vai no envio.

### Processo de cada mudança

1. **Especificação e plano** para mudanças grandes (`docs/superpowers/specs` e `docs/superpowers/plans`). As mudanças pequenas são combinadas na conversa.
2. **TDD** tarefa a tarefa, com um registro das decisões tomadas no caminho.
3. **Revisão final** da branch inteira por um revisor independente (outro modelo, sem o contexto de quem implementou). Problemas críticos e importantes são corrigidos com um teste que falha primeiro; os menores vão para `docs/pendencias.md`.
4. **Aceite no aparelho** antes do merge: no celular (Galaxy S25, o APK de release por cima, sem apagar dados) e no PC, com registro temporário quando o problema é de tempo (sincronia, fila, começo junto da Sala).
5. **Mudanças visuais** começam por protótipos aprovados (com as opções lado a lado quando há escolha) antes de mexer no código.
6. O **README** e o **Baseline Profile** são atualizados junto com a mudança.

### Testes instáveis conhecidos

Passam ao rodar de novo sozinhos (efeito de carga ou do Windows):

- `lintAnalyzeDebugUnitTest` (crash do analisador do lint);
- `ResamplingPcmSourceTest` (tempo, sob carga; mede a melhor de 3 voltas, com limite de 4 s e de 6 s no CI);
- `SettingsContentTest` (espera ociosa do Compose, sob carga);
- `MigrationTest` (FileNotFound no esquema JSON recém-gerado na primeira compilação);
- `DesktopSeparationSchedulerTest` e `DesktopGameplayFlowTest` (tempo, sob carga pesada);
- `GuitarSeparatorGoldenTest` ("bad allocation" do ONNX Runtime com a memória apertada pela suíte inteira).

Pendências e pontos menores das revisões ficam em `docs/pendencias.md`.

## Desenvolvimento

### Riff no PC

O mesmo código do celular, com Compose Multiplatform: `:core:ui`, `:core:database`, `:core:datastore`, `:core:data`, `:core:encore`, `:core:audio`, `:feature:home`, `:feature:library`, `:feature:gameplay`, `:feature:results`, `:feature:settings`, `:feature:calibration`, `:feature:search`, `:feature:report`, `:feature:licenses`, `:feature:ranking` e `:core:separation` são multiplataforma (Android e PC), e `:desktop` é o app do PC. No PC já dá para jogar: **Sozinho → Escolher pasta** importa as pastas de mapas e os `.sng` de uma pasta do computador, e uma música abre a partida e o Resultado com o recorde. As **Configurações** (Geral, Sons, Jogo e Visual da pista; o idioma fica em `%APPDATA%\Riff\language.txt`) e a **Calibração** também funcionam no PC; a **pesquisa no Encore** (Biblioteca → Pesquisar Mapas: baixa o `.sng` para "Baixados pelo Riff" dentro da pasta da biblioteca) também; o **Reportar** (envelope da tela inicial; vai para o mesmo Apps Script do celular, com `report.url` do `local.properties` embutido no build, e as informações do PC são sistema, versão do Riff e idioma, sem o nome do computador) e **Configurações → Licenças e privacidade** (lista do PC gerada pelo plugin genérico do AboutLibraries no `:desktop`, mais LWJGL, GLFW, libffi e FFmpeg à mão) também; a **guitarra separada por IA** também (a mesma pergunta, fila e aba "Guitarra IA" do celular; separa só com o Riff aberto e continua a fila na próxima abertura; sem "Separar só carregando"); o **ranking** também (★ Ranking na tela inicial e "Nome no ranking" nas Configurações; cada partida do PC conta na tabela **PC com palheta** ou **PC sem palheta**, conforme "Tocar com palheta" quando ela começou; as partidas do PC de antes do ranking contam com palheta, o padrão de fábrica); e o **instalador** `.msi` (especificação em `docs/superpowers/specs/2026-10-31-riff-windows-design.md`).

**Jogar no PC (teclado, como no Clone Hero):** cores **A** (verde), **S** (vermelho), **J** (amarelo), **K** (azul) e **L** (laranja); palheta **↑** ou **↓**; pausa e continuar **Enter** ou **Esc**. Segure as cores da nota (ou do acorde) e palhete na hora; HOPO depois de um acerto e tap valem só apertando ou soltando a cor. Segurar uma tecla não repete. A partida pausa sozinha se a janela perde o foco (Alt+Tab). Na Calibração, aperte qualquer tecla (menos Esc) junto com cada batida; o atraso salvo vale nas partidas. Na janela deitada a pista fica no meio, com o placar e o combo à esquerda e o multiplicador à direita.

**Jogar no PC com controle (base: controle de PS3, cabo USB):** cores **△** (verde), **○** (vermelho), **□** (amarelo), **✕** (azul) e **R1** (laranja); palheta no **direcional ↑/↓** ou no **analógico esquerdo**; **Start** pausa e continua. O controle é lido pelo GLFW (LWJGL), que entende XInput e DirectInput e a base de mapeamentos do SDL; um controle fora dela usa a ordem crua do DualShock 3. Com a janela sem foco o controle não faz nada, e desconectá-lo pausa a partida. Para ver como o Windows enxerga o controle: `./scripts/gw :desktop:runPadCheck` (20 s; imprime cada botão apertado). **Configurações → Controles** (só no PC): "Tocar com palheta" (desligado, apertar a cor na hora basta, como o toque do celular) e, para cada ação (as cinco cores, palheta ↑, palheta ↓ e pausa), clicar a linha e apertar a tecla ou o botão do controle que quiser; escolher um já usado por outra ação troca os dois, Esc cancela, e "Voltar ao padrão" desfaz tudo. Esc sempre pausa e o analógico esquerdo sempre palheta.

**Sem mouse:** todas as telas do PC funcionam só com o teclado ou só com o controle. O item com o foco ganha um contorno claro; setas (ou direcional) movem o foco, Enter (ou ✕) confirma e Esc (ou ○) volta; nos volumes e na velocidade, ←/→ mudam o valor. Na pausa, as setas andam entre os botões. O controle nos menus fica desligado com a partida tocando.

- Os dados do Riff no PC (banco, preferências) ficam em `%APPDATA%\Riff`; a biblioteca do PC é separada da do celular.
- No celular quem monta as dependências é o Hilt; no PC, o `DesktopGraph`. As telas e os ViewModels são os mesmos (no celular, cada ViewModel ganha uma subclasse `@HiltViewModel` fina).
- **IA no PC:** o mesmo Demucs em ONNX (ONNX Runtime para JVM 1.18; o modelo de 130 MB vem do asset do celular, copiado para os recursos do `:desktop` no build), metade dos processadores lógicos menos um; só a thread que chama o ONNX Runtime é de prioridade baixa (as threads internas dele ficam em prioridade normal); as faixas `guitar.m4a`/`backing.m4a` são gravadas em AAC pelo FFmpeg (bytedeco) em `%APPDATA%\Riff\separacoes`. Medido em 2026-11-04 no PC do usuário: uma música de 4 min separou em 2 min 20 s (7 threads, ~38% de CPU, pico de 504 MB de heap). Medição: `./scripts/gw :desktop:runSeparationCheck --args="<pasta do mapa ou .sng>"`. Fechar o Riff no meio devolve a música à fila (sem contar tentativa) e os temporários são apagados na próxima abertura. Um Riff só por vez: a trava `riff.lock` na pasta de dados recusa um segundo aberto (o aviso "O Riff já está aberto"), também nos ~2 s em que o primeiro ainda está fechando.
- A versão do Riff fica em `gradle.properties` (`riff.versionName`), lida pelo app do celular e pelo Reportar do PC.
- O Android Lint dos módulos multiplataforma roda com `./scripts/gw lintMultiplatform`.
- **Áudio no PC:** o FFmpeg (bytedeco) decodifica ogg, opus, mp3 e wav (soltos ou dentro de `.sng`) sempre em 48 kHz estéreo, e o Java Sound toca (16 bits, buffer de 2.048 quadros ≈ 42 ms). Os sons de erro e do menu são os mesmos WAV do celular. Medição: `./scripts/gw :desktop:runAudioCheck --args="<pasta do mapa ou .sng>"` toca 20 s cortando a guitarra a cada 2 s e imprime a deriva entre o relógio e a posição da música.
- **A pista no PC:** o `stage/` do `:core:ui` é todo comum; as texturas (fundos e estampas) são geradas pelo Canvas do Compose, com três detalhes por plataforma (o desfoque, o mipmap e a matriz de perspectiva, que no celular segue o `setPolyToPoly` do Android). As cenas dos testes de tela da pista ficam em `core/ui/src/commonTest` e servem aos dois: as referências do PC ficam em `core/ui/src/test/screenshots/desktop`.
- **Palheta (regras para o PC, no `GameEngine`):** `onStrum` julga o acorde inteiro numa palhetada (nota simples aceita cores mais baixas seguradas junto; acorde exige exatamente as cores dele; cada nota do acorde conta); `onFret` acerta o tap sempre e o HOPO depois de um acerto, sem palhetar; palhetada sem nota ou com cor errada quebra o combo, perdoada até 50 ms depois de um acerto sem palheta. O toque do celular segue pelo `onPress`.
- Medido em 2026-11-01 no PC do usuário (Windows): a deriva oscila entre −13 e +17 ms sem acumular em 20 s (faixas separadas em pasta e um `.sng` com 5 faixas opus); o som leva ~650 ms para começar na primeira vez (a partida espera, sincronizada) e volta na hora ao continuar; 1 minuto de opus decodifica em ~145 ms.

```bash
./scripts/gw :desktop:run   # abre a janela do Riff no Windows
```

**Instalador do Windows:** `./scripts/gw :desktop:packageMsi` gera `desktop/build/compose/binaries/main/msi/Riff-<versão>.msi` (~268 MB; a versão é o `riff.versionName`). Instala só para o usuário (sem administrador), com atalho na área de trabalho e no menu Iniciar; um `.msi` novo substitui o antigo (`upgradeUuid` fixo; cada `.msi` distribuído precisa de um `riff.versionName` novo, porque um recompilado com a mesma versão por cima pode dar o erro 1638 do Windows Installer), e instalar, atualizar ou desinstalar não mexe em `%APPDATA%\Riff`. O pacote leva o próprio Java: um **Temurin 21** baixado pela toolchain do Gradle (o JBR do Android Studio, que compila, não tem o `jdk.jpackage` nem o `jdk.crypto.mscapi`), com os módulos listados no `desktop/build.gradle.kts` e as DLLs do Visual C++ no `runtime\bin` (o ONNX Runtime 1.18 precisa do `msvcp140.dll`). O app instalado usa o proxy do Windows (`-Djava.net.useSystemProxies=true`) e confia nos certificados do Java (`cacerts`) e nos do Windows (`Windows-ROOT`, de antivírus ou redes que inspecionam o HTTPS) juntos, pelo `TrustBothStores` no começo do `main`; o `:desktop:run` também roda nesse Java. Sem assinatura de código: o Windows mostra o aviso de app desconhecido ("Mais informações" → "Executar assim mesmo").

```bash
./scripts/gw :desktop:runInstallCheck       # confere ONNX Runtime, modelo, FFmpeg, SQLite, GLFW, TLS e números em português no Temurin 21
./scripts/gw :desktop:verifyDistributable   # a mesma conferência dentro do app empacotado (createDistributable)
./scripts/gw :desktop:packageMsi            # o instalador
```

Publicar no GitHub Releases é só quando for pedido: `gh release create v<versão> desktop/build/compose/binaries/main/msi/Riff-<versão>.msi --title "Riff <versão>" --notes-file <notas>`. **Junto do `.msi` vão sempre os arquivos `fonte-*`** (o código-fonte do OpenJDK/Temurin, do FFmpeg, do LAME e do JavaCPP nas versões do instalador, com `fonte-LEIAME.txt` e `fonte-SHA256SUMS.txt`, como na v0.6.0): a GPL 2 e a LGPL pedem o código-fonte junto do binário, e os avisos do app apontam para a página da versão. Se o Temurin ou o FFmpeg mudarem de versão, baixe os novos (o commit do Temurin está no `SOURCE` de `runtime/release`).

### Comandos

```bash
./scripts/gw :app:assembleDebug                # APK de debug
./scripts/gw test                              # testes unitários
./scripts/gw detekt -PdetektAutoCorrect        # formata (ktlint) e corrige
./scripts/gw detekt                            # análise estática
./scripts/gw lintDebug lintMultiplatform       # Android Lint (módulos Android e multiplataforma)
```

O Robolectric não funciona quando o caminho do `~/.m2` tem espaço (ele codifica o espaço como `%20`). Nesse caso, passe `-Priff.robolectric.mavenRepoLocal=<pasta sem espaço>`, como o `scripts/gw` já faz, ou coloque a propriedade no `gradle.properties` do usuário para o Android Studio.

### Módulos

| Módulo | Conteúdo |
|---|---|
| `:app` | Activity, Application com Hilt e navegação tela inicial → Biblioteca → Jogo → Resultado; Baseline Profile em `src/release/generated/baselineProfiles` |
| `:core:ui` | Multiplataforma (celular e PC). Identidade "Pôster de turnê": tema escuro único, fontes New Rocker e Oswald (licenças OFL em `core/ui/licenses/`) e textos em `commonMain/composeResources`, componentes em `commonMain`; peças de desenho da pista (com `StageCache`, sem alocar por quadro) ainda só no Android (`androidMain`); screenshots em `src/test/screenshots` |
| `:core:model` | Modelos de domínio |
| `:core:chart` | Leitura de mapas do Clone Hero (`notes.chart`, `notes.mid`, `song.ini` e pacotes `.sng`) |
| `:core:game` | Relógio da música e motor do jogo (julgamento, combo e pontuação) |
| `:core:audio` | Player da música (MediaPlayer, ou faixas separadas misturadas com a guitarra cortada no erro), e sons de acerto, erro e menu (SoundPool), gerados por `scripts/gerar_sfx.py`, `scripts/montar_sfx_erro.py` e `scripts/montar_sfx_menu.py` |
| `:core:database` | Multiplataforma. Room (no PC, com o SQLite embutido): mapas, dificuldades, ajustes de sincronia, recordes, separações e filas (esquemas em `schemas/`) |
| `:core:datastore` | Multiplataforma. Preferências do jogador e pasta da biblioteca |
| `:core:data` | Multiplataforma. Repositório de mapas, acesso a pastas (SAF no celular, `java.io` no PC; leitura, `.sng` como pasta e gravação), importador de mapas e download de mapas do Encore |
| `:core:encore` | Multiplataforma. Cliente do Encore (enchor.us): pesquisa, marca de guitarra separada e download |
| `:core:separation` | Guitarra separada por IA: o modelo htdemucs_6s em ONNX (`scripts/exportar_modelo_ia.sh`), a STFT e a separação por trechos portadas do demucs.onnx, a decodificação, a gravação em `.m4a` e o trabalho em segundo plano (WorkManager) |
| `:feature:library` | Multiplataforma. Tela da Biblioteca: escolher pasta, importar, lista e relatório |
| `:feature:search` | Pesquisa no Encore: resultados, download com progresso e Jogar |
| `:feature:gameplay` | A partida: pista em movimento, toque com vários dedos, chama, sons, pausa, sincronia e fim |
| `:feature:results` | Resultado da partida e recorde |
| `:feature:calibration` | Calibração do atraso do áudio: metrônomo, toques, resultado e Zerar |
| `:feature:settings` | Configurações em abas: idioma, nome no ranking, sons, jogo, visual da pista e guitarra IA |
| `:feature:ranking` | Pedido do nome, músicas com pontuação e placar do ranking online |
| `:feature:report` | Reportar problema ou sugestão |
| `:feature:licenses` | Licenças e privacidade (lista das bibliotecas pelo AboutLibraries) |
| `:feature:home` | Multiplataforma. Tela inicial: Sozinho, Sala, Ranking, Reportar e Configurações |
| `:core:room` | Multiplataforma. Cliente do Firebase (REST + SSE, login anônimo), `RoomRepository`, relógio do servidor e presença |
| `:core:room:testing` | Firebase falso usado nos testes |
| `:feature:room` | Multiplataforma. Telas da Sala: entrar, sala, placar ao vivo e resultado da sala |
| `:desktop` | O Riff no PC (Windows): tela inicial, Biblioteca, pesquisa no Encore, partida, Resultado, Configurações, Calibração, Reportar, Licenças, guitarra por IA, ranking e o instalador `.msi` (`./scripts/gw :desktop:run`, `:desktop:packageMsi`) |
| `:baselineprofile` | Percursos automáticos (UiAutomator) que geram o Baseline Profile e medem abertura e partida (Macrobenchmark) |

### Screenshots

```bash
./scripts/gw recordRoborazziAndroidHostTest recordRoborazziDesktop recordRoborazziJvm   # regrava depois de uma mudança visual intencional
./scripts/gw verifyRoborazziAndroidHostTest verifyRoborazziDesktop verifyRoborazziJvm   # compara com as referências
# Todos os módulos com telas são multiplataforma: AndroidHostTest (celular), Desktop (o lado PC dos módulos)
# e Jvm (o app do PC)
```

Os testes de tela só rodam de novo quando o código muda: depois de trocar só uma referência, use `--rerun` na tarefa de teste para comparar de verdade.

Rode o `verifyRoborazziAndroidHostTest verifyRoborazziDesktop verifyRoborazziJvm` antes de commitar mudanças de UI (no Windows). O CI compara de novo só as do celular, que são exatas: no Linux o Robolectric desenha as telas iguais às gravadas no Windows (as fontes do app vêm junto com ele). As do PC (Skia) saem diferentes no Linux (letras e bordas suavizadas de outro jeito, 1,5–4% das telas, e os símbolos de fora das fontes do app com outro tamanho): no CI os testes do PC rodam, mas sem comparar as imagens. Quando o CI falha, ele guarda as comparações e os relatórios no artefato `falhas-ci` da execução.

### Desempenho

O **Baseline Profile** diz ao Android quais partes do app compilar antes da primeira execução, para a abertura e a partida já começarem rápidas. Ele é gerado no celular por percursos automáticos (abrir o app, rolar a biblioteca, jogar 20 s de uma música e sair).

**Vale só para o build release** (`./scripts/gw :app:assembleRelease`, assinado com a mesma chave do debug): o Android ignora o perfil em apps de debug. Os números abaixo são do release. As otimizações do desenho da pista (sem alocar memória a cada quadro) valem nos dois.

```bash
scripts/desempenho.sh perfil    # gera o perfil de novo
scripts/desempenho.sh medir     # mede abertura e partida, sem e com o perfil
scripts/desempenho.sh tudo      # os dois
uv run --with perfetto python3 scripts/quadros_travados.py   # fps e quadros com atraso, a partir dos traces
```

- Celular conectado, desbloqueado, com a tela ligada (tempo de tela longo) e sem economia de bateria; não mexa nele durante a execução (10 a 15 minutos).
- A música do percurso precisa estar na biblioteca (o percurso rola a lista até achá-la): por padrão "Smells Like Teen Spirit"; outra com `scripts/desempenho.sh tudo "-Pandroid.testInstrumentationRunnerArguments.riff.song=<nome>"`.
- O perfil de abertura (`startup-prof.txt`) leva só a abertura do app; a biblioteca e a partida entram no perfil geral. A medição da partida conta só os 20 s de jogo (abrir a música e sair ficam de fora).
- O app medido substitui o de debug no celular sem apagar os dados (mesma chave, veja [Chave de release](#chave-de-release)). Antes, o script para o app e copia os dados para uma pasta nova em `.copias-dados/<data>/` (fora do git); no fim, reinstala o debug, mesmo se algo falhar.
- **Restaurar uma cópia** (só se os dados sumirem), com o app de debug instalado e parado (`adb shell am force-stop com.rodrigomachado.riff`): para cada arquivo da pasta, `adb push <arquivo> /data/local/tmp/` e `adb shell "run-as com.rodrigomachado.riff sh -c 'cat /data/local/tmp/<arquivo> > databases/<arquivo>'"` (o `riff_settings.preferences_pb` vai para `files/datastore/`). Apague do aparelho o `riff.db-wal`/`-shm` que não estiver na cópia.
- **Quando regerar o perfil:** depois de mudanças grandes em telas ou no gameplay. Um perfil desatualizado não quebra nada: só deixa de acelerar o que mudou.
- O `frameDurationCpuMs` do Macrobenchmark inclui a espera na fila da tela ("Buffer Stuffing", o app adiantado). Para saber se a partida trava, use o `quadros_travados.py`.

Medido em 2026-10-31 (0.5.0, abrindo na tela inicial nova) no mesmo Galaxy S25, com o visual que estava escolhido no celular:

| Medida | Sem perfil | Com perfil |
|---|---|---|
| Abertura até a primeira tela | 204 ms | 192 ms |
| Partida: quadros por segundo | 120 | 120 |
| Partida: quadros com atraso do app (em ~2.430) | 1 | 0 a 1 |

`frameDurationCpuMs` p50 / p90: 5,6 / 10,9 ms sem perfil e 4,5 / 6,3 ms com perfil.

Antes, em 2026-10-16 (0.3.0, abrindo no Tocando agora) num Galaxy S25 (Android 16, tela a 120 Hz), com o visual mais pesado da pista (Chamas, Caveira alada, notas Caveiras, botões Alto-falantes e a imagem do mapa):

| Medida | Sem perfil | Com perfil |
|---|---|---|
| Abertura até a primeira tela | 176 ms | 151 ms |
| Partida: quadros por segundo | 120 | 120 |
| Partida: quadros com atraso do app (em ~2.430) | 1 a 4 | 0 a 1 |

Com o visual padrão (2026-10-12): abertura 188 / 163 ms; 120 fps; 0 a 2 / 1 quadro com atraso; `frameDurationCpuMs` p50 / p90 de 5,0 / 6,8 ms sem perfil e 4,3 / 6,0 ms com perfil.

Na medição anterior (2026-10-09, perfil de abertura igual ao geral e a partida contando a navegação), a abertura com perfil deu 148 ms; a variação entre execuções é de algumas dezenas de ms.

## Créditos e licenças

- Som de erro e sons do menu: montados com gravações de guitarra com licença Creative Commons 0 do Freesound (origem e autores em `third_party/README.md`).
- Som de acerto e metrônomo da calibração: sintetizados pelo próprio projeto.
- Separação da guitarra: modelo Demucs v4 htdemucs_6s (Meta, MIT), adaptado para ONNX pelo demucs.onnx (Sevag Hanssian, MIT); ONNX Runtime (Microsoft, MIT); JTransforms (Piotr Wendykier, BSD-2).
- Fontes New Rocker e Oswald: licença OFL (`core/ui/licenses/`).
- Mapas baixados pela pesquisa: Encore (enchor.us), feitos pela comunidade do Clone Hero.
- Nada do Guitar Hero nem de projetos que redesenham seus assets: a pista, as gemas e a chama são desenhadas em código.

## Status

O roteiro da especificação principal está concluído (2026-10-09). Depois vieram: a 0.5.0 sem o Spotify e com a tela inicial nova; o Riff no Windows (teclado, controle, ranking com três tabelas e o instalador `.msi`); o multiplayer por código de sala; o visual novo (0.6.0); e a Biblioteca com o X em cada música e a barra de seleção (0.6.2).

Pendências conhecidas (detalhes em `docs/pendencias.md`): pontos menores das revisões finais. As ideias para depois também estão lá.

Cada mudança no uso do app atualiza a seção correspondente deste README.
