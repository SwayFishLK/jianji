package com.jianji.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自造短语索引（PhraseIndex）JVM 直测：命中规则（全拼前缀 / 完全一致 / 简拼）、
 * 门槛、整段吃满、分数随次数与新鲜度增长。只测纯逻辑，不碰 Android。
 */
class PhraseIndexTest {

    private fun index(vararg e: PhraseIndex.Entry) = PhraseIndex().also { it.rebuild(e.toList()) }

    private fun PhraseIndex.hitTexts(q: String) = hits(q, 20000L).map { it.text }

    @Test
    fun `全拼前缀与完全一致命中，短输入不命中`() {
        val ix = index(PhraseIndex.Entry("五条悟", "wutiaowu", "wtw", 3, 20000))
        assertTrue("五条悟" in ix.hitTexts("wutiaowu"))
        assertTrue("五条悟" in ix.hitTexts("wutiaow"))
        assertTrue(ix.hitTexts("wu").isEmpty())
        assertTrue(ix.hitTexts("wut").isEmpty())
        assertTrue(ix.hitTexts("wutiaowux").isEmpty())
    }

    @Test
    fun `简拼完全一致才命中，整段吃满`() {
        val ix = index(PhraseIndex.Entry("五条悟", "wutiaowu", "wtw", 3, 20000))
        val hit = ix.hits("wtw", 20000)
        assertEquals(1, hit.size)
        assertEquals("五条悟", hit.first().text)
        assertEquals("整段吃满", 3, hit.first().consumed)
        assertTrue(ix.hitTexts("wt").isEmpty())
        assertTrue(ix.hitTexts("wtwa").isEmpty())
    }

    @Test
    fun `门槛以下不出候选，简拼切不开只认全拼`() {
        val weak = index(PhraseIndex.Entry("五条悟", "wutiaowu", "wtw", Learning.PHRASE_MIN_COUNT - 1, 20000))
        assertTrue(weak.hitTexts("wutiaowu").isEmpty())
        assertTrue(weak.hitTexts("wtw").isEmpty())

        val noIni = index(PhraseIndex.Entry("花呗", "huabei", null, 5, 20000))
        assertEquals(listOf("花呗"), noIni.hitTexts("huabei"))
        assertTrue(noIni.hitTexts("hb").isEmpty())
    }

    @Test
    fun `候选分随次数与新鲜度增长`() {
        val few = index(PhraseIndex.Entry("五条悟", "wutiaowu", "wtw", 3, 20000))
        val more = index(PhraseIndex.Entry("五条悟", "wutiaowu", "wtw", 30, 20000))
        val old = index(PhraseIndex.Entry("五条悟", "wutiaowu", "wtw", 3, 20000 - 400))
        val s = few.hits("wtw", 20000).first().score
        assertTrue("次数多分更高", more.hits("wtw", 20000).first().score > s)
        assertTrue("很久没用分更低", old.hits("wtw", 20000).first().score < s)
    }
}
