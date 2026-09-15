#!/usr/bin/env python3
"""
xNglobord for Windows -- a floating on-screen xi38 keyboard.

Desktop port of the Android xNglobord IME. Windows desktops don't have
a portable "install a custom IME" story as simple as Android's, so this
ships as a small always-on-top Tkinter window: it types the characters
you click straight into whatever window currently has Windows focus, by
sending synthetic input via the Win32 SendInput API. The keyboard
window itself is marked WS_EX_NOACTIVATE, so clicking its keys never
steals focus away from the app you're typing into -- the same trick
real on-screen-keyboard utilities use.

Requirements:
    - Windows 10/11
    - Python 3.9+ from python.org (Tkinter is included; no pip installs
      needed -- everything here is stdlib + ctypes)

Run:
    py xnglo_tk.py

Usage:
    Click into the app you want to type into first (a browser, Word,
    a terminal, etc.), then click keys on the xNglobord window -- it
    keeps typing into whatever window was last focused. Long-press a
    letter for its capital form. Long-press space for the font picker.
    Tap ?123 for numbers/symbols (double-tap to lock that page).

Known limitations vs. the Android app:
    - Voice input (the mic key) is not ported -- it's a no-op here.
    - The comma key's long-press ", : ; > <" popup is not ported;
      comma is a plain key.
    - A small number of games/apps that read raw keyboard scancodes
      instead of the standard input APIs may not see SendInput text.
"""
import ctypes
import ctypes.wintypes as wt
import os
import sys
import time
import tkinter as tk
from tkinter import font as tkfont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from xnglo_core import (  # noqa: E402
    KeyboardEngine, Dictionary, LocalFonts, FontManager,
    LAYOUT_LETTERS, LAYOUT_NUMERIC, YELLOW_HEX_LABELS, PINK_OPERATOR_LABELS,
    MODE_SWITCH_CODE, SHIFT_CODE,
)
from ttf_name import read_family_name  # noqa: E402

# When frozen by PyInstaller, bundled data (fonts/, dictionaries/) lives
# under sys._MEIPASS instead of next to this script.
HERE = getattr(sys, "_MEIPASS", os.path.dirname(os.path.abspath(__file__)))
DICTIONARIES_DIR = os.path.join(HERE, "dictionaries")
FONTS_DIR = os.path.join(HERE, "fonts")
CONFIG_DIR = os.path.join(os.environ.get("APPDATA", os.path.expanduser("~")), "xnglobord")

# --- Win32 constants ---
GWL_EXSTYLE = -20
WS_EX_NOACTIVATE = 0x08000000
WS_EX_TOOLWINDOW = 0x00000080
INPUT_KEYBOARD = 1
KEYEVENTF_UNICODE = 0x0004
KEYEVENTF_KEYUP = 0x0002
VK_BACK = 0x08
VK_RETURN = 0x0D
FR_PRIVATE = 0x10

user32 = ctypes.windll.user32 if sys.platform == "win32" else None
gdi32 = ctypes.windll.gdi32 if sys.platform == "win32" else None


class KEYBDINPUT(ctypes.Structure):
    _fields_ = [("wVk", wt.WORD), ("wScan", wt.WORD), ("dwFlags", wt.DWORD),
                ("time", wt.DWORD), ("dwExtraInfo", ctypes.POINTER(wt.ULONG))]


class INPUT(ctypes.Structure):
    class _I(ctypes.Union):
        _fields_ = [("ki", KEYBDINPUT)]
    _anonymous_ = ("_i",)
    _fields_ = [("type", wt.DWORD), ("_i", _I)]


def now_ms() -> int:
    return int(time.time() * 1000)


def make_window_noactivate(root: tk.Tk) -> None:
    """Marks the Tk window WS_EX_NOACTIVATE | WS_EX_TOOLWINDOW so clicking
    it never steals Windows focus from the app we're typing into."""
    if user32 is None:
        return
    hwnd = ctypes.windll.user32.GetParent(root.winfo_id()) or root.winfo_id()
    style = user32.GetWindowLongPtrW(hwnd, GWL_EXSTYLE)
    style |= WS_EX_NOACTIVATE | WS_EX_TOOLWINDOW
    user32.SetWindowLongPtrW(hwnd, GWL_EXSTYLE, style)


