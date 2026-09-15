"""
xnglo_core.py

Platform-independent port of the Android app's Kotlin logic:
  XngloIME.kt          -> KeyboardEngine (key state machine)
  XngloDictionary.kt   -> Dictionary
  LocalFonts.kt        -> LocalFonts / LocalFontOption
  FontManager.kt       -> FontManager
  keys_xi38.xml        -> LAYOUT_LETTERS
  keys_numeric.xml     -> LAYOUT_NUMERIC

This module has no GUI or OS dependency. A front end (GTK on Linux,
Tkinter on Windows, etc.) supplies four host callbacks and drives the
engine with on_press()/on_release() for each key. See the docstring on
KeyboardEngine for the callback contract.

NOT ported from the Android app (out of scope for this pass):
  - MicVoiceInput.kt (Android SpeechRecognizer-based voice input)
  - the comma long-press ", : ; > <" popup (comma is a plain key here)
  - true IME-level composing-text spans; candidate tracking works the
    same way the original did (a plain currentWord buffer), not as an
    OS input-method text service
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from typing import Callable, List, Optional

# --- special key codes, same values as XngloIME.kt's companion object ---
KEYCODE_DELETE = -5
KEYCODE_ENTER = -4
MODE_SWITCH_CODE = -2
SHIFT_CODE = -1
MIC_CODE = -3  # accepted so layouts match, but MicVoiceInput isn't ported; see note above

LOWERCASE_A = ord('a')
LOWERCASE_Z = ord('z')
CASE_OFFSET = 32  # 'a' (97) - 'A' (65)

LONG_PRESS_MS = 500
CAPS_LOCK_DOUBLE_TAP_MS = 350

HEX_LETTER_CODES = {ord(c) for c in "LYVWPF"}       # hex digits 10-15, xi38's own letters
OPERATOR_LETTER_CODES = {ord(c) for c in "EUIOMX"}  # hscii font remaps these glyphs visually only


# --- layouts, transcribed from keys_xi38.xml / keys_numeric.xml ---
# Each row is a list of (code, label, width_percent). width_percent is
# advisory for the renderer; codes/labels are what matter functionally.
LAYOUT_LETTERS: List[List[tuple]] = [
    [(ord(c), c, 10) for c in "qwertyuiop"],
    [(ord(c), c, 10) for c in "asdfghjkl"] + [(MIC_CODE, "\U0001F3A4", 10)],
    [(SHIFT_CODE, "\u21e7", 12)] + [(ord(c), c, 11) for c in "zxcvbnm"] + [(KEYCODE_DELETE, "\u232b", 11)],
    [(MODE_SWITCH_CODE, "?123", 14), (44, ",:", 10), (32, "space (long-press: font)", 36),
     (46, ".", 10), (64, "@", 10), (KEYCODE_ENTER, "\u23ce", 20)],
]

LAYOUT_NUMERIC: List[List[tuple]] = [
    [(ord(c), c, 10) for c in "0123"] + [(43, "+", 10), (45, "-", 10), (47, "/", 10), (42, "*", 10),
                                          (37, "%", 10), (61, "=", 10)],
    [(ord(c), c, 10) for c in "4567"] + [(ord(c), c, 10) for c in "EUIOMX"],
    [(ord(c), c, 10) for c in "89"] + [(ord(c), c, 10) for c in "LY"] +
    [(95, "_", 10), (34, '"', 10), (35, "#", 10), (36, "$", 10), (38, "&", 10), (42, "*", 10)],
    [(ord(c), c, 10) for c in "VWPF"] + [(40, "(", 10), (91, "[", 10), (123, "{", 10),
                                          (41, ")", 10), (93, "]", 10), (125, "}", 10)],
    [(MODE_SWITCH_CODE, "xyz", 11)] + [(ord(c), c, 11) for c in "'`~|^\\?"] + [(46, ".", 12)],
]

# Hex digit keys and operator-glyph keys are visually yellow / pink in the
# original (XngloKeyboardView.colorForLabel) -- front ends can use these
# sets to reproduce that.
YELLOW_HEX_LABELS = set("0123456789LYVWPF")
PINK_OPERATOR_LABELS = set("EUIOMX")


@dataclass
class LocalFontOption:
    font_id: str
    display_name: str
    asset_file_name: str


class LocalFonts:
    # same order as LocalFonts.kt -- please don't reshuffle
    ALL: List[LocalFontOption] = [
        LocalFontOption("eNgliSxe38", "xNgloiNgliS", "eNgliSxe38.ttf"),
        LocalFontOption("hindixv38", "xNglovinqi", "hindixv38.ttf"),
        LocalFontOption("bengalixb38", "xNglobNgali", "bengalixb38.ttf"),
        LocalFontOption("jeluguxj38", "xNglojelugu", "jeluguxj38.ttf"),
        LocalFontOption("knRaxk38", "xNgloknRa", "knRaxk38.ttf"),
        LocalFontOption("pnzabixp38", "xNglopnzabi", "pnzabixp38.ttf"),
        LocalFontOption("mlyalxmxm38", "xNglomlyalxm", "mlyalxmxm38.ttf"),
        LocalFontOption("oriyaxo38", "xNglooriya", "oriyaxo38.ttf"),
        LocalFontOption("guzrajixg38", "xNgloguzraji", "guzrajixg38.ttf"),
        LocalFontOption("tmilxt38", "xNglotmil", "tmilxt38.ttf"),
        LocalFontOption("sinhlaxs38", "xNglosinvla", "sinhlaxs38.ttf"),
    ]
    DEFAULT_FONT_ID = "hindixv38"

    @classmethod
    def by_id(cls, font_id: str) -> Optional[LocalFontOption]:
        return next((f for f in cls.ALL if f.font_id == font_id), None)


class FontManager:
    """Reads/writes the selected font id in a small local prefs file
    (the desktop equivalent of Android SharedPreferences)."""

    def __init__(self, config_dir: str):
        os.makedirs(config_dir, exist_ok=True)
        self._path = os.path.join(config_dir, "xnglobord_prefs.txt")

    def get_selected_font_id(self) -> str:
        try:
            with open(self._path, "r", encoding="utf-8") as f:
                value = f.read().strip()
                return value if LocalFonts.by_id(value) else LocalFonts.DEFAULT_FONT_ID
        except FileNotFoundError:
            return LocalFonts.DEFAULT_FONT_ID

    def set_selected_font_id(self, font_id: str) -> None:
        with open(self._path, "w", encoding="utf-8") as f:
            f.write(font_id)

    def get_selected_option(self) -> LocalFontOption:
        return LocalFonts.by_id(self.get_selected_font_id()) or LocalFonts.ALL[1]


class Dictionary:
    """Port of XngloDictionary.kt: pools every *.txt file in a directory
    (one word per line) keyed by first character, case-sensitive prefix
    match, sorted by (length, alphabetically)."""

    def __init__(self):
        self._by_first_char: dict[str, List[str]] = {}
        self._loaded = False

    def load_all(self, dictionaries_dir: str) -> None:
        if self._loaded:
            return
        self._loaded = True
        if not os.path.isdir(dictionaries_dir):
            return
        for name in os.listdir(dictionaries_dir):
            if not name.endswith(".txt"):
                continue
            path = os.path.join(dictionaries_dir, name)
            try:
                with open(path, "r", encoding="utf-8") as f:
                    for line in f:
                        word = line.strip()
                        if word:
                            self._add_word(word)
            except OSError:
                continue  # a missing/unreadable file shouldn't crash the keyboard
        for words in self._by_first_char.values():
            words.sort(key=lambda w: (len(w), w))

    def _add_word(self, word: str) -> None:
        bucket = self._by_first_char.setdefault(word[0], [])
        if word not in bucket:
            bucket.append(word)

    def suggestions_for(self, prefix: str, limit: int = 5) -> List[str]:
        if not prefix:
            return []
        bucket = self._by_first_char.get(prefix[0])
        if not bucket:
            return []
        return [w for w in bucket if w != prefix and w.startswith(prefix)][:limit]


class KeyboardEngine:
    """
    Port of XngloIME.kt's onKey/onPress/onRelease state machine, decoupled
    from Android's InputConnection/Handler/KeyboardView.

    Host (GUI front end) must supply these callbacks at construction:
      commit_text(text: str)         -- type `text` into the focused app
      delete_backward()              -- delete one character before the caret
      send_enter()                   -- send an Enter keystroke
      schedule(delay_ms, fn) -> tok  -- run fn() once after delay_ms (timer)
      cancel(token)                  -- cancel a pending schedule() call

    Host should also poll/observe these engine properties after each
    call to redraw: is_shift_active, is_caps_lock, is_numeric_mode,
    current_word, and call render_candidates() itself when it wants an
    updated suggestion list (engine calls on_candidates_changed for you
    automatically after every edit if you pass it in).
    """

    def __init__(
        self,
        commit_text: Callable[[str], None],
        delete_backward: Callable[[], None],
        send_enter: Callable[[], None],
        schedule: Callable[[int, Callable[[], None]], object],
        cancel: Callable[[object], None],
        dictionary: Dictionary,
        on_candidates_changed: Callable[[List[str]], None] = lambda words: None,
        on_state_changed: Callable[[], None] = lambda: None,
        on_font_picker_requested: Callable[[], None] = lambda: None,
    ):
        self._commit_text = commit_text
        self._delete_backward = delete_backward
        self._send_enter = send_enter
        self._schedule = schedule
        self._cancel = cancel
        self._dictionary = dictionary
        self._on_candidates_changed = on_candidates_changed
        self._on_state_changed = on_state_changed
        self._on_font_picker_requested = on_font_picker_requested

        self.current_word = ""
        self.is_shift_active = False
        self.is_caps_lock = False
        self.is_numeric_mode = False
        self._is_numeric_locked = False
        self._last_shift_tap_ms = 0
        self._last_mode_tap_ms = 0

        self._space_timer = None
        self._space_long_press_fired = False
        self._letter_timer = None
        self._letter_long_press_fired = False
        self._letter_long_press_code = -1

    # --- public entry points, called by the GUI on key down / key up ---

    def on_press(self, code: int, now_ms: int) -> None:
        if code == 32:  # space
            self._space_long_press_fired = False
            self._space_timer = self._schedule(LONG_PRESS_MS, self._fire_space_long_press)
        elif LOWERCASE_A <= code <= LOWERCASE_Z:
            self._letter_long_press_fired = False
            self._letter_long_press_code = code
            self._letter_timer = self._schedule(LONG_PRESS_MS, self._fire_letter_long_press)

    def on_release(self, code: int, now_ms: int) -> None:
        if code == 32 and self._space_timer is not None:
            self._cancel(self._space_timer)
            self._space_timer = None
        elif LOWERCASE_A <= code <= LOWERCASE_Z and self._letter_timer is not None:
            self._cancel(self._letter_timer)
            self._letter_timer = None

    def on_key(self, code: int, now_ms: int) -> None:
        if code == KEYCODE_DELETE:
            self._delete_backward()
            if self.current_word:
                self.current_word = self.current_word[:-1]
            self._changed()
        elif code == KEYCODE_ENTER:
            self._send_enter()
            self.current_word = ""
            self._changed()
        elif code == 32:  # space
            if self._space_long_press_fired:
                self._space_long_press_fired = False
            else:
                self._commit_text(" ")
                self.current_word = ""
                self._changed()
        elif code == 46:  # period
            self._commit_text(".")
            self.current_word = ""
            self._changed()
            self._maybe_auto_return_from_numeric()
        elif code == MODE_SWITCH_CODE:
            self._handle_mode_switch_tap(now_ms)
        elif code == SHIFT_CODE:
            self._handle_shift_tap(now_ms)
        elif code == MIC_CODE:
            pass  # voice input not ported on desktop; key is a no-op here
        elif code in HEX_LETTER_CODES or code in OPERATOR_LETTER_CODES:
            self._commit_text(chr(code))
            self.current_word = ""
            self._changed()
            self._maybe_auto_return_from_numeric()
        else:
            if (LOWERCASE_A <= code <= LOWERCASE_Z and self._letter_long_press_fired
                    and code == self._letter_long_press_code):
                self._letter_long_press_fired = False  # long-press already committed the capital
            else:
                use_shift = self.is_shift_active and LOWERCASE_A <= code <= LOWERCASE_Z
                code_to_commit = code - CASE_OFFSET if use_shift else code
                if use_shift and not self.is_caps_lock:
                    self.is_shift_active = False
                ch = chr(code_to_commit)
                self._commit_text(ch)
                if ch.isalpha():
                    self.current_word += ch
                else:
                    self.current_word = ""
                self._changed()
                self._maybe_auto_return_from_numeric()

    def render_candidates(self) -> List[str]:
        suggestions = self._dictionary.suggestions_for(self.current_word)
        self._on_candidates_changed(suggestions)
        return suggestions

    def apply_candidate(self, word: str) -> None:
        if self.current_word:
            for _ in self.current_word:
                self._delete_backward()
        self._commit_text(word)
        self.current_word = ""
        self._changed()

    def reset_for_new_field(self) -> None:
        self.is_numeric_mode = False
        self._is_numeric_locked = False
        self.is_shift_active = False
        self.is_caps_lock = False
        self.current_word = ""
        self._changed()

    # --- internal helpers, mirroring XngloIME.kt private methods ---

    def _fire_space_long_press(self) -> None:
        self._space_long_press_fired = True
        self._on_font_picker_requested()

    def _fire_letter_long_press(self) -> None:
        self._letter_long_press_fired = True
        upper = chr(self._letter_long_press_code).upper()
        self._commit_text(upper)
        self.current_word += upper
        if self.is_shift_active and not self.is_caps_lock:
            self.is_shift_active = False
        self._changed()

    def _handle_shift_tap(self, now_ms: int) -> None:
        if self.is_caps_lock:
            self.is_caps_lock = False
            self.is_shift_active = False
        elif self.is_shift_active and (now_ms - self._last_shift_tap_ms) < CAPS_LOCK_DOUBLE_TAP_MS:
            self.is_caps_lock = True
        else:
            self.is_shift_active = not self.is_shift_active
        self._last_shift_tap_ms = now_ms
        self._changed()

    def _handle_mode_switch_tap(self, now_ms: int) -> None:
        is_double_tap = (now_ms - self._last_mode_tap_ms) < CAPS_LOCK_DOUBLE_TAP_MS
        self._last_mode_tap_ms = now_ms

        if not self.is_numeric_mode:
            self.is_numeric_mode = True
            self._is_numeric_locked = False
        elif is_double_tap and not self._is_numeric_locked:
            self._is_numeric_locked = True
        else:
            self.is_numeric_mode = False
            self._is_numeric_locked = False

        if self.is_shift_active or self.is_caps_lock:
            self.is_shift_active = False
            self.is_caps_lock = False
        self._changed()

    def _maybe_auto_return_from_numeric(self) -> None:
        if self.is_numeric_mode and not self._is_numeric_locked:
            self.is_numeric_mode = False
            self._changed()

    def _changed(self) -> None:
        self._on_state_changed()
        self.render_candidates()
