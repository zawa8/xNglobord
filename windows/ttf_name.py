"""
Tiny, dependency-free parser for a TTF/OTF file's 'name' table, just
enough to read the font's real family name (nameID 1, Windows platform)
so we can select it by name after loading it as a private font via
AddFontResourceEx. No external font libraries required.
"""
import struct


def read_family_name(path: str, fallback: str) -> str:
    try:
        with open(path, "rb") as f:
            data = f.read()
        num_tables = struct.unpack(">H", data[4:6])[0]
        name_table_offset = None
        for i in range(num_tables):
            rec_off = 12 + i * 16
            tag = data[rec_off:rec_off + 4]
            if tag == b"name":
                name_table_offset = struct.unpack(">I", data[rec_off + 8:rec_off + 12])[0]
                break
        if name_table_offset is None:
            return fallback

        base = name_table_offset
        count = struct.unpack(">H", data[base + 2:base + 4])[0]
        string_offset = struct.unpack(">H", data[base + 4:base + 6])[0]
        best = None
        for i in range(count):
            rec_off = base + 6 + i * 12
            platform_id, encoding_id, language_id, name_id, length, offset = struct.unpack(
                ">HHHHHH", data[rec_off:rec_off + 12]
            )
            if name_id != 1:  # 1 = Font Family name
                continue
            value_off = base + string_offset + offset
            raw = data[value_off:value_off + length]
            if platform_id == 3:  # Windows, usually UTF-16BE
                text = raw.decode("utf-16-be", errors="ignore")
                best = text  # prefer Windows-platform entries
                break
            elif platform_id in (0, 1) and best is None:  # Unicode / Mac, usually ASCII-ish
                try:
                    best = raw.decode("utf-16-be", errors="ignore") if platform_id == 0 else raw.decode(
                        "mac-roman", errors="ignore")
                except UnicodeDecodeError:
                    best = raw.decode("latin-1", errors="ignore")
        return best.strip() if best else fallback
    except (OSError, struct.error, IndexError):
        return fallback
