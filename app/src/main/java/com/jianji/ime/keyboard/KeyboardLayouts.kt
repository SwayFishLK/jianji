package com.jianji.ime.keyboard

/**
 * 内置布局（参照搜狗 26 键习惯）：
 * - 字母页：字母键上方小字提示长按备选；底排 [收起][123][，/ ,][空格][。/ .][中/英][⏎]
 * - 数字页：左侧 % / - + 竖列、中间 1-9/0 网格、右侧 ⌫ . @，底排返回/符
 * - 符号页：4×4 符号网格 + 右侧 [返回][∧][∨][锁] + 底部分类标签栏
 */
object KeyboardLayouts {

    private fun letter(c: Char) = KeyDef(KeyAction.LETTER, c.toString())

    /**
     * 字母页底排标点随语言切换：中文 `，`/`。`，英文 `,`/`.`。
     * 两个布局只差这两个键，避免在 View 里改键帽文本导致长按/上划命中不一致。
     */
    private fun lettersLayout(comma: String, period: String): KeyboardLayout =
        KeyboardLayout(
            rows = listOf(
                KeyRow("qwertyuiop".map { letter(it) }),
                KeyRow("asdfghjkl".map { letter(it) }, pad = 0.5f),
                KeyRow(
                    listOf(
                        KeyDef(KeyAction.SHIFT, "⇧", weight = 1.35f, style = KeyStyle.FN),
                        *"zxcvbnm".map { letter(it) }.toTypedArray(),
                        KeyDef(KeyAction.BACKSPACE, "⌫", weight = 1.35f, style = KeyStyle.FN),
                    ),
                    pad = 0.15f,
                ),
                KeyRow(
                    listOf(
                        KeyDef(KeyAction.PAGE_SYMBOLS, "符", weight = 1.0f, style = KeyStyle.FN),
                        KeyDef(KeyAction.PAGE_NUMBERS, "123", weight = 1.0f, style = KeyStyle.FN),
                        KeyDef(KeyAction.TEXT, comma, weight = 1.0f, style = KeyStyle.FN),
                        KeyDef(KeyAction.SPACE, "空格", weight = 2.7f, style = KeyStyle.SPACE),
                        KeyDef(KeyAction.TEXT, period, weight = 1.0f, style = KeyStyle.FN),
                        KeyDef(KeyAction.LANG_TOGGLE, "中/英", weight = 1.45f, style = KeyStyle.FN),
                        KeyDef(KeyAction.ENTER, "", weight = 1.35f, style = KeyStyle.ACCENT, icon = KeyIcon.ENTER),
                    ),
                    pad = 0.25f,
                ),
            ),
        )

    /** 行宽统一用「权重」控制；各行 sum + pad*2 尽量对齐 */
    val qwerty: KeyboardLayout by lazy { lettersLayout("，", "。") }

    /** 英文模式字母页（底排 `,` `.`；长按备选不会出现中文标点） */
    val qwertyEnglish: KeyboardLayout by lazy { lettersLayout(",", ".") }

    val numbers: KeyboardLayout by lazy {
        KeyboardLayout(
            rows = listOf(
                KeyRow(
                    listOf(
                        KeyDef(KeyAction.TEXT, "%", weight = 1.15f),
                        KeyDef(KeyAction.TEXT, "1", weight = 1.7f),
                        KeyDef(KeyAction.TEXT, "2", weight = 1.7f),
                        KeyDef(KeyAction.TEXT, "3", weight = 1.7f),
                        KeyDef(KeyAction.BACKSPACE, "⌫", style = KeyStyle.FN, weight = 1.15f),
                    ),
                    pad = 0.15f,
                ),
                KeyRow(
                    listOf(
                        KeyDef(KeyAction.TEXT, "/", weight = 1.15f),
                        KeyDef(KeyAction.TEXT, "4", weight = 1.7f),
                        KeyDef(KeyAction.TEXT, "5", weight = 1.7f),
                        KeyDef(KeyAction.TEXT, "6", weight = 1.7f),
                        KeyDef(KeyAction.TEXT, ".", weight = 1.15f),
                    ),
                    pad = 0.15f,
                ),
                KeyRow(
                    listOf(
                        KeyDef(KeyAction.TEXT, "-", weight = 1.15f),
                        KeyDef(KeyAction.TEXT, "7", weight = 1.7f),
                        KeyDef(KeyAction.TEXT, "8", weight = 1.7f),
                        KeyDef(KeyAction.TEXT, "9", weight = 1.7f),
                        KeyDef(KeyAction.TEXT, "@", weight = 1.15f),
                    ),
                    pad = 0.15f,
                ),
                KeyRow(
                    listOf(
                        KeyDef(KeyAction.TEXT, "+", style = KeyStyle.FN, weight = 1.15f),
                        KeyDef(KeyAction.PAGE_LETTERS, "返回", style = KeyStyle.FN, weight = 1.7f),
                        KeyDef(KeyAction.TEXT, "0", weight = 1.7f),
                        KeyDef(KeyAction.PAGE_SYMBOLS, "符", style = KeyStyle.FN, weight = 1.7f),
                        KeyDef(KeyAction.ENTER, "", style = KeyStyle.ACCENT, icon = KeyIcon.ENTER, weight = 1.15f),
                    ),
                    pad = 0.15f,
                ),
            ),
        )
    }

