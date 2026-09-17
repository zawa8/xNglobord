using System.Collections.Generic;

namespace XngloBord;

/// <summary>One key: the code committed (positive = literal char code,
/// negative = special action), the label shown, and its width as a
/// percentage of the row (matches android:keyWidth="N%p").</summary>
public readonly record struct KeyDef(int Code, string Label, double WidthPercent);

/// <summary>
/// Transcribed directly from res/xml/keys_xi38.xml and
/// res/xml/keys_numeric.xml on the xNglobord main branch (as of the
/// digit row / QRTA row / punctuation row / resized bottom row
/// commits). Keep this in sync manually if those files change --
/// there's no automated sync between the Android resource XML and
/// this file.
/// </summary>
public static class Layouts
{
    private const double Default10 = 10.0;

    public static readonly List<List<KeyDef>> Letters = new()
    {
        // Digit row
        new()
        {
            new(48, "0", Default10), new(49, "1", Default10), new(50, "2", Default10),
            new(51, "3", Default10), new(52, "4", Default10), new(53, "5", Default10),
            new(54, "6", Default10), new(55, "7", Default10), new(56, "8", Default10),
            new(57, "9", Default10),
        },
        // Direct-access capitals row
        new()
        {
            new(81, "Q", Default10), new(82, "R", Default10), new(84, "T", Default10),
            new(65, "A", Default10), new(83, "S", Default10), new(68, "D", Default10),
            new(71, "G", Default10), new(72, "H", Default10), new(74, "J", Default10),
            new(75, "K", Default10),
        },
        // Punctuation/bracket row
        new()
        {
            new(123, "{", Default10), new(91, "[", Default10), new(40, "(", Default10),
            new(60, "<", Default10), new(62, ">", Default10), new(45, "-", Default10),
            new(58, ":", Default10), new(34, "\"", Default10), new(47, "/", Default10),
            new(63, "?", Default10),
        },
        // qwertyuiop
        new()
        {
            new(113, "q", Default10), new(119, "w", Default10), new(101, "e", Default10),
            new(114, "r", Default10), new(116, "t", Default10), new(121, "y", Default10),
            new(117, "u", Default10), new(105, "i", Default10), new(111, "o", Default10),
            new(112, "p", Default10),
        },
        // asdfghjkl + mic (mic is a no-op on desktop -- see KeyboardEngine)
        new()
        {
            new(97, "a", Default10), new(115, "s", Default10), new(100, "d", Default10),
            new(102, "f", Default10), new(103, "g", Default10), new(104, "h", Default10),
            new(106, "j", Default10), new(107, "k", Default10), new(108, "l", Default10),
            new(KeyboardEngine.MicCode, "\U0001F3A4", Default10),
        },
        // shift, zxcvbnm, backspace
        new()
        {
            new(KeyboardEngine.ShiftCode, "\u21e7", 12), new(122, "z", 11), new(120, "x", 11),
            new(99, "c", 11), new(118, "v", 11), new(98, "b", 11), new(110, "n", 11),
            new(109, "m", 11), new(KeyboardEngine.KeycodeDelete, "\u232b", 11),
        },
        // ?123, comma, space, period, @, enter
        new()
        {
            new(KeyboardEngine.ModeSwitchCode, "?123", 14), new(44, ",:", 12),
            new(32, "space", 32), new(46, ".", 12), new(64, "@", 10),
            new(KeyboardEngine.KeycodeEnter, "\u23ce", 20),
        },
    };

    public static readonly List<List<KeyDef>> Numeric = new()
    {
        new()
        {
            new(48, "0", Default10), new(49, "1", Default10), new(50, "2", Default10),
            new(51, "3", Default10), new(43, "+", Default10), new(45, "-", Default10),
            new(47, "/", Default10), new(42, "*", Default10), new(37, "%", Default10),
            new(61, "=", Default10),
        },
        new()
        {
            new(52, "4", Default10), new(53, "5", Default10), new(54, "6", Default10),
            new(55, "7", Default10), new(69, "E", Default10), new(85, "U", Default10),
            new(73, "I", Default10), new(79, "O", Default10), new(77, "M", Default10),
            new(88, "X", Default10),
        },
        // L Y V W P F: xi38's own letters for hex 10-15, replacing the standard A-F
        new()
        {
            new(56, "8", Default10), new(57, "9", Default10), new(76, "L", Default10),
            new(89, "Y", Default10), new(95, "_", Default10), new(34, "\"", Default10),
            new(35, "#", Default10), new(36, "$", Default10), new(38, "&", Default10),
            new(42, "*", Default10),
        },
        new()
        {
            new(86, "V", Default10), new(87, "W", Default10), new(80, "P", Default10),
            new(70, "F", Default10), new(40, "(", Default10), new(91, "[", Default10),
            new(123, "{", Default10), new(41, ")", Default10), new(93, "]", Default10),
            new(125, "}", Default10),
        },
        new()
        {
            new(KeyboardEngine.ModeSwitchCode, "xyz", 11), new(39, "'", 11), new(96, "`", 11),
            new(126, "~", 11), new(124, "|", 11), new(94, "^", 11), new(92, "\\", 11),
            new(63, "?", 11), new(46, ".", 12),
        },
    };

    /// <summary>Hex digits (0-9, L Y V W P F) and E U I O M X get the same
    /// yellow/pink key-color treatment as the Android app
    /// (XngloKeyboardView.colorForLabel/OPERATOR_LETTERS).</summary>
    public static readonly HashSet<string> YellowHexLabels = new() { "0","1","2","3","4","5","6","7","8","9","L","Y","V","W","P","F" };
    public static readonly HashSet<string> PinkOperatorLabels = new() { "E","U","I","O","M","X" };
}
