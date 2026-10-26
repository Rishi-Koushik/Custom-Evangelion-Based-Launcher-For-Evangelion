"""Synthesises the NERV sound set: a ringtone, notification, alarm and charging sound for each unit.

Everything here is original: NERV-style alert tones, MAGI blips and klaxons, no music from the show.
Each unit has its own key, tempo and instrument so the four sets sound related but distinct:
  unit01 Misato   E minor, brass stabs + MAGI square blips + two-tone klaxon
  unit00 Rei      D lydian, slow FM bells over a soft sine pad
  unit02 Asuka    A minor, fast square-wave arpeggios with drum hits
  unit08 Mari     G major, swung plucked arpeggios with a bell on top

Writes OGG Vorbis (needs ffmpeg on PATH):
  D:/Claude/quickstar/res/raw/nerv_{ring,notif,alarm}_<unit>.ogg   (Quickstar sets them as the phone's sounds)
  nerv-os/glyph/app/src/main/res/raw/nerv_charge_<unit>.ogg          (NERV Glyph plays it on plug-in)

Run from anywhere:  python gen_sounds.py
"""
import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np
from scipy import signal

SR = 44100
HERE = Path(__file__).resolve().parent
QUICKSTAR_RAW = Path(r"D:\Claude\quickstar\res\raw")
GLYPH_RAW = HERE.parent / "glyph" / "app" / "src" / "main" / "res" / "raw"
RNG = np.random.default_rng(7)


def hz(note):
    """'E4', 'F#5', 'Bb3' -> frequency."""
    names = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}
    n = names[note[0]]
    rest = note[1:]
    if rest[0] == "#":
        n, rest = n + 1, rest[1:]
    elif rest[0] == "b":
        n, rest = n - 1, rest[1:]
    return 440.0 * 2 ** ((n + 12 * (int(rest) + 1) - 69) / 12)


def tt(d):
    return np.arange(int(SR * d)) / SR


def phase(f, d):
    """Running phase for a constant or per-sample frequency."""
    f = np.broadcast_to(np.asarray(f, dtype=float), (int(SR * d),))
    return 2 * np.pi * np.cumsum(f) / SR


def wave_of(kind, ph):
    if kind == "sine":
        return np.sin(ph)
    if kind == "square":
        return np.sign(np.sin(ph)) * 0.6
    if kind == "saw":
        return 2 * ((ph / (2 * np.pi)) % 1.0) - 1
    if kind == "tri":
        return 2 * np.abs(2 * ((ph / (2 * np.pi)) % 1.0) - 1) - 1
    raise ValueError(kind)


def env(n, attack=0.005, decay=0.25, sustain=0.0, release=0.05):
    """Attack, exponential decay toward sustain, short release at the end."""
    x = np.arange(n) / SR
    a = np.clip(x / max(attack, 1e-4), 0, 1)
    d = sustain + (1 - sustain) * np.exp(-np.maximum(x - attack, 0) / max(decay, 1e-4))
    r = np.clip((n / SR - x) / max(release, 1e-4), 0, 1)
    return a * d * r


