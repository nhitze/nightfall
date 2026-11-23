#!/usr/bin/env python3
"""Generate original, synthetic loop placeholders for Nightfall's offline demo."""

import cmath
import math
import random
import struct
import wave
from pathlib import Path


SAMPLE_RATE = 22_050
FRAME_COUNT = 1 << 18
OUTPUT = Path(__file__).resolve().parents[1] / "Nightfall" / "Resources" / "Sounds"


def inverse_fft(values):
    size = len(values)
    j = 0
    for i in range(1, size):
        bit = size >> 1
        while j & bit:
            j ^= bit
            bit >>= 1
        j ^= bit
        if i < j:
            values[i], values[j] = values[j], values[i]

    length = 2
    while length <= size:
        angle = 2 * math.pi / length
        root = complex(math.cos(angle), math.sin(angle))
        half = length // 2
        for start in range(0, size, length):
            factor = 1 + 0j
            for offset in range(half):
                even = values[start + offset]
                odd = values[start + offset + half] * factor
                values[start + offset] = even + odd
                values[start + offset + half] = even - odd
                factor *= root
        length *= 2

    return [value.real / size for value in values]


def spectral_weight(name, frequency):
    normalized = max(frequency / (SAMPLE_RATE / 2), 1 / FRAME_COUNT)
    if name in ("Rain", "Forest"):
        return 1 / math.sqrt(normalized + 0.02)
    if name in ("Ocean", "Wind"):
        return 1 / (normalized + 0.08) ** 1.15
    if name == "Fan":
        return 1 / (normalized + 0.06) ** 0.55
    if name == "Fireplace":
        return 1 / (normalized + 0.04) ** 0.72
    if name == "Train":
        return 1 / (normalized + 0.035) ** 1.05
    return 1


def make_base(name):
    randomizer = random.Random(f"nightfall-placeholder-{name}")
    spectrum = [0j] * FRAME_COUNT
    for bin_number in range(1, FRAME_COUNT // 2):
        frequency = bin_number * SAMPLE_RATE / FRAME_COUNT
        weight = spectral_weight(name, frequency)
        magnitude = randomizer.random() * weight
        phase = randomizer.uniform(0, 2 * math.pi)
        value = cmath.rect(magnitude, phase)
        spectrum[bin_number] = value
        spectrum[FRAME_COUNT - bin_number] = value.conjugate()
    samples = inverse_fft(spectrum)

    peak = max(abs(sample) for sample in samples) or 1
    return [sample / peak * 0.32 for sample in samples]


def synthesize(name):
    samples = make_base(name)
    for index in range(FRAME_COUNT):
        phase = 2 * math.pi * index / FRAME_COUNT
        if name == "Ocean":
            swell = 0.22 + 0.78 * ((0.5 + 0.5 * math.sin(phase - math.pi / 2)) ** 1.7)
            samples[index] *= swell
        elif name == "Wind":
            gust = 0.38 + 0.62 * (0.5 + 0.5 * math.sin(phase * 3 - 1.1))
            samples[index] *= gust
        elif name == "Train":
            passing = 0.48 + 0.52 * (0.5 + 0.5 * math.sin(phase * 2 - math.pi / 2))
            rhythm = 0.9 + 0.1 * math.sin(phase * 32)
            samples[index] *= passing * rhythm
        elif name == "Fan":
            samples[index] += 0.035 * math.sin(phase * 27) + 0.012 * math.sin(phase * 54)
        elif name == "Fireplace":
            for pulse, location in enumerate((0.08, 0.27, 0.51, 0.73, 0.91)):
                distance = abs(((index / FRAME_COUNT - location + 0.5) % 1) - 0.5)
                width = 0.008 + pulse * 0.0005
                samples[index] += 0.24 * math.exp(-((distance / width) ** 2) * 5)
        elif name == "Forest":
            for location, cycles in ((0.15, 14), (0.47, 17), (0.79, 13)):
                local = (index / FRAME_COUNT - location) % 1
                if local < 0.018:
                    envelope = math.sin(math.pi * local / 0.018) ** 2
                    frequency = 1600 + 800 * local / 0.018
                    samples[index] += 0.04 * envelope * math.sin(2 * math.pi * cycles * index / FRAME_COUNT)

    peak = max(abs(sample) for sample in samples) or 1
    samples = [max(-0.78, min(0.78, sample / peak * 0.72)) for sample in samples]
    return samples


def write_wav(path, samples):
    pcm = bytearray()
    for sample in samples:
        pcm.extend(struct.pack("<h", int(sample * 32767)))
    with wave.open(str(path), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(SAMPLE_RATE)
        output.writeframes(pcm)


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for name in ("Rain", "Ocean", "Fan", "Fireplace", "Train", "Wind", "Forest"):
        target = OUTPUT / f"{name}.wav"
        write_wav(target, synthesize(name))
        print(f"Wrote {target.name}")


if __name__ == "__main__":
    main()
