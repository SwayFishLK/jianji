package com.jianji.ime.engine

import com.jianji.ime.Learning
import com.jianji.ime.PhraseIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 自造短语（整串重复输入学习）JVM 直测：用真实词库模拟「wutiaowu 打 3 次 → 五条悟」：
 *
 *  - 次数不到门槛不出候选（只打一次不会记住）；
 *  - 达到门槛后，全拼（wutiaowu / 前缀 wutiao）与简拼（wtw）、混着打（wutw）都出整串，且排在第一；
 *  - 短输入（wu / wt / wti）不被短语抢占；
 *  - 候选整段吃满（consumed = 输入长度）；
 *  - 简拼上屏（wtw 点选整串）不丢记忆：次数继续涨、全拼照旧命中（见 Learning.mergePhrase）；
 *  - 音节切分（按音节数回溯）单独直测。
 */
class PhraseCandidateTest {

    private val dict: JianDict by lazy {
        val f = listOf(File("src/main/assets/dict.bin"), File("app/src/main/assets/dict.bin"))
            .firstOrNull { it.exists() } ?: error("找不到 dict.bin（工作目录 ${File(".").absolutePath}）")
        JianDict.load(f.inputStream())
    }

    private val today = Learning.today()

    /**
     * 模拟服务端状态：3 次「wutiaowu → 五 条 悟」整串上屏后（每个字 +3 次、两声搭配各 3 次），
     * 短语「五条悟」按 stat 落库；userBoost / 短语命中照服务端接法。
     */
    private fun engineAt(stat: Learning.PhraseStat?): FlatPinyinEngine {
        val wordStats = HashMap<String, Learning.WordStat>()
        val pairs = HashMap<String, Int>()
        for (w in listOf("五", "条", "悟")) wordStats[w] = Learning.WordStat(3, today)
        pairs[Learning.pairKey("五", "条")] = 3
        pairs[Learning.pairKey("条", "悟")] = 3
        val last = "悟"
        val index = PhraseIndex().also {
            it.rebuild(
                stat?.let { s -> listOf(PhraseIndex.Entry("五条悟", s.pinyin, dict.syllablesOf(s.pinyin, 3), s.count, s.day)) }
                    ?: emptyList(),
            )
        }
        return FlatPinyinEngine(
            dict,
            userBoost = { w ->
                Learning.boost(wordStats[w], today) + Learning.pairBoost(pairs[Learning.pairKey(last, w)])
            },
            phraseHits = { q -> index.hits(q, today) },
        )
    }

    private fun trained(count: Int = Learning.PHRASE_MIN_COUNT) = Learning.PhraseStat("wutiaowu", count, today)

    @Test
    fun `音节切分：按音节数切开，切不开返回 null`() {
        assertEquals(listOf("wu", "tiao", "wu"), dict.syllablesOf("wutiaowu", 3))
        assertEquals(listOf("ni", "hao"), dict.syllablesOf("nihao", 2))
        assertEquals(listOf("ni", "hao", "ma"), dict.syllablesOf("nihaoma", 3))
        assertEquals(listOf("xi", "an"), dict.syllablesOf("xian", 2)) // 西安：靠音节数约束切开（先 = 1 音节）
        assertEquals(listOf("xian"), dict.syllablesOf("xian", 1))
        assertEquals(null, dict.syllablesOf("wutiaowu", 2))
        assertEquals(null, dict.syllablesOf("zzz", 1))
        assertEquals(null, dict.syllablesOf("", 1))
    }

    @Test
    fun `打满门槛：全拼与简拼与混合打都出整串且排第一，候选整段吃满`() {
        val e = engineAt(trained())
        val full = e.candidates("wutiaowu", 10)
        println("wutiaowu -> " + full.joinToString(" / ") { "${it.text}:${"%.2f".format(it.score)}(${it.consumed})" })
        assertEquals("五条悟", full.first().text)
        assertEquals(8, full.first().consumed)

        val prefix = e.candidates("wutiao", 10)
        assertEquals("五条悟", prefix.first().text)

        for (q in listOf("wtw", "wutw", "wutiaow")) {
            val hit = e.candidates(q, 10)
            println("$q -> " + hit.joinToString(" / ") { "${it.text}:${"%.2f".format(it.score)}(${it.consumed})" })
            assertEquals("$q：五条悟排第一", "五条悟", hit.first().text)
            assertEquals("$q：整段吃满", q.length, hit.first().consumed)
        }
    }

    @Test
    fun `次数不够不出候选，短输入不抢位`() {
        val weak = engineAt(trained(Learning.PHRASE_MIN_COUNT - 1))
        assertTrue("只打一次 / 两次不该记住", weak.candidates("wutiaowu", 100).none { it.text == "五条悟" })
        assertTrue(weak.candidates("wtw", 100).none { it.text == "五条悟" })

        val e = engineAt(trained())
        assertTrue("wu 太短：短语不抢常用字候选位", e.candidates("wu", 100).none { it.text == "五条悟" })
        assertTrue("wt 只有两个字母：不命中简拼", e.candidates("wt", 100).none { it.text == "五条悟" })
        assertTrue("wti 只覆盖前两个音节又不足 4 字母", e.candidates("wti", 100).none { it.text == "五条悟" })
        assertTrue("拼音对不上：不出", e.candidates("wutianwu", 100).none { it.text == "五条悟" })
    }

    /** 回归：用简拼 wtw 点选整串后，次数继续涨、全拼照旧能出（旧版这里会把记忆覆盖坏） */
    @Test
    fun `简拼上屏不丢记忆：拼音只升不降、次数只增不减`() {
        var stat = trained(3)
        stat = Learning.mergePhrase(stat, "wtw", today) // 用简拼点选整串上屏
        assertEquals("拼音没被简写覆盖", "wutiaowu", stat.pinyin)
        assertEquals("次数继续涨", 4, stat.count)

        val e = engineAt(stat)
        for (q in listOf("wutiaowu", "wtw", "wutw")) {
            assertEquals("$q：记忆还在", "五条悟", e.candidates(q, 10).first().text)
        }

        // 旧机器上已被简写覆盖坏的记录：下次打全拼自动修回
        val broken = Learning.PhraseStat("wtw", 4, today)
        assertTrue("坏记录下全拼对不上", engineAt(broken).candidates("wutiaowu", 10).none { it.text == "五条悟" })
        val healed = Learning.mergePhrase(broken, "wutiaowu", today)
        assertEquals("wutiaowu", healed.pinyin)
        assertEquals(5, healed.count)
        assertEquals("五条悟", engineAt(healed).candidates("wutiaowu", 10).first().text)
    }
}
