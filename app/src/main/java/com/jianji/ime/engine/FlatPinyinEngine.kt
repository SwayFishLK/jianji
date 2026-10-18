package com.jianji.ime.engine

import kotlin.math.ln

/**
 * M2 扁平前缀引擎：
 *
 *  1. 全拼前缀：full 以 q 开头（天然覆盖「末尾残缺音节补全」，如 nih → 你好）
 *  2. 简拼前缀（q ≥ 2 个字母）：initials 以 q 开头（如 nh → 你好）
 *  3. 前缀回退：q 在音节边界处切开后，前缀段做完全匹配（kaifazhe → 开发、开）
 *  4. 简拼前缀回退：主路径全空时（jjjjj）按前缀逐级做简拼完全匹配
 *  5. 全句组合：q 整段切成 2 个以上的词拼成短语/整句（nihaoma → 你好吗、woxiangshuruyijuhua → 我想输入一句话）
 *
 * 每个候选都带 consumed（吃掉的拼音字母数）：小于输入长度时，上屏后剩余拼音留在组句里
 * 继续出候选（对齐搜狗/青简的「部分上屏」：kaifazhe 选 开发 → 剩 zhe 继续）。
 *
 * 排序分 = ln(1+词频) × 来源权重 + 完全匹配加分 + 用户词频加分（封顶）。
 * 同一词取最高分。常数集中在这里，方便上机后微调。
 */
