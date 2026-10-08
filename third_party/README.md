# Dependências de terceiros versionadas

## Spotify App Remote SDK 0.8.0

- Origem: https://github.com/spotify/android-sdk/releases/tag/v0.8.0-appremote_v2.1.0-auth (`spotify-app-remote-release-0.8.0.aar`)
- SHA-256: `b5a6dd880eaf01f63a871cba9ef7af77c341f8a94ffc8fdf2e9021f9a9d4c198`
- Licença: Apache 2.0 (cabeçalho do `AndroidManifest.xml` do AAR)
- Por que aqui: o Spotify publica o App Remote só no GitHub, fora do Maven. Um repositório Maven local evita a dependência direta de `.aar`, que o AGP não aceita em módulos de biblioteca.
- O SDK usa o Gson em tempo de execução; a versão fica no version catalog (`gson`).
