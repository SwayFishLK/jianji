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
 *  - 达到门槛后，全拼（wutiaowu / 前缀 wutiaow）与简拼（wtw）都出整串，且排在第一；
 *  - 短输入（wu / wt）不被短语抢占；
 *  - 候选整段吃满（consumed = 输入长度）；
 *  - 简拼切分（按音节数回溯）单独直测。
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
     * 短语「五条悟」记了 phraseCount 次；userBoost / 短语命中照服务端接法。
     */
    private fun engineAt(phraseCount: Int): FlatPinyinEngine {
        val wordStats = HashMap<String, Learning.WordStat>()
        val pairs = HashMap<String, Int>()
        for (w in listOf("五", "条", "悟")) wordStats[w] = Learning.WordStat(3, today)
        pairs[Learning.pairKey("五", "条")] = 3
        pairs[Learning.pairKey("条", "悟")] = 3
        val last = "悟"
        val index = PhraseIndex().also {
            it.rebuild(
                listOf(
                    PhraseIndex.Entry("五条悟", "wutiaowu", dict.initialsOf("wutiaowu", 3), phraseCount, today),
                ),
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

    @Test
    fun `简拼切分：按音节数取首字母，切不开返回 null`() {
        assertEquals("wtw", dict.initialsOf("wutiaowu", 3))
        assertEquals("nh", dict.initialsOf("nihao", 2))
        assertEquals("nhm", dict.initialsOf("nihaoma", 3))
        assertEquals("xa", dict.initialsOf("xian", 2)) // 西安：靠音节数约束切开（先 = 1 音节）
        assertEquals("x", dict.initialsOf("xian", 1))
        assertEquals(null, dict.initialsOf("wutiaowu", 2))
        assertEquals(null, dict.initialsOf("zzz", 1))
        assertEquals(null, dict.initialsOf("", 1))
    }

    @Test
    fun `打满门槛：全拼与前缀与简拼都出整串且排第一，候选整段吃满`() {
        val e = engineAt(Learning.PHRASE_MIN_COUNT)
        val full = e.candidates("wutiaowu", 10)
        println("wutiaowu -> " + full.joinToString(" / ") { "${it.text}:${"%.2f".format(it.score)}(${it.consumed})" })
        assertEquals("五条悟", full.first().text)
        assertEquals(8, full.first().consumed)

        val prefix = e.candidates("wutiaow", 10)
        assertEquals("五条悟", prefix.first().text)

        val abbrev = e.candidates("wtw", 10)
        println("wtw -> " + abbrev.joinToString(" / ") { "${it.text}:${"%.2f".format(it.score)}(${it.consumed})" })
        assertEquals("五条悟", abbrev.first().text)
        assertEquals(3, abbrev.first().consumed)
    }

    @Test
    fun `次数不够不出候选，短输入不抢位`() {
        val weak = engineAt(Learning.PHRASE_MIN_COUNT - 1)
        assertTrue("只打一次 / 两次不该记住", weak.candidates("wutiaowu", 100).none { it.text == "五条悟" })
        assertTrue(weak.candidates("wtw", 100).none { it.text == "五条悟" })

        val e = engineAt(Learning.PHRASE_MIN_COUNT)
        assertTrue("wu 太短：短语不抢常用字候选位", e.candidates("wu", 100).none { it.text == "五条悟" })
        assertTrue("wt 只有两个字母：不命中简拼", e.candidates("wt", 100).none { it.text == "五条悟" })
        assertTrue("拼音对不上：不出", e.candidates("wutianwu", 100).none { it.text == "五条悟" })
    }
}
