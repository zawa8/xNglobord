using System;
using System.Collections.Generic;
using System.Drawing.Text;
using System.IO;
using System.Linq;
using System.Text;
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
/// name read via System.Drawing.Text.PrivateFontCollection.
///
/// History: an earlier version used WPF's own
/// Fonts.GetFontFamilies(fileUri) to load fonts directly with no OS
/// registration at all -- didn't reliably pick up these custom subset
/// fonts in practice (confirmed on a real device: font-picker
/// selection had no visible effect). Switched to AddFontResourceEx
/// (the technique already proven to work with these exact .ttf files
/// on Android and the old Python port) for registration, but the
/// family-name lookup was then a hand-rolled binary TTF 'name' table
/// parser (TtfName.cs) -- also confirmed not to fix it, replaced here
/// with PrivateFontCollection, a proven BCL API for exactly this.
/// </summary>
public sealed class FontManager
{
    private readonly string configPath;
    private readonly string fontsDir;
    private readonly Dictionary<string, FontFamily> cache = new();
    private readonly HashSet<string> registeredFiles = new();
    private readonly PrivateFontCollection privateFonts = new();

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
    /// reads its real embedded family name via
    /// System.Drawing.Text.PrivateFontCollection (a proven BCL API --
    /// more reliable here than a hand-rolled binary 'name' table parser
    /// turned out to be with these particular custom subset fonts), and
    /// returns a FontFamily built from that name. Falls back to the
    /// system default font if the file is missing or registration fails.</summary>
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
                bool alreadyRegistered = !registeredFiles.Add(path);
                string realName;
                if (!alreadyRegistered)
                {
                    int result = NativeMethods.AddFontResourceEx(path, NativeMethods.FR_PRIVATE, IntPtr.Zero);
                    diag.Append($" AddFontResourceEx={result}");

                    privateFonts.AddFontFile(path);
                    var addedFamily = privateFonts.Families.LastOrDefault();
                    realName = addedFamily?.Name ?? option.DisplayName;
                    diag.Append($" gdiPlusName='{addedFamily?.Name ?? "(none)"}'");
                }
                else
                {
                    diag.Append(" AddFontResourceEx=(already registered)");
                    var match = privateFonts.Families.FirstOrDefault(f =>
                        string.Equals(f.Name, option.DisplayName, StringComparison.OrdinalIgnoreCase));
                    realName = match?.Name ?? option.DisplayName;
                }

                family = new FontFamily(realName);
                diag.Append($" -> FontFamily.Source='{family.Source}'");
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
