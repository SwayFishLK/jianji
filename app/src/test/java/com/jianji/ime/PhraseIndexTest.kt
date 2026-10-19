package com.jianji.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自造短语索引（PhraseIndex）JVM 直测：按音节分段的命中规则（全拼 / 简拼 / 混着打）、
 * 门槛、整段吃满、分数随次数与新鲜度增长。只测纯逻辑，不碰 Android。
 */
class PhraseIndexTest {

    /** wu|tiao|wu = 五条悟 */
    private val wtw = listOf("wu", "tiao", "wu")

    private fun entry(
        text: String = "五条悟",
        pinyin: String = "wutiaowu",
        syllables: List<String>? = wtw,
        count: Int = 3,
        day: Long = 20000,
    ) = PhraseIndex.Entry(text, pinyin, syllables, count, day)

    private fun index(vararg e: PhraseIndex.Entry) = PhraseIndex().also { it.rebuild(e.toList()) }

    private fun PhraseIndex.hitTexts(q: String) = hits(q, 20000L).map { it.text }

    @Test
    fun `全拼与混合打法都命中，短输入不命中`() {
        val ix = index(entry())
        for (q in listOf("wutiaowu", "wutiaow", "wutiao", "wutia", "wuti", "wut")) {
            if (q.length >= Learning.PHRASE_MIN_QUERY) {
                assertTrue("$q 应命中", "五条悟" in ix.hitTexts(q))
            }
        }
        assertTrue("wu 太短", ix.hitTexts("wu").isEmpty())
        assertTrue("wut 太短", ix.hitTexts("wut").isEmpty())
        assertTrue("拼音对不上", ix.hitTexts("wutianwu").isEmpty())
        assertTrue("超出整段拼音", ix.hitTexts("wutiaowux").isEmpty())
    }

    @Test
    fun `混着打的 wutw 与全简拼 wtw 都命中，整段吃满`() {
        val ix = index(entry())
        for (q in listOf("wtw", "wutw", "wutiw", "wutiaow")) {
            val hit = ix.hits(q, 20000)
            assertEquals("$q 只该命中这一条", 1, hit.size)
            assertEquals("五条悟", hit.first().text)
            assertEquals("$q 整段吃满", q.length, hit.first().consumed)
        }
        assertTrue("wt 覆盖不全又太短", ix.hitTexts("wt").isEmpty())
        assertTrue("wti 只覆盖前两个音节又不足 4 字母", ix.hitTexts("wti").isEmpty())
    }

    @Test
    fun `两字短语：简拼与半截都按音节走`() {
        val ix = index(entry("你好", "nihao", listOf("ni", "hao"), count = 5))
        for (q in listOf("nihao", "niha", "nih", "nh", "nha", "nhao")) {
            assertTrue("$q 应命中", "你好" in ix.hitTexts(q))
        }
        assertTrue("ni 太短", ix.hitTexts("ni").isEmpty())
        assertTrue("n 太短", ix.hitTexts("n").isEmpty())
        assertTrue("拼音对不上", ix.hitTexts("nihaoa").isEmpty())
    }

    @Test
    fun `门槛以下不出候选，音节切不开退回整段匹配`() {
        val weak = index(entry(count = Learning.PHRASE_MIN_COUNT - 1))
        assertTrue(weak.hitTexts("wutiaowu").isEmpty())
        assertTrue(weak.hitTexts("wtw").isEmpty())

        val noSyl = index(entry("花呗", "huabei", null, count = 5))
        assertEquals(listOf("花呗"), noSyl.hitTexts("huabei"))
        assertTrue("切不开就不认简拼", noSyl.hitTexts("hb").isEmpty())
        assertTrue("但短输入也不抢位", noSyl.hitTexts("hua").isEmpty())
        assertEquals(listOf("花呗"), noSyl.hitTexts("huab"))
    }

    @Test
    fun `候选分随次数与新鲜度增长`() {
        val few = index(entry())
        val more = index(entry(count = 30))
        val old = index(entry(day = 20000 - 400))
        val s = few.hits("wtw", 20000).first().score
        assertTrue("次数多分更高", more.hits("wtw", 20000).first().score > s)
        assertTrue("很久没用分更低", old.hits("wtw", 20000).first().score < s)
    }
}
