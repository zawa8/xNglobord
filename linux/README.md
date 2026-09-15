# xNglobord -- Ubuntu port

A floating on-screen xi38 keyboard for Ubuntu/X11, ported from the
Android xNglobord IME (`main` branch). Android's IME framework has no
Linux desktop equivalent, so this ships as a small always-on-top GTK
window instead of a system-level input method: you pick a target
window, then click (or long-press) xNglobord's keys and the characters
get typed there via `xdotool`.

## What's ported from the Android app

- The full xi38 letter layout (`keys_xi38.xml`) and numeric/symbol page
  (`keys_numeric.xml`), including the yellow hex-digit / pink
  operator-letter key colouring
- Long-press-for-capitals on every a-z key, and the two-tap shift /
  double-tap caps-lock behaviour (`XngloIME.kt`'s exact timings: 500 ms
  long-press, 350 ms double-tap window)
- The `?123` / `xyz` numeric-mode toggle, including its one-shot vs.
  double-tap-to-lock behaviour
- The pooled xi38 dictionary + candidates strip (`XngloDictionary.kt`),
  seeded with the same `xe38`/`xv38`/`xp38` word lists
- The 11-font local font picker (`LocalFonts.kt`/`FontManager.kt`),
  reachable by long-pressing space or the font dropdown, using the
  actual bundled `.ttf` files

`xnglo_core.py` is a straight, platform-independent Python port of the
Kotlin state machine; `xnglo_gtk.py` is the GTK front end that drives
it. See `xnglo_core.py`'s docstring for what's intentionally **not**
ported yet (voice input, the comma long-press popup).

## Install

```
sudo apt install python3-gi gir1.2-gtk-3.0 xdotool libfontconfig1
```

## Run

```
python3 xnglo_gtk.py
```

1. Click **Pick target window**, then click the window you want to
   type into.
2. Type using xNglobord's on-screen keys.

## Known limitation: X11 vs. Wayland

This relies on `xdotool`, which needs an X11 session -- it cannot
inject text into other applications' windows under Wayland (this is a
Wayland security restriction, not something fixable from inside a
single app). If `xdotool` errors appear in your terminal, log out and
choose "Ubuntu on Xorg" at the login screen, then relaunch.

## Selected font not showing correctly?

Fonts are registered with fontconfig for this process only
(`FcConfigAppFontAddFile`) and the on-screen family name is read via
`fc-scan`. If a particular `.ttf` fails to resolve, xNglobord falls
back to the font's display name / your system default -- the keys
still work, just not in the custom hscii glyphs.
