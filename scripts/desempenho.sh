#!/usr/bin/env bash
# Gera o Baseline Profile e/ou mede o desempenho no aparelho conectado (spec do polimento §2).
# Uso: scripts/desempenho.sh perfil|medir|tudo
# O app medido substitui o de debug (mesma chave, mesmos dados); no fim o debug volta.
set -euo pipefail
cd "$(dirname "$0")/.."
ADB="${ADB:-/mnt/c/Users/Rodrigo Machado/AppData/Local/Android/Sdk/platform-tools/adb.exe}"
SERIAL="${ANDROID_SERIAL:-RQCY106ML5P}"
PACOTE=com.rodrigomachado.riff
COPIA="build/copia-dados-riff"
# Sem isto, o connectedAndroidTest desinstala o app no fim e apaga biblioteca, recordes e ajustes.
MANTER=(-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true)

copia_de_seguranca() {
    mkdir -p "$COPIA"
    for arquivo in databases/riff.db databases/riff.db-wal databases/riff.db-shm \
        files/datastore/riff_settings.preferences_pb; do
        "$ADB" -s "$SERIAL" exec-out run-as "$PACOTE" cat "$arquivo" > "$COPIA/$(basename "$arquivo")" 2>/dev/null || true
    done
    echo "cópia dos dados em $COPIA"
}

perfil() {
    ./scripts/gw :app:generateReleaseBaselineProfile "${MANTER[@]}"
}

medir() {
    ./scripts/gw :baselineprofile:connectedBenchmarkReleaseAndroidTest "${MANTER[@]}" \
        -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark
    python3 scripts/resumo_benchmark.py baselineprofile/build/outputs
}

volta_debug() {
    ./scripts/gw :app:assembleDebug -q
    "$ADB" -s "$SERIAL" install -r --user 0 "$(wslpath -w app/build/outputs/apk/debug/app-debug.apk)"
}

case "${1:-}" in
    perfil) copia_de_seguranca; perfil; volta_debug ;;
    medir) copia_de_seguranca; medir; volta_debug ;;
    tudo) copia_de_seguranca; perfil; medir; volta_debug ;;
    *) echo "uso: $0 perfil|medir|tudo"; exit 2 ;;
esac
