package com.jianji.ime

import com.jianji.ime.engine.Candidate

/**
 * 自造短语索引（见 Learning.PhraseStat）：整串重复输入学到的习惯 → 候选。
 *
 *  - 全拼：输入 ≥ Learning.PHRASE_MIN_QUERY 个字母且是短语拼音的前缀（wutiaow / wutiaowu → 五条悟），
 *    或输入与短语拼音完全一致；短输入（wu）不命中，短语不抢常用字的候选位；
 *  - 简拼：输入与短语简拼完全一致（wtw → 五条悟），简拼切不开的短语（initials = null）只认全拼；
 *  - 门槛：上屏次数 < Learning.PHRASE_MIN_COUNT 的短语不出候选（只打一次不会记住）；
 *  - 命中即整段吃满（consumed = 输入长度），候选分见 Learning.phraseScore（次数对数 + 新鲜度）。
 *
 * 纯逻辑（不依赖 Android）：service 重建索引（词库载好后补简拼），JVM 直测。
 */
class PhraseIndex {

    /** 索引项：上屏串 + 全拼 + 简拼（音节切不开时 null = 只认全拼） */
    class Entry(
        val text: String,
        val pinyin: String,
        val initials: String?,
        val count: Int,
        val day: Long,
    )

    private var list: List<Entry> = emptyList()

    fun rebuild(entries: List<Entry>) {
        list = entries
    }

    fun hits(q: String, nowDay: Long): List<Candidate> {
        if (list.isEmpty()) return emptyList()
        var out: ArrayList<Candidate>? = null
        for (e in list) {
            if (e.count < Learning.PHRASE_MIN_COUNT) continue
            val full = e.pinyin == q ||
                (q.length >= Learning.PHRASE_MIN_QUERY && e.pinyin.startsWith(q))
            val abbrev = e.initials != null && q.length >= 2 && e.initials == q
            if (!full && !abbrev) continue
            val c = Candidate(e.text, Learning.phraseScore(e.count, e.day, nowDay), q.length)
            if (out == null) out = ArrayList(4)
            out.add(c)
        }
        return out ?: emptyList()
    }
}
