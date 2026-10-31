# Riff 0.5.0: sem o Spotify, com tela inicial nova

Data: 2026-10-30 · Status: aprovado em conversa pelo usuário ("pode seguir"); dúvidas do revisor respondidas em 2026-10-30
Primeiro de três projetos, nesta ordem (decisão do usuário): **1. tirar o Spotify** → 2. Riff no Windows (teclado e controle) → 3. multiplayer por código de sala (até 4, placar ao vivo, Firebase gratuito). Este documento cobre só o 1.

## 1. Intenção

O Riff passa a ser um jogo só com a Biblioteca (mapas da pasta e do Encore), preparando o terreno para o Windows e o multiplayer. A versão com Spotify (0.4.0) fica guardada como está no GitHub (tag e release `v0.4.0`); daqui em diante o código segue sem o Spotify. É o **mesmo app** (mesmo pacote `com.rodrigomachado.riff`), numa versão nova que atualiza por cima e mantém os dados.

Motivos (conversa de 2026-10-30): o usuário quer uma versão para Android e Windows com multiplayer; o modo Spotify não existe no PC sem cadastro e complicaria os dois próximos projetos.

## 2. Decisões do usuário

- Mesma aplicação, versão nova substituindo a atual (não um segundo app). Número: **0.5.0** (o Android só atualiza para um número maior; "0.4.0b" foi a ideia inicial do usuário).
- Tudo do Riff de hoje fica, **menos o Spotify**: Biblioteca, pesquisa no Encore, guitarra separada por IA (com a fila), ranking online, visual da pista, calibração, Reportar, Licenças.
- **Tela inicial** (escolhida entre maquetes, opção "F de antes", `.superpowers/brainstorm/83292-1793377438/content/tela-inicial-f-tamanhos.html`, opção 1):
  - no topo, nos cantos: ícone de **Reportar** (envelope) à esquerda e de **Configurações** (engrenagem) à direita;
  - **no meio do topo**, entre os dois, a palheta da separação por IA (`SeparationIndicator`), só enquanto houver separação ou fila (decisão do usuário depois da leitura do revisor); sem fila, a tela é igual à maquete;
  - o logo **Riff** (letreiro do app, New Rocker com as sombras vermelha e preta);
  - dois quadros altos lado a lado: **Sozinho** (vermelho, ícone de nota) abre a Biblioteca; **Sala** (escuro, ícone de bandeira, "em breve") desligado até o projeto 3;
  - no pé da tela, o cartaz vermelho **★ Ranking** (como os PosterButton do app), que abre o Ranking.
- Os mapas baixados pela fila do Spotify ficam na Biblioteca (já são mapas como os outros). **Suposição minha aceita pelo usuário.**

## 3. Objetivos e não objetivos

**Objetivos**
- Nenhum rastro do Spotify no app instalado: telas, permissões, serviço de notificações, textos, licenças, privacidade, Reportar.
- O app abre na tela inicial nova; dela se chega a tudo o que existe hoje (menos o Spotify).
- Atualizar por cima da 0.4.0 sem perder recordes, separações, ajustes de sincronia dos mapas da Biblioteca, nome do ranking, configurações que continuam valendo.

**Não objetivos**
- Windows, teclado, controle, "tocar sem palheta" (projeto 2).
- Multiplayer, Sala funcionando (projeto 3). Aqui a Sala só aparece desligada.
- Mudar a Biblioteca, a partida, a pesquisa ou o ranking além de tirar o que era do Spotify.

**Anti-objetivos**
- Perder dados do jogador na atualização.
- Deixar caminhos mortos (botões, rotas, opções) que levem a algo do Spotify.
- Pedir o "Acesso às notificações" ou manter o `NotificationListenerService` declarado.

## 4. O que sai

- **Telas e fluxos:** Tocando agora (`:feature:nowplaying`), o pedido de "Acesso às notificações", o começo automático da próxima música, a escolha de mapa para uma música do Spotify (Biblioteca aberta "para casar"), a pesquisa aberta a partir do Spotify (com a música já preenchida e o mapa casado ao baixar), os controles ⏮ ⏯ ⏭ na pausa da partida, o painel "Confirmar mapa e jogar" de mapa sugerido, a "Sincronia desta música" ligada a uma faixa do Spotify (o ajuste por mapa da Biblioteca fica).
- **Configurações:** a aba **Spotify** inteira (fila de mapas, apagar não jogados, dados móveis, sugestões, "ao acabar a música").
- **Código:** o módulo `:core:spotify` (sessão de mídia, `RiffNotificationListener`, fila da sessão), a fila de mapas do Spotify em `:core:data` (`queue/`: prefetcher, cleaner, dispatcher, downloads, preparing, match activity), o casamento música × mapa (`TrackMatcher`, `TrackMatchRepository` e o que só servia a ele), os players do Spotify em `:core:audio`, os modelos do Spotify em `:core:model` (`PlaybackState`, `NowPlaying`, `SpotifyConnection`, `TrackMatch` e afins), as rotas e a navegação do Tocando agora em `:app`.
- **Manifesto:** o serviço `RiffNotificationListener` e a permissão `BIND_NOTIFICATION_LISTENER_SERVICE`; qualquer `<queries>` do pacote do Spotify.
- **Textos:** categoria "Spotify" do Reportar; Spotify nas Licenças e no texto de privacidade; README (seções do Spotify, instalação, Configurações).

