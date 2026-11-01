"""Writes the table a Storage Terminal's search reads Chinese names by: every reading of every common character, without
tones, a line for each reading: the syllable, then its characters.

    uv run --with pypinyin python tools/pinyin_table.py
"""
from pathlib import Path
import unicodedata

from pypinyin.pinyin_dict import pinyin_dict

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/residual/pinyin.txt"


def plain(reading):
    """A reading with its tone marks off, ü written v as pinyin is typed."""
    out = ""
    for c in unicodedata.normalize("NFD", reading.lower()):
        if c == "̈":
            out = out[:-1] + "v"
        elif "a" <= c <= "z":
            out += c
    return out


def main():
    syllables = {}
    for code, readings in pinyin_dict.items():
        if 0x4E00 <= code <= 0x9FFF:
            for reading in {plain(r) for r in readings.split(",")}:
                if reading:
                    syllables.setdefault(reading, []).append(chr(code))
    OUT.write_text("".join(s + "".join(sorted(chars)) + "\n" for s, chars in sorted(syllables.items())), encoding="utf-8")
    print(len(syllables), "syllables,", sum(map(len, syllables.values())), "readings,", OUT.stat().st_size, "bytes")


if __name__ == "__main__":
    main()
