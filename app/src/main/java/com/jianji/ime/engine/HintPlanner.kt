package com.jianji.ime.engine

/**
 * 中文模式的附加候选规划（纯逻辑）：
 *  - 英文词提示（[EnglishHint]）：最多一个，位置 0 / 1；
 *  - 首选词配一个 emoji（[EmojiMap]）：只在首选词把整段输入吃满、且是 2~4 字词时给。
 *
 * 服务层按 [Plan] 拼候选栏；这样插入顺序、门槛都能 JVM 直测。
 */
object HintPlanner {

    /**
     * @param english 英文词；null = 不出
     * @param englishIndex 0 = 第一；1 = 跟在首选中文候选后面
     * @param emoji 首选词对应的一个 emoji；null = 不出（插在首选词后面）
     */
    data class Plan(val english: String?, val englishIndex: Int, val emoji: String?) {
        val isEmpty: Boolean get() = english == null && emoji == null
    }

    val EMPTY = Plan(null, -1, null)

    fun plan(
        query: String,
        candidates: List<Candidate>,
        english: EnglishDict?,
        emojis: EmojiMap?,
    ): Plan {
        val en = if (english != null) EnglishHint.pick(english, query, candidates) else null

        // emoji 只跟「首选词」：必须整段吃满（还在组句 / 只吃了前几个字母时不打扰）。
        // emoji-map.bin 只收 2~4 字词 + 少量单字名词（猫 / 狗），所以这里放宽到 1~4。
        val top = candidates.firstOrNull()
        val emoji = if (top != null && emojis != null &&
            top.consumed >= query.length && top.text.length in 1..4
        ) {
            emojis.emojiFor(top.text)
        } else {
            null
        }

        return Plan(en?.word, en?.index ?: -1, emoji)
    }
}