package com.jianji.ime.keyboard

/** 键位动作 */
enum class KeyAction {
    LETTER,         // 输出字母（大小写由 shift 状态决定）
    TEXT,           // 输出固定文本（标点、数字、符号等）
    SHIFT,          // 大小写
    BACKSPACE,      // 退格（长按连删；上划清空）
    ENTER,          // 回车 / 编辑器动作
    SPACE,          // 空格
    PAGE_NUMBERS,   // 切到数字页
    PAGE_SYMBOLS,   // 切到符号页
    PAGE_LETTERS,   // 返回字母页 / 数字页
    HIDE_KEYBOARD,  // 收起键盘
    SYMBOL_PREV,    // 符号页上一页
    SYMBOL_NEXT,    // 符号页下一页
    LANG_TOGGLE,    // 中英切换
}

/** 键帽视觉风格 */
enum class KeyStyle { LETTER, FN, ACCENT, SPACE }

/** 矢量图标（自绘，避免字体符号大小不一） */
enum class KeyIcon { NONE, ENTER, PREV, NEXT }

/**
 * 一个键的定义。
 * @param weight 宽度权重（行内单位）
 * @param icon   非 NONE 时绘制矢量图标，忽略 label
 */
data class KeyDef(
    val action: KeyAction,
    val label: String,
    val text: String = label,
    val weight: Float = 1f,
    val style: KeyStyle = KeyStyle.LETTER,
    val icon: KeyIcon = KeyIcon.NONE,
)

/** 一行键。pad 为行左右额外内缩（权重单位，用于第二行等居中）。 */
data class KeyRow(
    val keys: List<KeyDef>,
    val pad: Float = 0f,
)

/**
 * 整页键盘布局。建议各行 sumWeights + pad*2 对齐到同一网格（如 10）。
 * @param keyHeight    键高（dp），符号页可压矮一点
 * @param tabs         非空时在底部绘制标签栏（符号页的「常用/中文/…」）
 * @param tabBarHeight 标签栏高度（dp）
 */
data class KeyboardLayout(
    val rows: List<KeyRow>,
    val keyHeight: Float = 48f,
    val tabs: List<String> = emptyList(),
    val tabBarHeight: Float = 36f,
)

/** 大小写状态：单击 = 一次性大写（打完一个字母复位），双击 = 锁定大写 */
enum class ShiftState { OFF, SINGLE, LOCK }

/** 符号页的一个标签分类：多页 4×4 符号网格 */
data class SymbolTab(
    val name: String,
    val pages: List<KeyboardLayout>,
)
