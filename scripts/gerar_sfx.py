#!/usr/bin/env python3
"""Gera os sons de acerto do Riff. Tudo sintetizado aqui, sem samples de terceiros.
O som de erro vem de gravações CC0: scripts/montar_sfx_erro.py.

Uso: python3 scripts/gerar_sfx.py core/audio/src/main/res/raw
"""
import math
import random
import struct
import sys
import wave

RATE = 44_100

# Uma nota por trilha, pentatônica de Mi menor: Mi3, Sol3, Lá3, Si3, Ré4.
LANE_NOTES_HZ = [164.81, 196.00, 220.00, 246.94, 293.66]
HIT_SECONDS = 0.25
HIT_FADE_SECONDS = 0.06
HIT_PEAK = 10 ** (-12 / 20)  # −12 dBFS: fica atrás da música


def write(path, samples):
    with wave.open(path, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 30_000)) for s in samples))


def lowpass(samples, cutoff):
    alpha = 1 - math.exp(-2 * math.pi * cutoff / RATE)
    y = 0.0
    out = []
    for x in samples:
        y += alpha * (x - y)
        out.append(y)
    return out


def highpass(samples, cutoff):
    return [x - low for x, low in zip(samples, lowpass(samples, cutoff))]


def pluck(freq, seconds, seed):
    """Corda dedilhada (Karplus-Strong): ruído num atraso do tamanho do período, suavizado a cada volta."""
    rng = random.Random(seed)
    period = int(RATE / freq)
    delay = [rng.random() * 2 - 1 for _ in range(period)]
    out = []
    for i in range(int(RATE * seconds)):
        j = i % period
        value = delay[j]
        delay[j] = 0.998 * 0.5 * (value + delay[(j + 1) % period])
        out.append(value)
    return out


def hit(lane):
    """Guitarra com distorção leve ("crunch") e caixa de amplificador, na nota da trilha."""
    samples = pluck(LANE_NOTES_HZ[lane], HIT_SECONDS, seed=60 + lane)
    samples = highpass(samples, 120)
    samples = [math.tanh(2.5 * x) for x in samples]
    samples = highpass(lowpass(lowpass(samples, 4_000), 5_500), 70)
    attack = int(RATE * 0.001)
    fade = int(RATE * HIT_FADE_SECONDS)
    count = len(samples)
    shaped = [x * min(1, i / attack) * min(1, (count - i) / fade) for i, x in enumerate(samples)]
    peak = max(abs(x) for x in shaped)
    return [x / peak * HIT_PEAK for x in shaped]


if __name__ == "__main__":
    target = sys.argv[1]
    for lane in range(len(LANE_NOTES_HZ)):
        write(f"{target}/sfx_hit_{lane + 1}.wav", hit(lane))
