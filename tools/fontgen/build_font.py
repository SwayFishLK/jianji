#!/usr/bin/env python3
"""简记 · 候选字体子集化（M5）

用 fontTools 把上游字体裁剪成 charset.txt 的字符集，并按 SIL OFL 要求改名
（子集 = 修改版；文楷的保留名称「霞鹜 / 落霞孤鹜 / LXGW」、悠哉的「悠哉 / Yozai」
都不能出现在修改版的字体名里）。

依赖：python3 + fonttools（pip install fonttools brotli zopfli）

用法：
  python tools/fontgen/build_font.py <上游字体.ttf> <字符集.txt> <输出.ttf> "字族名"
示例（M5 实际使用）：
  python tools/fontgen/charset.py
  python tools/fontgen/build_font.py reference\\fonts\\LXGWWenKaiGBScreen.ttf tools\\fontgen\\charset.txt app\\src\\main\\assets\\fonts\\jianji-kai.ttf "Jianji Kai"
"""

import io
import os
import subprocess
import sys

from fontTools.ttLib import TTFont

if hasattr(sys.stdout, 'buffer'):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')


def main():
    if len(sys.argv) != 5:
        print(__doc__)
        sys.exit(2)
    src, charset, out, family = sys.argv[1:5]
    os.makedirs(os.path.dirname(out) or '.', exist_ok=True)

    subprocess.run(
        [sys.executable, '-m', 'fontTools.subset', src,
         f'--text-file={charset}',
         f'--output-file={out}',
         '--layout-features=*',   # 保留 GSUB/GPOS（字距、连字等）
         '--name-IDs=*',          # 保留版权/许可等 name 记录
         '--name-languages=*',
         '--drop-tables+=DSIG',   # 改过就不该带原数字签名
         '--recommended-glyphs'],
        check=True,
    )

    f = TTFont(out)
    ver = f['name'].getDebugName(5) or ''
    ps = family.replace(' ', '')
    for rec in f['name'].names:
        if rec.nameID == 1:
            rec.string = family
        elif rec.nameID == 2:
            rec.string = 'Regular'
        elif rec.nameID == 3:
            rec.string = f'{family} Regular;{ver}'
        elif rec.nameID == 4:
            rec.string = f'{family} Regular'
        elif rec.nameID == 6:
            rec.string = f'{ps}-Regular'
        elif rec.nameID == 16:
            rec.string = family
        elif rec.nameID == 17:
            rec.string = 'Regular'
    f.save(out)

    f = TTFont(out, lazy=True)
    print(f'OK → {out}')
    print(f'  字形数 {f["maxp"].numGlyphs}，cmap {len(f.getBestCmap())}，字族 "{f["name"].getDebugName(1)}"')
    print('  版权（nameID 0）:', (f['name'].getDebugName(0) or '').replace('\r\n', ' | '))
    print('  许可（nameID 13）:', (f['name'].getDebugName(13) or '')[:80])
    f.close()


if __name__ == '__main__':
    main()
