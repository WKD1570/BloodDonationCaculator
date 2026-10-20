#!/usr/bin/env python3
"""
Draws the app icon's bitmap versions from the shapes in
app/src/main/res/drawable/ic_launcher_foreground.xml (a calendar page with a blood drop):

- app/src/main/res/mipmap-*/ic_launcher.webp and ic_launcher_round.webp, the icons Android 7.x
  (API 24-25) shows, since adaptive icons only exist from API 26.
- docs/play-store/icon-512.png, the Play Store listing icon: a full-bleed square, which Play masks
  itself.

Keep the shapes below in step with the vector drawable. Needs Pillow (pip install pillow).
Usage: python3 tools/render_icons.py
"""
import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

RED = (0xE1, 0x1D, 0x48)
WHITE = (0xFF, 0xFF, 0xFF)

# The adaptive icon's 108-unit canvas shows only its middle 72 units (18..90) through the launcher
# mask; the bitmaps map that visible square onto their own shape.
VISIBLE_START, VISIBLE_SIZE = 18, 72

SUPERSAMPLE = 8
LEGACY_SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def rounded_rect(left, top, right, bottom, radius, steps=16):
    points = []
    corners = [
        (right - radius, top + radius, -90),
        (right - radius, bottom - radius, 0),
        (left + radius, bottom - radius, 90),
        (left + radius, top + radius, 180),
    ]
    for cx, cy, start in corners:
        for i in range(steps + 1):
            angle = math.radians(start + 90 * i / steps)
            points.append((cx + radius * math.cos(angle), cy + radius * math.sin(angle)))
    return points


def cubic(p0, p1, p2, p3, steps=48):
    points = []
    for i in range(steps + 1):
        t = i / steps
        u = 1 - t
        points.append(
            (
                u**3 * p0[0] + 3 * u**2 * t * p1[0] + 3 * u * t**2 * p2[0] + t**3 * p3[0],
                u**3 * p0[1] + 3 * u**2 * t * p1[1] + 3 * u * t**2 * p2[1] + t**3 * p3[1],
            )
        )
    return points


def drop():
    """Tip at (54,52), round bottom of radius 8 centred on (54,65.5), as in the vector."""
    left = cubic((54, 52), (51.6, 56), (46, 60.2), (46, 65.5))
    bottom = [
        (54 + 8 * math.cos(math.radians(a)), 65.5 + 8 * math.sin(math.radians(a)))
        for a in [180 - 180 * i / 48 for i in range(49)]
    ]
    right = cubic((62, 65.5), (62, 60.2), (56.4, 56), (54, 52))
    return left + bottom + right


PAGE = rounded_rect(30, 33, 78, 79, 7)
RINGS = [rounded_rect(39, 27, 45, 38, 3), rounded_rect(63, 27, 69, 38, 3)]
HOLES = [[(34, 44), (74, 44), (74, 47), (34, 47)], drop()]


def render(size, shape):
    """The icon at size x size px; shape is "square" (full bleed), "rounded" or "circle"."""
    big = size * SUPERSAMPLE
    inset = 0 if shape == "square" else big * 2 / 48
    span = big - 2 * inset
    scale = span / VISIBLE_SIZE

    def to_px(points):
        return [(inset + (x - VISIBLE_START) * scale, inset + (y - VISIBLE_START) * scale) for x, y in points]

    # Colour and alpha are drawn and scaled down separately, so the anti-aliased edge doesn't pick
    # up the black of transparent pixels.
    color = Image.new("RGB", (big, big), RED)
    draw = ImageDraw.Draw(color)
    for outline in [PAGE] + RINGS:
        draw.polygon(to_px(outline), fill=WHITE)
    for hole in HOLES:
        draw.polygon(to_px(hole), fill=RED)

    alpha = Image.new("L", (big, big), 0)
    mask = ImageDraw.Draw(alpha)
    box = [inset, inset, big - inset, big - inset]
    if shape == "square":
        mask.rectangle(box, fill=255)
    elif shape == "rounded":
        mask.rounded_rectangle(box, radius=span * 8 / 44, fill=255)
    else:
        mask.ellipse(box, fill=255)

    icon = color.resize((size, size), Image.LANCZOS)
    icon.putalpha(alpha.resize((size, size), Image.LANCZOS))
    return icon


def main():
    for density, size in LEGACY_SIZES.items():
        folder = os.path.join(ROOT, f"app/src/main/res/mipmap-{density}")
        render(size, "rounded").save(os.path.join(folder, "ic_launcher.webp"), "WEBP", lossless=True, method=6)
        render(size, "circle").save(os.path.join(folder, "ic_launcher_round.webp"), "WEBP", lossless=True, method=6)
    store = os.path.join(ROOT, "docs/play-store")
    os.makedirs(store, exist_ok=True)
    render(512, "square").save(os.path.join(store, "icon-512.png"))
    print("Wrote legacy launcher icons and docs/play-store/icon-512.png")


if __name__ == "__main__":
    main()
