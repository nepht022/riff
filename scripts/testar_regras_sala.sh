#!/usr/bin/env bash
# Testa as regras do banco da sala (firebase/database.rules.json) no emulador do Firebase, a partir do WSL.
# O emulador precisa de Java 11+ e o WSL não tem: roda tudo pelo Windows (Node do Windows e o Temurin 21 que o
# Gradle baixou). O JDK entra no PATH do WSL, que o WSL traduz para o PATH do processo do Windows.
# Projeto `demo-riff`: o emulador nunca toca num Firebase de verdade.
set -euo pipefail
cd "$(dirname "$0")/../firebase"
jdk="${RIFF_JDK:-/mnt/c/Users/Rodrigo Machado/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2}"
if [[ ! -x "$jdk/bin/java.exe" ]]; then
  echo "Java não encontrado em $jdk (defina RIFF_JDK com a pasta de um JDK 11+ do Windows)." >&2
  exit 1
fi
export PATH="$jdk/bin:$PATH"
if [[ ! -d node_modules ]]; then
  cmd.exe /c npm ci
fi
exec cmd.exe /c npm test
