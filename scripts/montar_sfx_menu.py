#!/usr/bin/env python3
"""Monta os sons de guitarra dos botões do menu a partir de gravações reais (CC0, em third_party/sons).

No estilo dos menus de jogos de guitarra, mas só com as nossas gravações: navegar é um toque abafado curto,
confirmar é um power chord (a nota gravada somada à quinta e à oitava, afinadas da mesma gravação), voltar é
a palheta arrastando na corda.

Precisa de ffmpeg e numpy. Uso: python3 scripts/montar_sfx_menu.py third_party/sons core/audio/src/main/res/raw
Origem e licença de cada gravação: third_party/README.md.
"""
import math
import subprocess
import sys
import wave

import numpy as np

RATE = 44_100
PEAK = 10 ** (-14 / 20)  # −14 dBFS: abaixo do som de acerto, para o menu não soar mais alto que o jogo

NOTE = "12042_dist_sng_g_3rd_str_pm_speedy.mp3"
CHORD = "12006_dist_chr_gmin_pm_speedy.mp3"
SCRATCH = "576575_guit_scratch_gazbooth.mp3"


def load(path):
    raw = subprocess.run(
        ["ffmpeg", "-loglevel", "error", "-i", path, "-ac", "1", "-ar", str(RATE), "-f", "s16le", "-"],
        check=True,
        capture_output=True,
    ).stdout
    x = np.frombuffer(raw, dtype=np.int16).astype(np.float64) / 32768
    return x[int(np.argmax(np.abs(x) > np.abs(x).max() * 0.05)) :]


def shift(x, semitones):
    """Desafina tocando mais rápido ou mais devagar."""
    ratio = 2 ** (semitones / 12)
    return np.interp(np.arange(0, len(x) - 1, ratio), np.arange(len(x)), x)


def shape(x, seconds, fade):
    """Corta em [seconds], com ataque de 1 ms e fade de [fade] no fim."""
    y = x[: int(seconds * RATE)].copy()
    count = min(len(y), int(fade * RATE))
    y[:44] *= np.linspace(0, 1, 44)
    y[-count:] *= np.linspace(1, 0, count) ** 2
    return y


def lowpass(x, cutoff, q=0.707):
    """Passa-baixa de 2ª ordem (biquad): abranda o chiado da distorção."""
    w = 2 * math.pi * cutoff / RATE
    alpha, cos = math.sin(w) / (2 * q), math.cos(w)
    b0, b1, b2 = (1 - cos) / 2, 1 - cos, (1 - cos) / 2
    a0, a1, a2 = 1 + alpha, -2 * cos, 1 - alpha
    y = np.zeros_like(x)
    x1 = x2 = y1 = y2 = 0.0
    for i, v in enumerate(x):
        out = (b0 * v + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0
        x2, x1, y2, y1 = x1, v, y1, out
        y[i] = out
    return y


def norm(x):
    return x / (np.abs(x).max() + 1e-9)


def mix(*parts):
    out = np.zeros(max(len(p) for p in parts))
    for p in parts:
        out[: len(p)] += p
    return out


def power_chord(note, root, seconds, fade):
    fifth, octave = shift(note, root + 7), shift(note, root + 12)
    return shape(mix(norm(shift(note, root)), 0.8 * norm(fifth), 0.5 * norm(octave)), seconds, fade)


def save(path, samples):
    y = norm(samples) * PEAK
    with wave.open(path, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes((np.clip(y, -1, 1) * 32767).astype("<i2").tobytes())


def main(source, target):
    note, chord, scratch = (load(f"{source}/{name}") for name in (NOTE, CHORD, SCRATCH))
    sounds = {
        "sfx_menu_navigate_1": lowpass(shape(shift(chord, -5), 0.12, 0.05), 3500),
        "sfx_menu_navigate_2": lowpass(shape(note, 0.15, 0.07), 3500),
        "sfx_menu_confirm_1": lowpass(power_chord(note, -5, 0.45, 0.2), 4000),
        "sfx_menu_confirm_2": lowpass(power_chord(note, -3, 0.25, 0.1), 4000),
        "sfx_menu_back": lowpass(shape(scratch, 0.35, 0.12), 5000),
    }
    for name, samples in sounds.items():
        save(f"{target}/{name}.wav", samples)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