def lowpass(x, fc, order=2):
    sos = signal.butter(order, min(fc, SR * 0.45), "low", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def highpass(x, fc, order=2):
    sos = signal.butter(order, fc, "high", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


# ---------- instruments ----------

def tone(f, d, kind="sine", **e):
    return wave_of(kind, phase(f, d)) * env(int(SR * d), **e)


def brass(f, d, cutoff=2400):
    """Two detuned saws through a lowpass that opens on the attack: a short brass stab."""
    n = int(SR * d)
    x = wave_of("saw", phase(f * 0.997, d)) + wave_of("saw", phase(f * 1.004, d)) + 0.5 * wave_of("saw", phase(f / 2, d))
    x = lowpass(x, cutoff, 2) * 0.5
    return x * env(n, attack=0.012, decay=d * 0.6, sustain=0.25, release=0.06)


def blip(f, d=0.07):
    """MAGI terminal blip."""
    return tone(f, d, "square", attack=0.001, decay=d * 0.8, sustain=0.0, release=0.01) * 0.55


def bell(f, d, ratio=3.5, index=3.0, decay=1.1):
    """FM bell: modulator at a non-integer ratio, index falling with the envelope."""
    n = int(SR * d)
    e = env(n, attack=0.002, decay=decay, sustain=0.0, release=0.1)
    mod = np.sin(phase(f * ratio, d)) * index * e
    return np.sin(phase(f, d) + mod) * e


def pad(f, d, kind="sine"):
    n = int(SR * d)
    x = sum(wave_of(kind, phase(f * k, d)) for k in (1.0, 1.003, 0.997)) / 3
    return lowpass(x, 1800) * env(n, attack=d * 0.35, decay=10, sustain=1.0, release=d * 0.35)


def pluck(f, d, bright=0.5):
    """Karplus-Strong plucked string."""
    n = int(SR * d)
    p = max(2, int(round(SR / f)))
    y = np.zeros(n + 2 * p + 1)
    y[: p + 1] = RNG.uniform(-1, 1, p + 1)
    a = 0.5 + bright * 0.45
    # y[i] = 0.996 * (a * y[i-p] + (1-a) * y[i-p-1]); one period at a time only reads the period before it
    for i in range(p + 1, n + p + 1, p):
        y[i:i + p] = 0.996 * (a * y[i - p:i] + (1 - a) * y[i - p - 1:i - 1])
    return y[p + 1:p + 1 + n] * env(n, attack=0.001, decay=d, sustain=0.0, release=0.03)


def klaxon(f1, f2, d):
    """Two-tone alert horn: a square-saw blend gliding between the two pitches."""
    n = int(SR * d)
    half = n // 2
    f = np.concatenate([np.full(half, f1), np.full(n - half, f2)])
    f = signal.lfilter([0.02], [1, -0.98], f) / (0.02 / (1 - 0.98))  # short glide
    ph = phase(f, d)
    x = 0.6 * wave_of("square", ph) + 0.4 * wave_of("saw", ph)
    return lowpass(x, 3200) * env(n, attack=0.01, decay=10, sustain=1.0, release=0.03) * 0.55


def kick(d=0.25):
    n = int(SR * d)
    f = 45 + 110 * np.exp(-np.arange(n) / SR / 0.03)
    return np.sin(phase(f, d)) * env(n, attack=0.001, decay=0.12, release=0.02)


def snare(d=0.18):
    n = int(SR * d)
    x = highpass(RNG.uniform(-1, 1, n), 1800) * 0.7 + np.sin(phase(190, d)) * 0.4
    return x * env(n, attack=0.001, decay=0.05, release=0.02)


def sweep(f0, f1, d, kind="saw", cutoff=4000):
    n = int(SR * d)
    f = f0 * (f1 / f0) ** (np.arange(n) / n)
    x = wave_of(kind, phase(f, d))
    return lowpass(x, cutoff) * env(n, attack=d * 0.8, decay=10, sustain=1.0, release=0.04)


# ---------- mixing ----------

class Track:
    def __init__(self, length):
        self.x = np.zeros(int(SR * length))

    def add(self, sig, at, gain=1.0):
        i = int(SR * at)
        if i >= len(self.x):
            return
        j = min(len(self.x), i + len(sig))
        self.x[i:j] += sig[: j - i] * gain


def reverb(x, rt=1.2, mix=0.22):
    n = int(SR * rt)
    ir = RNG.normal(0, 1, n) * np.exp(-np.arange(n) / SR / (rt / 6.9))
    ir = lowpass(ir, 5000)
    wet = signal.fftconvolve(x, ir)[: len(x)]
    wet *= np.max(np.abs(x)) / max(1e-9, np.max(np.abs(wet)))
    return x * (1 - mix) + wet * mix


def master(x, peak=0.89, fade=0.02):
    x = highpass(x, 30)
    x = np.tanh(x / max(1e-9, np.max(np.abs(x))) * 1.4)  # gentle glue
    x = x / np.max(np.abs(x)) * peak
    n = int(SR * fade)
    x[:n] *= np.linspace(0, 1, n)
    x[-n:] *= np.linspace(1, 0, n)
    return x


# ---------- the four units ----------

def unit01():
    """Misato / EVA-01: brass stabs in E minor, MAGI blips, a two-tone klaxon."""
    bpm = 132
    b = 60 / bpm

    ring = Track(9.8)
    for bar in range(5):
        t0 = bar * 4 * b * 1.0 + 0.1
        for k, n in enumerate(("E6", "G6", "B6")):
            ring.add(blip(hz(n)), t0 + k * b / 4, 0.5)
        for beat, chord in ((1, ("E3", "B3", "E4")), (2.5, ("E3", "B3", "G4")), (3, ("D3", "A3", "F#4"))):
            for n in chord:
                ring.add(brass(hz(n), b * 0.45), t0 + beat * b, 0.35)
        motif = ("E4", "G4", "A4", "B4") if bar % 2 == 0 else ("B4", "A4", "G4", "F#4")
        for k, n in enumerate(motif):
            ring.add(brass(hz(n), b * 0.4, 3200), t0 + (k * 0.5 + 1) * b, 0.4)
        ring.add(kick(), t0, 0.7)
        ring.add(kick(), t0 + 2 * b, 0.7)
        if bar % 2 == 1:
            ring.add(klaxon(hz("B4"), hz("E4"), 2 * b), t0 + 2 * b, 0.35)

    notif = Track(1.4)
    for k, n in enumerate(("E6", "G6", "B6")):
        notif.add(blip(hz(n)), 0.02 + k * 0.08, 0.6)
    for n in ("E3", "B3", "E4", "G4"):
        notif.add(brass(hz(n), 0.6), 0.28, 0.3)

    alarm = Track(6.0)
    for k in range(6):
        alarm.add(klaxon(hz("B4"), hz("E4"), 0.8), k * 1.0, 0.6)
        for j, n in enumerate(("E6", "E6", "B6")):
            alarm.add(blip(hz(n)), k * 1.0 + 0.82 + j * 0.05, 0.35)
        alarm.add(kick(), k * 1.0, 0.6)

    charge = Track(2.2)
    charge.add(sweep(110, 880, 1.2, "saw", 2500), 0.0, 0.35)
    for k, n in enumerate(("E5", "B5", "E6")):
        charge.add(blip(hz(n), 0.06), 1.0 + k * 0.06, 0.5)
    for n in ("E2", "E3", "B3", "E4"):
        charge.add(brass(hz(n), 0.9, 1800), 1.2, 0.35)
    charge.add(kick(0.4), 1.2, 0.8)
    return ring, notif, alarm, charge, 1.4


def unit00():
    """Rei / EVA-00: slow FM bells in D lydian over a sine pad."""
    bpm = 76
    b = 60 / bpm

    ring = Track(12.5)
    ring.add(pad(hz("D3"), 12.5), 0, 0.25)
    ring.add(pad(hz("A3"), 12.5), 0, 0.18)
    motif = (("D5", 0), ("A5", 1), ("G#5", 1.5), ("E6", 2), ("C#6", 3))
    for bar in range(4):
        t0 = 0.15 + bar * 4 * b
        for n, beat in motif:
            ring.add(bell(hz(n), 2.2, 3.5, 2.5, 1.0), t0 + beat * b, 0.32)
        ring.add(bell(hz("D4"), 2.8, 1.41, 1.5, 1.4), t0, 0.28)

    notif = Track(1.8)
    notif.add(bell(hz("A5"), 1.6, 3.5, 2.2, 0.8), 0.02, 0.5)
    notif.add(bell(hz("E6"), 1.5, 3.5, 2.2, 0.7), 0.16, 0.42)

    alarm = Track(6.0)
    alarm.add(pad(hz("D3"), 6.0), 0, 0.2)
    for k in range(12):
        n = ("E6", "A5")[k % 2]
        alarm.add(bell(hz(n), 0.8, 3.5, 3.0, 0.35), k * 0.5, 0.45)
        alarm.add(tone(hz("D2"), 0.3, "sine", decay=0.15), k * 0.5, 0.35)

    charge = Track(2.4)
    charge.add(sweep(220, 1320, 1.1, "tri", 6000), 0, 0.25)
    for k, n in enumerate(("D5", "A5", "E6")):
        charge.add(bell(hz(n), 1.3, 3.5, 2.0, 0.7), 1.0 + k * 0.09, 0.35)
    charge.add(tone(hz("D2"), 1.0, "sine", decay=0.5), 1.0, 0.5)
    return ring, notif, alarm, charge, 1.8


def unit02():
    """Asuka / EVA-02: fast square arpeggios in A minor with drums."""
    bpm = 168
    b = 60 / bpm

    ring = Track(11.5)
    arps = (("A4", "C5", "E5", "A5"), ("F4", "A4", "C5", "F5"), ("G4", "B4", "D5", "G5"), ("E4", "G#4", "B4", "E5"))
    t = 0.05
    for bar in range(8):
        arp = arps[bar % 4]
        for k in range(8):
            ring.add(tone(hz(arp[k % 4]), b * 0.45, "square", decay=0.09, sustain=0.2), t + k * b / 2, 0.32)
        ring.add(tone(hz(arp[0]) / 4, b * 3.5, "saw", decay=0.4, sustain=0.4) , t, 0.25)
        for beat in range(4):
            ring.add(kick() if beat % 2 == 0 else snare(), t + beat * b, 0.6 if beat % 2 == 0 else 0.4)
        t += 4 * b

    notif = Track(1.1)
    for k, n in enumerate(("A5", "C6", "E6", "A6")):
        notif.add(tone(hz(n), 0.12, "square", decay=0.06), 0.02 + k * 0.055, 0.5)
    notif.add(snare(), 0.24, 0.35)

    alarm = Track(6.0)
    for k in range(24):
        f = hz("E6") if k % 2 == 0 else hz("A5")
        alarm.add(tone(f, 0.22, "square", decay=0.5, sustain=0.6), k * 0.25, 0.42)
        if k % 2 == 0:
            alarm.add(kick(), k * 0.25, 0.55)

    charge = Track(1.9)
    for k, n in enumerate(("A3", "C4", "E4", "A4", "C5", "E5", "A5")):
        charge.add(tone(hz(n), 0.1, "square", decay=0.05), k * 0.07, 0.4)
    for n in ("A2", "E3", "A3", "C#4"):
        charge.add(brass(hz(n), 0.9, 2600), 0.55, 0.3)
    charge.add(kick(0.35), 0.55, 0.8)
    charge.add(snare(), 0.55, 0.4)
    return ring, notif, alarm, charge, 1.2


def unit08():
    """Mari / Unit-08: swung plucked arpeggios in G major with a bell on top."""
    bpm = 120
    b = 60 / bpm
    swing = 0.16

    ring = Track(12.0)
    chords = (("G3", "B3", "D4", "G4"), ("E3", "G3", "B3", "E4"), ("C3", "E3", "G3", "C4"), ("D3", "F#3", "A3", "D4"))
    t = 0.05
    for bar in range(6):
        ch = chords[bar % 4]
        for k in range(8):
            off = swing * b if k % 2 else 0
            ring.add(pluck(hz(ch[(k * 3) % 4]) * 2, 0.45, 0.6), t + k * b / 2 + off * 0.5, 0.45)
        top = ("D6", "B5", "G5", "A5")[bar % 4]
        ring.add(bell(hz(top), 1.6, 2.0, 1.8, 0.6), t + b * 1.5, 0.25)
        ring.add(kick(), t, 0.45)
        ring.add(kick(), t + 2 * b, 0.45)
        ring.add(snare(0.1), t + b, 0.2)
        ring.add(snare(0.1), t + 3 * b, 0.2)
        t += 4 * b

    notif = Track(1.4)
    notif.add(sweep(hz("G5"), hz("D6"), 0.12, "tri", 7000), 0.01, 0.2)
    for k, n in enumerate(("G5", "B5", "D6")):
        notif.add(pluck(hz(n), 0.5, 0.7), 0.1 + k * 0.07, 0.55)
    notif.add(bell(hz("G6"), 1.0, 2.0, 1.5, 0.5), 0.3, 0.3)

    alarm = Track(6.0)
    t = 0.0
    step = 0.3
    while t < 5.9:
        for n in ("G5", "D6"):
            alarm.add(pluck(hz(n), 0.3, 0.7), t, 0.5)
        alarm.add(kick(), t, 0.4)
        t += step
        step = max(0.14, step * 0.95)

    charge = Track(2.0)
    charge.add(sweep(hz("G3"), hz("G6"), 0.8, "tri", 7000), 0, 0.25)
    for k, n in enumerate(("G4", "B4", "D5", "G5", "B5")):
        charge.add(pluck(hz(n), 0.6, 0.7), 0.65 + k * 0.06, 0.5)
    charge.add(bell(hz("D6"), 1.2, 2.0, 1.8, 0.6), 0.95, 0.35)
    return ring, notif, alarm, charge, 1.0


UNITS = {"unit01": unit01, "unit00": unit00, "unit02": unit02, "unit08": unit08}


def write_ogg(x, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / "s.wav"
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "5", str(path)],
                       check=True)


def main():
    for uid, make in UNITS.items():
        ring, notif, alarm, charge, rt = make()
        write_ogg(master(reverb(ring.x, rt, 0.2)), QUICKSTAR_RAW / f"nerv_ring_{uid}.ogg")
        write_ogg(master(reverb(notif.x, rt * 0.7, 0.18)), QUICKSTAR_RAW / f"nerv_notif_{uid}.ogg")
        write_ogg(master(reverb(alarm.x, rt * 0.5, 0.12), fade=0.005), QUICKSTAR_RAW / f"nerv_alarm_{uid}.ogg")
        write_ogg(master(reverb(charge.x, rt, 0.22)), GLYPH_RAW / f"nerv_charge_{uid}.ogg")
        print(uid, "done")


if __name__ == "__main__":
    main()
