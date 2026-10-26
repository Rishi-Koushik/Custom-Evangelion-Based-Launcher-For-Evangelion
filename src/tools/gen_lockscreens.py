"""Builds the per-unit lock-screen posters NERV Home sets on the lock screen (the home screen keeps the skyline).

Sources (from you) in tools/lockscreens_src:  misato.webp (EVA-01), rei.webp (EVA-00), asuka.webp (EVA-02), mari.webp
(EVA-08). Misato's and Mari's carry Gemini's semi-transparent white sparkle near the bottom right; it is undone here by
reversing its alpha blend (outline measured from the image, opacity fitted per image), then the thin leftover edge is
repaired from its surroundings, except across the railing bar.

Each poster is cropped to the phone's 9:20 shape (keeping the pilot and the kanji column) and scaled to 1260 x 2800.
Writes launcher/app/src/main/res/drawable-nodpi/lock_<unit>.webp.   Run from anywhere:  python gen_lockscreens.py
"""
from pathlib import Path

import cv2
import numpy as np
from PIL import Image
from scipy.optimize import minimize_scalar

HERE = Path(__file__).resolve().parent
SRC = HERE / "lockscreens_src"
OUT = HERE.parent / "launcher" / "app" / "src" / "main" / "res" / "drawable-nodpi"
W, H = 1260, 2800

# unit -> (source, remove Gemini mark, left edge of the 9:20 crop as a fraction of the spare width)
POSTERS = {
    "unit01": ("misato.webp", True, 0.5),
    "unit00": ("rei.webp", False, 0.5),
    "unit02": ("asuka.webp", False, 0.05),  # keep the kanji column on the left
    "unit08": ("mari.webp", True, 0.5),
}

# the sparkle in the 800 x 1600 sources: a concave 4-point star |dx/R|^P + |dy/R|^P <= 1
CX, CY, R, P = 667.0, 1465.5, 28.5, 0.62


def unmark(img):
    h, w = img.shape[:2]
    y, x = np.mgrid[0:h, 0:w].astype(float)
    f = (np.abs(x - CX) / R + 1e-9) ** P + (np.abs(y - CY) / R + 1e-9) ** P
    m = np.clip((1 - f) * R * 0.55 + 0.5, 0, 1)
    lum = img.mean(axis=2)
    band = (y > 1471) & (y < 1492)  # the lower lobe sits on plain grey wall: fit the opacity there
    inner = (m > 0.99) & (lum > 150) & (lum < 235) & band
    ring = (m == 0) & (np.abs(x - CX) < 40) & (np.abs(y - CY) < 40) & (lum > 135) & (lum < 215) & band

    def undo(a):
        al = (a * m)[..., None]
        return (img - 255 * al) / (1 - al)

    a = minimize_scalar(lambda a: (np.median(undo(a).mean(axis=2)[inner]) - np.median(undo(a).mean(axis=2)[ring])) ** 2,
                        bounds=(0.05, 0.8), method="bounded").x
    out = np.clip(undo(a), 0, 255).round().astype(np.uint8)
    edge = ((f > 0.80) & (f < 1.22) & ((y < 1455) | (y > 1472))).astype(np.uint8) * 255
    print(f"  sparkle opacity {a:.3f}")
    return cv2.inpaint(out, edge, 2, cv2.INPAINT_TELEA)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for unit, (name, mark, bias) in POSTERS.items():
        print(unit, name)
        img = np.asarray(Image.open(SRC / name).convert("RGB")).astype(float)
        if mark:
            img = unmark(img)
        im = Image.fromarray(np.asarray(img, dtype=np.uint8))
        cw = round(im.height * W / H)
        x0 = round((im.width - cw) * bias)
        im = im.crop((x0, 0, x0 + cw, im.height)).resize((W, H), Image.LANCZOS)
        im.save(OUT / f"lock_{unit}.webp", quality=90, method=6)
        print(f"  -> lock_{unit}.webp {(OUT / f'lock_{unit}.webp').stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
