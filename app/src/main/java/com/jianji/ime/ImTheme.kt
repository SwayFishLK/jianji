package com.jianji.ime

import android.content.Context
import androidx.core.content.ContextCompat

/**
 * 「墨夜」（方案 A）单色调色板。
 *
 * 全部自绘视图每帧直接读这里的颜色字段。
 * 说明：M4.2 曾引入「液态玻璃 / 墨夜」双主题（含玻璃颜色与透明度设置），
 * 试做「液态玻璃 2.0」后按用户决定移除，只保留最初的深色方案；
 * 设备没有系统背景模糊、输入法也拿不到下层画面做真折射，玻璃质感只能靠绘制模拟，
 * 相关设计要点见 docs/PROGRESS.md 留档（以后再做）。
 */
object ImTheme {

    // 绘制颜色（每帧读取；refresh 后 invalidate 即生效）

    /** 根背景：候选预留区 + 键盘底板 */
    var windowBg = 0

    /** 覆盖面板背景（更多候选 / 剪贴板 / 表情） */
    var panelBg = 0

    /** 抬起面：卡片、分段控件底 */
    var cardBg = 0

    var keyBg = 0
    var keyPressed = 0
    var keyFnBg = 0
    var keyText = 0
    var keyFnText = 0
    var keyHintText = 0
    var accent = 0
    var accentPressed = 0
    var candText = 0
    var candTranslation = 0
    var divider = 0

    /** 长按备选条（浮层，读性优先，用更实的底） */
    var popupBg = 0
    var popupSel = 0
    var popupBorder = 0
    var popupText = 0

    /** 警示色（删除 / 清空确认） */
    var warn = 0

    /** 生词标记（B2 / N2 以上的等级角标；暖金，和主色区分开） */
    var candMark = 0

    fun refresh(context: Context) {
        inkPalette(context)
    }

    private fun inkPalette(context: Context) {
        windowBg = c(context, R.color.ime_bg)
        panelBg = c(context, R.color.ime_panel_bg)
        cardBg = 0xFF252A34.toInt()
        keyBg = c(context, R.color.key_bg)
        keyPressed = c(context, R.color.key_pressed)
        keyFnBg = c(context, R.color.key_fn_bg)
        keyText = c(context, R.color.key_text)
        keyFnText = c(context, R.color.key_fn_text)
        keyHintText = c(context, R.color.key_hint_text)
        accent = c(context, R.color.accent)
        accentPressed = c(context, R.color.accent_pressed)
        candText = c(context, R.color.cand_text)
        candTranslation = c(context, R.color.cand_translation)
        divider = 0x1FFFFFFF
        popupBg = 0xFF262B36.toInt()
        popupSel = 0xFF3A4150.toInt()
        popupBorder = 0xFF3F4552.toInt()
        popupText = 0xFFE6E8EC.toInt()
        warn = 0xFFE08A8A.toInt()
        candMark = 0xFFD9A85C.toInt()
    }

    fun withA(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    fun accentAlpha(alpha: Int): Int = withA(accent, alpha)

    private fun c(context: Context, id: Int): Int = ContextCompat.getColor(context, id)
}
