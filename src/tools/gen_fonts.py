"""Builds the three NERV fonts that ship inside the apps, so an OS update can't change or break them.

Nothing OS 5 / Android 17 broke the system "sans-serif-condensed" family at bold weight (Latin text fell back to a
circled-letter font), which is what NERV Home's labels and buttons used. Since then every NERV app carries its own copy:

  nerv_cond.ttf   Roboto at width 75, weight 700 (a fixed condensed bold cut from the variable font)  - SIL OFL 1.1
  nerv_serif.ttf  Noto Serif Bold                                                                   - SIL OFL 1.1
  nerv_mono.ttf   Droid Sans Mono                                                                   - Apache 2.0

Sources are the phone's own /system/fonts files, pulled into tools/fonts_src (adb pull). Each is trimmed to Latin
and punctuation; Japanese text falls back to the system's CJK fonts as before.

Writes res/font/ in NERV Home, NERV Glyph and Quickstar.  Run from anywhere:  python gen_fonts.py
"""
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

HERE = Path(__file__).resolve().parent
SRC = HERE / "fonts_src"
NERV = HERE.parent
OUT_DIRS = [
    NERV / "launcher" / "app" / "src" / "main" / "res" / "font",
    NERV / "glyph" / "app" / "src" / "main" / "res" / "font",
    Path(r"D:\Claude\quickstar\res\font"),
]
# Basic Latin, Latin-1, Latin Extended-A, general punctuation, currency, arrows, a few symbols the UI uses (· • ✓ ⚠ ›)
UNICODES = "U+0020-007E,U+00A0-017F,U+2000-206F,U+20A0-20BF,U+2190-21FF,U+2022,U+2026,U+2122,U+2713,U+26A0,U+25A0-25FF"


def trim(font):
    opts = subset.Options()
    opts.layout_features = ["*"]
    opts.name_IDs = ["*"]
    opts.name_legacy = True
    opts.notdef_outline = True
    opts.glyph_names = False
    s = subset.Subsetter(opts)
    s.populate(unicodes=subset.parse_unicodes(UNICODES))
    s.subset(font)
    return font


def build():
    cond = TTFont(SRC / "Roboto-Regular.ttf")
    cond = instancer.instantiateVariableFont(cond, {"wght": 700, "wdth": 75, "ital": 0}, updateFontNames=False)
    fonts = {
        "nerv_cond.ttf": trim(cond),
        "nerv_serif.ttf": trim(TTFont(SRC / "NotoSerif-Bold.ttf")),
        "nerv_mono.ttf": trim(TTFont(SRC / "DroidSansMono.ttf")),
    }
    for d in OUT_DIRS:
        d.mkdir(parents=True, exist_ok=True)
        for name, f in fonts.items():
            f.save(d / name)
    for name in fonts:
        print(name, (OUT_DIRS[0] / name).stat().st_size, "bytes")


if __name__ == "__main__":
    build()
