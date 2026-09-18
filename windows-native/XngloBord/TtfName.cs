using System;
using System.IO;
using System.Text;

namespace XngloBord;

/// <summary>
/// Tiny, dependency-free parser for a TTF/OTF file's 'name' table --
/// just enough to read the font's real family name (nameID 1, Windows
/// platform preferred) so it can be referenced by name after being
/// registered with <see cref="NativeMethods.AddFontResourceEx"/>.
///
/// Port of the old windows/ttf_name.py from the earlier Python port.
/// Used here instead of relying on WPF's own
/// <c>Fonts.GetFontFamilies(fileUri)</c> file-based loading, which
/// doesn't reliably pick up these custom subset fonts (missing some
/// metadata WPF's own font parser expects, most likely) -- GDI-based
/// loading via AddFontResourceEx is the same proven-working technique
/// both the Android app (Typeface.createFromAsset) and the old Python
/// port (also AddFontResourceEx) already use successfully with these
/// exact .ttf files.
/// </summary>
internal static class TtfName
{
    public static string ReadFamilyName(string path, string fallback)
    {
        try
        {
            byte[] data = File.ReadAllBytes(path);
            int numTables = ReadUInt16BE(data, 4);
            int? nameTableOffset = null;
            for (int i = 0; i < numTables; i++)
            {
                int recOff = 12 + i * 16;
                string tag = Encoding.ASCII.GetString(data, recOff, 4);
                if (tag == "name")
                {
                    nameTableOffset = (int)ReadUInt32BE(data, recOff + 8);
                    break;
                }
            }
            if (nameTableOffset is null) return fallback;

            int b = nameTableOffset.Value;
            int count = ReadUInt16BE(data, b + 2);
            int stringOffset = ReadUInt16BE(data, b + 4);
            string? best = null;

            for (int i = 0; i < count; i++)
            {
                int recOff = b + 6 + i * 12;
                int platformId = ReadUInt16BE(data, recOff);
                int nameId = ReadUInt16BE(data, recOff + 6);
                int length = ReadUInt16BE(data, recOff + 8);
                int offset = ReadUInt16BE(data, recOff + 10);

                if (nameId != 1) continue; // 1 = Font Family name

                int valueOff = b + stringOffset + offset;
                if (valueOff < 0 || valueOff + length > data.Length) continue;

                if (platformId == 3) // Windows, UTF-16BE
                {
                    best = Encoding.BigEndianUnicode.GetString(data, valueOff, length);
                    break; // prefer Windows-platform entries
                }
                else if (platformId is 0 or 1 && best is null) // Unicode / Mac
                {
                    best = platformId == 0
                        ? Encoding.BigEndianUnicode.GetString(data, valueOff, length)
                        : Encoding.GetEncoding("iso-8859-1").GetString(data, valueOff, length); // close enough to mac-roman for ASCII names
                }
            }

            var trimmed = best?.Trim();
            return string.IsNullOrEmpty(trimmed) ? fallback : trimmed;
        }
        catch (Exception)
        {
            return fallback;
        }
    }

    private static int ReadUInt16BE(byte[] data, int offset) => (data[offset] << 8) | data[offset + 1];

    private static uint ReadUInt32BE(byte[] data, int offset) =>
        ((uint)data[offset] << 24) | ((uint)data[offset + 1] << 16) | ((uint)data[offset + 2] << 8) | data[offset + 3];
}
