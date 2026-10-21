package com.jianji.ime

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable

/**
 * 当前皮肤的实时调色板。
 *
 * 当前 5 套：深渊 / 极光 / 星云 / 余烬 4 套液态玻璃，加旧版「墨夜」经典平涂。
 * 全部自绘视图每帧直接读这里的颜色字段；主题切换后 [refresh] + invalidate 即时生效。
 *
 * 说明：输入法窗口不做真实透明/背景模糊，也不采样键盘下方的应用画面；
 * 底片由 [SkinBackdropDrawable] 用主题渐变 + 光晕模拟，键帽再叠半透明玻璃渐变。
 * 墨夜（[SkinTheme.classic]）则回到旧版的实色键帽，只把深色部分略微压暗。
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

    /** 经典平涂皮肤（墨夜）：不画玻璃键面 / 彩色底片 */
    var isClassic: Boolean = false
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

    /** 4 段纵向渐变：顶部高光 → 透明中段 → 底部回光；模拟样品 145deg 玻璃键 */
    var keyGrad: IntArray = IntArray(4)
    var keyPressedGrad: IntArray = IntArray(4)
    var keyFnGrad: IntArray = IntArray(4)
    var keyBorder = 0
    var keyHighlight = 0
    var keyBottomHighlight = 0
    var keyFnBorder = 0
    var keyFnHighlight = 0
    var keyShadow = 0

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

    /**
     * 打开符号 / 表情 / 剪贴板 / 更多候选面板时，临时切回旧版「墨夜」调色板。
     * 只改内存里的实时色，不动 Prefs；关闭面板后调用 [refresh] 恢复用户主题。
     * 这样面板和上方候选栏是同一套默认外观，不会「下面默认、上面皮肤」。
     */
    fun useClassicChrome() {
        apply(SkinThemes.byId("ink"))
    }

    /** 键盘底片（按当前主题新建；主题切换时重新赋给根布局） */
    fun backdrop(): Drawable = SkinBackdropDrawable(SkinThemes.byId(themeId))

    private fun apply(t: SkinTheme) {
        themeId = t.id
        themeName = t.name
        themeSub = t.sub
        isLight = t.light
        isClassic = t.classic

        windowTop = t.windowTop
        windowBottom = t.windowBottom
        windowBg = t.windowBottom
        panelBg = t.panelBg
        cardBg = t.cardBg

        // 键帽：把样品 liquid 的 145° 玻璃渐变直接翻译成 4 段纵向渐变。
        // 深色键是「顶部 17% 白 → 中段 2% → 底部 12%」，彩色底片从中段透出来；
        // 浅色键是「顶部 58% 白 → 中段 10% → 底部 36% 白」。
        if (t.light) {
            keyGrad = intArrayOf(
                0x94FFFFFF.toInt(), 0x29FFFFFF, 0x1AFFFFFF, 0x5CFFFFFF,
            )
            keyPressedGrad = intArrayOf(
                0xEBDCEFFF.toInt(), 0xE8D6ECFF.toInt(), 0xE4D2E8FF.toInt(), 0xEBDCEEFF.toInt(),
            )
            keyFnGrad = intArrayOf(
                0x5EFFFFFF, 0x2AFFFFFF, 0x18FFFFFF, 0x17FFFFFF,
            )
            keyShadow = 0x12325582
            keyBottomHighlight = 0x1A1F508C
            keyFnBorder = withA(WHITE, 0xEB)
            keyFnHighlight = withA(WHITE, 0xFA)
        } else {
            keyGrad = intArrayOf(
                0x2BFFFFFF, 0x0AFFFFFF, 0x06FFFFFF, 0x24FFFFFF,
            )
            keyPressedGrad = intArrayOf(
                0x4DFFFFFF, 0x40FFFFFF, 0x36FFFFFF, 0x45FFFFFF,
            )
            keyFnGrad = intArrayOf(
                0x16FFFFFF, 0x0AFFFFFF, 0x04FFFFFF, 0x05FFFFFF,
            )
            keyShadow = 0x10000000
            keyBottomHighlight = 0x14FFFFFF
            keyFnBorder = withA(WHITE, 0x2E)
            keyFnHighlight = withA(WHITE, 0x40)
        }
        keyBorder = withA(WHITE, if (t.light) 0xF5 else 0x59)
        keyHighlight = withA(WHITE, if (t.light) 0xFF else 0x80)

        // 墨夜：恢复旧版实色键帽，不需要玻璃描边 / 高光 / 阴影。
        if (t.classic) {
            keyGrad = intArrayOf(t.keyBg, t.keyBg, t.keyBg, t.keyBg)
            keyPressedGrad = IntArray(4) { t.keyPressed }
            keyFnGrad = IntArray(4) { t.keyFnBg }
            keyBorder = 0
            keyHighlight = 0
            keyBottomHighlight = 0
            keyFnBorder = 0
            keyFnHighlight = 0
            keyShadow = 0
        }

        // 回车：回到初版的浅主题色，但键面改成半透明玻璃，让底片颜色透出来。
        enterEye = 0x66000000
        if (t.classic) {
            accentTop = t.accent
            accentBottom = t.accent
            accentPressedTop = t.accent2
            accentPressedBottom = t.accent2
            accentBorder = 0
            accentGlow = 0
            enterEye = t.accent
        } else if (t.light) {
            accentTop = mix(t.accent, BLACK, 0.02f)
            accentBottom = mix(t.accent, BLACK, 0.20f)
            accentPressedTop = mix(t.accent, BLACK, 0.10f)
            accentPressedBottom = mix(t.accent, BLACK, 0.30f)
        } else {
            accentTop = withA(mix(t.accent, WHITE, 0.04f), 0xC8)
            accentBottom = withA(mix(t.accent, BLACK, 0.20f), 0x98)
            accentPressedTop = withA(mix(t.accent, BLACK, 0.06f), 0xE0)
            accentPressedBottom = withA(mix(t.accent, BLACK, 0.28f), 0xB0)
        }
        accentBorder = withA(WHITE, if (t.light) 0xF0 else 0xC0)
        accentGlow = withA(t.accent, if (t.light) 0x38 else 0x60)

        // 平涂控件（符号面板按键、剪贴板小圆钮等）用接近键面中部的实色。
        // 浅色主题下若只向白色混，键面会和面板背景糊在一起，所以轻微压入主题色。
        if (t.classic) {
            keyBg = opaque(t.keyBg)
            keyPressed = opaque(t.keyPressed)
            keyFnBg = opaque(t.keyFnBg)
        } else {
            keyBg = opaque(
                if (t.light) mix(t.windowBottom, t.accent, 0.10f)
                else mix(t.windowBottom, WHITE, 0.10f),
            )
            keyPressed = opaque(mix(t.windowBottom, t.accent, if (t.light) 0.22f else 0.34f))
            keyFnBg = opaque(mix(t.windowBottom, WHITE, if (t.light) 0.20f else 0.05f))
        }

        if (t.classic) {
            keyText = t.keyText
            keyFnText = t.fnText
            keyHintText = t.hintText
        } else {
            keyText = if (t.light) t.keyText else mix(t.keyText, WHITE, 0.22f)
            keyFnText = if (t.light) t.fnText else mix(t.fnText, WHITE, 0.22f)
            keyHintText = if (t.light) t.hintText else mix(t.hintText, 0xFFC8D7E8.toInt(), 0.70f)
        }
        accent = t.accent
        accentPressed = if (t.classic) t.accent2 else mix(t.accent, BLACK, 0.20f)
        candText = if (t.classic) 0xFFEDEFF3.toInt() else t.keyText
        candTranslation = if (t.classic) {
            0xFF8B93A3.toInt()
        } else if (t.light) {
            t.hintText
        } else {
            mix(t.hintText, 0xFFB4C7DC.toInt(), 0.80f)
        }
        divider = if (t.classic) 0x1FFFFFFF else withA(if (t.light) BLACK else WHITE, 0x18)

        if (t.classic) {
            popupBg = 0xFF262B36.toInt()
            popupSel = 0xFF3A4150.toInt()
            popupBorder = 0xFF3F4552.toInt()
            popupText = 0xFFE6E8EC.toInt()
        } else {
            popupBg = opaque(mix(t.panelBg, t.accent, 0.05f))
            popupSel = opaque(mix(t.panelBg, t.accent, 0.20f))
            popupBorder = withA(t.accent, 0x7A)
            popupText = t.keyText
        }
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