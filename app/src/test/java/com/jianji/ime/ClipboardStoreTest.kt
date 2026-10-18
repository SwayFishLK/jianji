package com.jianji.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 剪贴板存储的纯逻辑部分（编码 / 去重 / 上限），与 Android 无关。 */
class ClipboardStoreTest {

    @Test
    fun encodeDecodeRoundTrip() {
        val items = listOf(
            "你好，世界",
            "line1\nline2\n\nline4",
            "emoji 😀🙂 和制表\t符",
            "\"引号\" 和 'single' 与 \\反斜杠\\",
            " 前后空白保留（写入前已 trim，这里测编码本身） ",
        )
        val raw = ClipboardStore.encode(items)
        assertEquals(items, ClipboardStore.decode(raw))
    }

    @Test
    fun decodeToleratesBadLines() {
        val raw = "5L2g5aW9\n这不是base64!!!\n5LiW55WM"
        assertEquals(listOf("你好", "世界"), ClipboardStore.decode(raw))
    }

    @Test
    fun decodeEmpty() {
        assertEquals(emptyList<String>(), ClipboardStore.decode(null))
        assertEquals(emptyList<String>(), ClipboardStore.decode(""))
    }

    @Test
    fun pushHistoryPrependsMovesAndDedups() {
        val h = mutableListOf<String>()
        assertTrue(ClipboardStore.pushHistory(h, "一"))
        assertTrue(ClipboardStore.pushHistory(h, "二"))
        assertTrue(ClipboardStore.pushHistory(h, "三"))
        assertEquals(listOf("三", "二", "一"), h)
        // 已在最前：不算变化
        assertFalse(ClipboardStore.pushHistory(h, "三"))
        // 在中间：上移
        assertTrue(ClipboardStore.pushHistory(h, "一"))
        assertEquals(listOf("一", "三", "二"), h)
        // 空白 / 空：拒绝
        assertFalse(ClipboardStore.pushHistory(h, "   "))
        assertFalse(ClipboardStore.pushHistory(h, ""))
        assertEquals(listOf("一", "三", "二"), h)
    }

    @Test
    fun pushHistoryCapsAtMax() {
        val h = mutableListOf<String>()
        for (i in 1..ClipboardStore.HISTORY_MAX + 10) {
            ClipboardStore.pushHistory(h, "第 $i 条")
        }
        assertEquals(ClipboardStore.HISTORY_MAX, h.size)
        assertEquals("第 ${ClipboardStore.HISTORY_MAX + 10} 条", h.first())
        assertEquals("第 11 条", h.last())
    }

    @Test
    fun normalizeRules() {
        assertNull(ClipboardStore.normalize(""))
        assertNull(ClipboardStore.normalize("  \n\t "))
        assertEquals("abc", ClipboardStore.normalize("  abc  "))
        assertNull(ClipboardStore.normalize("x".repeat(ClipboardStore.TEXT_MAX + 1)))
        assertEquals("x".repeat(ClipboardStore.TEXT_MAX), ClipboardStore.normalize("x".repeat(ClipboardStore.TEXT_MAX)))
    }

    @Test
    fun shouldSkipOnlyForNonForcedMatch() {
        // 打开面板顺手一抓：与跳过记录相同 → 跳过（删除/清空后不复活）
        assertTrue(ClipboardStore.shouldSkip("老内容", false, "老内容"))
        // 不同内容 → 正常抓取
        assertFalse(ClipboardStore.shouldSkip("新内容", false, "老内容"))
        // 没有跳过记录 → 正常抓取
        assertFalse(ClipboardStore.shouldSkip("任意", false, null))
        // 剪贴板真变化（force=true）→ 从不跳过，新复制照旧入列
        assertFalse(ClipboardStore.shouldSkip("老内容", true, "老内容"))
        assertFalse(ClipboardStore.shouldSkip("新内容", true, null))
    }
}
