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
| `:app` | Activity, Application com Hilt e navegação Biblioteca → Jogo → Resultado |
| `:core:ui` | Identidade "Pôster de turnê": tema escuro único, fontes New Rocker e Oswald (licenças OFL em `core/ui/licenses/`), componentes e peças de desenho da pista, com screenshots em `src/test/screenshots` |
| `:core:model` | Modelos de domínio |
| `:core:chart` | Leitura de mapas do Clone Hero (`notes.chart`, `notes.mid` e `song.ini`), e o casamento entre faixa do Spotify e mapa |
| `:core:game` | Relógio da música e motor do jogo (julgamento, combo e pontuação) |
| `:core:audio` | Player da música (MediaPlayer) e sons de acerto e erro (SoundPool), gerados por `scripts/gerar_sfx.py` |
| `:core:database` | Room: mapas, dificuldades, casamentos com faixas do Spotify e recordes (esquemas em `schemas/`) |
| `:core:datastore` | Preferências do jogador e pasta da biblioteca |
| `:core:data` | Repositório de mapas, acesso a pastas por SAF e importador de mapas |
| `:feature:library` | Tela da Biblioteca: escolher pasta, importar, lista e relatório |
| `:feature:gameplay` | A partida: pista em movimento, toque com vários dedos, chama, sons, pausa e fim |
| `:feature:results` | Resultado da partida e recorde |

## Screenshots

```bash
./scripts/gw :core:ui:recordRoborazziDebug   # regrava as referências depois de uma mudança visual intencional
./scripts/gw :core:ui:verifyRoborazziDebug   # compara com as referências
```

A comparação de screenshots é local: rode o `verifyRoborazziDebug` antes de commitar mudanças de UI. O CI não compara as imagens, porque o Robolectric desenha as fontes de forma diferente no Linux e no Windows (veja `docs/pendencias.md`).

## Status

Fundação, identidade visual, biblioteca e gameplay com o áudio da pasta prontos. Próximos planos: integração com o Spotify (inclui o casamento manual), calibração e configurações.

Identidade visual "Pôster de turnê" aplicada ao design system (`docs/superpowers/specs/2026-10-07-identidade-visual-design.md`).
