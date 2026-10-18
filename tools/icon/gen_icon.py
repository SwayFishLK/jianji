"""简记应用图标生成（M5.1 方案 B：墨底蓝「简」，候选字体同款文楷字形）。

用法：
    python tools/icon/gen_icon.py [字体.ttf] [输出 res 目录]
默认字体 reference/fonts/LXGWWenKaiGBScreen.ttf（不入库，缺了就重新下载，见 tools/fontgen/README.md）；
默认输出 app/src/main/res。

生成内容：
    传统图标   mipmap-*/ic_launcher.png、ic_launcher_round.png（圆角方 / 圆形，墨底 + 蓝「简」）
    自适应前景 mipmap-*/ic_launcher_foreground.png（透明底，字形在 66/108 安全区内）
自适应图标 XML（mipmap-anydpi-v26/）与背景色（values/colors.xml 的 ic_launcher_background）随手工维护。
"""

import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FONT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    ROOT, "reference", "fonts", "LXGWWenKaiGBScreen.ttf")
RES = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, "app", "src", "main", "res")

BG = (23, 25, 30)        # #17191E 「墨夜」底
FG = (91, 141, 239)      # #5B8DEF 主题蓝
GLYPH = "简"

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
SS = 4  # 超采样倍数


def draw_glyph(size_px, font_px):
    im = Image.new("RGBA", (size_px, size_px), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    f = ImageFont.truetype(FONT, font_px)
    d.text((size_px / 2, size_px / 2 + size_px * 0.01), GLYPH, font=f, fill=FG, anchor="mm")
    return im


def legacy(px, shape):
    """传统图标：满幅墨底 + 蓝「简」（圆角方 / 圆形）。"""
    s = px * SS
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    if shape == "round":
        d.ellipse([0, 0, s - 1, s - 1], fill=BG)
    else:
        d.rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * 0.22), fill=BG)
    im.alpha_composite(draw_glyph(s, int(s * 0.52)))
    return im.resize((px, px), Image.LANCZOS)


def adaptive_fg(px):
    """自适应前景：透明底，字形约 108 画布的 43%（在 66 安全区内，留遮罩呼吸感）。"""
    s = px * SS
    return draw_glyph(s, int(s * 0.43)).resize((px, px), Image.LANCZOS)


def main():
    if not os.path.exists(FONT):
        sys.exit(f"字体不存在：{FONT}（可先下载，见 tools/fontgen/README.md）")
    for name, scale in DENSITIES.items():
        out = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(out, exist_ok=True)
        base = int(48 * scale)
        legacy(base, "square").save(os.path.join(out, "ic_launcher.png"))
        legacy(base, "round").save(os.path.join(out, "ic_launcher_round.png"))
        adaptive_fg(int(108 * scale)).save(os.path.join(out, "ic_launcher_foreground.png"))
        print(f"mipmap-{name}: launcher {base}px, foreground {int(108 * scale)}px")
    print("完成")


if __name__ == "__main__":
    main()
