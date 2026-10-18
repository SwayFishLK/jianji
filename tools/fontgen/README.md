# 候选字体管线（M5，留档）

> **现状（M5.1）：候选字体跟随系统，本管线未接入发布包** —— 用户最终选定跟随系统，M5 的文楷子集已从
> APK 撤出（`assets/fonts` 删除、`Fonts.kt` 与候选绘制接线一并移除）。将来若要自带圆润字体：
> 按下面步骤生成子集 → 放回 `app/src/main/assets/fonts/` → 恢复字体载入接线即可；应用图标字形也由
> `tools/icon/gen_icon.py` 用同款字体渲染。

候选栏/网格里显示的汉字与译文，曾用**霞鹜文楷 Screen 子集**（`app/src/main/assets/fonts/jianji-kai.ttf`），
让候选区比系统黑体更「圆润」。本目录是这套子集的生成脚本与字符集，换字体/升级字体重跑即可。

## 依赖

- Python 3 + `pip install fonttools brotli zopfli`

## 生成步骤

```powershell
# 1) 从随包数据提取字符集（会打印各来源字符数）
python tools\fontgen\charset.py
# 2) 子集化 + 按 OFL 改名，写入 app 资产
python tools\fontgen\build_font.py reference\fonts\LXGWWenKaiGBScreen.ttf tools\fontgen\charset.txt app\src\main\assets\fonts\jianji-kai.ttf "Jianji Kai"
```

产物：`jianji-kai.ttf`（约 5.1MB，APK 内压缩后约 3.5MB；字形 10140 / cmap 9911）。

## 字符集口径（charset.py）

- `dict.bin` 只取**词频 ≥ 1** 的词（93224 / 132884 个）。词频 0 的是词库里的生僻字条目
  （CJK 扩展 A/B 等约 1.6 万个字体缺字形、2.9 万字形才够），全部纳入会让子集膨胀到 17MB；
  这些字在设备上走系统字体回退显示（有译文/词频的常规候选全部覆盖）。
- `gloss-en/ja/zh.bin` 的释义与读音、`english.bin` 词表全取；
- extras：ASCII 可见字符 + 常用中文标点/全角符号（用户学习词可能出现）。
- 合计 9912 字符（仅 1 个 ✕ U+2715 字体没有，回退系统）。

## 许可与命名（重要）

- 上游均为 **SIL OFL 1.1** 字体，可随应用分发；子集属「修改版」。
- 文楷的保留名称：`霞鹜`、`霞鶩`、`落霞孤鹜`、`落霞孤鶩`、`LXGW`（其「附加许可」只允许
  **网页字体子集**沿用，App 内置子集不行）；悠哉的保留名称：`悠哉`、`Yozai`、`YOz`。
  → 子集一律改名为新字族 **Jianji Kai**（`build_font.py` 自动改 name 表 1/2/3/4/6/16/17），
  同时保留版权（nameID 0）、商标（7）与许可声明（13/14）。
- 应用内署名与完整许可文本见 `NOTICE` 与 `app/src/main/assets/legal/`（设置页「开源许可」）。

## 字体来源与备选

| 用途 | 文件 | 版本 | 来源 |
|---|---|---|---|
| 当前发布 | `LXGWWenKaiGBScreen.ttf` | v1.522（GB 字形，屏幕优化） | https://github.com/lxgw/LxgwWenKai-Screen/releases |
| 备选 ① | `LXGWWenKaiScreen.ttf` | v1.522（原版字形） | 同上 |
| 备选 ② | `Yozai-Regular.ttf` | v0.868 | https://github.com/lxgw/yozai-font/releases |

三者的候选观感对比见 `docs/mockups/font-compare-m5.png`（同字符集、按真机 3x 密度渲染）。
换字体：同一条 `build_font.py` 命令、输出文件与字族名不变，然后 `gradle :app:assembleDebug` 重装即可。
