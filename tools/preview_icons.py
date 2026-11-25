#!/usr/bin/env python3
"""Renders every vector drawable in res/drawable to one contact sheet PNG for a visual check."""
import io
import os
import xml.etree.ElementTree as ET

import cairosvg
from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DRAW = os.path.join(ROOT, "res", "drawable")
A = "{http://schemas.android.com/apk/res/android}"


def to_svg(path):
    root = ET.parse(path).getroot()
    if root.tag != "vector":
        return None
    vw, vh = root.get(A + "viewportWidth"), root.get(A + "viewportHeight")
    parts = []
    for p in root.iter("path"):
        color = p.get(A + "fillColor", "#FFFFFFFF")
        argb = color.lstrip("#")
        if len(argb) == 8:
            alpha = int(argb[:2], 16) / 255.0
            rgb = "#" + argb[2:]
        else:
            alpha, rgb = 1.0, "#" + argb
        rule = "evenodd" if p.get(A + "fillType") == "evenOdd" else "nonzero"
        parts.append('<path d="%s" fill="%s" fill-opacity="%.3f" fill-rule="%s"/>' % (p.get(A + "pathData"), rgb, alpha, rule))
    return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %s %s" width="96" height="96">%s</svg>' % (vw, vh, "".join(parts))


def main():
    names = sorted(f[:-4] for f in os.listdir(DRAW) if f.endswith(".xml"))
    tiles = []
    for n in names:
        svg = to_svg(os.path.join(DRAW, n + ".xml"))
        if svg is None:
            continue
        png = cairosvg.svg2png(bytestring=svg.encode("utf-8"))
        tiles.append((n, Image.open(io.BytesIO(png)).convert("RGBA")))
    cols = 8
    cell_w, cell_h = 150, 130
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell_w, rows * cell_h), (40, 44, 52, 255))
    d = ImageDraw.Draw(sheet)
    for i, (n, img) in enumerate(tiles):
        x, y = (i % cols) * cell_w, (i // cols) * cell_h
        sheet.alpha_composite(img, (x + (cell_w - 96) // 2, y + 6))
        d.text((x + 6, y + 108), n, fill=(230, 230, 230, 255))
    out = os.path.join(ROOT, "build", "preview", "icons.png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    sheet.save(out)
    print(out, len(tiles))


if __name__ == "__main__":
    main()
