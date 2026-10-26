"""Generate the NERV icon pack: vector drawables, appfilter.xml, drawable.xml and an HTML preview sheet.

Usage:
    python gen_icons.py                       # seed component list only
    python gen_icons.py phone_apps.txt        # also map components dumped from the phone:
        adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER > phone_apps.txt
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"

# EVA-01 palette: (gradient start, gradient end, glyph colour)
TONES = {
    "p": ("#8B5CD6", "#3A1F66", "#A3FF3A"),
    "g": ("#A3FF3A", "#517F1D", "#0B0612"),
    "o": ("#FF9A3A", "#C24200", "#170900"),
    "d": ("#2A1F37", "#0D0912", "#A3FF3A"),
}

# Glyphs on a 24x24 grid, stroke only (same set as the browser preview).
def circle(cx, cy, r):
    return f"M{cx - r},{cy}a{r},{r} 0 1,0 {2 * r},0a{r},{r} 0 1,0 {-2 * r},0"

def rect(x, y, w, h):
    return f"M{x},{y}h{w}v{h}h{-w}z"

GLYPHS = {
    "phone": ("g", ["M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z"]),
    "messages": ("p", ["M4 5h16v11H9l-5 4z", "M8 9h8M8 12h5"]),
    "contacts": ("d", [circle(12, 9, 3.5), "M5 20a7 7 0 0 1 14 0"]),
    "browser": ("d", [circle(12, 12, 8), "M4 12h16M12 4a12 12 0 0 1 0 16M12 4a12 12 0 0 0 0 16"]),
    "camera": ("o", ["M4 8h3l2-3h6l2 3h3v11H4z", circle(12, 13, 3.5)]),
    "gallery": ("p", [rect(4, 5, 16, 14), "M4 16l5-5 4 4 3-3 4 4", circle(15.5, 9, 1.5)]),
    "music": ("d", ["M9 18V6l10-2v12", circle(7, 18, 2), circle(17, 16, 2)]),
    "maps": ("p", ["M9 4L3 6v14l6-2 6 2 6-2V4l-6 2z", "M9 4v14M15 6v14"]),
    "clock": ("d", [circle(12, 12, 8), "M12 7v5l3 2"]),
    "mail": ("d", [rect(4, 6, 16, 12), "M4 6l8 7 8-7"]),
    "calendar": ("p", [rect(4, 5, 16, 15), "M4 10h16M9 3v4M15 3v4M8 14h2M14 14h2M8 17h2"]),
    "notes": ("d", ["M6 3h9l3 3v15H6z", "M9 9h6M9 13h6M9 17h4"]),
    "files": ("p", ["M4 6h6l2 2h8v11H4z"]),
    "calculator": ("d", [rect(6, 3, 12, 18), "M9 7h6M9 12h1M14 12h1M9 16h1M14 16h1"]),
    "settings": ("d", ["M12 3l7.8 4.5v9L12 21l-7.8-4.5v-9z", circle(12, 12, 3)]),
    "store": ("g", ["M5 8h14l-1 12H6z", "M9 8V6a3 3 0 0 1 6 0v2", "M10.5 12.5v4l3.5-2z"]),
    "video": ("o", [rect(3, 6, 18, 12), "M10 9.5v5l4.5-2.5z"]),
    "weather": ("p", [circle(9, 9, 3), "M9 3v1.5M3 9h1.5M4.8 4.8l1 1", "M8 19h9a3.5 3.5 0 0 0 0-7 5 5 0 0 0-9.5 2A2.5 2.5 0 0 0 8 19z"]),
    "recorder": ("o", [rect(9, 3, 6, 11), "M6 11a6 6 0 0 0 12 0M12 17v4M9 21h6"]),
    "compass": ("d", [circle(12, 12, 8), "M15 9l-2 4-4 2 2-4z"]),
    "chat": ("g", ["M4 20l1.5-4A8 8 0 1 1 9 19z", "M9 9.5c.5 2 2.5 4 4.5 4.5l1-1.5 2 1"]),
    "social": ("p", [rect(4, 4, 16, 16), circle(12, 12, 3.5), "M16.5 7.5h.01"]),
    "send": ("d", ["M21 3L3 10.5l7 2.5 2.5 7z", "M10 13l11-10"]),
    "spotify": ("g", [circle(12, 12, 8), "M7.5 9.5c3-1 6.5-.7 9 .8M8 12.5c2.5-.7 5.3-.4 7.3.7M8.5 15.3c2-.5 4-.3 5.6.5"]),
    "shop": ("o", ["M4 7h16l-1.5 9h-13z", "M8 20h.01M16 20h.01", "M4 7L3 4H1"]),
    "pay": ("p", [rect(3, 6, 18, 12), "M3 10h18M7 15h3"]),
    "health": ("o", ["M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.5A4 4 0 0 1 19 10c0 5.5-7 10-7 10z", "M8 12h2l1-2 2 4 1-2h2"]),
    "magi": ("o", ["M12 3l4 7H8z", "M6 13l4 7H2zM18 13l4 7h-8z"]),
    "terminal": ("g", ["M5 7l4 4-4 4M11 17h8"]),
    "assistant": ("d", [circle(8, 12, 2), circle(15, 9, 2.5), circle(16, 16, 1.5)]),
    "home": ("p", ["M4 11l8-7 8 7v9H4z", "M10 20v-5h4v5"]),
    "videocall": ("g", [rect(3, 7, 12, 10), "M15 11l6-3v8l-6-3"]),
    "safety": ("o", ["M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z", "M9 12l2 2 4-4"]),
    "cloud": ("p", ["M7 18h10a4 4 0 0 0 0-8 6 6 0 0 0-11.5 1.5A3.3 3.3 0 0 0 7 18z"]),
}

# Seed component list: Google apps + realme UI 5 stock apps. Anything the phone dump adds is
# matched by package keyword below; apps that match nothing get the auto octagon frame.
SEED = {
    "com.android.chrome/com.google.android.apps.chrome.Main": "browser",
    "com.google.android.gm/com.google.android.gm.ConversationListActivityGmail": "mail",
    "com.google.android.youtube/com.google.android.youtube.app.honeycomb.Shell$HomeActivity": "video",
    "com.google.android.apps.maps/com.google.android.maps.MapsActivity": "maps",
    "com.android.vending/com.android.vending.AssetBrowserActivity": "store",
    "com.google.android.apps.photos/com.google.android.apps.photos.home.HomeActivity": "gallery",
    "com.google.android.dialer/com.google.android.dialer.extensions.GoogleDialtactsActivity": "phone",
    "com.google.android.apps.messaging/com.google.android.apps.messaging.ui.ConversationListActivity": "messages",
    "com.google.android.contacts/com.android.contacts.activities.PeopleActivity": "contacts",
    "com.google.android.calendar/com.android.calendar.AllInOneCalendarActivity": "calendar",
    "com.google.android.apps.nbu.files/com.google.android.apps.nbu.files.home.HomeActivity": "files",
    "com.google.android.deskclock/com.android.deskclock.DeskClock": "clock",
    "com.google.android.calculator/com.android.calculator2.Calculator": "calculator",
    "com.google.android.apps.youtube.music/com.google.android.apps.youtube.music.activities.MusicActivity": "music",
    "com.google.android.apps.nbu.paisa.user/com.google.nbu.paisa.flutter.gpay.app.LauncherActivity": "pay",
    "com.google.android.keep/com.google.android.keep.activities.BrowseActivity": "notes",
    "com.google.android.googlequicksearchbox/com.google.android.googlequicksearchbox.SearchActivity": "assistant",
    "com.android.settings/com.android.settings.Settings": "settings",
    "com.oplus.camera/com.oplus.camera.Camera": "camera",
    "com.coloros.alarmclock/com.coloros.alarmclock.AlarmClock": "clock",
    "com.coloros.calculator/com.android.calculator2.Calculator": "calculator",
    "com.coloros.weather2/com.oplus.weather.main.view.WeatherMainActivity": "weather",
    "com.coloros.soundrecorder/oplus.multimedia.soundrecorder.slidebar.TransParentActivity": "recorder",
    "com.whatsapp/com.whatsapp.Main": "chat",
    "com.instagram.android/com.instagram.mainactivity.LauncherActivity": "social",
    "org.telegram.messenger/org.telegram.ui.LaunchActivity": "send",
    "com.spotify.music/com.spotify.music.MainActivity": "spotify",
    "in.amazon.mShop.android.shopping/com.amazon.mShop.navigation.MainActivity": "shop",
}

# For components dumped from the phone: first keyword found in the package/activity wins.
KEYWORDS = [
    # Nothing Phone (3) / Google apps that would otherwise hit a wrong generic keyword come first
    ("chromecast", "home"), ("tachyon", "videocall"), ("safetyhub", "safety"), ("subscriptions.red", "cloud"),
    ("apps.adm/", "compass"), ("apps.bard/", "assistant"), ("apps.docs/", "files"), ("fitbit", "health"),
    ("ntessentialspace", "notes"), ("smartcenter", "magi"), ("android.videos/", "video"),
    ("dialer", "phone"), ("incallui", "phone"), ("messaging", "messages"), ("mms", "messages"), ("contacts", "contacts"),
    ("chrome", "browser"), ("browser", "browser"), ("camera", "camera"), ("gallery", "gallery"), ("photos", "gallery"),
    ("music", "music"), ("maps", "maps"), ("alarmclock", "clock"), ("deskclock", "clock"), ("gm/", "mail"), ("email", "mail"),
    ("calendar", "calendar"), ("notes", "notes"), ("keep", "notes"), ("filemanager", "files"), ("files", "files"),
    ("calculator", "calculator"), ("settings", "settings"), ("vending", "store"), ("youtube", "video"), ("weather", "weather"),
    ("recorder", "recorder"), ("compass", "compass"), ("whatsapp", "chat"), ("instagram", "social"), ("telegram", "send"),
    ("spotify", "spotify"), ("amazon", "shop"), ("paisa", "pay"), ("health", "health"), ("googlequicksearchbox", "assistant"),
]


def vector(name, tone_key):
    c1, c2, ink = TONES[tone_key]
    tile = "M12.5,2H35.5L46,12.5V35.5L35.5,46H12.5L2,35.5V12.5Z"
    strokes = "\n".join(
        f'        <path android:pathData="{d}" android:strokeColor="{ink}" android:strokeWidth="1.8" '
        f'android:strokeLineCap="round" android:strokeLineJoin="round" />'
        for d in GLYPHS[name][1]
    )
    return f"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt"
    android:width="48dp" android:height="48dp" android:viewportWidth="48" android:viewportHeight="48">
    <path android:pathData="{tile}">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="14" android:startY="2" android:endX="34" android:endY="46">
                <item android:offset="0" android:color="{c1}" />
                <item android:offset="1" android:color="{c2}" />
            </gradient>
        </aapt:attr>
    </path>
    <group android:translateX="12" android:translateY="12">
{strokes}
    </group>
</vector>
"""