class FlatPinyinEngine(
    private val dict: JianDict,
    private val userCount: (String) -> Int = { 0 },
) : PinyinEngine {

    override fun candidates(query: String, limit: Int): List<Candidate> {
        val q = buildString(query.length) {
            for (c in query) if (c in 'a'..'z') append(c)
        }
        if (q.isEmpty()) return emptyList()
        val best = HashMap<String, Cand>(256)
        val exactTop = scanFull(q, best)
        if (q.length >= 2) scanInitials(q, best)
        if (q.length >= 3) scanBoundary(q, best)
        // 主路径一无所获时（输入比词更长，如 jjjjj）：按前缀逐级找完全简拼匹配
        if (best.isEmpty() && q.length >= 3) scanInitialsFallback(q, best)
        // 全句组合（多词连打）：把 q 整段切成 2 个以上的词，产出「整句候选」。只有当「整段输入已被
        // 一个像样的词吃掉」时才放弃简拼段（防「晚上吗」压过「为什么」）；命中词很弱（如 nhm 的女孩们
        // freq=263）时仍然给简拼组合机会（你好吗）。整段本身就是词库里的词且不弱 → 不出整句候选
        //（对齐青简「整段是一个词就不必当整句显示」）；其余整句候选放开排，只留和最优咬得住的。
        scanCompose(
            q, best,
            allowAbbrev = best.values.none { it.consumed >= q.length && it.score >= FULL_CONSUMER_TRUST },
            exactTop = exactTop,
        )

        val out = ArrayList<Candidate>(best.size)
        for ((w, c) in best) {
            val boost = minOf(userCount(w), 20) * 0.3f
            out.add(Candidate(w, c.score + boost, c.consumed))
        }
        out.sortByDescending { it.score }
        return if (out.size > limit) ArrayList(out.subList(0, limit)) else out
    }

    /** 候选：分数 + 消耗的字母数 */
    private class Cand(val score: Float, val consumed: Int)

    /** 同词贪心合并：保留更高分（分数变了说明来源更强，连同它的消耗长度一起采用） */
    private fun putBest(best: HashMap<String, Cand>, w: String, s: Float, consumed: Int) {
        val old = best[w]
        if (old == null || s > old.score) best[w] = Cand(s, consumed)
    }

    // —————— 1) 全拼前缀 ——————

    /** 全拼前缀扫描；返回「与输入等长的精确词」里的最高分（整句抑制用），没有则 0 */
    private fun scanFull(q: String, best: HashMap<String, Cand>): Float {
        val top = TopByScore(160)
        var exactTop = 0f
        val n = dict.size
        var i = dict.lowerBoundFull(q)
        while (i < n && dict.startsWithFull(i, q)) {
            val base = ln(1f + dict.freqAt(i))
            // 拼音与输入完全一致 → 优先；只是「还没打完」的补全 → 让位给精确词
            val s = if (dict.pinyinLenAt(i) == q.length) {
                // 整段吃满：抬一手（整段命中该胜过「只吃一截」的前缀候选）
                exactTop = maxOf(exactTop, base + EXACT_BONUS)
                base + EXACT_BONUS
            } else {
                base - 0.5f
            }
            top.offer(i, s)
            i++
        }
        merge(top, best, q.length)
        return exactTop
    }

    // —————— 2) 简拼前缀 ——————

    private fun scanInitials(q: String, best: HashMap<String, Cand>) {
        val top = TopByScore(120)
        val n = dict.size
        var j = dict.lowerBoundInitials(q)
        while (j < n && dict.startsWithInitials(j, q)) {
            val e = dict.entryOfInitials(j)
            var s = ln(1f + dict.freqAt(e)) * 0.45f - 0.5f
            if (dict.compareInitialsAt(j, q) == 0) s += 0.5f // 简拼完全一致
            top.offer(e, s)
            j++
        }
        merge(top, best, q.length)
    }

    // —————— 3) 前缀回退（音节边界处完全匹配） ——————

    private fun scanBoundary(q: String, best: HashMap<String, Cand>) {
        val n = q.length
        for (b in boundaries(q)) {
            if (b <= 1 || b >= n) continue
            val p = q.substring(0, b)
            var i = dict.lowerBoundFull(p)
            var taken = 0
            while (i < dict.size && taken < 10 &&
                dict.pinyinLenAt(i) == p.length && dict.startsWithFull(i, p)
            ) {
                // 回退候选：前缀吃得越长权重越高（kaifazhe → 开发 优先于 卡/开），消耗长度 = 边界；
                // 单字候选再压一手：超高频单字（你 600 万）不该盖过多字词（nihaom：「你」 vs 「你好」）
                val w = dict.wordAt(i)
                var s = ln(1f + dict.freqAt(i)) * (0.45f + 0.06f * b)
                if (w.length == 1) s *= SINGLE_BOUNDARY_DISCOUNT
                putBest(best, w, s, b)
                i++
                taken++
            }
        }
    }

    // —————— 4) 简拼前缀回退（主路径全空时） ——————

    /**
     * 输入比词更长时（jjjjj）：从最长前缀开始找「完全等于某词简拼」的匹配，
     * 找到即止（最多下探 4 级），权重随前缀长度增加。
     */
    private fun scanInitialsFallback(q: String, best: HashMap<String, Cand>) {
        var tried = 0
        var k = q.length - 1
        while (k >= 2 && tried < 4) {
            tried++
            val p = q.substring(0, k)
            var j = dict.lowerBoundInitials(p)
            var taken = 0
            var any = false
            while (j < dict.size && taken < 8 && dict.compareInitialsAt(j, p) == 0) {
                any = true
                val e = dict.entryOfInitials(j)
                putBest(best, dict.wordAt(e), ln(1f + dict.freqAt(e)) * (0.45f + 0.06f * k), k)
                j++
                taken++
            }
            if (any) return
            k--
        }
    }

    // —————— 5) 全句组合（整句 / 多词连打） ——————

    /** 路径：累积分、段数、拼出的词串、是否全程简拼（裸声母语气词的代价只在全简拼读法里从轻） */
    private class Path(val score: Float, val segs: Int, val text: String, val allAbbrev: Boolean)

    /** 一段拼音匹配到的词（abbrev = 靠简拼匹配，打分已打折、代价也从轻） */
    private class Seg(val word: String, val score: Float, val abbrev: Boolean)

    /** 单字母末段只允许接这些语气词（nihaom → 你好+吗；不问 zz 里硬凑单字） */
    private val particles = setOf(
        "的", "了", "吗", "呢", "吧", "啊", "嘛", "么", "哦", "呀", "啦", "哪",
        "哈", "喂", "嗯", "噢", "哟", "唉", "哇", "咯", "喽", "嘞", "呐", "呗", "咧",
    )

    /**
     * 全句组合：把 q 整段切成 2 个以上的词，产出「整句候选」（woxiangshuruyijuhua → 我想输入一句话）。
     *
     * 路径分 = Σ段分 − Σ段代价 + 全句覆盖加分，再打 COMPOSE_DISCOUNT 折。段代价 ≈ 语言模型里
     * log P(词) 的归一化项（少段、常见词占优）；单字段再加重（词没配成词，多字长串碎拼会被自然压掉）。
     * 覆盖加分只给铺满整段输入的路径，让配得通的整句排到词级候选前面（对齐搜狗/青简：首选是整句）。
     *
     *  - 每段优先「全拼完全匹配」（西安/先 这类同音词取词频最高者）；
     *    allowAbbrev 时也认「简拼完全匹配」（nh → 你好、m → 吗，打进 0.45 折）；
     *  - 非末段多字词要够常见（SEG_MIN_FREQ），单字词只放行常用高频字（SINGLE_MIN_FREQ）；
     *  - 末段单音节全拼匹配时语气词优先（nihaoma → 你好吗 而不是 你好马）；
     *  - 整段精确命中一个像样的词时不出整句候选（青简：整段是一个词就不当整句显示）；
     *  - 只发和最优路径咬得住的（COMPOSE_MARGIN 内，差的读数多是碎拼噪声），最多 COMPOSE_MAX 个。
     */
    private fun scanCompose(q: String, best: HashMap<String, Cand>, allowAbbrev: Boolean, exactTop: Float) {
        val n = q.length
        if (n < 2) return
        if (exactTop >= EXACT_FULL_TRUST) return
        val paths = arrayOfNulls<ArrayList<Path>>(n + 1)
        paths[0] = arrayListOf(Path(0f, 0, "", true))
        for (i in 1..n) {
            val here = ArrayList<Path>(PATH_BEAM)
            val isFinal = i == n
            for (j in 0 until i) {
                val from = paths[j] ?: continue
                val seg = segment(q, j, i, isFinal, allowAbbrev) ?: continue
                for (p in from) {
                    val cost = segCost(seg, isFinal, p.allAbbrev)
                    offerPath(here, Path(p.score + seg.score - cost, p.segs + 1, p.text + seg.word, p.allAbbrev && seg.abbrev))
                }
            }
            paths[i] = if (here.isEmpty()) null else here
        }
        val full = paths[n] ?: return
        full.sortByDescending { it.score }
        val floorRaw = full[0].score - COMPOSE_MARGIN
        var taken = 0
        for (p in full) {
            if (p.score < floorRaw) break
            val s = (p.score + COVER_BONUS) * COMPOSE_DISCOUNT
            if (p.segs < 2 || s < COMPOSE_MIN_SCORE) continue
            if (!best.containsKey(p.text)) {
                best[p.text] = Cand(s, n)
                if (++taken >= COMPOSE_MAX) break
            }
        }
    }

    /**
     * 一段的代价：简拼段基础代价从轻（段分本身已打过折），单字词按是否末段再加重。
     * 裸声母语气词的加重只在「全程简拼」的读法里从轻（nhm 的 m → 吗）；
     * 跟在全拼词后面的裸声母多半是没切干净的尾字母（nihaozaijian 的 n），按全价压。
     */
    private fun segCost(seg: Seg, isFinal: Boolean, inAllAbbrev: Boolean): Float {
        val base = if (seg.abbrev) ABBREV_COST else SEG_COST
        if (seg.word.length != 1) return base
        var extra = if (isFinal) SINGLE_FINAL_EXTRA else SINGLE_NONFINAL_EXTRA
        if (seg.abbrev && inAllAbbrev) extra *= ABBREV_SEG_FACTOR
        return base + extra
    }

    /** q[j..i) 这段拼音的最佳词（全拼完全匹配优先，可用时再看简拼完全匹配） */
    private fun segment(q: String, j: Int, i: Int, isFinal: Boolean, allowAbbrev: Boolean): Seg? {
        val span = q.substring(j, i)
        var word: String? = null
        var score = Float.NEGATIVE_INFINITY
        var abbrev = false

        // a) 全拼完全匹配
        var k = dict.lowerBoundFull(span)
        var taken = 0
        var wTop: String? = null
        var fTop = 0
        var wPar: String? = null
        var fPar = 0
        while (k < dict.size && taken < 16 &&
            dict.pinyinLenAt(k) == span.length && dict.startsWithFull(k, span)
        ) {
            val w = dict.wordAt(k)
            val f = dict.freqAt(k)
            // 非末段要够常见：多字词看 SEG_MIN_FREQ，单字词只放行常用高频字（输入 freq 10549 要能过）
            val ok = isFinal ||
                (w.length >= 2 && f >= SEG_MIN_FREQ) ||
                (w.length == 1 && f >= SINGLE_MIN_FREQ)
            if (ok) {
                if (wTop == null) {
                    wTop = w
                    fTop = f
                }
                if (isFinal && w.length == 1 && w in particles) {
                    wPar = w
                    fPar = f
                }
                if (!isFinal) break // 非末段只取词频最高的合规则者
            }
            k++
            taken++
        }
        if (wTop != null) {
            val usePar = wPar != null && fPar * 3 >= fTop
            val w = if (usePar) wPar!! else wTop!!
            val f = if (usePar) fPar else fTop
            word = w
            score = ln(1f + f)
            abbrev = false
        }

        // b) 简拼完全匹配（音节数 = span 长度：nh → 你好、m → 吗）
        if (allowAbbrev) {
            var e = dict.lowerBoundInitials(span)
            var jt = 0
            while (e < dict.size && jt < 16 && dict.compareInitialsAt(e, span) == 0) {
                val cand = dict.entryOfInitials(e)
                val w = dict.wordAt(cand)
                val ok = if (span.length == 1) {
                    // 单字母段只当末段的语气词用
                    isFinal && w.length == 1 && w in particles
                } else {
                    // 简拼段（≥2 音节）：末段随意，非末段要够常见（简拼精度低，门槛比全拼高）
                    isFinal || dict.freqAt(cand) >= ABBREV_SEG_MIN_FREQ
                }
                if (ok) {
                    val s = ln(1f + dict.freqAt(cand)) * ABBREV_SEG_FACTOR
                    if (s > score) {
                        word = w
                        score = s
                        abbrev = true
                    }
                    break
                }
                e++
                jt++
            }
        }
        return word?.let { Seg(it, score, abbrev) }
    }

    /** 每个位置保留前 PATH_BEAM 条路径，文本相同去重取高分 */
    private fun offerPath(list: ArrayList<Path>, p: Path) {
        var worst = 0
        for (k in list.indices) {
            if (list[k].text == p.text) {
                if (p.score > list[k].score) list[k] = p
                return
            }
            if (list[k].score < list[worst].score) worst = k
        }
        if (list.size < PATH_BEAM) list.add(p)
        else if (p.score > list[worst].score) list[worst] = p
    }

    /** q 所有可能的音节切分边界（位置 = 音节结束处），带记忆化 */
    private fun boundaries(q: String): IntArray {
        val n = q.length
        val vis = BooleanArray(n + 1)
        val out = ArrayList<Int>(8)
        fun dfs(pos: Int) {
            if (pos >= n || vis[pos]) return
            vis[pos] = true
            val maxL = minOf(dict.maxSyllableLen, n - pos)
            for (l in 1..maxL) {
                val s = q.substring(pos, pos + l)
                if (s in dict.syllableSet) {
                    out.add(pos + l)
                    dfs(pos + l)
                }
            }
        }
        dfs(0)
        return out.toIntArray()
    }

    private fun merge(top: TopByScore, best: HashMap<String, Cand>, consumed: Int) {
        for (k in 0 until top.size) {
            putBest(best, dict.wordAt(top.entries[k]), top.scores[k], consumed)
        }
    }

    /** 定容最小堆：按分数选前 cap 名 */
    private class TopByScore(private val cap: Int) {
        val entries = IntArray(cap)
        val scores = FloatArray(cap)
        var size = 0
            private set

        fun offer(e: Int, s: Float) {
            if (size < cap) {
                entries[size] = e
                scores[size] = s
                siftUp(size)
                size++
            } else if (s > scores[0]) {
                entries[0] = e
                scores[0] = s
                siftDown(0)
            }
        }

        private fun swap(a: Int, b: Int) {
            val e = entries[a]; entries[a] = entries[b]; entries[b] = e
            val s = scores[a]; scores[a] = scores[b]; scores[b] = s
        }

        private fun siftUp(i0: Int) {
            var i = i0
            while (i > 0) {
                val p = (i - 1) shr 1
                if (scores[p] <= scores[i]) break
                swap(i, p)
                i = p
            }
        }

        private fun siftDown(i0: Int) {
            var i = i0
            while (true) {
                val l = i * 2 + 1
                if (l >= size) break
                val r = l + 1
                val m = if (r < size && scores[r] < scores[l]) r else l
                if (scores[i] <= scores[m]) break
                swap(i, m)
                i = m
            }
        }
    }

    private companion object {
        /** 组合里每段的「归一化代价」（≈ 语言模型里 log P(词) 的归一化项）：越偏好少段、常见词 */
        const val SEG_COST = 6.0f

        /** 非末段单字词的额外代价：词没配成词，可信度低（常用虚词/动词才放行，仍要从重压住碎拼） */
        const val SINGLE_NONFINAL_EXTRA = 9.0f

        /** 末段单字词的额外代价：语气词/收尾单字很常见，从轻 */
        const val SINGLE_FINAL_EXTRA = 4.0f

        /** 简拼段的基础代价（段分已打过折，代价从轻） */
        const val ABBREV_COST = 4.5f

        /** 全句覆盖加分：铺满整段输入的路径抬一手（整句候选排词级候选前面，对齐搜狗） */
        const val COVER_BONUS = 4.0f

        /** 组合非末段多字词的词频门槛（要放行「输入」freq 10549 这类常用词） */
        const val SEG_MIN_FREQ = 2500

        /** 组合非末段简拼词的词频门槛（简拼精度低，门槛更高） */
        const val ABBREV_SEG_MIN_FREQ = 10000

        /** 非末段单字词的词频门槛（只放行常用字：我你的是在去好开听说学…，挡低频字碎拼） */
        const val SINGLE_MIN_FREQ = 100000

        /** 简拼段打折（与 scanInitials 的 0.45 对齐）；单字段额外代价也按此折算 */
        const val ABBREV_SEG_FACTOR = 0.45f

        /** 组合候选整体折扣与最低分门槛 */
        const val COMPOSE_DISCOUNT = 0.9f
        const val COMPOSE_MIN_SCORE = 3.0f

        /** 整句候选只发和最优路径差在 COMPOSE_MARGIN 内的（更差的读数多是碎拼噪声） */
        const val COMPOSE_MARGIN = 2.0f

        /** 整个输入被一个词吃掉、且分数不低于此值时，认为读法已确认，放弃简拼组合 */
        const val FULL_CONSUMER_TRUST = 4.0f

        /** 整段精确命中一个词的加分：整段吃满的读法抬一手，胜「只吃一截」的前缀候选 */
        const val EXACT_BONUS = 4.0f

        /** 整段精确命中一个词、且分数不低于此值时不出整句候选（青简：整段是一个词就按词级排序放） */
        const val EXACT_FULL_TRUST = 9.0f

        /** 前缀回退里的单字候选压一手：超高频单字（你 600 万）不该盖过多字词（nihaom：「你」 vs 「你好」） */
        const val SINGLE_BOUNDARY_DISCOUNT = 0.85f

        /** 最多补几个组合候选 */
        const val COMPOSE_MAX = 4

        /** 每个位置保留的路径条数 */
        const val PATH_BEAM = 16
    }
}
