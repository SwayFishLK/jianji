package com.jianji.ime.engine

import java.util.Locale

/**
 * 中文模式里的英文词提示：输入看起来像英文时，最多给「最可能的那一个」词。
 *
 * 判断规则（宁可少出，不抢中文）：
 *  - 只认 2 个字母以上、且是英文词表里的词（2 个字母必须整词命中，避免 th → the 这类噪声）；
 *  - 英文词要够常见（[MIN_FREQ]）；前缀补全时，若输入本身也是词且词频不低，优先整词；
 *  - 中文已经有整段强命中（如 you → 有、women → 我们、can → 餐）时不出英文：这是拼音；
 *  - 中文有任意「整段吃满」的候选时，英文排第 2 位（不抢首选 / 空格）；否则排第 1 位。
 *
 * 纯逻辑，方便 JVM 直测；数据来自 EnglishDict（english.bin）。
 */
object EnglishHint {

    /** 低于这个词频（Zipf×1000）不出：挡住生僻补全（2500 ≈ emoji / debug 一档） */
    const val MIN_FREQ = 2500

    /** 中文整段命中且分数不低于此值 = 读法已确认，不再出英文（与引擎整句抑制同一量级） */
    const val STRONG_CHINESE = 9.0f

    const val MIN_QUERY = 2

    /**
     * @param word 要上屏的英文词
     * @param freq 词频
     * @param index 候选栏位置：0 = 排第一；1 = 跟在首选中文候选后面
     */
    data class Hit(val word: String, val freq: Int, val index: Int)

    fun pick(dict: EnglishDict, query: String, candidates: List<Candidate>): Hit? {
        if (query.length < MIN_QUERY || query.any { it !in 'a'..'z' }) return null
        val q = query.lowercase(Locale.ROOT)
        val best = dict.best(q) ?: return null
        val exact = dict.freqOf(q)

        // 2 个字母只认整词（th → the、he → her 这类补全太吵）；3 个字母起允许补全。
        // 补全时若输入本身就是词、且词频不比最佳补全低太多，优先整词（car 不该被 care 顶掉）。
        val word = when {
            q.length == MIN_QUERY -> if (exact >= 0) q else return null
            exact > 0 && exact * 2L >= best.freq -> q
            else -> best.word
        }
        val freq = if (word == q) exact else best.freq
        if (freq < MIN_FREQ) return null

        // 拼音读法已经整段成立（you → 有 / women → 我们 / can → 惨…）：中文优先，不出英文
        if (candidates.any { it.consumed >= q.length && it.score >= STRONG_CHINESE }) return null

        // 首选中文候选已经整段成立（hel → 荷兰）→ 英文排第二，不抢首选与空格；
        // 首选只吃了前几个字母（hello → 和）→ 英文排第一，否则它会被压在候选深处。
        val top = candidates.firstOrNull()
        val index = if (top != null && top.consumed >= q.length) 1 else 0
        return Hit(word, freq, index)
    }
}