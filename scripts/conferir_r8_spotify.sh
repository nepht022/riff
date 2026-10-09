#!/usr/bin/env bash
# Confere no APK release que o SDK do Spotify mantém o que ele usa por reflexão.
# O SDK acha o app do Spotify com Class.forName("...ReleaseSpotifyLocator").getConstructor().newInstance():
# sem o construtor, o "Tocando agora" diz "Spotify não instalado" (docs/pendencias.md, 2026-10-09).
# Uso: scripts/conferir_r8_spotify.sh (depois de ./scripts/gw :app:assembleRelease)
set -euo pipefail
cd "$(dirname "$0")/.."
APK=app/build/outputs/apk/release/app-release.apk
SDK="${ANDROID_SDK:-/mnt/c/Users/Rodrigo Machado/AppData/Local/Android/Sdk}"
DEXDUMP="$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -1)dexdump.exe"
[ -f "$APK" ] || { echo "sem $APK: rode ./scripts/gw :app:assembleRelease"; exit 2; }
PASTA=build/conferir-r8
rm -rf "$PASTA" && mkdir -p "$PASTA"
unzip -q -o "$APK" 'classes*.dex' -d "$PASTA"
falhou=0
# (O DebugSpotifyLocator só existe no SDK de debug: no AAR publicado ele falta de propósito.)
for classe in ReleaseSpotifyLocator; do
    descritor="Lcom/spotify/android/appremote/internal/$classe;"
    achou=0
    for dex in "$PASTA"/classes*.dex; do
        # Bloco da classe no dexdump: procura um construtor público sem argumentos.
        if "$DEXDUMP" "$(wslpath -w "$dex")" 2>/dev/null | tr -d '\r' \
            | awk -v d="$descritor" '/Class descriptor/{f=index($0, d)>0} f' \
            | grep -A2 "name          : '<init>'" | grep -q "type          : '()V'"; then
            achou=1
        fi
    done
    if [ $achou = 1 ]; then echo "ok: $classe mantém o construtor"; else echo "FALHA: $classe sem o construtor"; falhou=1; fi
done
exit $falhou
