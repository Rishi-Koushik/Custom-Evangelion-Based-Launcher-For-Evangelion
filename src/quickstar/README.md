# Quickstar for Nothing OS

Customise the quick settings panel, tiles and icons on a Nothing phone. Built for and tested on a
Nothing Phone (3), Nothing OS 4.1 (Android 16).

## Install

Phone plugged in over USB with USB debugging on:

```powershell
.\build.ps1 -Install
```

That builds `quickstar.apk` (no Gradle or downloads; it uses the Android SDK in
`%LOCALAPPDATA%\Android\Sdk` plus the JDK), installs it, and grants the two permissions it needs:

```
adb shell pm grant dev.rishi.quickstar android.permission.WRITE_SECURE_SETTINGS
adb shell appops set dev.rishi.quickstar WRITE_SETTINGS allow
```

The grants are lost if you uninstall the app. The app shows a setup card with these commands until they are done.

## What it does

| Tab | What | How |
|---|---|---|
| Control | A custom control centre (Wi-Fi/Bluetooth pills, toggle grid, brightness + volume sliders, media, SIM row, Smart View, Modes). Themes: NERV Unit-01 (purple/green), Unit-00 (blue/amber), Unit-02 (red/orange) with cut-corner shapes, hex-grid backdrop and mono type, plus a Glass theme. Blur, opacity, roundness and toggle choices are adjustable | `ControlA11yService` draws it above the status bar; swipe down from the top corner |
| Panel | Seed colour + theme style (tonal spot, vibrant, ...) for the whole system palette, panel included; font size | writes `theme_customization_overlay_packages`, SystemUI regenerates the palette |
| Layout | Reorder, resize (1x1 / 2x1 / 2x2), add and remove tiles, multiple pages | writes Nothing's `nt_sysui_qs_tiles` and `sysui_qs_tiles` |
| Tiles | Four custom tiles: your label, built-in glyph or your own image as the icon, opens an app, link or settings screen | `TileService` tiles `Tile1`..`Tile4` |
| Launcher | Optional home screen: monochrome themed icons, shapes (circle, squircle, octagon, ...), ADW/Nova icon packs, per-app custom icons | `HomeActivity` + `IconEngine` |

Every change is reversible: the first launch stores your original panel settings and **Restore original** on
the Panel and Layout tabs puts them back. A copy also sits in `backup/` from the day this was built.

## Control centre: what you switch on yourself

The control centre needs an **accessibility service**, because that is the only way an app can draw above the
status bar. Quickstar never enables it for you; the Control tab's **Turn on** button opens Android's
Accessibility settings, and you switch on *Quickstar control centre* there. The service reads no screen
content. If Android says "Restricted setting", open App info for Quickstar, use the menu, and choose
*Allow restricted settings*.

Optional extras, each a separate prompt or settings screen you approve: Wi-Fi name / SIM / Bluetooth
(runtime permissions), now-playing media (notification access, used only to read media sessions), and the
do-not-disturb button (do-not-disturb access).

Wi-Fi, mobile data, airplane mode and Bluetooth buttons open Android's own settings panel instead of toggling:
Android 13+ does not let apps flip those radios. Flashlight, rotation, battery saver, location, sound mode,
brightness and volume work directly.

## NERV units (synced with NERV Home)

Four themes: Unit-01 (purple/green), Unit-00 (blue/amber), Unit-02 (red/orange) and Mari's pink. Pick one by
**holding an empty spot on the NERV Home screen**, or from the unit chips on the Panel tab; either way the other
side follows. A unit restyles the launcher wallpaper, icons, widgets and battery-saver look (NERV Home), the system
panel and notification shade (palette seed), and the control centre. In battery saver the launcher and control
centre go pure black with a single outline colour from the active unit.

The two apps message each other with explicit broadcasts that share the sender's identity; each side accepts a
change only from the other app's uid. Palettes live in `D:/Claude/nerv-os/tools/gen_units.py`, which generates the
Java tables for both apps plus the per-unit icon sets, so they can't drift apart.

## The four units

Misato Katsuragi (purple + green), Unit-00 (light blue + white), Unit-02 (red + orange), Mari (pink + peach). Each has one
look, and that look is extra dark: every unit change also turns on Nothing's extra dark mode (`theme_extra_darkmode=1`),
so the panel, shade and apps are pure black. Colours are defined once in `nerv-os/tools/gen_units.py`.

## NERV sounds

Each unit has its own ringtone, notification and alarm sound in `res/raw` (made by
`D:/Claude/nerv-os/tools/gen_sounds.py`). Applying a unit copies that unit's files into the shared Ringtones /
Notifications / Alarms folders (under `NERV/`, once) and sets them as the defaults, which needs the `WRITE_SETTINGS`
grant. The sounds the phone had first are saved; the "NERV sounds" chip on the Panel tab turns the set off and puts
them back.

## NERV notification banners

`NervNotifListener` (notification access, label "NERV notifications") hands each new pop-up-worthy notification to
`BannerStack`, which the lock-hold accessibility service draws at the top of the screen in the unit's colours. Silent,
ongoing, media, group-summary and Do-Not-Disturb-held notifications are skipped, as Nothing's own pop-up would skip
them. `NervNotifs.sync` turns `heads_up_notifications_enabled` off only while the setting is on and both services are
connected, and restores the saved value otherwise. Nothing is stored or sent.

## Unlock hexagon wave

On unlock, NERV Home plays a wave of hexagons that unfold outward from the fingerprint sensor, on the same hex grid as
the wallpaper and with the old ripple's timing (front speed, head start, 1.7 s). Preview it any time with
`adb shell am start -n dev.nervos.home/.HomeActivity --ez hexwave true`.

## Limits worth knowing

- **Panel colours come from a palette seed, not per-element colours.** Android 16 only lets the system
  create or enable overlays on SystemUI (no shell, no root), so tile background/label colours cannot be set
  one by one. Tile backgrounds come from a low-chroma neutral palette, so even Vibrant tints them gently;
  accents and icons carry the stronger colour. Per-element colours would need root.
- **The tile grid is applied by nudging font size.** SystemUI re-reads the grid when it re-inflates, which a
  tiny font-scale change triggers. The screen flickers for a second and your launcher may redraw.
- The app targets SDK 33 on purpose: Android 14+ only lets apps at or below that read non-public settings
  such as `sysui_qs_tiles`.
- The launcher is opt-in. Nothing sets it as default; use **Set as default** on the Launcher tab.