def send_unicode_text(text: str) -> None:
    if user32 is None or not text:
        return
    inputs = []
    for ch in text:
        for flags in (KEYEVENTF_UNICODE, KEYEVENTF_UNICODE | KEYEVENTF_KEYUP):
            ki = KEYBDINPUT(0, ord(ch), flags, 0, None)
            inputs.append(INPUT(INPUT_KEYBOARD, ki))
    arr = (INPUT * len(inputs))(*inputs)
    user32.SendInput(len(inputs), arr, ctypes.sizeof(INPUT))


def send_vk(vk: int) -> None:
    if user32 is None:
        return
    down = INPUT(INPUT_KEYBOARD, KEYBDINPUT(vk, 0, 0, 0, None))
    up = INPUT(INPUT_KEYBOARD, KEYBDINPUT(vk, 0, KEYEVENTF_KEYUP, 0, None))
    arr = (INPUT * 2)(down, up)
    user32.SendInput(2, arr, ctypes.sizeof(INPUT))


class FontRegistry:
    """Loads the bundled .ttf files as private, process-only fonts via
    AddFontResourceEx and remembers each file's real family name."""

    def __init__(self):
        self._family_by_file: dict[str, str] = {}

    def register_all(self) -> None:
        for opt in LocalFonts.ALL:
            path = os.path.join(FONTS_DIR, opt.asset_file_name)
            if not os.path.isfile(path):
                continue
            if gdi32 is not None:
                gdi32.AddFontResourceExW(path, FR_PRIVATE, 0)
            self._family_by_file[opt.asset_file_name] = read_family_name(path, opt.display_name)

    def family_for(self, asset_file_name: str, fallback: str) -> str:
        return self._family_by_file.get(asset_file_name, fallback)


