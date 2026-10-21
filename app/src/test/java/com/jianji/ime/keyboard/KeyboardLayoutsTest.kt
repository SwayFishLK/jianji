package com.jianji.ime.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 字母页底排标点随中/英切换，符号分类含邮箱且不再有「更多」。 */
class KeyboardLayoutsTest {

    private fun bottomKeys(layout: KeyboardLayout): List<KeyDef> = layout.rows.last().keys

    @Test
    fun chinesePunctuationOnLetterPage() {
        val keys = bottomKeys(KeyboardLayouts.qwerty)
        assertEquals("，", keys[2].text)
        assertEquals("。", keys[4].text)
        assertTrue(KeyboardLayouts.alternatesFor(keys[2]).contains("、"))
        assertTrue(KeyboardLayouts.alternatesFor(keys[4]).contains("？"))
    }

    @Test
    fun englishPunctuationOnLetterPage() {
        val keys = bottomKeys(KeyboardLayouts.qwertyEnglish)
        assertEquals(",", keys[2].text)
        assertEquals(".", keys[4].text)
        // 英文标点不应继承中文标点的长按备选
        assertTrue(KeyboardLayouts.alternatesFor(keys[2]).isEmpty())
        assertTrue(KeyboardLayouts.alternatesFor(keys[4]).isEmpty())
    }

    @Test
    fun letterLayoutsDifferOnlyInPunctuation() {
        val zh = bottomKeys(KeyboardLayouts.qwerty)
        val en = bottomKeys(KeyboardLayouts.qwertyEnglish)
        assertEquals(zh.size, en.size)
        for (i in zh.indices) {
            if (i == 2 || i == 4) continue
            assertEquals(zh[i], en[i])
        }
    }

    @Test
    fun symbolCategoriesReplaceMoreWithEmailBeforeMath() {
        val names = KeyboardLayouts.symbolTabs.map { it.name }
        assertEquals(
            listOf("1.常用", "2.中文", "3.英文", "4.网络", "5.邮箱", "6.数学"),
            names,
        )
        assertFalse(names.any { it.contains("更多") })
        val email = KeyboardLayouts.symbolTabs.first { it.name == "5.邮箱" }
        val symbols = email.pages.flatMap { page ->
            page.rows.flatMap { row -> row.keys.filter { it.action == KeyAction.TEXT }.map { it.text } }
        }
        assertTrue(symbols.contains("@qq.com"))
        assertTrue(symbols.contains("@gmail.com"))
    }
}