package com.jianji.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模糊音变体生成（纯逻辑，自带小音节表）。
 * 覆盖：平翘舌 / 前后鼻音（含多打的鼻音尾巴）/ n-l / 不猜的情况。
 */
class FuzzyVariantsTest {

    private val syllables = setOf(
        "zong", "zhong", "guo", "si", "shi", "jin", "tian",
        "lan", "nan", "ni", "li", "hao", "an", "ang", "chang", "chan",
    )

    private fun vs(q: String, mask: Int): List<String> =
        FuzzyVariants.variants(q, mask, syllables).also { println("变体 $q -> $it") }

    @Test
    fun `关着的时候没有变体`() {
        assertTrue(vs("zongguo", 0).isEmpty())
    }

    @Test
    fun `平翘舌：z 补 h`() {
        val v = vs("zongguo", FuzzyVariants.GROUP_ZH)
        assertTrue(v.contains("zhongguo"))
    }

    @Test
    fun `平翘舌：zh 去 h`() {
        val v = vs("zhongguo", FuzzyVariants.GROUP_ZH)
        assertTrue(v.contains("zongguo"))
    }

    @Test
    fun `平翘舌：s 补 h（sishi 类）`() {
        val v = vs("si", FuzzyVariants.GROUP_ZH)
        assertTrue(v.contains("shi"))
    }

    @Test
    fun `鼻音：后鼻音改前、前鼻音补后`() {
        val v = vs("chang", FuzzyVariants.GROUP_NASAL)
        assertTrue(v.contains("chan"))
    }

    @Test
    fun `鼻音：多打一个 g 的整串`() {
        val v = vs("jintiang", FuzzyVariants.GROUP_NASAL)
        assertTrue(v.contains("jintian"))
    }

    @Test
    fun `鼻音：多打的 g 卡在词中`() {
        val v = vs("tiangqi", FuzzyVariants.GROUP_NASAL)
        assertTrue(v.contains("tianqi"))
    }

    @Test
    fun `鼻音：多打一个 n 的尾巴`() {
        val v = vs("jintiann", FuzzyVariants.GROUP_NASAL)
        assertTrue(v.contains("jintian"))
    }

    @Test
    fun `n-l 互换`() {
        val v = vs("languo", FuzzyVariants.GROUP_NL)
        assertTrue(v.contains("nanguo"))
    }

    @Test
    fun `切不出音节不猜、原串不返回`() {
        assertTrue(vs("zzz", FuzzyVariants.GROUP_ZH).isEmpty())
        assertTrue(!vs("nihao", FuzzyVariants.GROUP_ZH).contains("nihao"))
    }

    @Test
    fun `变体数封顶`() {
        val v = FuzzyVariants.variants(
            "zongzhongchangzhanzhong",
            FuzzyVariants.GROUP_ZH or FuzzyVariants.GROUP_NASAL or FuzzyVariants.GROUP_NL,
            syllables,
        )
        assertTrue("不应超过 16 个：${v.size}", v.size <= 16)
        assertEquals(v.size, v.distinct().size)
    }
}
