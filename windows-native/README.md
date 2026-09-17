# xNglobord for Windows — native C# (WPF)

A floating on-screen xi38 keyboard for Windows, built as a real native
app (C#/.NET/WPF) instead of a PyInstaller-wrapped Python script. This
replaces the earlier `windows/` port (removed in this commit — see git
history if you need it), which had two real problems this rewrite
fixes:

1. **Windows Smart App Control was blocking it.** PyInstaller-built
   executables are a very common malware-packaging pattern, so
   unsigned PyInstaller `.exe`s get flagged far more readily than an
   ordinary compiled .NET app. This version is a genuine native
   Windows executable — no bundled Python interpreter at all — which
   removes that specific red flag. It's still an **unsigned** binary
   from a new publisher, though, so Smart App Control / SmartScreen
   may still warn on first run until it builds up reputation, or until
   it's code-signed with a real certificate. Not something a rewrite
   alone can fully fix.
2. **Characters weren't actually typing into Notepad++.** The old
   Python/`ctypes` version's `INPUT`/`KEYBDINPUT` struct definitions
   didn't use an explicit, verified union layout — a known-fragile
   pattern on x64. This version's `NativeMethods.cs` uses
   `[StructLayout(LayoutKind.Explicit)]` with `[FieldOffset(0)]` on the
   union members, the same well-established pattern used by e.g. the
   InputSimulator library and most reference `SendInput` samples —
   byte-for-byte matches the real Win32 `tagINPUT` struct.

   If text *still* doesn't land in some specific app after this fix,
   the next most likely cause is **UIPI** (User Interface Privilege
   Isolation): Windows silently drops `SendInput` calls aimed at a
   window running at a *higher* integrity level than the sending
   process. If Notepad++ (or anything else) was launched "Run as
   administrator" while xNglobord was not, that's almost certainly
   why — run both at the same elevation level and try again.

## What's ported from the Android app (main branch)

Everything currently on the Android keyboard, ported from the same
`XngloIME.kt` this repo's Android app uses:

- Full xi38 letter layout (digit row, direct-access capitals row
  `QRTA SDGHJK`, punctuation/bracket row, qwerty rows, resized bottom
  row) and the numeric/symbol page, both transcribed from the current
  `keys_xi38.xml`/`keys_numeric.xml` — see `Layouts.cs`'s header
  comment: **keep it manually in sync** if those XML files change,
  there's no automated sync.
- Long-press-for-capitals on every a-z key; two-tap shift /
  double-tap caps-lock; `?123`/`xyz` numeric-mode toggle with its
  one-shot-vs-locked behavior — `KeyboardEngine.cs` is a direct,
  tested port of `XngloIME.kt`'s state machine (same 500ms long-press
  / 350ms double-tap timings).
- `( [ {` auto-pairing (typing `(` types `()` with the caret left in
  between — done here by sending an extra Left-arrow keystroke after
  the pair, since raw `SendInput` has no equivalent of `InputConnection`'s
  `newCursorPosition`) and long-press popups for `,`/`( [ {`, matching
  the Android app's `SymbolAltPopup` behavior.
- The pooled xi38 dictionary + candidates strip.
- The 11-font local font picker — loaded here even more simply than on
  Android or the old Python port: WPF's `Fonts.GetFontFamilies(Uri)`
  loads a `.ttf` file directly with no OS-level font registration and
  no manual name-table parsing needed (compare the old
  `windows/ttf_name.py`, now deleted).

**Not ported** (same as the old Python port, same reasons): voice
input (mic key — no-op here), and the comma key's long-press
`, : ; > <` popup is present, but its Android counterpart
`SymbolAltPopup`'s exact visual styling wasn't reproduced pixel-for-
pixel — functionally equivalent, not a visual clone.

## Requirements

- Windows 10 or 11
- Nothing to install to *run* it — the published `.exe` is
  self-contained (bundles the .NET runtime).
- To *build* it yourself: [.NET 8 SDK](https://dotnet.microsoft.com/download).

## Build

```
cd windows-native/XngloBord
dotnet publish -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o out_publish
```

Produces a single `xNglobord.exe` in `out_publish/` — no other files
needed alongside it except its `fonts/` and `dictionaries/` folders
(copied there automatically by the build, per the `.csproj`'s
`CopyToOutputDirectory` items).

CI (`.github/workflows/build-exe.yml`) does exactly this on every push
to `windoz_porting` and publishes the result as a GitHub Release.

## Run

1. Click into the app you want to type into (browser, Word, Notepad++,
   etc.) so it has Windows focus.
2. Click xNglobord's on-screen keys — they type into that app.
   xNglobord's own window is marked `WS_EX_NOACTIVATE`, so clicking it
   never steals focus away from your target app in the first place.
