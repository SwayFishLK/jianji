package com.jianji.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记忆逻辑（Learning）JVM 直测：存储串解析（含旧格式兼容）、排序加分、瘦身。
 * 只测纯逻辑，不碰 Android。
 */
class LearningTest {

    @Test
    fun `词表解析兼容旧格式`() {
        val m = Learning.parseWords("你好\t3\n开发\t12\t20000\n坏行\n")
        assertEquals(2, m.size)
        assertEquals(Learning.WordStat(3, 0), m["你好"]) // 旧格式没有日序 → 0
        assertEquals(Learning.WordStat(12, 20000), m["开发"])
    }

    @Test
    fun `词表序列化往返`() {
        val m = HashMap<String, Learning.WordStat>()
        m["你好"] = Learning.WordStat(3, 20000)
        m["开发"] = Learning.WordStat(1, 0)
        val again = Learning.parseWords(Learning.serializeWords(m))
        assertEquals(m, again)
    }

    @Test
    fun `搭配表往返`() {
        val p = HashMap<String, Int>()
        p[Learning.pairKey("你好", "世界")] = 4
        p[Learning.pairKey("开发", "者")] = 1
        val again = Learning.parsePairs(Learning.serializePairs(p))
        assertEquals(p, again)
    }

    @Test
    fun `加分：次数越多越大、封顶`() {
        val day = 20000L
        val b1 = Learning.boost(Learning.WordStat(1, day), day)
        val b10 = Learning.boost(Learning.WordStat(10, day), day)
        val b200 = Learning.boost(Learning.WordStat(200, day), day)
        val b999 = Learning.boost(Learning.WordStat(999, day), day)
        assertTrue("1 次要加一点：$b1", b1 > 0f)
        assertTrue("10 次 > 1 次", b10 > b1)
        assertTrue("200 次 > 10 次", b200 > b10)
        assertEquals("200 次封顶", b200, b999, 0.001f)
    }

    @Test
    fun `加分：今天用过 大于 一周内 大于 一月内 大于 很久以前`() {
        val day = 20000L
        val today = Learning.boost(Learning.WordStat(5, day), day)
        val week = Learning.boost(Learning.WordStat(5, day), day + 3)
        val month = Learning.boost(Learning.WordStat(5, day), day + 20)
        val old = Learning.boost(Learning.WordStat(5, day), day + 90)
        assertTrue("$today > $week", today > week)
        assertTrue("$week > $month", week > month)
        assertTrue("$month > $old", month > old)
        assertEquals(0f, Learning.boost(null, day), 0.0001f)
    }

    @Test
    fun `搭配加分单调且封顶`() {
        assertEquals(0f, Learning.pairBoost(null), 0.0001f)
        assertEquals(0f, Learning.pairBoost(0), 0.0001f)
        assertTrue(Learning.pairBoost(3) > Learning.pairBoost(1))
        assertEquals(Learning.pairBoost(20), Learning.pairBoost(80), 0.0001f)
    }

    @Test
    fun `瘦身保留高频`() {
        val m = HashMap<String, Learning.WordStat>()
        for (i in 1..100) m["词$i"] = Learning.WordStat(i, 0)
        Learning.pruneWords(m, limit = 10)
        assertEquals(10, m.size)
        assertTrue(m.containsKey("词100") && m.containsKey("词91"))
        assertTrue(!m.containsKey("词1"))

        val p = HashMap<String, Int>()
        for (i in 1..30) p[Learning.pairKey("a", "b$i")] = i
        Learning.prunePairs(p, limit = 5)
        assertEquals(5, p.size)
        assertEquals(30, p[Learning.pairKey("a", "b30")])
    }
}
