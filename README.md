# Riff

Jogo de ritmo para Android no estilo Guitar Hero: você toca junto a música que está tocando no Spotify, usando mapas do Clone Hero que você mesmo importa. É um projeto de uso pessoal e não é publicado na Play Store (veja a seção 3 da especificação).

- Especificação: `docs/superpowers/specs/2026-10-05-riff-design.md`
- Planos: `docs/superpowers/plans/`

## Requisitos

- Android Studio (o JBR 21 é usado como JDK) e Android SDK com a plataforma 37
- No WSL, o Gradle roda pelo `./scripts/gw`, que chama o `gradlew.bat` do Windows

## Comandos

```bash
./scripts/gw :app:assembleDebug                # APK de debug
./scripts/gw test                              # testes unitários
./scripts/gw detekt -PdetektAutoCorrect        # formata (ktlint) e corrige
./scripts/gw detekt                            # análise estática
./scripts/gw lintDebug                         # Android Lint
```

No Linux e no macOS, use `./gradlew` no lugar de `./scripts/gw`.

O Robolectric não funciona quando o caminho do `~/.m2` tem espaço (ele codifica o espaço como `%20`). Nesse caso, passe `-Priff.robolectric.mavenRepoLocal=<pasta sem espaço>`, como o `scripts/gw` já faz, ou coloque a propriedade no `gradle.properties` do usuário para o Android Studio.

## Módulos

| Módulo | Conteúdo |
|---|---|
| `:app` | Activity, Application com Hilt e navegação Tocando agora → Jogo → Resultado (e a Biblioteca) |
| `:core:ui` | Identidade "Pôster de turnê": tema escuro único, fontes New Rocker e Oswald (licenças OFL em `core/ui/licenses/`), componentes e peças de desenho da pista, com screenshots em `src/test/screenshots` |
| `:core:model` | Modelos de domínio |
| `:core:chart` | Leitura de mapas do Clone Hero (`notes.chart`, `notes.mid` e `song.ini`), e o casamento entre faixa do Spotify e mapa |
| `:core:game` | Relógio da música e motor do jogo (julgamento, combo e pontuação) |
| `:core:audio` | Player da música (MediaPlayer, ou faixas separadas misturadas com a guitarra cortada no erro) e sons de acerto e erro (SoundPool), gerados por `scripts/gerar_sfx.py` |
| `:core:database` | Room: mapas, dificuldades, casamentos com faixas do Spotify e recordes (esquemas em `schemas/`) |
| `:core:datastore` | Preferências do jogador e pasta da biblioteca |
| `:core:spotify` | Conexão com o app do Spotify (App Remote SDK), autorização, estado do player e o player da partida sobre o Spotify |
| `:core:data` | Repositório de mapas, acesso a pastas por SAF e importador de mapas |
| `:feature:library` | Tela da Biblioteca: escolher pasta, importar, lista e relatório |
| `:feature:gameplay` | A partida: pista em movimento, toque com vários dedos, chama, sons, pausa e fim |
| `:feature:results` | Resultado da partida e recorde |
| `:feature:settings` | Configurações: volume geral, de acerto e de erro, velocidade das notas e modo canhoto |
| `:feature:nowplaying` | Tela inicial "Tocando agora": faixa atual, mapa casado, confirmar ou escolher o mapa e jogar |

## Spotify

1. Crie um app em https://developer.spotify.com/dashboard com o redirect URI `riff://callback` e a plataforma **Android**, com o package `com.rodrigomachado.riff` e o SHA-1 da sua chave de debug (`keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`). Em modo de desenvolvimento, a conta do Spotify usada no aparelho precisa estar em "User Management".
2. No `local.properties` (fora do git):
   ```
   spotify.clientId=<Client ID do Dashboard>
   spotify.redirectUri=riff://callback
   ```
3. O aparelho precisa do app do Spotify instalado e logado numa conta Premium. Na primeira conexão o Riff abre o login do Spotify pedindo o controle remoto; aceite.

Sem `spotify.clientId`, o app compila e mostra "Spotify não configurado"; a biblioteca continua jogando com o áudio das pastas. O AAR do SDK está em `third_party/maven` (veja `third_party/README.md`). Falhas de conexão aparecem no logcat com a tag `RiffSpotify`.

## Screenshots

```bash
./scripts/gw :core:ui:recordRoborazziDebug   # regrava as referências depois de uma mudança visual intencional
./scripts/gw :core:ui:verifyRoborazziDebug   # compara com as referências
```

A comparação de screenshots é local: rode o `verifyRoborazziDebug` antes de commitar mudanças de UI. O CI não compara as imagens, porque o Robolectric desenha as fontes de forma diferente no Linux e no Windows (veja `docs/pendencias.md`).

## Status

Fundação, identidade visual, biblioteca, gameplay, integração com o Spotify e configurações prontos. Próximo plano: calibração.

Identidade visual "Pôster de turnê" aplicada ao design system (`docs/superpowers/specs/2026-10-07-identidade-visual-design.md`).
