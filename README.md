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
