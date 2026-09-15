#!/usr/bin/env python3
"""
xNglobord for Ubuntu -- a floating on-screen xi38 keyboard.

Desktop port of the Android xNglobord IME. Since Linux desktops don't
have a single portable "input method service" API the way Android does,
this ships as a floating always-on-top GTK window: click (or long-press)
its keys and the characters are typed into whichever window you pick as
the "target" via xdotool, the same way an on-screen keyboard app works.

Requirements (Ubuntu):
    sudo apt install python3-gi gir1.2-gtk-3.0 xdotool libfontconfig1

Run:
    python3 xnglo_gtk.py

Usage:
    1. Click "Pick target window", then click the window you want to
       type into (e.g. a browser, a terminal, LibreOffice).
    2. Click keys on the xNglobord keyboard; they're typed into the
       target window. Long-press a letter for its capital form. Long-
       press space to open the font picker. Tap ?123 for numbers/
       symbols (double-tap to lock that page).

Known limitations vs. the Android app:
    - Works on X11 (via xdotool). Wayland compositors generally block
      the synthetic-input APIs xdotool needs for security reasons; on
      Wayland this will fail to type into other windows (xdotool errors
      will show in the terminal). GNOME/Ubuntu still default to X11 on
      many setups; if you're on Wayland, log into an "Ubuntu on Xorg"
      session from the login screen.
    - Voice input (the mic key) is not ported -- it's a no-op here.
    - The comma key's long-press ", : ; > <" popup is not ported;
      comma is a plain key.
"""
import ctypes
import ctypes.util
import os
import subprocess
import sys
import time

import gi
gi.require_version("Gtk", "3.0")
from gi.repository import Gtk, GLib, Gdk, Pango  # noqa: E402

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from xnglo_core import (  # noqa: E402
    KeyboardEngine, Dictionary, LocalFonts, FontManager,
    LAYOUT_LETTERS, LAYOUT_NUMERIC, YELLOW_HEX_LABELS, PINK_OPERATOR_LABELS,
    KEYCODE_DELETE, KEYCODE_ENTER, MODE_SWITCH_CODE, SHIFT_CODE, MIC_CODE,
)

HERE = os.path.dirname(os.path.abspath(__file__))
DICTIONARIES_DIR = os.path.join(HERE, "dictionaries")
FONTS_DIR = os.path.join(HERE, "fonts")
CONFIG_DIR = os.path.expanduser("~/.config/xnglobord")


def now_ms() -> int:
    return int(time.time() * 1000)


class FontRegistry:
    """Registers the bundled .ttf files with fontconfig for this process
    only (FcConfigAppFontAddFile), and figures out each file's real
    family name via fc-scan so Pango can select it by name."""

    def __init__(self):
        self._family_by_file: dict[str, str] = {}
        try:
            self._fc = ctypes.CDLL(ctypes.util.find_library("fontconfig") or "libfontconfig.so.1")
        except OSError:
            self._fc = None

    def register_all(self) -> None:
        for opt in LocalFonts.ALL:
            path = os.path.join(FONTS_DIR, opt.asset_file_name)
            if not os.path.isfile(path):
                continue
            if self._fc is not None:
                self._fc.FcConfigAppFontAddFile(None, path.encode("utf-8"))
            self._family_by_file[opt.asset_file_name] = self._scan_family(path, opt.display_name)

    @staticmethod
    def _scan_family(path: str, fallback: str) -> str:
        try:
            out = subprocess.run(
                ["fc-scan", "--format", "%{family}", path],
                capture_output=True, text=True, timeout=5,
            )
            family = out.stdout.strip().split(",")[0].strip()
            return family or fallback
        except (OSError, subprocess.SubprocessError):
            return fallback

    def family_for(self, asset_file_name: str, fallback: str) -> str:
        return self._family_by_file.get(asset_file_name, fallback)


