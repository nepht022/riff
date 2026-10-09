#!/usr/bin/env python3
"""Monta as seis variações do som de erro a partir de gravações reais de guitarra (CC0, em third_party/sons).

Precisa de ffmpeg e numpy. Uso: python3 scripts/montar_sfx_erro.py third_party/sons core/audio/src/main/res/raw
Origem e licença de cada gravação: third_party/README.md.
"""
import subprocess
import sys
import wave

import numpy as np

RATE = 44_100
PEAK = 10 ** (-9 / 20)  # −9 dBFS: um pouco acima do som de acerto

SOURCES = {
    "strum": "353605_muted_guitar_strum_chiller345.mp3",
    "chord": "12006_dist_chr_gmin_pm_speedy.mp3",
    "note": "12042_dist_sng_g_3rd_str_pm_speedy.mp3",
    "scratch": "576575_guit_scratch_gazbooth.mp3",
    "pluck": "483882_electric_guitar_string_pluck_noise_joao_janz.mp3",
}


def load(path):
    raw = subprocess.run(
        ["ffmpeg", "-loglevel", "error", "-i", path, "-ac", "1", "-ar", str(RATE), "-f", "s16le", "-"],
        check=True,
        capture_output=True,
    ).stdout
    return np.frombuffer(raw, dtype=np.int16).astype(np.float64) / 32768


def norm(x):
    return x / (np.max(np.abs(x)) + 1e-9)


def onset(x, threshold=0.08):
    """Começo do som: primeira amostra acima de 8% do pico, com uma folga de 40 amostras."""
    return max(0, int(np.argmax(np.abs(x) > threshold * np.max(np.abs(x)))) - 40)


def trim(x, seconds, fade=0.04):
    y = x[: int(seconds * RATE)].copy()
    count = min(len(y), int(fade * RATE))
    y[-count:] *= np.linspace(1, 0, count)
    return y


def cut(x, seconds):
    """Trecho a partir do começo do som, normalizado, com fade curto no fim."""
    y = x[onset(x) :][: int(seconds * RATE)].copy()
    count = int(0.012 * RATE)
    y[-count:] *= np.linspace(1, 0, count)
    return norm(y)


def shift(x, semitones):
    """Desafina tocando mais rápido ou mais devagar."""
    ratio = 2 ** (semitones / 12)
    return np.interp(np.arange(0, len(x) - 1, ratio), np.arange(len(x)), x)


def layer(parts, seconds):
    out = np.zeros(int(seconds * RATE))
    for sound, at, gain in parts:
        start = int(at * RATE)
        count = min(len(sound), len(out) - start)
        out[start : start + count] += sound[:count] * gain
    return out


def ending(x):
    last = int(np.max(np.nonzero(np.abs(x) > 0.01))) + int(0.02 * RATE)
    return norm(x[:last])


def variations(src):
    strum = norm(src["strum"])
    strum = strum[onset(strum) :]
    chord = norm(src["chord"])
    chord = chord[onset(chord) :]
    note = norm(src["note"])
    note = note[onset(note) :]
    # Duas "emboladas" de base: acorde + nota fora do tom + raspada; três abafadas descendo + ruído de corda.
    m1 = ending(layer([(cut(src["chord"], 0.14), 0.0, 1.0), (shift(cut(src["note"], 0.12), 1), 0.07, 0.9),
                       (cut(src["scratch"], 0.18), 0.15, 0.7)], 0.6))
    m2 = ending(layer([(cut(src["chord"], 0.11), 0.0, 1.0), (shift(cut(src["chord"], 0.11), -1), 0.065, 0.9),
                       (shift(cut(src["note"], 0.13), -3), 0.13, 1.0), (cut(src["pluck"], 0.15), 0.2, 0.5)], 0.6))
    return [
        ([(strum, 0, 0.9), (trim(chord, 0.3), 0.04, 0.7)], 0.34),
        ([(m1, 0, 1.0), (trim(chord, 0.2, 0.08), 0.0, 0.4)], 0.36),
        ([(m2, 0, 1.0), (trim(strum, 0.2), 0.0, 0.5)], 0.33),
        ([(trim(chord, 0.18), 0, 0.9), (shift(trim(note, 0.2), 1), 0.08, 0.9)], 0.32),
        ([(shift(trim(chord, 0.25), -1), 0, 0.8), (strum, 0.1, 0.8)], 0.38),
        ([(trim(m1, 0.3), 0, 0.7), (trim(m2, 0.3), 0.03, 0.7)], 0.35),
    ]


def write(path, samples):
    with wave.open(path, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes((samples * 32767).astype(np.int16).tobytes())


if __name__ == "__main__":
    source_dir, target = sys.argv[1], sys.argv[2]
    sources = {name: load(f"{source_dir}/{file}") for name, file in SOURCES.items()}
    for number, (parts, seconds) in enumerate(variations(sources), start=1):
        write(f"{target}/sfx_miss_{number}.wav", norm(trim(layer(parts, 1.0), seconds)) * PEAK)
