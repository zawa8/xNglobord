using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Windows.Media;

namespace XngloBord;

public sealed record LocalFontOption(string FontId, string DisplayName, string AssetFileName)
{
    /// <summary>The font's real internal family name, as set by the pff
    /// repo's scripts/xi38py/rename_utf_fonts.py -- always exactly
    /// FontId + "utf" (verified against that script directly: its
    /// FONT_RENAMES table maps e.g. 'hindixv38' -> 'hindixv38utf' for
    /// all 11 fonts, no exceptions). Hardcoded here rather than
    /// discovered at runtime, since two different runtime-discovery
    /// approaches (a hand-rolled TTF 'name' table parser, then
    /// System.Drawing.Text.PrivateFontCollection) both turned out to be
    /// unreliable with these particular custom subset fonts.</summary>
    public string RealFamilyName => FontId + "utf";
}

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
/// .ttf via AddFontResourceEx (GDI, process-private) + its known real
/// family name (LocalFontOption.RealFamilyName -- see that property's
/// own doc comment for why this is hardcoded rather than discovered).
///
/// History: an earlier version used WPF's own
/// Fonts.GetFontFamilies(fileUri) to load fonts directly with no OS
/// registration at all -- didn't reliably pick up these custom subset
/// fonts in practice (confirmed on a real device: font-picker
/// selection had no visible effect). Switched to AddFontResourceEx for
/// registration (confirmed working), but paired it first with a
/// hand-rolled binary TTF 'name' table parser, then with
/// System.Drawing.Text.PrivateFontCollection, to discover each font's
/// real name at runtime -- both also confirmed broken on a real device
/// (the PrivateFontCollection one silently returned a *different*
/// font's name after failing to add a new one, a known GDI+ quirk with
/// some font files). Hardcoding the name is what actually worked.
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

    /// <summary>Human-readable detail on what happened during the most
    /// recent <see cref="LoadFontFamily"/> call -- file found?,
    /// AddFontResourceEx return value, parsed TTF family name, what
    /// FontFamily.Source WPF actually resolved to. Shown in the UI
    /// (MainWindow's FontDiagText) since this app can't be run under a
    /// debugger on a normal user's machine.</summary>
    public string LastDiagnostic { get; private set; } = "";

    /// <summary>Loads (and caches) the FontFamily for a given font option:
    /// registers the .ttf with GDI via AddFontResourceEx (process-private,
    /// FR_PRIVATE, needed so WPF can actually find/render it by name),
    /// then builds a FontFamily from its known real name (see
    /// LocalFontOption.RealFamilyName). Falls back to the system default
    /// font if the file is missing or registration fails.</summary>
    public FontFamily LoadFontFamily(LocalFontOption option)
    {
        if (cache.TryGetValue(option.AssetFileName, out var cached))
        {
            LastDiagnostic = $"{option.AssetFileName}: (cached) -> {cached.Source}";
            return cached;
        }

        var path = Path.Combine(fontsDir, option.AssetFileName);
        FontFamily family;
        var diag = new StringBuilder();
        diag.Append($"{option.AssetFileName}: path={path}");
        try
        {
            bool exists = File.Exists(path);
            diag.Append($" exists={exists}");
            if (exists)
            {
                if (registeredFiles.Add(path))
                {
                    int result = NativeMethods.AddFontResourceEx(path, NativeMethods.FR_PRIVATE, IntPtr.Zero);
                    diag.Append($" AddFontResourceEx={result}");
                }
                else
                {
                    diag.Append(" AddFontResourceEx=(already registered)");
                }

                family = new FontFamily(option.RealFamilyName);
                diag.Append($" realFamilyName='{option.RealFamilyName}' -> FontFamily.Source='{family.Source}'");
            }
            else
            {
                family = new FontFamily();
                diag.Append(" -> using system default");
            }
        }
        catch (Exception ex)
        {
            // Best-effort: missing file, bad registration, etc. should
            // fall back to the system default rather than crash the
            // keyboard.
            family = new FontFamily();
            diag.Append($" EXCEPTION: {ex.GetType().Name}: {ex.Message}");
        }

        LastDiagnostic = diag.ToString();
        cache[option.AssetFileName] = family;
        return family;
    }
}
