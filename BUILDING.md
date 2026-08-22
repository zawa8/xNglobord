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

Done (this scaffold):
- Gradle project structure, manifest, IME service registration
- `XngloIME.kt`: minimal `InputMethodService` that inflates a keyboard,
  sends keystrokes to the focused field, handles backspace/space/enter
- `keys_placeholder.xml`: a 2-row stand-in layout (x a i u e o + space/
  backspace/enter) just to prove the pipeline end-to-end

Not yet built (see `readme.md` for the full spec):
- the real 38-sound key layout
- h-suffix aspiration (k+h -> K, g+h -> G, c+h -> C, z+h -> Z, t+h -> T,
  d+h -> D, j+h -> J, q+h -> Q, b+h -> B, s+h -> S)
- long-press on a-z for the caps variant
- the shared xi38 dictionary/auto-complete across all xNglo language
  variants (xe38, xv38, xb38, xp38, xg38, xo38, xj38, xk38, xt38,
  xmr38, xm38, xs38)
