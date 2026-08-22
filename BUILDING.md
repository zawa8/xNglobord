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

Not yet built (see `readme.md` for the full spec):
- the shared xi38 dictionary/auto-complete across all xNglo language
  variants (xe38, xv38, xb38, xp38, xg38, xo38, xj38, xk38, xt38,
  xmr38, xm38, xs38)
