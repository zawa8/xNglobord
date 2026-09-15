# xNglobord -- Windows port

A floating on-screen xi38 keyboard for Windows, ported from the
Android xNglobord IME (`main` branch). Android's IME framework has no
Windows desktop equivalent, so this ships as a small always-on-top
Tkinter window instead of a system-level input method: whatever
Windows app last had focus keeps receiving your keystrokes, sent via
the Win32 `SendInput` API, while xNglobord's own window is marked
`WS_EX_NOACTIVATE` so clicking its keys never steals that focus away.

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
  actual bundled `.ttf` files (loaded as private, per-process fonts via
  `AddFontResourceEx`, with real family names read straight out of each
  file's `name` table -- see `ttf_name.py`)

`xnglo_core.py` is a straight, platform-independent Python port of the
Kotlin state machine, shared byte-for-byte with the Ubuntu port;
`xnglo_tk.py` is the Tkinter front end that drives it and handles all
the Win32 interop. See `xnglo_core.py`'s docstring for what's
intentionally **not** ported yet (voice input, the comma long-press
popup).

## Requirements

- Windows 10 or 11
- Python 3.9+ from [python.org](https://www.python.org/) (includes
  Tkinter; no `pip install` needed -- everything used here is stdlib +
  `ctypes`)

## Run

```
py xnglo_tk.py
```

1. Click into the app you want to type into (browser, Word, a
   terminal, etc.) so it has Windows focus.
2. Click xNglobord's on-screen keys -- they type into that app, and
   xNglobord's own window never takes focus away from it.

## Known limitations

- A small number of apps/games that read raw keyboard scancodes
  instead of the standard text-input APIs may not see the injected
  text (this affects any `SendInput`-based on-screen keyboard, not
  just this one).
- Voice input (the mic key) isn't ported.
