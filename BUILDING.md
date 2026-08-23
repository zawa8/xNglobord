# Building xNglobord

This is a standard Gradle Android project. No sandboxed CI here has the
Android SDK, so this scaffold has been checked for XML well-formedness
and Kotlin brace balance, but **not yet compiled with a real Android
build** -- do that first thing after pulling.

## Fastest way to build

Open the repo root in **Android Studio** (Koala/2024.1+). It will:
- detect the missing Gradle wrapper jar/scripts and offer to regenerate
  them (or run `gradle wrapper` yourself once if you have a system
  Gradle install)
- sync, download the Gradle 8.7 + AGP 8.5.2 + Kotlin 1.9.24 toolchain
- let you Run on a device/emulator, which installs the app

## Command line

```
gradle wrapper          # one-time, generates gradlew + gradle-wrapper.jar
./gradlew assembleDebug
```

## After installing

The app itself has no launcher UI yet (it's just the IME service) --
enable it in **Settings > System > Languages & input > On-screen
keyboard > Manage keyboards**, turn on "xNglobord", then switch to it
from any text field's keyboard-switcher.

## What's here vs. what's next

Done:
- Gradle project structure, manifest, IME service registration
- `XngloIME.kt`: `InputMethodService` that inflates the keyboard, sends
  keystrokes to the focused field, handles backspace/space/enter
- `keys_xi38.xml`: the real 38-sound layout, standard QWERTY positions
  (the 26 lowercase base graphemes are literally a-z), with long-press
  popups on every key for its capital form
- h-suffix aspiration in `XngloIME.kt`: typing h right after
  k/g/c/z/t/d/j/q/b/s swaps that letter for its aspirated capital
  (K G C Z T D J Q B S) instead of inserting a literal h
- shared xi38 dictionary (`XngloDictionary.kt` + `assets/dictionaries/`):
  pools word lists from every xNglo language variant present as a
  `.txt` file, prefix-matches the word currently being typed, and shows
  a tappable candidates strip above the keyboard. Seeded with xe38 +
  2575 xv38 + 2516 xp38 words (the latter two pulled from
  translet-xnglo's 3k word list -- note the xv38/xp38 columns there are
  currently byte-for-byte identical, so xp38 is really Hindi data under
  the Punjabi label until that's fixed upstream)
- local font picker (`LocalFonts.kt` + `FontManager.kt` +
  `SettingsActivity.kt`): same font list/order as translet-xnglo's
  LocalFontPicker.tsx, backed by SharedPreferences instead of
  localStorage. Reachable via the gear icon next to xNglobord in
  Settings > Languages & input > On-screen keyboard. 15 matching .ttf
  files bundled in `assets/fonts/` (1.3MB total). Currently applies to
  the candidates strip text only.

Not yet built:
- more per-language dictionaries (xb38, xg38, xo38, xj38, xk38, xt38,
  xmr38, xm38, xs38) -- just drop `assets/dictionaries/<code>.txt`,
  one word per line, no code changes needed
- frequency-ranked suggestions (currently sorted by length then
  alphabetically, per language file)
- true composing-text span (`ic.setComposingText`) instead of the
  manual `currentWord` tracking -- would add underline styling on the
  in-progress word
- font picker currently only affects the candidates strip; extending
  it to the key labels themselves would need a custom KeyboardView
  subclass (the stock one doesn't expose a Typeface setter)