    // —————— 符号页（分类 × 多页 4×4 网格） ——————

    private val symbolRightColumn = listOf(
        KeyDef(KeyAction.PAGE_LETTERS, "返回", style = KeyStyle.FN, weight = 1.15f),
        KeyDef(KeyAction.SYMBOL_PREV, "", style = KeyStyle.FN, weight = 1.15f, icon = KeyIcon.PREV),
        KeyDef(KeyAction.SYMBOL_NEXT, "", style = KeyStyle.FN, weight = 1.15f, icon = KeyIcon.NEXT),
        KeyDef(KeyAction.BACKSPACE, "⌫", style = KeyStyle.FN, weight = 1.15f),
    )

    private fun symbolPage(tabNames: List<String>, symbols: String): KeyboardLayout {
        val cells = symbols.split(' ').filter { it.isNotEmpty() }
        val rows = cells.chunked(4).mapIndexed { r, four ->
            val keys = four.map { KeyDef(KeyAction.TEXT, it, weight = 1.7f) }.toMutableList()
            symbolRightColumn.getOrNull(r)?.let { keys.add(it) }
            KeyRow(keys, pad = 0.1f)
        }
        return KeyboardLayout(rows, keyHeight = 42f, tabs = tabNames, tabBarHeight = 36f)
    }

    private val tabNames = listOf("1.常用", "2.中文", "3.英文", "4.网络", "5.邮箱", "6.数学")

    private fun tab(name: String, vararg pages: String): SymbolTab =
        SymbolTab(name, pages.map { symbolPage(tabNames, it) }.toList())

    val symbolTabs: List<SymbolTab> by lazy {
        listOf(
            tab(
                "1.常用",
                "。 ， 、 ？ ！ ： ； “ ” ‘ ’ 《 》 （ ） …",
                "· ～ — —— ℃ ℉ × ÷ ± ≈ ≠ ≤ ≥ √ ‰ °",
            ),
            tab(
                "2.中文",
                "、 〃 〈 〉 「 」 『 』 【 】 〖 〗 〔 〕 〜 ﹏",
                "＄ ％ ＃ ＠ ＆ ＊ ＋ － ／ ＝ ＼ ＾ ＿ ｜ ￥ ￠",
            ),
            tab(
                "3.英文",
                "~ ` ! @ # \$ % ^ & * ( ) - _ = +",
                "[ ] { } \\ | ; : ' \" , . < > / ?",
            ),
            tab(
                "4.网络",
                "www. http:// https:// .com .cn .net .org .edu .gov .io .me .tv .top .xyz .app @",
            ),
            tab(
                "5.邮箱",
                EmailSuffixes.symbols.joinToString(" "),
            ),
            tab(
                "6.数学",
                "＋ － × ÷ ＝ ≠ ≈ ± ∞ √ ² ³ ½ ¼ ¾",
                "∑ ∏ ∫ ≤ ≥ ∵ ∴ ∈ ∉ ∪ ∩ ∧ ∨ △ ⊕ ⊗",
            ),
        )
    }

    /** 符号页第一页（键盘切换入口用） */
    val symbolsFirstPage: KeyboardLayout
        get() = symbolTabs.first().pages.first()

    // —————— 长按备选（字母键上方小字提示，搜狗风格） ——————

    private val alternates: Map<String, List<String>> = mapOf(
        "q" to listOf("1"), "w" to listOf("2"), "e" to listOf("3"),
        "r" to listOf("4"), "t" to listOf("5"), "y" to listOf("6"),
        "u" to listOf("7"), "i" to listOf("8"), "o" to listOf("9"),
        "p" to listOf("0"),
        "a" to listOf("~"), "s" to listOf("!"), "d" to listOf("@"),
        "f" to listOf("#"), "g" to listOf("%"), "h" to listOf("“"),
        "j" to listOf("”"), "k" to listOf("*"), "l" to listOf("?"),
        "z" to listOf("("), "x" to listOf(")"), "c" to listOf("-"),
        "v" to listOf("/"), "b" to listOf("'"), "n" to listOf(";"),
        "m" to listOf("："),
        "，" to listOf("、"), "。" to listOf("？"),
    )

    fun alternatesFor(def: KeyDef): List<String> =
        if (def.action == KeyAction.LETTER || def.action == KeyAction.TEXT) {
            alternates[def.label].orEmpty()
        } else {
            emptyList()
        }
}