class TargetWindow:
    """Tracks the X11 window we type into, via xdotool. Our own GTK
    window sets accept_focus=False so picking/clicking our keyboard
    never steals X focus from the target in the first place."""

    def __init__(self):
        self.window_id: str | None = None
        self.label = "(none picked yet)"

    def pick_interactively(self) -> None:
        try:
            out = subprocess.run(["xdotool", "selectwindow"], capture_output=True, text=True, timeout=30)
            wid = out.stdout.strip()
            if wid:
                self.window_id = wid
                self.label = self._name_for(wid)
        except (OSError, subprocess.SubprocessError) as e:
            self.label = f"(pick failed: {e})"

    def auto_pick_active(self) -> None:
        try:
            out = subprocess.run(["xdotool", "getactivewindow"], capture_output=True, text=True, timeout=5)
            wid = out.stdout.strip()
            if wid:
                self.window_id = wid
                self.label = self._name_for(wid)
        except (OSError, subprocess.SubprocessError):
            pass

    @staticmethod
    def _name_for(wid: str) -> str:
        try:
            out = subprocess.run(["xdotool", "getwindowname", wid], capture_output=True, text=True, timeout=5)
            return out.stdout.strip() or wid
        except (OSError, subprocess.SubprocessError):
            return wid

    def type_text(self, text: str) -> None:
        if not self.window_id or not text:
            return
        try:
            subprocess.run(
                ["xdotool", "type", "--window", self.window_id, "--clearmodifiers", "--", text],
                timeout=5,
            )
        except (OSError, subprocess.SubprocessError):
            pass

    def send_key(self, key_name: str) -> None:
        if not self.window_id:
            return
        try:
            subprocess.run(["xdotool", "key", "--window", self.window_id, key_name], timeout=5)
        except (OSError, subprocess.SubprocessError):
            pass


