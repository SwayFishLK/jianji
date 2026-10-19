package com.jianji.ime.engine

/**
 * 模糊音查询变体（不动词库：输入侧按词库音节表切段、逐段生成变体再拼回）。
 *
 *  - [GROUP_ZH]    平翘舌：zh↔z、ch↔c、sh↔s
 *  - [GROUP_NASAL] 前后鼻音：ang↔an、eng↔en、ing↔in
 *  - [GROUP_NL]    n↔l（音节开头）
 *
 * 变体数封顶（[MAX]），优先改动少的；同一串只出现一次。
 * 切不动的尾巴（jintiang 的 g、jintia 的补全在词库里天然前缀）按字符级规则补：
 * 多余鼻音尾巴丢掉、'z' 类前缀补/去 'h'；整段切不出音节时不猜（返回空）。
 */
object FuzzyVariants {

    const val GROUP_ZH = 1
    const val GROUP_NASAL = 2
    const val GROUP_NL = 4

    private const val MAX = 16

    /** q 的模糊音变体（不含 q 本身）；mask = 0 / 太短 / 切不出音节 → 空表 */
    fun variants(q: String, mask: Int, syllables: Set<String>): List<String> {
        if (mask == 0 || q.length < 2) return emptyList()
        val maxLen = syllables.maxOfOrNull { it.length } ?: 6

        // 1) 贪心切段（最长音节优先），切不动就停（剩下的当尾巴）
        val parts = ArrayList<String>(8)
        var i = 0
        while (i < q.length) {
            var len = 0
            var k = minOf(maxLen, q.length - i)
            while (k >= 1) {
                if (syllables.contains(q.substring(i, i + k))) {
                    len = k
                    break
                }
                k--
            }
            if (len == 0) break
            parts.add(q.substring(i, i + len))
            i += len
        }
        if (parts.isEmpty()) return emptyList()
        val tail = q.substring(i)

        // 2) 逐段展开（改动少的优先），每步封顶
        var cur = ArrayList<V>(MAX)
        cur.add(V("", 0))
        for (p in parts) cur = expand(cur, p, segVariants(p, mask))
        if (tail.isNotEmpty()) cur = expand(cur, tail, tailVariants(tail, mask))

        val out = ArrayList<String>(cur.size)
        for (v in cur) if (v.s != q) out.add(v.s)
        return out
    }

    /** 变体串 + 累计改动段数 */
    private class V(val s: String, val d: Int)

    private fun expand(cur: List<V>, base: String, vs: List<String>): ArrayList<V> {
        val next = ArrayList<V>(minOf(cur.size * vs.size + 1, 256))
        for (c in cur) {
            for ((k, v) in vs.withIndex()) {
                next.add(V(c.s + v, c.d + if (k == 0 && v == base) 0 else 1))
            }
        }
        next.sortWith(compareBy({ it.d }, { it.s }))
        val out = ArrayList<V>(MAX)
        for (v in next) {
            var dup = false
            for (o in out) if (o.s == v.s) { dup = true; break }
            if (!dup) out.add(v)
            if (out.size >= MAX) break
        }
        return out
    }

    /** 完整音节段：原段 + 各组的单步变体 */
    private fun segVariants(s: String, mask: Int): List<String> {
        val out = ArrayList<String>(4)
        out.add(s)
        if (mask and GROUP_ZH != 0) {
            when {
                s.startsWith("zh") -> out.add("z" + s.substring(2))
                s.startsWith("ch") -> out.add("c" + s.substring(2))
                s.startsWith("sh") -> out.add("s" + s.substring(2))
                s.startsWith("z") -> out.add("zh" + s.substring(1))
                s.startsWith("c") -> out.add("ch" + s.substring(1))
                s.startsWith("s") -> out.add("sh" + s.substring(1))
            }
        }
        if (mask and GROUP_NASAL != 0) {
            when {
                s.endsWith("ang") -> out.add(s.dropLast(1))
                s.endsWith("an") -> out.add(s + "g")
                s.endsWith("eng") -> out.add(s.dropLast(1))
                s.endsWith("en") -> out.add(s + "g")
                s.endsWith("ing") -> out.add(s.dropLast(1))
                s.endsWith("in") -> out.add(s + "g")
            }
        }
        if (mask and GROUP_NL != 0) {
            when {
                s.startsWith("n") -> out.add("l" + s.substring(1))
                s.startsWith("l") -> out.add("n" + s.substring(1))
            }
        }
        return out.distinct()
    }

    /** 尾巴（不是完整音节）：先拔多余的鼻音（jintiang 的 g、tiang|qi 里的 g、jintiann 的 n），再用前缀级规则（z→zh 等） */
    private fun tailVariants(s: String, mask: Int): List<String> {
        val out = ArrayList<String>(5)
        out.add(s)
        if (mask and GROUP_NASAL != 0) {
            when {
                // 尾巴本身就是多余鼻音
                s == "g" || s == "ng" || s == "n" -> out.add("")
                // g 被吞进后一段（tiangqi → tian + g + qi）：把 g 还给前一段的鼻音（拼回原串被过滤）或直接丢掉
                s.startsWith("g") -> out.add(s.substring(1))
            }
        }
        for (v in segVariants(s, mask)) if (v !in out) out.add(v)
        return out
    }
}
