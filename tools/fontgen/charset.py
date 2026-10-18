#!/usr/bin/env python3
"""简记 · 候选字体字符集提取（M5）

从随包发布的 app/src/main/assets/*.bin 数据里，算出「会显示在候选/译文里的全部字符」，
产出 charset.txt 供 build_font.py 子集化使用。

字符集口径（重要）：
  * dict.bin：只取词频 >= 1 的词。词频 0 的词是词库里的生僻字条目（约 3.9 万个罕见字，
    分布在 CJK 扩展 A/B 等），加上它们子集要 2.9 万字形（17MB），而日常输入几乎不会出现；
    这些字显示时回退系统字体（缺字形时 Android 会走系统 fallback），按需再放宽。
  * gloss-*.bin：全部释义与读音文本（词性缩写、英文、日语假名/汉字）。
  * english.bin：英文模式候选词。
  * extras：键盘可上屏的标点 + ASCII 可见字符 + 常用全角符号（用户学习词可能含这些）。

用法：
  python tools/fontgen/charset.py [assets目录] [输出charset.txt]
"""

import io
import struct
import sys

if hasattr(sys.stdout, 'buffer'):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')


def parse_dict(path):
    """dict.bin "JJD1" → (words, freqs)。布局见 tools/dictgen/DictGen.java。"""
    b = open(path, 'rb').read()
    assert b[:4] == b'JJD1', 'not a JJD1 file: ' + path
    count, _syl, pin_len, word_len, _ini, _syl_len = struct.unpack('>6i', b[4:28])
    off = 28 + pin_len
    word_blob = b[off + (count + 1) * 4: off + (count + 1) * 4 + word_len]
    off2 = off + (count + 1) * 4 + word_len
    freqs = struct.unpack(f'>{count}i', b[off2 + (count + 1) * 4: off2 + (count + 1) * 4 + count * 4])
    woffs = struct.unpack(f'>{count + 1}i', b[off2: off2 + (count + 1) * 4])
    words = [word_blob[woffs[i]:woffs[i + 1]].decode('utf-8') for i in range(count)]
    return words, freqs


def parse_gloss(path):
    """gloss-*.bin "JJG1" → 词面 + 释义/读音文本。布局见 tools/dictgen/GlossGen.java。"""
    b = open(path, 'rb').read()
    assert b[:4] == b'JJG1', 'not a JJG1 file: ' + path
    count, _senses, word_len, text_len = struct.unpack('>4i', b[4:20])
    word_blob = b[20:20 + word_len].decode('utf-8')
    toff = 20 + word_len + (count + 1) * 4 * 2
    text_blob = b[toff:toff + text_len].decode('utf-8')
    return set(word_blob) | set(text_blob)


def parse_english(path):
    """english.bin "JJE1" → 词表。布局见 tools/dictgen/EnglishGen.java。"""
    b = open(path, 'rb').read()
    assert b[:4] == b'JJE1', 'not a JJE1 file: ' + path
    _count, word_len = struct.unpack('>2i', b[4:12])
    return set(b[12:12 + word_len].decode('utf-8'))


def main():
    assets = sys.argv[1] if len(sys.argv) > 1 else r'app\src\main\assets'
    out = sys.argv[2] if len(sys.argv) > 2 else r'tools\fontgen\charset.txt'

    chars = set()

    words, freqs = parse_dict(assets + r'\dict.bin')
    kept = 0
    for w, fr in zip(words, freqs):
        if fr >= 1:
            chars |= set(w)
            kept += 1
    print(f'dict.bin: {len(words)} 词，取词频>=1 的 {kept} 词')

    for n in ('gloss-en.bin', 'gloss-ja.bin', 'gloss-zh.bin'):
        s = parse_gloss(assets + '\\' + n)
        chars |= s
        print(f'{n}: +{len(s)} 字符')

    s = parse_english(assets + r'\english.bin')
    chars |= s
    print(f'english.bin: +{len(s)} 字符')

    extras = ''.join(chr(x) for x in range(0x20, 0x7F))
    extras += '，。！？、；：“”‘’（）《》〈〉【】〔〕「」『』—…·～￥℃°×÷±§№→←↑↓★☆♪♥♦✓✕　％＃＠＆＊＋－＝／＼｜＄￡€≠≤≥∞√'
    extras += ''.join(chr(x) for x in range(0xFF01, 0xFF5F))
    before = len(chars)
    chars |= set(extras)
    print(f'extras（标点/ASCII/全角）: +{len(chars) - before} 字符')

    text = ''.join(sorted(chars))
    open(out, 'w', encoding='utf-8', newline='').write(text)
    print(f'共 {len(chars)} 字符 → {out}')


if __name__ == '__main__':
    main()
