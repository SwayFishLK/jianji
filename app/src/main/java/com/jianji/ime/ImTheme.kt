package com.jianji.ime

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable

/**
 * 当前皮肤的实时调色板。
 *
 * v0.5 起：8 套「液态玻璃」配色（来自 dsh-dream-skin 的 Mirage 主题）。
 * 全部自绘视图每帧直接读这里的颜色字段；主题切换后 [refresh] + invalidate 即时生效。
 *
 * 说明：输入法窗口不做真实透明/背景模糊，也不采样键盘下方的应用画面；
 * 底片由 [SkinBackdropDrawable] 用主题渐变 + 光晕模拟，键帽再叠半透明玻璃渐变。
 */
object ImTheme {

    // —————— 当前主题 ——————

    var themeId: String = SkinThemes.DEFAULT_ID
        private set
    var themeName: String = SkinThemes.byId(SkinThemes.DEFAULT_ID).name
        private set
    var themeSub: String = SkinThemes.byId(SkinThemes.DEFAULT_ID).sub
        private set
    var isLight: Boolean = false
        private set

    // —————— 底片 / 面板 ——————

    /** 根背景兜底色（窗口内容由 backdrop 铺满） */
    var windowBg = 0
    var windowTop = 0
    var windowBottom = 0

    /** 覆盖面板背景（更多候选 / 剪贴板 / 表情 / 符号） */
    var panelBg = 0

    /** 抬起面：卡片、分段控件底 */
    var cardBg = 0

    // —————— 键帽（实心兜底色，给符号面板等平涂控件） ——————

    var keyBg = 0
    var keyPressed = 0
    var keyFnBg = 0

    // —————— 键帽（KeyboardView 的液态玻璃渐变） ——————

    var keyTop = 0
    var keyBottom = 0
    var keyPressedTop = 0
    var keyPressedBottom = 0
    var keyFnTop = 0
    var keyFnBottom = 0
    var keyBorder = 0
    var keyHighlight = 0

    // —————— 回车键：加深的主题色玻璃 ——————

    var accentTop = 0
    var accentBottom = 0
    var accentPressedTop = 0
    var accentPressedBottom = 0
    var accentBorder = 0
    var accentGlow = 0

    /** 回车小鱼的眼睛（深色半透明，避免和键底色融在一起） */
    var enterEye = 0x66000000

    // —————— 文字 / 强调 ——————

    var keyText = 0
    var keyFnText = 0
    var keyHintText = 0
    var accent = 0
    var accentPressed = 0
    var candText = 0
    var candTranslation = 0
    var divider = 0

    // —————— 长按备选条 ——————

    var popupBg = 0
    var popupSel = 0
    var popupBorder = 0
    var popupText = 0

    /** 警示色（删除 / 清空确认） */
    var warn = 0

    /** 生词标记（B2 / N2 以上的等级角标；暖金，和主色区分开） */
    var candMark = 0

    fun refresh(context: Context) {
        apply(SkinThemes.byId(Prefs.themeId(context)))
    }

    /** 键盘底片（按当前主题新建；主题切换时重新赋给根布局） */
    fun backdrop(): Drawable = SkinBackdropDrawable(SkinThemes.byId(themeId))

    private fun apply(t: SkinTheme) {
        themeId = t.id
        themeName = t.name
        themeSub = t.sub
        isLight = t.light

        windowTop = t.windowTop
        windowBottom = t.windowBottom
        windowBg = t.windowBottom
        panelBg = t.panelBg
        cardBg = t.cardBg

        // 键帽：在主题底片上叠一层带极轻主题色的半透明白玻璃。
        // 深色约 8%–19% 白，浅色约 28%–50% 白；保证字母清晰，同时能透出底片颜色。
        keyTop = withA(mix(WHITE, t.accent, if (t.light) 0.06f else 0.28f),
            if (t.light) 0xC8 else 0x40)
        keyBottom = withA(mix(t.windowBottom, t.accent2, if (t.light) 0.04f else 0.22f),
            if (t.light) 0x78 else 0x24)
        keyPressedTop = withA(mix(WHITE, t.accent, if (t.light) 0.16f else 0.46f),
            if (t.light) 0xE8 else 0x78)
        keyPressedBottom = withA(mix(t.windowBottom, t.accent, if (t.light) 0.16f else 0.34f),
            if (t.light) 0x98 else 0x40)
        keyFnTop = withA(mix(WHITE, t.accent, if (t.light) 0.02f else 0.10f),
            if (t.light) 0xA8 else 0x2C)
        keyFnBottom = withA(WHITE, if (t.light) 0x58 else 0x13)
        keyBorder = withA(WHITE, if (t.light) 0xE0 else 0x58)
        keyHighlight = withA(WHITE, if (t.light) 0xF0 else 0x88)

        // 回车：实色主题渐变 + 白色描边 + 外发光 —— 比样本里的透明回车更深、更明确。
        accentTop = mix(t.accent, BLACK, if (t.light) 0.02f else 0.10f)
        accentBottom = mix(t.accent, BLACK, if (t.light) 0.20f else 0.36f)
        accentPressedTop = mix(t.accent, BLACK, if (t.light) 0.10f else 0.22f)
        accentPressedBottom = mix(t.accent, BLACK, if (t.light) 0.30f else 0.46f)
        accentBorder = withA(WHITE, if (t.light) 0xF0 else 0xC0)
        accentGlow = withA(t.accent, if (t.light) 0x38 else 0x60)

        // 平涂控件（符号面板按键、剪贴板小圆钮等）用接近键面中部的实色。
        // 浅色主题下若只向白色混，键面会和面板背景糊在一起，所以轻微压入主题色。
        keyBg = opaque(
            if (t.light) mix(t.windowBottom, t.accent, 0.10f)
            else mix(t.windowBottom, WHITE, 0.10f),
        )
        keyPressed = opaque(mix(t.windowBottom, t.accent, if (t.light) 0.22f else 0.34f))
        keyFnBg = opaque(mix(t.windowBottom, WHITE, if (t.light) 0.20f else 0.05f))

        keyText = t.keyText
        keyFnText = t.fnText
        keyHintText = t.hintText
        accent = t.accent
        accentPressed = mix(t.accent, BLACK, 0.20f)
        candText = t.keyText
        candTranslation = t.hintText
        divider = withA(if (t.light) BLACK else WHITE, 0x18)

        popupBg = opaque(mix(t.panelBg, t.accent, 0.05f))
        popupSel = opaque(mix(t.panelBg, t.accent, 0.20f))
        popupBorder = withA(t.accent, 0x7A)
        popupText = t.keyText
        warn = if (t.light) 0xFFB23A3A.toInt() else 0xFFE08A8A.toInt()
        candMark = if (t.light) 0xFFB4782A.toInt() else 0xFFD9A85C.toInt()
    }

    fun withA(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)

    fun accentAlpha(alpha: Int): Int = withA(accent, alpha)

    private fun opaque(color: Int): Int = color or 0xFF000000.toInt()

    /** 只混 RGB，alpha 由调用方决定。 */
    private fun mix(a: Int, b: Int, f: Float): Int {
        val t = f.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) * (1f - t) + Color.red(b) * t).toInt().coerceIn(0, 255),
            (Color.green(a) * (1f - t) + Color.green(b) * t).toInt().coerceIn(0, 255),
            (Color.blue(a) * (1f - t) + Color.blue(b) * t).toInt().coerceIn(0, 255),
        )
    }

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()
}