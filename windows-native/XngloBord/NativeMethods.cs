using System;
using System.Runtime.InteropServices;

namespace XngloBord;

/// <summary>
/// Win32 interop for synthetic keyboard input and window styling.
///
/// This replaces the earlier Python/ctypes version
/// (windows/xnglo_tk.py), which is the suspected source of the
/// "characters don't get typed in Notepad++" bug: its INPUT/KEYBDINPUT
/// ctypes structs used a bare Union without <c>LayoutKind.Explicit</c>
/// semantics spelled out, which is a known-fragile pattern on x64 --
/// the union's actual byte layout can come out wrong if the runtime
/// doesn't lay out a nested-union-via-inheritance ctypes trick exactly
/// like the real C <c>tagINPUT</c> struct. Wrong bytes there mean
/// SendInput silently no-ops or corrupts unrelated fields depending on
/// which window happens to read them -- consistent with "works
/// sometimes, not in this specific app".
///
/// This C# version uses <see cref="StructLayout"/>(<see
/// cref="LayoutKind.Explicit"/>) with explicit <see
/// cref="FieldOffsetAttribute"/> on the union members, which is the
/// long-established, heavily-used pattern for SendInput wrappers in
/// .NET (the same layout the InputSimulator library and most
/// reference SendInput samples use) -- byte-for-byte matches the real
/// Win32 <c>INPUT</c> struct on x64, no ambiguity.
///
/// If characters still don't land in a specific target app after this
/// fix, the next most likely cause is UIPI (User Interface Privilege
/// Isolation): Windows silently drops SendInput calls aimed at a
/// window running at a *higher* integrity level than the sending
/// process. If Notepad++ (or anything else) was launched "Run as
/// administrator" while xNglobord was not, that's almost certainly
/// why -- run both at the same elevation level.
/// </summary>
internal static class NativeMethods
{
    #region SendInput structs (LayoutKind.Explicit -- matches real Win32 tagINPUT)

    [StructLayout(LayoutKind.Sequential)]
    public struct KEYBDINPUT
    {
        public ushort wVk;
        public ushort wScan;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct MOUSEINPUT
    {
        public int dx;
        public int dy;
        public uint mouseData;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct HARDWAREINPUT
    {
        public uint uMsg;
        public ushort wParamL;
        public ushort wParamH;
    }

    [StructLayout(LayoutKind.Explicit)]
    public struct InputUnion
    {
        [FieldOffset(0)] public MOUSEINPUT mi;
        [FieldOffset(0)] public KEYBDINPUT ki;
        [FieldOffset(0)] public HARDWAREINPUT hi;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct INPUT
    {
        public uint type;
        public InputUnion u;
    }

    public const uint INPUT_KEYBOARD = 1;
    public const uint KEYEVENTF_UNICODE = 0x0004;
    public const uint KEYEVENTF_KEYUP = 0x0002;
    public const ushort VK_BACK = 0x08;
    public const ushort VK_RETURN = 0x0D;
    public const ushort VK_LEFT = 0x25;

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

    #endregion

    #region Window styling (WS_EX_NOACTIVATE, so our own window never steals focus)

    public const int GWL_EXSTYLE = -20;
    public const int WS_EX_NOACTIVATE = 0x08000000;
    public const int WS_EX_TOOLWINDOW = 0x00000080;

    [DllImport("user32.dll", EntryPoint = "GetWindowLongPtr", SetLastError = true)]
    private static extern IntPtr GetWindowLongPtr64(IntPtr hWnd, int nIndex);

    [DllImport("user32.dll", EntryPoint = "SetWindowLongPtr", SetLastError = true)]
    private static extern IntPtr SetWindowLongPtr64(IntPtr hWnd, int nIndex, IntPtr dwNewLong);

    public static IntPtr GetWindowLongPtr(IntPtr hWnd, int nIndex) => GetWindowLongPtr64(hWnd, nIndex);
    public static IntPtr SetWindowLongPtr(IntPtr hWnd, int nIndex, IntPtr dwNewLong) => SetWindowLongPtr64(hWnd, nIndex, dwNewLong);

    #endregion

    #region Private font loading (AddFontResourceEx)

    public const uint FR_PRIVATE = 0x10;

    [DllImport("gdi32.dll", SetLastError = true)]
    public static extern int AddFontResourceEx(string lpszFilename, uint fl, IntPtr pdv);

    #endregion

    /// <summary>Types Unicode text into whichever window currently has
    /// keyboard focus (never our own -- see WS_EX_NOACTIVATE above),
    /// one KEYEVENTF_UNICODE down+up pair per char.</summary>
    public static void SendUnicodeText(string text)
    {
        if (string.IsNullOrEmpty(text)) return;

        var inputs = new INPUT[text.Length * 2];
        for (int i = 0; i < text.Length; i++)
        {
            char c = text[i];
            inputs[i * 2] = new INPUT
            {
                type = INPUT_KEYBOARD,
                u = new InputUnion { ki = new KEYBDINPUT { wVk = 0, wScan = c, dwFlags = KEYEVENTF_UNICODE, time = 0, dwExtraInfo = IntPtr.Zero } }
            };
            inputs[i * 2 + 1] = new INPUT
            {
                type = INPUT_KEYBOARD,
                u = new InputUnion { ki = new KEYBDINPUT { wVk = 0, wScan = c, dwFlags = KEYEVENTF_UNICODE | KEYEVENTF_KEYUP, time = 0, dwExtraInfo = IntPtr.Zero } }
            };
        }

        uint sent = SendInput((uint)inputs.Length, inputs, Marshal.SizeOf(typeof(INPUT)));
        if (sent != inputs.Length)
        {
            int err = Marshal.GetLastWin32Error();
            System.Diagnostics.Debug.WriteLine($"SendInput only accepted {sent}/{inputs.Length} events (GetLastError={err}).");
        }
    }

    /// <summary>Sends a single virtual-key down+up (e.g. VK_BACK, VK_RETURN).</summary>
    public static void SendVirtualKey(ushort vk)
    {
        var inputs = new[]
        {
            new INPUT { type = INPUT_KEYBOARD, u = new InputUnion { ki = new KEYBDINPUT { wVk = vk, wScan = 0, dwFlags = 0, time = 0, dwExtraInfo = IntPtr.Zero } } },
            new INPUT { type = INPUT_KEYBOARD, u = new InputUnion { ki = new KEYBDINPUT { wVk = vk, wScan = 0, dwFlags = KEYEVENTF_KEYUP, time = 0, dwExtraInfo = IntPtr.Zero } } },
        };
        SendInput((uint)inputs.Length, inputs, Marshal.SizeOf(typeof(INPUT)));
    }
}
