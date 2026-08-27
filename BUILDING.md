# Building xNglobord

**You don't need Android Studio or a local Android SDK to get an .apk.**
Every push to `main` triggers a GitHub Actions workflow
(`.github/workflows/build-apk.yml`) that builds a debug APK on GitHub's
own servers and publishes it as a GitHub Release.

## Getting the APK (no local build needed)

1. Go to the repo's **Releases** page (right sidebar on GitHub, or
   `github.com/zawa8/xNglobord/releases`) on your phone.
2. Open the latest **"Build N"** release.
3. Tap the `.apk` file under Assets to download it directly.
4. Open the downloaded file to install (Android will prompt to allow
   installs from this source the first time).

You can also trigger a build manually without pushing anything: go to
the **Actions** tab -> **Build APK** workflow -> **Run workflow**.

This is a **debug build** (works fine for testing, not signed for Play
Store). If a build fails, check the Actions tab -- open the failed run
and share the log output and I can fix it.

## Building locally instead (optional)

Open the repo root in **Android Studio** (Koala/2024.1+). It will:
- detect the missing Gradle wrapper jar/scripts and offer to regenerate
  them (or run `gradle wrapper` yourself once if you have a system
  Gradle install)
- sync, download the Gradle 8.7 + AGP 8.5.2 + Kotlin 1.9.24 toolchain
- let you Run on a device/emulator, which installs the app

Or from the command line:

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
  (the 26 lowercase base graphemes are literally a-z)
- long-press caps on every a-z key (including h), and long-press the
  spacebar for the font picker: both handled entirely in Kotlin
  (`XngloIME`'s onPress/onRelease + a Handler timer), NOT via
  `android:popupCharacters`. That attribute triggers the framework's
  own built-in mini-keyboard popup -- a separate internal KeyboardView
  instance that `XngloKeyboardView`'s custom rendering doesn't reach,
  so it rendered as a plain unthemed white rectangle. Long-press 'a'
  commits 'A' directly instead, no framework popup involved.
- `XngloKeyboardView`: a KeyboardView subclass that overrides `onDraw()`
  and renders every key itself (background + label) using its own
  Paint, so it can apply a custom Typeface. (An earlier version tried
  reflection into the stock KeyboardView's private `mPaint` field --
  that silently did nothing on a real device, since Android blocks
  reflective access to framework-private fields for apps targeting
  API 28+.)
- shared xi38 dictionary (`XngloDictionary.kt` + `assets/dictionaries/`):
  pools word lists from every xNglo language variant present as a
  `.txt` file, prefix-matches the word currently being typed, and shows
  a tappable candidates strip above the keyboard. Seeded with xe38 +
  2575 xv38 + 2516 xp38 words (the latter two pulled from
  translet-xnglo's 3k word list -- note the xv38/xp38 columns there are
  currently byte-for-byte identical, so xp38 is really Hindi data under
  the Punjabi label until that's fixed upstream)
- local font picker (`LocalFonts.kt` + `FontManager.kt` +
  `FontPickerPopup.kt` + `SettingsActivity.kt`): 11 xNglo hscii fonts
  (source: zawa8/font's englosoftw8asc files, per xnglofont.md),
  default hindixv38 (xNglohindi). Two ways to change it: **long-press
  the spacebar** for an in-keyboard popup list, or the gear icon in
  Settings > Languages & input > On-screen keyboard. Backed by
  SharedPreferences. Applies to both the keyboard's key labels and the
  candidates strip text.
- numbers/symbols page (`keys_numeric.xml`): a "?123" key on the
  letter layout switches to it (digits, common symbols, an "ABC" key
  to switch back). Digits/symbols aren't tracked as part of xi38 words.

Removed:
- h-suffix aspiration (typing h right after k/g/c/z/t/d/j/q/b/s to get
  its capital form, e.g. k+h -> K) and its Settings toggle. Long-press
  already reaches every capital letter the same way as any other key,
  so the h-suffix behavior was redundant -- h is now a plain letter
  key like any other (tap = h, long-press = H).

Not yet built:
- more per-language dictionaries (xb38, xg38, xo38, xj38, xk38, xt38,
  xmr38, xm38, xs38) -- just drop `assets/dictionaries/<code>.txt`,
  one word per line, no code changes needed
- frequency-ranked suggestions (currently sorted by length then
  alphabetically, per language file)
- true composing-text span (`ic.setComposingText`) instead of the
  manual `currentWord` tracking -- would add underline styling on the
  in-progress word
- general Gboard-parity polish (auto-capitalization at sentence start,
  gesture typing, etc.) -- the spec says "rest all will be same as
  Gboard" but only the features above have concrete asks so far
