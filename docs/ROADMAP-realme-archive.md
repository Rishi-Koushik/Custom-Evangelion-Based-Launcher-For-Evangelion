# NERV OS — realme 12 Pro+ (RMX3840 / RMX3841)

`preview/index.html` is the design target. Nothing below touches the phone until you decide to.

## Why not a full custom ROM (for now)
- No official or maintained LineageOS / crDroid build exists for RMX3840.
- realme only allows bootloader unlock on the **Chinese** firmware. The known XDA method flashes the
  China ROM via region change; non-Indian units lose mobile signal until flashed back. It wipes the phone.
- On a generic GSI, the **in-display fingerprint** is often broken or flaky, and Play Store / banking apps
  fail Play Integrity on an unlocked bootloader.

Your must-haves (Play Store, fingerprint, every Android app) all work by keeping realme UI underneath
and replacing the layer you see. No unlock, no wipe, uninstall any time.

## Plan
| Piece | What it is | Status |
|---|---|---|
| NERV Icons | Icon pack APK: octagon icons for all basic apps (Google + realme stock + common apps), every other app auto-framed in the same octagon | Source + generator done (`iconpack/`) |
| Launcher | **Lawnchair** (open source, Launcher3-based → native Android spring animations), set up with NERV Icons, dark drawer, folders | Install + config |
| MAGI widget | Small widget app: CPU / memory / battery in the MAGI style | Next |
| Depth wallpaper | Live wallpaper: your image + on-device subject cutout, clock drawn between them on the home screen | Next |
| Battery-saver look | Live wallpaper listens for Android's power-save mode (`PowerManager.ACTION_POWER_SAVE_MODE_CHANGED`) and draws pure `#000` — AMOLED pixels fully off. Red/black NERV icons + UI need our own launcher fork (Lawnchair can't swap icon packs by itself) | Wallpaper: next · red UI: with launcher fork |
| Lock screen / charging / boot | Owned by realme UI; can't be replaced without unlock. Use realme's own lock-screen clock styles + wallpaper | Not possible safely |

## Battery saver mode (designed in the preview)
- Turns on automatically at **15% or lower** while unplugged; plugging in turns an auto-enabled saver off.
  On the phone set this in realme's battery settings (power saving → turn on automatically at 15%); our wallpaper/launcher follow that system switch.
- Wallpaper → pure black (no image decoded or drawn). UI → red `#FF2020` on black, sharp edges,
  no blur, glow, gradients or looping animations (blur and glow are the GPU-heavy parts).

## Battery health / efficiency rules for everything we ship
- No background services, no wakelocks, no polling. React to system broadcasts only (power-save, battery, screen on/off).
- Icon pack is static vector resources: zero runtime cost.
- Live wallpaper redraws only on visibility change / minute tick, never in a loop; nothing drawn while the screen is off.
- Dark-first UI on the AMOLED panel; pure black in saver.
- Phone settings worth enabling: realme's optimized/smart charging (stops at 80% overnight), avoid gaming while
  fast-charging (heat is what wears the battery most), keep 20–80% when you can.

Fingerprint unlock stays exactly as it is (realme lock screen).

## Building the icon pack
Needs the Android SDK (Android Studio, or command-line tools ~1 GB) + JDK 17+ (JDK 21 installed).
```
python tools/gen_icons.py phone_apps.txt   # optional: map the exact apps on your phone
gradle :app:assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
```
Get `phone_apps.txt` with USB debugging on:
```
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER > phone_apps.txt
```

## Unit themes (v1.0)
Hold the home screen to switch between Unit-01, Unit-00, Unit-02 and Mari's pink Eva. Everything follows the unit:
skyline wallpaper (home + lock), NERV icons (`nerv_*_u00/_u02/_u08`), MAGI widgets, drawer/folder surfaces, and the
battery-saver look (single outline colour per unit). Quickstar is told over a sender-checked broadcast and restyles the
system panel, shade and its control centre. Palettes are defined once in `tools/gen_units.py`; rerun it after editing.
The pre-theme source is kept in `launcher_src_backup_0.9/` and the old build is `NERV-Home-0.9.apk`.
