using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Windows.Media;

namespace XngloBord;

public sealed record LocalFontOption(string FontId, string DisplayName, string AssetFileName);

/// <summary>Same order/entries as the Android app's LocalFonts.kt -- please don't reshuffle.</summary>
public static class LocalFonts
{
    public static readonly List<LocalFontOption> All = new()
    {
        new("eNgliSxe38", "xNgloiNgliS", "eNgliSxe38.ttf"),
        new("hindixv38", "xNglovinqi", "hindixv38.ttf"),
        new("bengalixb38", "xNglobNgali", "bengalixb38.ttf"),
        new("jeluguxj38", "xNglojelugu", "jeluguxj38.ttf"),
        new("knRaxk38", "xNgloknRa", "knRaxk38.ttf"),
        new("pnzabixp38", "xNglopnzabi", "pnzabixp38.ttf"),
        new("mlyalxmxm38", "xNglomlyalxm", "mlyalxmxm38.ttf"),
        new("oriyaxo38", "xNglooriya", "oriyaxo38.ttf"),
        new("guzrajixg38", "xNgloguzraji", "guzrajixg38.ttf"),
        new("tmilxt38", "xNglotmil", "tmilxt38.ttf"),
        new("sinhlaxs38", "xNglosinvla", "sinhlaxs38.ttf"),
    };
    public const string DefaultFontId = "hindixv38";

    public static LocalFontOption? ById(string fontId) => All.FirstOrDefault(f => f.FontId == fontId);
}

/// <summary>
/// Reads/writes the selected font id in a small local prefs file (the
/// desktop equivalent of Android SharedPreferences), and loads each
/// .ttf via AddFontResourceEx (GDI, process-private) + its real family
/// name read straight out of the file (see TtfName.cs).
///
/// This replaced an earlier version that used WPF's own
/// Fonts.GetFontFamilies(fileUri) to load fonts directly with no OS
/// registration -- simpler in principle, but it didn't reliably pick
/// up these custom subset fonts in practice (confirmed: font-picker
/// selection had no visible effect after a real on-device test).
/// AddFontResourceEx is the same technique already proven to work with
/// these exact .ttf files, both on Android (Typeface.createFromAsset)
/// and in the earlier Python/Tkinter Windows port.
/// </summary>
public sealed class FontManager
{
    private readonly string configPath;
    private readonly string fontsDir;
    private readonly Dictionary<string, FontFamily> cache = new();
    private readonly HashSet<string> registeredFiles = new();

    public FontManager(string configDir, string fontsDir)
    {
        Directory.CreateDirectory(configDir);
        this.configPath = Path.Combine(configDir, "xnglobord_prefs.txt");
        this.fontsDir = fontsDir;
    }

    public string GetSelectedFontId()
    {
        try
        {
            var value = File.ReadAllText(configPath).Trim();
            return LocalFonts.ById(value) is not null ? value : LocalFonts.DefaultFontId;
        }
        catch (Exception)
        {
            return LocalFonts.DefaultFontId;
        }
    }

    public void SetSelectedFontId(string fontId) => File.WriteAllText(configPath, fontId);

    public LocalFontOption GetSelectedOption() => LocalFonts.ById(GetSelectedFontId()) ?? LocalFonts.All[1];

    /// <summary>Loads (and caches) the FontFamily for a given font option:
    /// registers the .ttf with GDI via AddFontResourceEx (process-private,
    /// FR_PRIVATE), reads its real embedded family name, and returns a
    /// FontFamily built from that name. Falls back to the system default
    /// font if the file is missing or registration fails.</summary>
    public FontFamily LoadFontFamily(LocalFontOption option)
    {
        if (cache.TryGetValue(option.AssetFileName, out var cached)) return cached;

        var path = Path.Combine(fontsDir, option.AssetFileName);
        FontFamily family;
        try
        {
            if (File.Exists(path))
            {
                if (registeredFiles.Add(path))
                {
                    NativeMethods.AddFontResourceEx(path, NativeMethods.FR_PRIVATE, IntPtr.Zero);
                }
                var realName = TtfName.ReadFamilyName(path, option.DisplayName);
                family = new FontFamily(realName);
            }
            else
            {
                family = new FontFamily();
            }
        }
        catch (Exception)
        {
            // Best-effort: missing file, bad registration, etc. should
            // fall back to the system default rather than crash the
            // keyboard.
            family = new FontFamily();
        }

        cache[option.AssetFileName] = family;
        return family;
    }
}