## 5. O que fica e muda

- **Tela inicial** nova (rota inicial do `NavHost` no lugar de `NowPlayingRoute`). O Voltar da Biblioteca e do Ranking volta para ela; o Voltar do sistema na tela inicial fecha o app. A notificação da separação ("abrir a Biblioteca") continua abrindo a Biblioteca, com a tela inicial embaixo.
- **Ícones da tela inicial** com descrição para o leitor de tela ("Reportar problema", "Configurações"); a Sala desligada é lida como "Sala, em breve" e não responde ao toque.
- **Partida:** só o modo Biblioteca (o código do modo Spotify sai do `GameplayViewModel`, da tela e da sessão).
- **Resultado e Ranking:** sem diferenças de comportamento; tiram o que só existia para o modo Spotify (ex.: seguir direto para a próxima música).
- **Pesquisa no Encore:** só a partir da Biblioteca.

## 6. Dados na atualização (0.4.0 → 0.5.0)

- **Banco (Room):** nova versão do esquema que **remove** as tabelas que só o Spotify usava: `track_matches` (casamentos e ajustes por faixa do Spotify) e `queue_downloads` (registro dos mapas da fila). Os mapas em si continuam na tabela de mapas e na pasta "Baixados pelo Riff". Migração coberta pelo `MigrationTest` (dados das outras tabelas preservados).
- **Preferências (DataStore):** as chaves só do Spotify (`queue_*`, `spotify_next_song_direct`) são apagadas na leitura, como já se fez com chaves antigas; o resto continua.
- `chart_offsets` (ajuste por mapa da Biblioteca), recordes, separações, fila de separações, nome e chave do ranking, fila do ranking: intactos.
- O que a 0.4.0 já limpava (token antigo, chave do Keystore) pode continuar ou sair; decisão do construtor.

## 7. Versão e distribuição

- `versionCode 5`, `versionName "0.5.0"`.
- A 0.4.0 continua no GitHub como release. A 0.5.0 só vira release quando o usuário pedir (a distribuição para amigos está em espera; ver memória/pendências).

## 8. Riscos

- **Código emaranhado:** o modo Spotify passa pela partida, pela pesquisa, pela Biblioteca e pelos dados; tirar tudo exige cuidado para não quebrar o modo Biblioteca. A suíte de testes cobre os dois modos hoje; os testes do modo Spotify saem junto, os da Biblioteca têm de continuar passando.
- **Baseline Profile e o desempenho:** os percursos do Macrobenchmark (`baselineprofile/Journeys.kt`) começam pelo Tocando agora; precisam começar pela tela inicial (o quadro Sozinho no lugar da etiqueta `nowplaying_open_library`). O celular do usuário está ligado ao PC (adb, com a Biblioteca carregada): o perfil é **refeito no aparelho** (`scripts/desempenho.sh`) e o desempenho medido de novo, com o README atualizado, antes da revisão final.
- **Dados:** a migração que remove tabelas é irreversível; o `MigrationTest` precisa provar que nada além delas some.

## 9. Testes e aceite

- Tela inicial: testes de tela (cada botão leva aonde deve, Sala desligada, descrições) e screenshot.
- Migração do banco (versão nova) e limpeza das preferências.
- A suíte inteira verde (com os testes do Spotify removidos junto do código), detekt, lint, screenshots, builds de debug e release; CI no GitHub.
- Revisão independente da branch.
- Aceite no celular do usuário: atualizar por cima da 0.4.0 e conferir que recordes, separações e ajustes continuam; jogar uma partida da Biblioteca; nada do Spotify aparece; o app não pede "Acesso às notificações".

## 10. Deixado para o construtor

- Como dividir a remoção em commits e a ordem.
- Os desenhos exatos dos ícones (nota, bandeira, envelope, engrenagem), desde que no estilo do app (desenhados em código, sem imagens prontas).
- Manter ou não as limpezas antigas de abertura (token e chave do Spotify da 0.2.0).
