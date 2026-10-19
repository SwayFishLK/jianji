package com.jianji.ime

import com.jianji.ime.engine.Candidate

/**
 * 自造短语索引（见 Learning.PhraseStat）：整串重复输入学到的习惯 → 候选。
 *
 * 命中规则按「音节分段」走：输入被切成一路对齐到短语音节的段，每段都是对应音节的前缀（至少 1 个字母），
 * 于是全拼（wutiaowu）、简拼（wtw）、混着打的（wutw / wutiaow）都能命中，不规范拼写不再把记忆「卡死」。
 *
 *  - 走完所有音节即可命中（wtw 这种全简拼不受长度限制）；
 *  - 只打到前几个音节时要求输入 ≥ Learning.PHRASE_MIN_QUERY 个字母（wutiao 给，wu / wut 不给），
 *    免得短语抢常用字的候选位；
 *  - 音节切不开的短语（老数据 / 生僻读音）退回「整段完全一致 或 ≥ PHRASE_MIN_QUERY 的前缀」；
 *  - 门槛：上屏次数 < Learning.PHRASE_MIN_COUNT 的短语不出候选（只打一次不会记住）；
 *  - 命中即整段吃满（consumed = 输入长度），候选分见 Learning.phraseScore（次数对数 + 新鲜度）。
 *
 * 纯逻辑（不依赖 Android）：service 重建索引（词库载好后补音节切分），JVM 直测。
 */
class PhraseIndex {

    /** 索引项：上屏串 + 整段拼音 + 音节切分（切不开 null = 只按整段匹配）+ 次数 + 最近日 */
    class Entry(
        val text: String,
        val pinyin: String,
        val syllables: List<String>?,
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
            if (!matches(e, q)) continue
            val c = Candidate(e.text, Learning.phraseScore(e.count, e.day, nowDay), q.length)
            if (out == null) out = ArrayList(4)
            out.add(c)
        }
        return out ?: emptyList()
    }

    /**
     * 输入能不能对上这个短语：从头一路对齐音节，每段是当前音节的前缀（长度 1 起，回溯找可行切法）。
     * 输入走完时：覆盖了全部音节 → 命中（简拼 wtw / 混合 wutw）；只覆盖前几个音节 → 要够长才命中。
     */
    private fun matches(e: Entry, q: String): Boolean {
        val syl = e.syllables
            ?: return e.pinyin == q || (q.length >= Learning.PHRASE_MIN_QUERY && e.pinyin.startsWith(q))
        fun dfs(i: Int, pos: Int): Boolean {
            if (pos == q.length) return i == syl.size || q.length >= Learning.PHRASE_MIN_QUERY
            if (i == syl.size) return false
            val s = syl[i]
            var take = 0
            while (take < s.length && pos + take < q.length && q[pos + take] == s[take]) {
                take++
                if (dfs(i + 1, pos + take)) return true
            }
            return false
        }
        return dfs(0, 0)
    }
}
