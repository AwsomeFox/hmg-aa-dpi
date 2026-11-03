#!/usr/bin/env python3
"""The AA DPI app's launcher icon (no image libraries): a rounded blue square with
a 3x3 grid of dots, smaller to the right (denser layout). Writes app/res/drawable-
{mdpi,hdpi,xhdpi}/ic_launcher.png.  python3 hmgapi-mod/tools/mkicon.py"""
import os, struct, zlib

def png(path, size):
    def px(x, y):
        s = size
        r = s * 0.18                       # corner radius
        cx = min(max(x, r), s - 1 - r); cy = min(max(y, r), s - 1 - r)
        inside = (x - cx) ** 2 + (y - cy) ** 2 <= r * r
        if not inside:
            return (0, 0, 0, 0)
        for i, col in enumerate((0.27, 0.5, 0.73)):
            for row in (0.27, 0.5, 0.73):
                rad = s * (0.095 - 0.022 * i)
                if (x - col * s) ** 2 + (y - row * s) ** 2 <= rad * rad:
                    return (240, 246, 255, 255)
        return (40, 120, 220, 255)
    raw = b"".join(b"\0" + b"".join(struct.pack("4B", *px(x, y)) for x in range(size)) for y in range(size))
    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").write(data)

here = os.path.dirname(os.path.abspath(__file__))
for d, s in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96)):
    png(os.path.join(here, "..", "app", "res", "drawable-" + d, "ic_launcher.png"), s)
print("icons written")
