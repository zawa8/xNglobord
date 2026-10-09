# xNglobord — Next Session Handoff

Repo: https://github.com/zawa8/xNglobord
Working: ~/xNglobord (Termux)
Branch: main @ ca6b1c6
Last tag: build-79

---

## Current state (working ✅)

- 11 fonts in `app/src/main/assets/fonts/` (xb38asc.ttf ... xs38asc.ttf)
- `LocalFonts.kt` fixed (assetFileName with 'asc' suffix)
- Bug fixed: font picker was falling back to system font
- APK builds work via `.github/workflows/build-apk.yml`
- Release auto-created on push to `main` (tag build-N)
- Latest APK tested on Android: **all good** ✅

## Task types for next session

### A. Layout changes (minor)
- Keyboard layout tweaks
- Font picker popup size/positioning
- Row styling, spacing, colors
- Touch target sizes

### B. Font updates
- Copy new fonts from `~/pff/xnglofonts/ttf/xi38ttf/xi38asc/*.ttf`
- Update `app/src/main/assets/fonts/`
- Also update `LocalFonts.kt` if font list changes

### C. Small feature improvements
- New keyboard behavior
- Additional symbols
- Settings toggles
- UX polish

---

## Important files

### Kotlin sources
`app/src/main/kotlin/com/xnglo/bord/`
- `LocalFonts.kt` — font list (id, displayName, assetFileName)
- `FontManager.kt` — load/cache Typeface, save/read pref
- `FontPickerPopup.kt` — long-press spacebar popup
- `XngloIME.kt` — main IME service (typing logic, key handling)
- (other files — check as needed)

### Assets
`app/src/main/assets/fonts/` — 11 TTF files
`app/src/main/assets/keys_xi38.xml` — key mappings (was modified in build-77)

### CI
`.github/workflows/build-apk.yml`
- Triggers: push to `main` + manual dispatch
- Creates tag `build-N` + Release with APK
- **Warning:** every push to main = new Release

---

## Common commands

### Clone / update
    cd ~
    git clone https://github.com/zawa8/xNglobord.git   # if not cloned
    cd ~/xNglobord
    git pull origin main

### Copy fonts from pff
    cp ~/pff/xnglofonts/ttf/xi38ttf/xi38asc/*.ttf \
       app/src/main/assets/fonts/

### Build APK (local — if Android SDK available)
    ./gradlew assembleDebug

### Trigger CI (if not pushing to main)
    gh workflow run build-apk.yml --ref <branch>

### Watch CI
    gh run list --workflow=build-apk.yml --limit 3

### Download APK from CI
    RUN_ID=$(gh run list --workflow=build-apk.yml --limit 1 \
              --json databaseId --jq '.[0].databaseId')
    gh run download $RUN_ID --name xNglobord-debug-apk

### Install APK to phone
    cp xNglobord-debug-N.apk /sdcard/
    # Then Files app -> tap -> Install

---

## Testing on Android

### Enable keyboard
Settings → System → Languages & input → On-screen keyboard →
Manage keyboards → xNglobord → ON

### Open font picker
In any text field: keyboard → long-press spacebar

### Font picker should show
11 labels in xnglo typeface (not system font):
xNgloiNgliS, xNglovinqi, xNglobordNgali, xNglojelugu, xNgloknRa,
xNglopnzabi, xNglomlyalxm, xNglooriya, xNgloguzraji, xNglotmil,
xNglosinvla

### Type test
- `Dhanyavaad` → `दhaनयa∀aaड`
- `five` → `fi∀e`
- `jug` → Latin (not Hindi ज)

---

## Workflow — how to start next session

1. Open new DeepSeek session (Termux)
2. Paste this file's content: `cat NEXT_SESSION.md`
3. Say what you want to work on:
   - "layout change: [describe]"
   - "update fonts"
   - "add feature: [describe]"
4. AI will guide step-by-step

---

## Known gotchas

- `xe38asc.ttf` is stale (from Sep 27) — pff deleted `xe38asc.sfd`, so it won't regenerate. All other 10 fonts are fresh.
- Every push to `main` = new Release (tag `build-N`)
- Font picker code catches exceptions silently — if font missing, falls back to system font. **Check for typos in assetFileName.**
- Termux: use `gh` CLI for GitHub ops

---

## Cross-repo context

- `~/pff` = font pipeline (source of fonts)
  - `xnglofonts/ttf/xi38ttf/xi38asc/` — TTF to copy
- `~/htrlib` = transliteration library (not directly used here)
- `~/xNglobord` = this app (Android IME)

---

## Contact/commits

Recent commits (main):
- ca6b1c6 — Merge PR #1 (LocalFonts fix)
- 424b356 — fix(LocalFonts): assetFileName asc suffix
- d2c17fb — tag build-78, xpdetid fonts and LocalFonts.kt
- e733106 — tag build-77, Modify key mappings in keys_xi38.xml

---

End of handoff. Copy this file's content when starting new session.
