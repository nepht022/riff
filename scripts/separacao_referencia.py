"""Referências dos testes de :core:separation, geradas pelo Demucs original (PyTorch), não pelo port.

stft_*: a STFT do Demucs num sinal aleatório de 5000 amostras; mix/guitar/backing: 6 s sintéticos separados
pelo apply_model do Demucs com trechos de 4 s e deslocamento 0 (o mesmo do port). Arquivos float32 little-endian,
planares (canal 0 inteiro, depois o 1); o espectro em planos [canal][real|imag][faixa][quadro].
"""
import sys
from fractions import Fraction
from pathlib import Path

import numpy as np
import torch
import demucs.apply as apply
from demucs.htdemucs import standalone_ispec, standalone_spec
from demucs.pretrained import get_model

out = Path(sys.argv[1])
out.mkdir(parents=True, exist_ok=True)
save = lambda name, a: np.ascontiguousarray(a, dtype="<f4").tofile(out / f"{name}.f32")

rng = np.random.default_rng(7)
x = torch.from_numpy(rng.uniform(-0.5, 0.5, (1, 2, 5000)).astype(np.float32))
z = standalone_spec(x)[0]                                   # [2, 2048, 5] complexo
save("stft_in", x[0].numpy())
save("stft_spec", torch.stack([z.real, z.imag], dim=1).numpy())  # [2, 2, 2048, 5]
save("stft_inverse", standalone_ispec(z[None], length=5000)[0].numpy())

sr, seconds = 44100, 6
t = np.arange(sr * seconds) / sr
saw = lambda f: 2 * ((t * f) % 1) - 1
guitar = 0.25 * (saw(110) + saw(165) + saw(220)) * (0.6 + 0.4 * np.sin(2 * np.pi * 2 * t))
bass = 0.3 * np.sin(2 * np.pi * 55 * t)
kick = 0.5 * np.exp(-30 * ((t % 0.5))) * np.sin(2 * np.pi * 60 * t)
noise = 0.05 * rng.standard_normal(t.size)
mix = np.stack([guitar + bass + kick + noise, 0.9 * guitar + bass + kick - noise]).astype(np.float32)
save("mix", mix)

model = get_model("htdemucs_6s").models[0].eval()
model.segment = Fraction(4)
apply.random.randint = lambda a, b: 0                     # deslocamento 0, como no port
wav = torch.from_numpy(mix)
ref = wav.mean(0)
norm = (wav - ref.mean()) / ref.std()
with torch.no_grad():
    sources = apply.apply_model(model, norm[None], shifts=1, split=True, overlap=0.25)[0]
sources = sources * ref.std() + ref.mean()
save("guitar", sources[4].numpy())
save("backing", (sources.sum(0) - sources[4]).numpy())
