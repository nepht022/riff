#!/usr/bin/env bash
# Gera core/separation/src/main/assets/htdemucs_6s_seg4.onnx e as referências dos testes (separacao_referencia.py).
# Precisa de python3 com torch 2.5, onnx, onnxscript, onnxslim, numpy e soundfile; e git.
set -euo pipefail
cd "$(dirname "$0")/.."
WORK="${WORK:-$(mktemp -d)}"
[ -d "$WORK/demucs.onnx" ] || git clone -q https://github.com/sevagh/demucs.onnx "$WORK/demucs.onnx"
git -C "$WORK/demucs.onnx" checkout -q 81fa192e6fcc88e35e887f6e6ccce91227f4e6f5
# O diffq só serve para modelos quantizados (o htdemucs_6s não é): sem ele a importação não quebra.
sed -i 's/^from diffq import DiffQuantizer, UniformQuantizer, restore_quantized_state$/DiffQuantizer = UniformQuantizer = restore_quantized_state = None/' \
  "$WORK/demucs.onnx/demucs-for-onnx/demucs/states.py"
export PYTHONPATH="$WORK/demucs.onnx/demucs-for-onnx"
python3 scripts/exportar_modelo_ia.py "$WORK/htdemucs_6s_seg4.onnx"
mkdir -p core/separation/src/main/assets
onnxslim "$WORK/htdemucs_6s_seg4.onnx" core/separation/src/main/assets/htdemucs_6s_seg4.onnx
python3 scripts/separacao_referencia.py core/separation/src/test/resources/separacao
