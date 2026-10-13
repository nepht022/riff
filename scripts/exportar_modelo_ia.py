"""Exporta o htdemucs_6s (Demucs v4, MIT) para ONNX com trechos de 4 s e a STFT fora do modelo.

Usa o Demucs adaptado do demucs.onnx (MIT, https://github.com/sevagh/demucs.onnx), que tira a STFT/iSTFT
de dentro da rede. Entradas: mix [1, 2, 176400] e spec [1, 4, 2048, 173]; saídas: spec_out [1, 6, 4, 2048, 173]
e mix_out [1, 6, 2, 176400].
"""
import sys
from fractions import Fraction

import torch
from demucs.htdemucs import standalone_magnitude, standalone_spec
from demucs.pretrained import get_model

model = get_model("htdemucs_6s").models[0].eval()
model.segment = Fraction(4)
wave = torch.randn(1, 2, 4 * 44100)
spec = standalone_magnitude(standalone_spec(wave))
torch.onnx.export(
    model, (wave, spec), sys.argv[1], export_params=True, opset_version=17, do_constant_folding=True,
    input_names=["mix", "spec"], output_names=["spec_out", "mix_out"],
)
