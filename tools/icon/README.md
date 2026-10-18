# 应用图标生成（M5.1）

方案 B「墨底蓝简」：墨底 `#17191E` + 主题蓝「简」 `#5B8DEF`。字形取自文楷
（生成时一次性渲染成位图，应用运行时不依赖字体文件）。

## 用法

```powershell
E:\Python\python.exe tools\icon\gen_icon.py [字体.ttf] [res 目录]
# 默认：reference\fonts\LXGWWenKaiGBScreen.ttf → app\src\main\res
```

## 生成物

- `mipmap-{mdpi..xxxhdpi}/ic_launcher.png`、`ic_launcher_round.png` —— 传统图标（圆角方 / 圆，48~192px）
- `mipmap-{...}/ic_launcher_foreground.png` —— 自适应前景（108~432px，透明底，字形 0.43 画布）
- 自适应 XML（`mipmap-anydpi-v26/ic_launcher(_round).xml`）与背景色
  （`values/colors.xml` 的 `ic_launcher_background`）为手工维护，清单 `android:icon` / `android:roundIcon` 已指向。

## 调整

- 字形大小：改 `gen_icon.py` 里的比例（自适应 0.43 / 传统 0.52）重跑再重建。
- 本机实测（Lenovo / Android 12L）：launcher 遮罩约 0.6 画布，**0.43 字形视觉合适，0.50 会贴边**。
- 换新字体：`python tools\icon\gen_icon.py <新字体.ttf>` 重出各密度 PNG 即可（不涉及代码）。
