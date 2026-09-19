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
/// font via a WPF pack URI pointing at the embedded fonts/ resource
/// (see the .csproj: fonts\** is a &lt;Resource&gt;, baked into the exe),
/// selecting by the font's known real family name (see
/// LocalFontOption.RealFamilyName's own doc comment).
///
/// History: three earlier approaches were all tried and all confirmed
/// broken on a real device:
///   1. WPF's Fonts.GetFontFamilies(fileUri) on a loose .ttf file --
///      no visible effect.
///   2. AddFontResourceEx (Win32 GDI) + a hand-rolled TTF 'name' table
///      parser to find the family name -- no visible effect.
///   3. AddFontResourceEx + System.Drawing.Text.PrivateFontCollection
///      to find the family name -- confirmed actively wrong (silently
///      reused a *different*, earlier-loaded font's name when
///      PrivateFontCollection failed to add a new family, a known
///      GDI+ quirk with some font files).
/// All three shared the same core mechanism: register with GDI, then
/// ask WPF to find it by name via new FontFamily(nameString). Since
/// that combination never worked even once the correct name was
/// confirmed independently (from the pff repo's own build script),
/// this drops GDI entirely in favor of WPF's own native embedded-font
/// pack-URI mechanism, a different code path altogether.
/// </summary>
public sealed class FontManager
{
    private readonly string configPath;
    private readonly Dictionary<string, FontFamily> cache = new();

    public FontManager(string configDir)
    {
        Directory.CreateDirectory(configDir);
        this.configPath = Path.Combine(configDir, "xnglobord_prefs.txt");
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
    /// recent <see cref="LoadFontFamily"/> call. Shown in the UI
    /// (MainWindow's FontDiagText) since this app can't be run under a
    /// debugger on a normal user's machine.</summary>
    public string LastDiagnostic { get; private set; } = "";

    /// <summary>Loads (and caches) the FontFamily for a given font option,
    /// via a pack URI into the embedded fonts/ resource, selected by its
    /// known real family name.</summary>
    public FontFamily LoadFontFamily(LocalFontOption option)
    {
        if (cache.TryGetValue(option.AssetFileName, out var cached))
        {
            LastDiagnostic = $"{option.AssetFileName}: (cached) -> {cached.Source}";
            return cached;
        }

        FontFamily family;
        var diag = new StringBuilder();
        try
        {
            var baseUri = new Uri("pack://application:,,,/", UriKind.Absolute);
            var familySelector = $"./fonts/#{option.RealFamilyName}";
            family = new FontFamily(baseUri, familySelector);
            diag.Append($"{option.AssetFileName}: familySelector='{familySelector}' -> FontFamily.Source='{family.Source}'");
        }
        catch (Exception ex)
        {
            // Best-effort: bad URI, resource not found, etc. should fall
            // back to the system default rather than crash the keyboard.
            family = new FontFamily();
            diag.Append($"{option.AssetFileName}: EXCEPTION: {ex.GetType().Name}: {ex.Message}");
        }

        LastDiagnostic = diag.ToString();
        cache[option.AssetFileName] = family;
        return family;
    }
}
