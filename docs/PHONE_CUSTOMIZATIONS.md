# Phone customizations: Nothing Phone (3) (A024, Nothing OS 5.0, Android 17)

Everything here was done without root and can be undone. The source for the apps is in `D:\Claude\nerv-os`
(NERV Home, NERV Icons, NERV Glyph, debloat lists), `D:\Claude\quickstar` (Quickstar) and `D:\Claude\amadeus` (Amadeus).
The phone was connected over USB for installs and one-time permission grants.

## 1. The NERV unit themes (the main customization)

Four themes, chosen by holding the home screen or holding the lock screen:

| Unit | Colours |
|---|---|
| Misato Katsuragi (Unit-01) | purple + neon green |
| Unit-00 (Rei) | light blue + white |
| Unit-02 (Asuka) | red + orange |
| Mari | pink + peach |

One pick restyles: the home and lock wallpaper, the app icons, the home widgets, the battery-saver look, and the system
palette seed (a faint tint on Nothing's panel tiles). The palettes are defined once in
`nerv-os/tools/gen_units.py`, which generates the code for both the launcher and Quickstar so they cannot drift apart.
Each unit has one look, and since 23 Oct 2026 that look is extra dark for all four: pure-black home surfaces, a darker
skyline, and Nothing's own extra dark mode (pure-black panel and shade).

## 2. NERV Home (launcher, `dev.nervos.home`, v1.6)

Your default home app. Evangelion-styled home screen:
- Tokyo-3 skyline wallpaper drawn per unit, with a depth clock sitting behind the towers.
- Lock screen: each unit sets its own poster (Misato with EVA-01, Rei with EVA-00, Asuka with EVA-02, Mari with
  EVA-08) while the home screen keeps the skyline. Built by `nerv-os/tools/gen_lockscreens.py` from the images in
  `tools/lockscreens_src` (Gemini's sparkle removed from Misato's and Mari's, all cropped to the phone's shape).
  The poster is set with a crop hint; without one Nothing OS zooms lock wallpapers in about 2x.
- Date, steps and "sync ratio" (battery) widgets, plus three MAGI boxes (memory, storage, battery) with 承認 / 否決 verdicts.
- NERV octagon icons, a dock, a "More" folder, and a swipe-up searchable app drawer.
- Battery saver look: pure black with thin outlines in the active unit's colour.
- Hold the home screen (or the lock screen): swipe through the four units, each previewed full screen as its lock
  screen (poster plus the lock clock in the unit's colour), and Apply. The CLOCK row picks the lock-screen clock face:
  Serif (Nothing's default), Terminal (Micrographics), Dot (Ndot) or Dial (Graphic). The face is saved on Apply and
  kept through every unit change until you pick another; Quickstar writes it with the unit's colour.
- Unlock effect: a wave of hexagons unfolds from the fingerprint sensor on the wallpaper's own hex grid (about 1.7 s).
  Preview it with `adb shell am start -n dev.nervos.home/.HomeActivity --ez hexwave true`.
- A quick-settings tile "NERV theme" opens the same picker, even over the lock screen.
- Episode title card: the first unlock of each day opens on a show-style card ("EPISODE:23 · 第弐拾参話 · FRIDAY"),
  then the hex wave. Preview: `adb shell am start -n dev.nervos.home/.HomeActivity --ez titlecard true`.
- Umbilical cable: unplugging slides down "UMBILICAL CABLE DISCONNECTED", plugging in "EXTERNAL POWER CONNECTED"
  (preview with `--ez umbilical false` / `true`).
- Internal power: in battery saver the home screen shows an Eva-style "activity limit" readout: time left on battery,
  worked out from how fast the level falls since unplugging (shows the level while it is still calculating).
- City defence: when battery saver turns on, Tokyo-3's towers sink into the Geofront before the saver look takes over,
  and rise back when it turns off (preview with `--ez cityshift true`).
- App drawer search is a MAGI console: `MAGI>` prompt, block cursor, and a 承認 / 否決 verdict on each query.
- Haptics per unit for the unlock wave and for Apply (follows the phone's touch-feedback setting).
- Four NERV live wallpapers are installed but Nothing's customiser does not list them, so they are unused.
- The pre-theme build is kept as `NERV-Home-0.9.apk`, source in `launcher_src_backup_0.9`.

## 3. NERV Icons (`dev.nervos.icons`, v0.2)

An icon pack of octagon icons for common apps (Google, Nothing, popular apps); any other app is framed in the same
octagon. Used by NERV Home. The per-unit colour variants (`_u00`, `_u02`, `_u08`) are generated into the launcher.

## 4. Quickstar (`dev.rishi.quickstar`)

Your own settings app, built without Gradle (`build.ps1`). Needs two one-time USB grants
(`WRITE_SECURE_SETTINGS`, `WRITE_SETTINGS`). Tabs:
- **Control:** lock-screen hold switch, and a NERV control-centre panel (built in all four unit styles) that is switched
  OFF, because you wanted Nothing's own pull-down.
- **Panel:** the unit chips, a colour seed and theme style for the system palette, font size, and Restore original.
- **Layout:** edits Nothing's quick-settings tile grid (order, size 1x1 / 2x1 / 2x2, add, remove, pages).
- **Tiles:** four custom quick-settings tiles (own label, icon, and what they open).
- **Launcher:** a small separate launcher with icon packs and shapes (not your default).

How the panel colour works: Android 16 only lets an app set one seed colour for the system palette, so Nothing's panel
shows a soft tint. Each unit uses a seed picked to read as its colours: purple, sky blue, red-orange, pink-peach.

### NERV sounds (Quickstar)
Each unit has its own ringtone, notification and alarm sound (original, synthesised by `nerv-os/tools/gen_sounds.py`).
Picking a unit makes its three the phone's defaults; the files sit in Ringtones/, Notifications/ and Alarms/ under
`NERV/`. Quickstar's Panel tab has a "NERV sounds" chip: off puts back the sounds you had before. Nothing's own
"Charging sounds and vibration" setting is separate, so turn it off if you only want the NERV one.

### NERV notifications (Quickstar)
Pop-up notifications arrive as NERV banners like the umbilical-cable one: black glass, a frame and type in the active
unit's colour, "受信 · TRANSMISSION · APP" on top (calls: "着信 · PATTERN BLUE · INCOMING CALL", alarms:
"警報 · EMERGENCY · ALARM"), the app's own buttons underneath. Tap opens it, swipe up dismisses; ordinary ones leave
after 5 s, calls and alarms stay until handled. On the lock screen private content shows as "CONTENT CLASSIFIED".
Switch it on with the "NERV notifications" chip on Quickstar's Panel tab; it needs notification access for
"NERV notifications" and the NERV lock-screen hold service (which draws the banners). While both run, Nothing's own
pop-ups (`heads_up_notifications_enabled`) are off; they come back by themselves if either stops or the chip is off.

## 5. Accessibility services (switched on by you)

- **NERV lock-screen hold** (Quickstar): opens the theme picker when you hold the lock screen (it spots Nothing's
  "Customise lock screen" button) and reads no other screen content. The frosted colour layer it used to draw over
  Nothing's panel was removed on 23 Oct 2026.
- **Amadeus Essential Key** (`dev.amadeus`): makes the Essential Key summon Amadeus (section 6).
- The Quickstar control-centre service exists but is OFF.

## 6. Amadeus (`dev.amadeus`)

A voice and chat assistant in the Amadeus (Steins;Gate) style. Source in `D:\Claude\amadeus`: an orb view, emotions
(face images), memory and a persona, a Gemini-backed brain, and a settings screen. Its key service claims the Nothing
Essential Key; each press summons her, or dismisses her if she is open. It uses the internet and "display over other apps".

## 7. NERV Glyph (`dev.nervos.glyph`, v0.6)

Glyph Matrix toys for the Phone (3) back panel:
- **A.T. Field:** octagon rings expand from the centre and fade; long press holds a steady layered field.
- **MAGI:** Melchior, Balthasar and Casper take turns "thinking"; long press calls the vote.
- **Sync Ratio:** battery as a ring gauge (long press shows the time); in battery saver a blinking octagon emergency frame.
- **Activity Limit:** five minutes of internal power counting down; long press starts, pauses, refills.
- **Pattern Blue:** the Angel alert, a flashing frame with the pattern name scrolling; long press for Pattern Orange.
- **Charging:** a foreground service opens the A.T. Field charging screen when a charger is plugged in, in the active
  unit's colours, with that unit's power-up sound and haptic (NERV Home tells it the unit). It uses no CPU
  until then and needs "Display over other apps".

## 8. Debloat

Optional apps removed for the main user only (system copies stay, so each is restorable): Google extras (YouTube Music,
Videos, Meet, Docs, Gemini/Bard, Keep, Safety Hub), Fitbit, Google Home, Google One, and several Nothing extras.
Two apps refused uninstall (`ntgamedashboard`, `ntessentialrecorder`) and are disabled instead.
Files: `nerv-os/debloat/nothing-phone-3.txt`, `debloat.sh`, `restore.sh`. Older realme-phone restore notes are in
`D:\Claude\realme-debloat-restore.txt`.

## 9. Settings changed on the phone

- `theme_customization_overlay_packages`: the system palette seed and style (set per unit, currently Unit-00).
- `theme_extra_darkmode`: set to 1 on every unit change (Nothing's pure-black extra dark mode on).
- `sysui_qs_tiles` / `nt_sysui_qs_tiles`: the quick-settings layout (originals backed up in `D:\Claude\quickstar\backup`).
- Wallpaper (home and lock) is set by NERV Home per unit.

## 10. Nothing OS 5 / Android 17 upgrade (24 Oct 2026)

Everything carried over (apps, permissions, services, sounds, wallpaper, Glyph Toys, lock-screen hold). One thing broke:
the system's bold condensed font started drawing Latin text as circled letters, which hit NERV Home's labels and
buttons and the notification banner titles. All three NERV apps now ship their own fonts in `res/font` (condensed
bold, serif bold, mono; built by `nerv-os/tools/gen_fonts.py` from the phone's own open-licensed files), with the
system fonts only as a fallback for Japanese. The home widget's weekday now shrinks to one line on long days.

### New on Nothing OS 5 (added 24 Oct 2026)
- **Panel colours:** Nothing's pull-down now takes each unit's hue on its tiles, slider and active icons (Vibrant style;
  seeds: Misato purple, Unit-00 blue, Unit-02 red, Mari magenta-pink). A separate icon colour is still ignored.
- **Serif system font:** every unit change also switches on Android's Noto Serif / Source Sans font pack
  (`android.theme.customization.font` in the theme setting). "Serif system font" chip on Quickstar's Panel tab.
- **Dot-matrix readouts:** MAGI boxes, the date line, saver countdown, drawer console and banner tags use Nothing's
  Ndot 57 font, read from the phone's own fonts (Japanese stays in the normal face; small kanji in dots were unreadable).
- **Lock-screen clock colour:** `lock_screen_custom_clock_face` gets the unit's colour as `seedColor` (Misato green,
  Unit-00 sky blue, Unit-02 orange, Mari pink) with colour id "NERV", so Nothing's wallpaper app stops resetting it to
  white. Other clock faces aren't offered by Nothing. "Restore original" on the Panel tab puts the old clock back.
- **Not used:** Nothing's Translucent ("Lucent") theme reads custom colours (`nt_lucent_theme_style_colors`) but made no
  visible difference on top of the NERV palette, so it is left alone.

## 11. Limits you hit

- No light mode: Android 16 reserves the day/night switch for system roles.
- Nothing's panel can only be tinted, not recoloured or reshaped, without root or replacing the pull-down. Tested on
  23 Oct 2026: every palette style leaves its background and tiles near-grey, a separate accent seed is ignored, and
  `cmd overlay fabricate` refuses without root.
- Wi-Fi, mobile data and airplane toggles cannot be flipped by an app on Android 13+ (relevant to the control centre).
- Per-element colours on SystemUI need root (Android 16 blocks shell-made overlays).
- The Glyph Matrix only takes an app's frames while that app is on screen or selected as a Glyph Toy, so calls,
  notifications and alarms cannot light it up from the background.
- Boot animation, system font and the call screen cannot be replaced without root (or making NERV Home the phone app).

## 12. Undoing things

- Panel and layout: Quickstar, Panel and Layout tabs, "Restore original".
- Launcher: Settings, Default home app, choose Nothing Launcher.
- Services: Settings, Accessibility, switch them off.
- Debloated apps: `bash restore.sh nothing-phone-3.txt`.
- Apps: uninstall normally. Uninstalling Quickstar drops its USB-granted permissions.
