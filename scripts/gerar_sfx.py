#!/usr/bin/env python3
"""Gera os efeitos sonoros do Riff. Tudo sintetizado aqui, sem samples de terceiros.

Uso: python3 scripts/gerar_sfx.py core/audio/src/main/res/raw
"""
import math
import random
import struct
import sys
import wave

RATE = 44_100


def write(path, samples):
    with wave.open(path, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 30_000)) for s in samples))


def hit():
    """Estalo curto: senoide aguda com um pouco de ruído, decaindo rápido."""
    rng = random.Random(1)
    count = int(RATE * 0.045)
    return [
        (0.55 * math.sin(2 * math.pi * 1_800 * i / RATE) + 0.45 * (rng.random() * 2 - 1)) * math.exp(-i / (RATE * 0.008))
        for i in range(count)
    ]


def miss():
    """Corda abafada: onda quadrada grave misturada a ruído filtrado, decaindo."""
    rng = random.Random(2)
    count = int(RATE * 0.18)
    filtered = 0.0
    samples = []
    for i in range(count):
        t = i / RATE
        square = 1.0 if math.sin(2 * math.pi * 98 * t) >= 0 else -1.0
        filtered = 0.85 * filtered + 0.15 * (rng.random() * 2 - 1)
        samples.append((0.5 * square + 0.8 * filtered) * math.exp(-t / 0.06) * 0.8)
    return samples


if __name__ == "__main__":
    target = sys.argv[1]
    write(f"{target}/sfx_hit.wav", hit())
    write(f"{target}/sfx_miss.wav", miss())
