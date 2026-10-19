package com.jianji.ime

import kotlin.math.ln
import kotlin.math.min

/**
 * 「记忆」的纯逻辑（不依赖 Android，JVM 直测）：
 *
 *  - 用户词表：词 → 打过几次 + 最近使用日（本地日序）；
 *  - 搭配表：前一个上屏词 → 后一个上屏词 的次数（词→词 bigram-lite，不用联网、不建索引）；
 *  - 候选加分 = 次数（对数、封顶）+ 新鲜度（今天/一周/一月）+ 搭配。
 *
 * 存储串（Prefs）：词表 `词\t次数\t日序`，搭配 `前词\t后词\t次数`；
 * 旧版本词表只有 `词\t次数` 两段，解析时兼容（日序记 0 = 未知）。
 */
object Learning {

    /** 词表上限（超出按次数瘦身） */
    const val MAX_WORDS = 3000

    /** 搭配表上限 */
    const val MAX_PAIRS = 1500

    /** 次数加分：1 次 ≈ +1.7、10 次 ≈ +6.0、200 次封顶 ≈ +13.3 */
    private const val COUNT_WEIGHT = 2.5f
    private const val COUNT_CAP = 200

    /** 新鲜度加分：今天用过 / 一周内 / 一月内 */
    private const val TODAY_BONUS = 2.5f
    private const val WEEK_BONUS = 1.2f
    private const val MONTH_BONUS = 0.4f

    /** 搭配加分：1 次 ≈ +1.1、20 次封顶 ≈ +4.9 */
    private const val PAIR_WEIGHT = 1.6f
    private const val PAIR_CAP = 20

    data class WordStat(val count: Int, val day: Long)

    /** 今天（本地日序）：用于新鲜度判断 */
    fun today(): Long = System.currentTimeMillis() / 86_400_000L

    fun parseWords(raw: String?): HashMap<String, WordStat> {
        val map = HashMap<String, WordStat>(256)
        if (raw.isNullOrEmpty()) return map
        for (line in raw.lineSequence()) {
            if (line.isEmpty()) continue
            val f = line.split('\t')
            if (f.size < 2) continue
            val w = f[0]
            val c = f[1].toIntOrNull() ?: continue
            if (w.isEmpty() || c <= 0) continue
            val day = if (f.size >= 3) f[2].toLongOrNull() ?: 0L else 0L
            map[w] = WordStat(c, day)
        }
        return map
    }

    fun serializeWords(words: Map<String, WordStat>): String {
        val sb = StringBuilder(words.size * 16)
        for ((w, s) in words) {
            sb.append(w).append('\t').append(s.count).append('\t').append(s.day).append('\n')
        }
        return sb.toString()
    }

    /** 搭配表 key（前词 + 分隔符 + 后词） */
    fun pairKey(prev: String, next: String): String = prev + '\u0001' + next

    fun parsePairs(raw: String?): HashMap<String, Int> {
        val map = HashMap<String, Int>(256)
        if (raw.isNullOrEmpty()) return map
        for (line in raw.lineSequence()) {
            if (line.isEmpty()) continue
            val f = line.split('\t')
            if (f.size != 3) continue
            val c = f[2].toIntOrNull() ?: continue
            if (f[0].isEmpty() || f[1].isEmpty() || c <= 0) continue
            map[pairKey(f[0], f[1])] = c
        }
        return map
    }

    fun serializePairs(pairs: Map<String, Int>): String {
        val sb = StringBuilder(pairs.size * 12)
        for ((k, c) in pairs) {
            val i = k.indexOf('\u0001')
            if (i <= 0) continue
            sb.append(k, 0, i).append('\t').append(k, i + 1, k.length).append('\t').append(c).append('\n')
        }
        return sb.toString()
    }

    /** 词频加分（次数 + 新鲜度）；没有记录 → 0 */
    fun boost(stat: WordStat?, nowDay: Long): Float {
        if (stat == null || stat.count <= 0) return 0f
        var b = ln(1f + min(stat.count, COUNT_CAP)) * COUNT_WEIGHT
        if (nowDay > 0 && stat.day > 0) {
            val age = nowDay - stat.day
            b += when {
                age <= 0 -> TODAY_BONUS
                age <= 7 -> WEEK_BONUS
                age <= 30 -> MONTH_BONUS
                else -> 0f
            }
        }
        return b
    }

    /** 搭配加分（前词刚上屏，当前候选常跟在它后面） */
    fun pairBoost(count: Int?): Float =
        if (count == null || count <= 0) 0f else ln(1f + min(count, PAIR_CAP)) * PAIR_WEIGHT

    /** 超上限时按次数瘦身（原地） */
    fun pruneWords(words: MutableMap<String, WordStat>, limit: Int = MAX_WORDS) {
        if (words.size <= limit) return
        val keep = words.entries.sortedByDescending { it.value.count }.take(limit)
        words.clear()
        for (e in keep) words[e.key] = e.value
    }

    fun prunePairs(pairs: MutableMap<String, Int>, limit: Int = MAX_PAIRS) {
        if (pairs.size <= limit) return
        val keep = pairs.entries.sortedByDescending { it.value }.take(limit)
        pairs.clear()
        for (e in keep) pairs[e.key] = e.value
    }
}
