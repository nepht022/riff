#!/usr/bin/env bash
# Gera o Baseline Profile e/ou mede o desempenho no aparelho conectado (spec do polimento §2).
# Uso: scripts/desempenho.sh perfil|medir|tudo [argumentos extras para o Gradle]
#   ex.: scripts/desempenho.sh tudo "-Pandroid.testInstrumentationRunnerArguments.riff.song=Outra Música"
# O app medido substitui o de debug (mesma chave, mesmos dados); no fim o debug volta.
set -euo pipefail
cd "$(dirname "$0")/.."
ADB="${ADB:-/mnt/c/Users/Rodrigo Machado/AppData/Local/Android/Sdk/platform-tools/adb.exe}"
SERIAL="${ANDROID_SERIAL:-RQCY106ML5P}"
PACOTE=com.rodrigomachado.riff
# Fora do build/ (um clean não apaga) e fora do git (.gitignore).
COPIAS=".copias-dados"
# Sem isto, o connectedAndroidTest desinstala o app no fim e apaga biblioteca, recordes e ajustes.
MANTER=(-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true)

# Cópia dos dados numa pasta nova por execução, com o app parado: banco e WAL saem sempre do mesmo momento.
# Só o app de debug deixa ler os dados; se não der para ler o banco, a pasta é descartada com um aviso.
copia_de_seguranca() {
    "$ADB" -s "$SERIAL" shell am force-stop "$PACOTE"
    local destino
    destino="$COPIAS/$(date +%Y-%m-%d_%H-%M-%S)"
    mkdir -p "$destino"
    for arquivo in databases/riff.db databases/riff.db-wal databases/riff.db-shm \
        files/datastore/riff_settings.preferences_pb; do
        local alvo
        alvo="$destino/$(basename "$arquivo")"
        "$ADB" -s "$SERIAL" exec-out run-as "$PACOTE" cat "$arquivo" > "$alvo" 2>/dev/null || true
        # Vazio ou mensagem do run-as: o arquivo não existe no aparelho (o WAL some depois de um checkpoint).
        if [ ! -s "$alvo" ] || grep -q "run-as:" "$alvo"; then rm -f "$alvo"; fi
    done
    if [ -s "$destino/riff.db" ]; then
        echo "cópia dos dados em $destino"
    else
        rm -rf "$destino"
        echo "AVISO: não deu para copiar os dados (o app instalado não é o de debug?)." \
            "Seguindo sem cópia nova; as anteriores estão em $COPIAS/." >&2
    fi
}

perfil() {
    ./scripts/gw :app:generateReleaseBaselineProfile "${MANTER[@]}" "${EXTRA[@]}"
}

medir() {
    ./scripts/gw :baselineprofile:connectedBenchmarkReleaseAndroidTest "${MANTER[@]}" "${EXTRA[@]}" \
        -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark
    python3 scripts/resumo_benchmark.py baselineprofile/build/outputs
}

volta_debug() {
    ./scripts/gw :app:assembleDebug -q
    "$ADB" -s "$SERIAL" install -r --user 0 "$(wslpath -w app/build/outputs/apk/debug/app-debug.apk)"
}

case "${1:-}" in
    perfil | medir | tudo) ;;
    *) echo "uso: $0 perfil|medir|tudo [argumentos extras para o Gradle]"; exit 2 ;;
esac
EXTRA=("${@:2}")
copia_de_seguranca
# O app de debug volta mesmo se a medição falhar no meio.
trap volta_debug EXIT
case "$1" in
    perfil) perfil ;;
    medir) medir ;;
    tudo) perfil; medir ;;
esac