def line_vector(name, colour="#9D4DFF"):
    """Battery-saver variant: black octagon, thin purple outline and glyph."""
    tile = "M12.5,2H35.5L46,12.5V35.5L35.5,46H12.5L2,35.5V12.5Z"
    strokes = "\n".join(
        f'        <path android:pathData="{d}" android:strokeColor="{colour}" android:strokeWidth="1.8" '
        f'android:strokeLineCap="round" android:strokeLineJoin="round" />'
        for d in GLYPHS[name][1]
    )
    return f"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp" android:height="48dp" android:viewportWidth="48" android:viewportHeight="48">
    <path android:pathData="{tile}" android:fillColor="#FF000000" android:strokeColor="{colour}" android:strokeWidth="1.2" />
    <group android:translateX="12" android:translateY="12">
{strokes}
    </group>
</vector>
"""


def svg(name):
    tone_key = GLYPHS[name][0]
    c1, c2, ink = TONES[tone_key]
    paths = "".join(f'<path d="{d}"/>' for d in GLYPHS[name][1])
    return (f'<svg viewBox="0 0 48 48" width="96" height="96"><defs><linearGradient id="g{name}" x1="14" y1="2" x2="34" y2="46" gradientUnits="userSpaceOnUse">'
            f'<stop offset="0" stop-color="{c1}"/><stop offset="1" stop-color="{c2}"/></linearGradient></defs>'
            f'<path d="M12.5,2H35.5L46,12.5V35.5L35.5,46H12.5L2,35.5V12.5Z" fill="url(#g{name})"/>'
            f'<g transform="translate(12 12)" fill="none" stroke="{ink}" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">{paths}</g></svg>')


def parse_dump(path):
    comps = []
    for line in Path(path).read_text(encoding="utf-8", errors="ignore").splitlines():
        m = re.search(r"([\w.]+)/([\w.$]+)", line)
        if m:
            pkg, act = m.groups()
            comps.append(f"{pkg}/{pkg}{act}" if act.startswith(".") else f"{pkg}/{act}")
    return comps


def main():
    mapping = dict(SEED)
    unmatched = []
    if len(sys.argv) > 1:
        for comp in parse_dump(sys.argv[1]):
            if comp in mapping:
                continue
            low = comp.lower()
            hit = next((g for kw, g in KEYWORDS if kw in low), None)
            if hit:
                mapping[comp] = hit
            else:
                unmatched.append(comp)

    drawable = RES / "drawable"
    xml = RES / "xml"
    drawable.mkdir(parents=True, exist_ok=True)
    xml.mkdir(parents=True, exist_ok=True)

    for name, (tone, _) in GLYPHS.items():
        (drawable / f"nerv_{name}.xml").write_text(vector(name, tone), encoding="utf-8")
    # auto-frame for apps without a NERV icon: launcher draws their icon on top of this tile, clipped to the mask
    (drawable / "nerv_iconback.xml").write_text(vector("settings", "d").replace(
        GLYPHS["settings"][1][0], "M0,0").replace(GLYPHS["settings"][1][1], "M0,0"), encoding="utf-8")
    (drawable / "nerv_iconmask.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="48dp" android:height="48dp"
    android:viewportWidth="48" android:viewportHeight="48">
    <path android:fillColor="#FFFFFFFF" android:pathData="M12.5,2H35.5L46,12.5V35.5L35.5,46H12.5L2,35.5V12.5Z" />
</vector>
""", encoding="utf-8")

    items = "\n".join(f'    <item component="ComponentInfo{{{c}}}" drawable="nerv_{g}" />' for c, g in sorted(mapping.items()))
    (xml / "appfilter.xml").write_text(f"""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <iconback img1="nerv_iconback" />
    <iconmask img1="nerv_iconmask" />
    <scale factor="0.62" />
{items}
</resources>
""", encoding="utf-8")
    entries = "\n".join(f'    <item drawable="nerv_{n}" />' for n in GLYPHS)
    (xml / "drawable.xml").write_text(f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <version>1</version>\n    <category title="NERV" />\n{entries}\n</resources>\n', encoding="utf-8")

    cells = "".join(f'<figure>{svg(n)}<figcaption>{n}</figcaption></figure>' for n in GLYPHS)
    (ROOT / "icons-preview.html").write_text(f"""<!doctype html><meta charset="utf-8"><title>NERV Icons</title>
<style>body{{background:#0b0712;color:#ddd;font:600 13px 'Barlow Condensed',sans-serif;letter-spacing:.08em;text-transform:uppercase;padding:24px}}
main{{display:grid;grid-template-columns:repeat(auto-fill,minmax(110px,1fr));gap:18px}}figure{{text-align:center}}figcaption{{margin-top:6px}}</style>
<h1 style="font:900 32px serif;margin-bottom:20px">NERV ICONS · {len(GLYPHS)} glyphs · {len(mapping)} apps mapped</h1><main>{cells}</main>""", encoding="utf-8")

    # NERV Home launcher gets the same tiles + mapping, plus the battery-saver line set
    launcher_res = ROOT.parent / "launcher" / "app" / "src" / "main" / "res"
    if launcher_res.parent.exists():
        (launcher_res / "drawable").mkdir(parents=True, exist_ok=True)
        (launcher_res / "xml").mkdir(parents=True, exist_ok=True)
        for name, (tone, _) in GLYPHS.items():
            (launcher_res / "drawable" / f"nerv_{name}.xml").write_text(vector(name, tone), encoding="utf-8")
            (launcher_res / "drawable" / f"nerv_line_{name}.xml").write_text(line_vector(name), encoding="utf-8")
        (launcher_res / "xml" / "appfilter.xml").write_text((xml / "appfilter.xml").read_text(encoding="utf-8"), encoding="utf-8")
        print(f"launcher: {2 * len(GLYPHS)} drawables written")

    print(f"{len(GLYPHS)} icons, {len(mapping)} components mapped, {len(unmatched)} unmatched (will get the auto octagon frame)")
    for c in unmatched:
        print("  unmatched:", c)


if __name__ == "__main__":
    main()