class XngloBordWindow(Gtk.Window):
    def __init__(self):
        super().__init__(title="xNglobord")
        self.set_type_hint(Gdk.WindowTypeHint.UTILITY)
        self.set_keep_above(True)
        self.set_skip_taskbar_hint(True)
        self.set_accept_focus(False)  # never steal X focus -- see TargetWindow docstring
        self.set_resizable(False)
        self.connect("destroy", Gtk.main_quit)

        self.target = TargetWindow()
        self.target.auto_pick_active()

        self.fonts = FontRegistry()
        self.fonts.register_all()
        self.font_manager = FontManager(CONFIG_DIR)

        self.dictionary = Dictionary()
        self.dictionary.load_all(DICTIONARIES_DIR)

        self.engine = KeyboardEngine(
            commit_text=self.target.type_text,
            delete_backward=lambda: self.target.send_key("BackSpace"),
            send_enter=lambda: self.target.send_key("Return"),
            schedule=lambda delay_ms, fn: GLib.timeout_add(delay_ms, self._fire_once(fn)),
            cancel=lambda token: GLib.source_remove(token),
            dictionary=self.dictionary,
            on_candidates_changed=self._render_candidates,
            on_state_changed=self._update_key_visuals,
            on_font_picker_requested=self._show_font_picker,
        )

        self.key_buttons: dict[int, list[Gtk.Button]] = {}
        self._build_ui()
        self._update_key_visuals()

    @staticmethod
    def _fire_once(fn):
        def wrapper():
            fn()
            return False  # don't repeat
        return wrapper

    # --- UI construction ---

    def _build_ui(self) -> None:
        outer = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
        self.add(outer)

        top = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        outer.pack_start(top, False, False, 4)
        pick_btn = Gtk.Button(label="Pick target window")
        pick_btn.connect("clicked", self._on_pick_target)
        top.pack_start(pick_btn, False, False, 0)
        self.target_label = Gtk.Label(label=f"Target: {self.target.label}")
        top.pack_start(self.target_label, False, False, 0)
        top.pack_end(Gtk.Label(label="Font:"), False, False, 0)
        self.font_combo = Gtk.ComboBoxText()
        for opt in LocalFonts.ALL:
            self.font_combo.append(opt.font_id, opt.display_name)
        self.font_combo.set_active_id(self.font_manager.get_selected_font_id())
        self.font_combo.connect("changed", self._on_font_changed)
        top.pack_end(self.font_combo, False, False, 0)

        self.candidates_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        outer.pack_start(self.candidates_box, False, False, 2)

        self.keys_container = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=3)
        outer.pack_start(self.keys_container, False, False, 4)
        self._render_layout(LAYOUT_LETTERS)

    def _render_layout(self, layout) -> None:
        for child in self.keys_container.get_children():
            self.keys_container.remove(child)
        self.key_buttons = {}

        for row in layout:
            row_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=3, homogeneous=True)
            self.keys_container.pack_start(row_box, False, False, 0)
            for code, label, _width in row:
                btn = Gtk.Button(label=label)
                btn.set_size_request(42, 42)
                self._style_key(btn, label)
                btn.connect("pressed", self._on_key_pressed, code)
                btn.connect("released", self._on_key_released, code)
                row_box.pack_start(btn, True, True, 0)
                self.key_buttons.setdefault(code, []).append(btn)
        self.keys_container.show_all()

    def _style_key(self, btn: Gtk.Button, label: str) -> None:
        css = Gtk.CssProvider()
        if label in YELLOW_HEX_LABELS:
            css.load_from_data(b"button { background: #f5d76e; }")
        elif label in PINK_OPERATOR_LABELS:
            css.load_from_data(b"button { background: #f2a2c2; }")
        else:
            return
        btn.get_style_context().add_provider(css, Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION)

    # --- key event plumbing ---

    def _on_key_pressed(self, _btn, code: int) -> None:
        self.engine.on_press(code, now_ms())

    def _on_key_released(self, _btn, code: int) -> None:
        self.engine.on_release(code, now_ms())
        self.engine.on_key(code, now_ms())
        if code == MODE_SWITCH_CODE:
            self._render_layout(LAYOUT_NUMERIC if self.engine.is_numeric_mode else LAYOUT_LETTERS)
            self._update_key_visuals()

    def _update_key_visuals(self) -> None:
        for btn in self.key_buttons.get(SHIFT_CODE, []):
            ctx = btn.get_style_context()
            if self.engine.is_caps_lock:
                ctx.add_class("suggested-action")
                btn.set_label("\u21ea")  # caps-lock glyph
            elif self.engine.is_shift_active:
                ctx.add_class("suggested-action")
                btn.set_label("\u21e7")
            else:
                ctx.remove_class("suggested-action")
                btn.set_label("\u21e7")

    def _render_candidates(self, words) -> None:
        for child in self.candidates_box.get_children():
            self.candidates_box.remove(child)
        family = self.fonts.family_for(
            self.font_manager.get_selected_option().asset_file_name,
            self.font_manager.get_selected_option().display_name,
        )
        for word in words:
            chip = Gtk.Button(label=word)
            chip.override_font(Pango.FontDescription(f"{family} 12"))
            chip.connect("clicked", lambda _b, w=word: self._on_candidate_clicked(w))
            self.candidates_box.pack_start(chip, False, False, 0)
        self.candidates_box.show_all()

    def _on_candidate_clicked(self, word: str) -> None:
        self.engine.apply_candidate(word)

    # --- target window picking ---

    def _on_pick_target(self, _btn) -> None:
        self.target.pick_interactively()
        self.target_label.set_text(f"Target: {self.target.label}")

    # --- font picker (long-press space, or the combo box) ---

    def _show_font_picker(self) -> None:
        menu = Gtk.Menu()
        for opt in LocalFonts.ALL:
            item = Gtk.MenuItem(label=opt.display_name)
            item.connect("activate", lambda _i, fid=opt.font_id: self._apply_font(fid))
            menu.append(item)
        menu.show_all()
        menu.popup_at_pointer(None)

    def _on_font_changed(self, combo: Gtk.ComboBoxText) -> None:
        font_id = combo.get_active_id()
        if font_id:
            self._apply_font(font_id)

    def _apply_font(self, font_id: str) -> None:
        self.font_manager.set_selected_font_id(font_id)
        self.font_combo.set_active_id(font_id)
        opt = LocalFonts.by_id(font_id)
        family = self.fonts.family_for(opt.asset_file_name, opt.display_name)
        font_desc = Pango.FontDescription(f"{family} 14")
        for buttons in self.key_buttons.values():
            for btn in buttons:
                btn.override_font(font_desc)
        self.engine.render_candidates()


def main():
    win = XngloBordWindow()
    win.show_all()
    Gtk.main()


if __name__ == "__main__":
    main()
