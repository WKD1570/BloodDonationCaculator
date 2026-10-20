#!/usr/bin/env python3
"""
Draws the Play Store listing graphics into docs/play-store/:

- feature-graphic.png (1024x500): the app icon's calendar and drop beside the app name.
- screenshots/NN-*.png (1080x1920): emulator captures under a short caption. Play takes at most a
  2:1 ratio and current phones are taller, so each capture is set on a 9:16 canvas, minus its
  status bar, running off the bottom edge.

Usage: python3 tools/render_store_graphics.py <font> <captures dir>
  font          Noto Sans CJK, the font the app's UI uses on Android:
                adb pull /system/fonts/NotoSansCJK-Regular.ttc
  captures dir  1080x2400 PNGs from `adb exec-out screencap -p`, named as in SCREENSHOTS below.
                Capture a debuggable build filled with made-up data, never real records.
Needs Pillow (pip install pillow). Run it as a plain script, so it can import render_icons.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

import render_icons as icon

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs/play-store")

APP_NAME = "연간채혈량 계산기"
TAGLINE = "다음 헌혈일 · 연간 채혈량 · 헌혈의 집"

# (output name, capture file, caption, sub-caption, rows to cut), in listing order: the first few
# matter most. Rows to cut are capture pixel rows dropped between the title bar and the content, for
# a screen that can't scroll its subject up any further.
SCREENSHOTS = [
    ("01-status", "status.png", "다음 헌혈 가능일을 한눈에", "최근 1년 채혈량과 종류별 헌혈 가능일", None),
    ("02-centers", "centers.png", "전국 헌혈의 집 찾기", "자주 헌혈하는 지역을 맨 위에", None),
    ("03-machines", "machines.png", "성분헌혈 기기로 센터 찾기", "혈소판 가능 여부와 기종별 대수", None),
    ("04-records", "records.png", "헌혈 기록을 한곳에", "종류·날짜·장소별로 정리돼요", None),
    ("05-calendar", "calendar.png", "헌혈 캘린더", "지난 헌혈과 다음 가능일을 달력에서", (232, 840)),
    ("06-mypage", "mypage.png", "헌혈 자격과 예측 혈량", "나이·체중·신장·성별로 바로 확인", None),
]

TOP = (0xF4, 0x3F, 0x5E)  # rose-500
BOTTOM = (0x9F, 0x12, 0x39)  # rose-800
WHITE = (255, 255, 255)

STATUS_BAR_PX = 84  # of a 1080x2400 capture at 420dpi


def font(path, size, weight):
    face = ImageFont.truetype(path, size, index=1)  # index 1 is Noto Sans CJK KR
    face.set_variation_by_axes([weight])
    return face


def gradient(width, height, horizontal=False):
    image = Image.new("RGB", (width, height))
    steps = width if horizontal else height
    draw = ImageDraw.Draw(image)
    for i in range(steps):
        t = i / max(steps - 1, 1)
        color = tuple(round(a + (b - a) * t) for a, b in zip(TOP, BOTTOM))
        draw.line([(i, 0), (i, height)] if horizontal else [(0, i), (width, i)], fill=color)
    return image


def centered_text(draw, canvas_width, y, text, face, fill):
    width = draw.textlength(text, font=face)
    draw.text(((canvas_width - width) / 2, y), text, font=face, fill=fill)


def glyph_mask(height):
    """The icon's calendar and drop as an anti-aliased mask, height px tall, holes cut out."""
    left, top, right, bottom = 30, 27, 78, 79  # the shapes' bounds in the 108-unit canvas
    scale = height * icon.SUPERSAMPLE / (bottom - top)
    size = (round((right - left) * scale), round((bottom - top) * scale))

    def to_px(points):
        return [((x - left) * scale, (y - top) * scale) for x, y in points]

    mask = Image.new("L", size, 0)
    draw = ImageDraw.Draw(mask)
    for outline in [icon.PAGE] + icon.RINGS:
        draw.polygon(to_px(outline), fill=255)
    for hole in icon.HOLES:
        draw.polygon(to_px(hole), fill=0)
    return mask.resize((size[0] // icon.SUPERSAMPLE, size[1] // icon.SUPERSAMPLE), Image.LANCZOS)


def feature_graphic(font_path):
    width, height = 1024, 500
    image = gradient(width, height, horizontal=True)
    draw = ImageDraw.Draw(image)
    mark = glyph_mask(250)
    title, tagline = font(font_path, 60, 800), font(font_path, 28, 500)
    text_width = max(draw.textlength(APP_NAME, font=title), draw.textlength(TAGLINE, font=tagline))
    gap = 56
    x = round((width - (mark.width + gap + text_width)) / 2)
    image.paste(WHITE, (x, (height - mark.height) // 2), mark)
    text_x = x + mark.width + gap
    draw.text((text_x, 170), APP_NAME, font=title, fill=WHITE)
    draw.text((text_x, 268), TAGLINE, font=tagline, fill=(255, 228, 232))
    return image


def screenshot(font_path, capture_path, caption, sub, cut=None):
    width, height = 1080, 1920
    image = gradient(width, height)
    draw = ImageDraw.Draw(image)
    centered_text(draw, width, 112, caption, font(font_path, 70, 800), WHITE)
    centered_text(draw, width, 222, sub, font(font_path, 38, 500), (255, 228, 232))

    capture = Image.open(capture_path).convert("RGB")
    if cut:
        start, end = cut
        joined = Image.new("RGB", (capture.width, capture.height - (end - start)))
        joined.paste(capture.crop((0, 0, capture.width, start)), (0, 0))
        joined.paste(capture.crop((0, end, capture.width, capture.height)), (0, start))
        capture = joined
    capture = capture.crop((0, STATUS_BAR_PX, capture.width, capture.height))
    shot_width = 864
    shot = capture.resize((shot_width, round(capture.height * shot_width / capture.width)), Image.LANCZOS)
    x, y, radius = (width - shot_width) // 2, 360, 44

    shadow = Image.new("L", (width, height), 0)
    bottom = min(y + shot.height, height + radius)  # a short screen ends above the canvas edge
    ImageDraw.Draw(shadow).rounded_rectangle([x, y + 12, x + shot_width, bottom], radius, fill=110)
    image.paste((60, 0, 20), (0, 0), shadow.filter(ImageFilter.GaussianBlur(24)))

    corners = Image.new("L", shot.size, 0)
    ImageDraw.Draw(corners).rounded_rectangle([0, 0, shot.width, shot.height], radius, fill=255)
    image.paste(shot, (x, y), corners)
    return image


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    font_path, captures = sys.argv[1], sys.argv[2]
    feature_graphic(font_path).save(os.path.join(OUT, "feature-graphic.png"))
    folder = os.path.join(OUT, "screenshots")
    os.makedirs(folder, exist_ok=True)
    for name, capture, caption, sub, cut in SCREENSHOTS:
        image = screenshot(font_path, os.path.join(captures, capture), caption, sub, cut)
        image.save(os.path.join(folder, f"{name}.png"))
    print(f"Wrote feature-graphic.png and {len(SCREENSHOTS)} screenshots to {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