class XngloBordApp:
    def __init__(self, root: tk.Tk):
        self.root = root
        root.title("xNglobord")
        root.attributes("-topmost", True)
        root.resizable(False, False)

        self.fonts = FontRegistry()
        self.fonts.register_all()
        self.font_manager = FontManager(CONFIG_DIR)

        self.dictionary = Dictionary()
        self.dictionary.load_all(DICTIONARIES_DIR)

        self._timers: dict[object, str] = {}

        self.engine = KeyboardEngine(
            commit_text=send_unicode_text,
            delete_backward=lambda: send_vk(VK_BACK),
            send_enter=lambda: send_vk(VK_RETURN),
            schedule=self._schedule,
            cancel=self._cancel,
            dictionary=self.dictionary,
            on_candidates_changed=self._render_candidates,
            on_state_changed=self._update_key_visuals,
            on_font_picker_requested=self._show_font_picker,
        )

        self.key_widgets: dict[int, list[tk.Button]] = {}
        self._build_ui()
        root.after(50, lambda: make_window_noactivate(root))

    # tkinter's `after` returns an id we hand back as the "token"
    def _schedule(self, delay_ms: int, fn):
        return self.root.after(delay_ms, fn)

    def _cancel(self, token) -> None:
        try:
            self.root.after_cancel(token)
        except (tk.TclError, ValueError):
            pass

    # --- UI construction ---

    def _build_ui(self) -> None:
        top = tk.Frame(self.root)
        top.pack(fill="x", padx=4, pady=4)
        tk.Label(top, text="Font:").pack(side="left")
        self.font_var = tk.StringVar(value=self.font_manager.get_selected_font_id())
        font_menu = tk.OptionMenu(
            top, self.font_var, *[o.font_id for o in LocalFonts.ALL],
            command=self._apply_font,
        )
        font_menu.pack(side="left", padx=4)
        tk.Label(
            top, text="(click your target app first, then click keys)", fg="#555"
        ).pack(side="left", padx=8)

        self.candidates_frame = tk.Frame(self.root)
        self.candidates_frame.pack(fill="x", padx=4)

        self.keys_frame = tk.Frame(self.root)
        self.keys_frame.pack(padx=4, pady=4)
        self._render_layout(LAYOUT_LETTERS)

    def _render_layout(self, layout) -> None:
        for child in self.keys_frame.winfo_children():
            child.destroy()
        self.key_widgets = {}

        for r, row in enumerate(layout):
            row_frame = tk.Frame(self.keys_frame)
            row_frame.grid(row=r, column=0, sticky="ew")
            for code, label, _width in row:
                bg = self._bg_for(label)
                btn = tk.Button(row_frame, text=label, width=4, height=2, bg=bg)
                btn.pack(side="left", padx=1, pady=1)
                btn.bind("<ButtonPress-1>", lambda _e, c=code: self._on_press(c))
                btn.bind("<ButtonRelease-1>", lambda _e, c=code: self._on_release(c))
                self.key_widgets.setdefault(code, []).append(btn)

    @staticmethod
    def _bg_for(label: str) -> str:
        if label in YELLOW_HEX_LABELS:
            return "#f5d76e"
        if label in PINK_OPERATOR_LABELS:
            return "#f2a2c2"
        return "SystemButtonFace"

    # --- key event plumbing ---

    def _on_press(self, code: int) -> None:
        self.engine.on_press(code, now_ms())

    def _on_release(self, code: int) -> None:
        self.engine.on_release(code, now_ms())
        self.engine.on_key(code, now_ms())
        if code == MODE_SWITCH_CODE:
            self._render_layout(LAYOUT_NUMERIC if self.engine.is_numeric_mode else LAYOUT_LETTERS)
            self._update_key_visuals()

    def _update_key_visuals(self) -> None:
        for btn in self.key_widgets.get(SHIFT_CODE, []):
            if self.engine.is_caps_lock:
                btn.config(relief="sunken", text="\u21ea")
            elif self.engine.is_shift_active:
                btn.config(relief="sunken", text="\u21e7")
            else:
                btn.config(relief="raised", text="\u21e7")

    def _render_candidates(self, words) -> None:
        for child in self.candidates_frame.winfo_children():
            child.destroy()
        opt = self.font_manager.get_selected_option()
        family = self.fonts.family_for(opt.asset_file_name, opt.display_name)
        try:
            candidate_font = tkfont.Font(family=family, size=11)
        except tk.TclError:
            candidate_font = tkfont.Font(size=11)
        for word in words:
            chip = tk.Button(self.candidates_frame, text=word, font=candidate_font,
                              command=lambda w=word: self.engine.apply_candidate(w))
            chip.pack(side="left", padx=2, pady=2)

    # --- font picker (long-press space, or the dropdown) ---

    def _show_font_picker(self) -> None:
        menu = tk.Menu(self.root, tearoff=0)
        for opt in LocalFonts.ALL:
            menu.add_command(label=opt.display_name, command=lambda fid=opt.font_id: self._apply_font(fid))
        x, y = self.root.winfo_pointerx(), self.root.winfo_pointery()
        menu.tk_popup(x, y)

    def _apply_font(self, font_id: str) -> None:
        self.font_manager.set_selected_font_id(font_id)
        self.font_var.set(font_id)
        opt = LocalFonts.by_id(font_id)
        family = self.fonts.family_for(opt.asset_file_name, opt.display_name)
        try:
            key_font = tkfont.Font(family=family, size=12)
        except tk.TclError:
            key_font = tkfont.Font(size=12)
        for widgets in self.key_widgets.values():
            for btn in widgets:
                btn.config(font=key_font)
        self.engine.render_candidates()


def main():
    if sys.platform != "win32":
        print("xnglo_tk.py uses the Win32 SendInput API and only runs on Windows.")
        sys.exit(1)
    root = tk.Tk()
    XngloBordApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
