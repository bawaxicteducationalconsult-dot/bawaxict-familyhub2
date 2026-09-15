#!/usr/bin/env python3
"""Generate the PLACEHOLDER launcher icons for the FamilyHub Test build.

Test branding only: a WiFi glyph on the site's dark green (the palette in
site/index.html :root). Nothing here is final branding — replace the icon for
the official launch build.

Pure standard library on purpose: the checked-out PNGs are the build inputs, so
nobody has to run this, and it works without Pillow.

    python3 tools/make_icons.py

Writes app/src/main/res/mipmap-<density>/ic_launcher.png for API 24-25; API 26+
uses the adaptive icon in mipmap-anydpi-v26/ + drawable/ic_launcher_foreground.xml.
"""

import os
import struct
import zlib

# mipmap density -> icon edge in px (launcher icon sizes).
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

BRAND_TOP = (0x22, 0x88, 0x5F)      # --green-600
BRAND_BOTTOM = (0x12, 0x3B, 0x2C)   # --green-900
GLYPH = (255, 255, 255)

SUPERSAMPLE = 4


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def rounded_rect_alpha(x, y, w, h, r):
    """1.0 inside the rounded rect, 0.0 outside, using distance to the corner."""
    dx = max(r - x, x - (w - 1.0 - r), 0.0)
    dy = max(r - y, y - (h - 1.0 - r), 0.0)
    if dx * dx + dy * dy <= r * r:
        return 1.0
    return 0.0


def glyph_alpha(x, y, w, h):
    """1.0 where the white WiFi glyph covers this sample point."""
    cx, cy = w / 2.0, h * 0.735
    thickness = w * 0.075

    # Dot.
    dot_r = w * 0.055
    dx, dy = x - cx, y - cy
    if dx * dx + dy * dy <= dot_r * dot_r:
        return 1.0

    # Three arcs, drawn as an angular band above the dot.
    up = cy - y                      # positive above the dot
    across = abs(x - cx)
    if up < 0 or up < across * 0.62:  # ~116 degree fan centred on vertical
        return 0.0
    dist = (dx * dx + up * up) ** 0.5
    for radius in (w * 0.135, w * 0.245, w * 0.355):
        if abs(dist - radius) <= thickness / 2.0:
            return 1.0
    return 0.0


def sample(x, y, w, h, r):
    """RGBA for one sample point."""
    if rounded_rect_alpha(x, y, w, h, r) <= 0.0:
        return (0, 0, 0, 0)
    t = (x / w) * 0.34 + (y / h) * 0.66          # ~160deg, like .brand-mark
    rgb = lerp(BRAND_TOP, BRAND_BOTTOM, min(max(t, 0.0), 1.0))
    if glyph_alpha(x, y, w, h) > 0.0:
        rgb = GLYPH
    return rgb + (255,)


def render(size):
    corner = size * 0.18
    ss = SUPERSAMPLE
    rows = []
    for py in range(size):
        row = bytearray()
        for px in range(size):
            # Box-filter the supersampled cell for cheap anti-aliasing.
            acc = [0, 0, 0, 0]
            for sy in range(ss):
                for sx in range(ss):
                    x = px + (sx + 0.5) / ss
                    y = py + (sy + 0.5) / ss
                    rgba = sample(x, y, size, size, corner)
                    for i in range(4):
                        acc[i] += rgba[i]
            n = ss * ss
            row += bytes(min(255, int(round(c / n))) for c in acc)
        rows.append(bytes(row))
    return rows


def write_png(path, size, rows):
    raw = bytearray()
    for row in rows:
        raw.append(0)          # filter type 0 (None) per scanline
        raw += row

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)  # 8-bit RGBA
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + chunk(b"IEND", b""))
    with open(path, "wb") as fh:
        fh.write(png)
    return len(png)


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    res = os.path.normpath(os.path.join(here, "..", "app", "src", "main", "res"))
    for density, size in SIZES.items():
        out_dir = os.path.join(res, "mipmap-%s" % density)
        os.makedirs(out_dir, exist_ok=True)
        out = os.path.join(out_dir, "ic_launcher.png")
        n = write_png(out, size, render(size))
        print("%-9s %3dpx  %6d bytes  %s" % (density, size, n, os.path.relpath(out, here)))


if __name__ == "__main__":
    main()
