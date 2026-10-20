package com.jianji.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 中文模式附加候选（英文词提示 + 一个 emoji）端到端直测：真实词库 / 英文词表 / emoji 映射。
 *
 * 关键边界：
 *  - hello → 英文 hello 排第一（拼音读不通）；
 *  - hel → help 排第二（荷兰 8.9 整段成立，不抢首选）；
 *  - you / women / can → 中文整段强命中，不出英文；
 *  - 2 个字母只认整词（th 不出 the）；
 *  - kaixin → 开心 + 😄；kaixind 还在组句（首选只吃 6 个字母）→ 不出 emoji。
 */
class HintPlannerTest {

    private fun asset(name: String): File =
        listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
            .firstOrNull { it.exists() } ?: error("找不到 $name")

    private val dict: JianDict by lazy { JianDict.load(asset("dict.bin").inputStream()) }
    private val engine: FlatPinyinEngine by lazy { FlatPinyinEngine(dict) }
    private val english: EnglishDict by lazy { EnglishDict.load(asset("english.bin").inputStream()) }
    private val emojis: EmojiMap by lazy { EmojiMap.load(asset("emoji-map.bin").inputStream()) }

    private fun plan(q: String) = HintPlanner.plan(q, engine.candidates(q, 100), english, emojis)

    @Test
    fun `hello：拼音不通，英文排第一`() {
        val p = plan("hello")
        assertEquals("hello", p.english)
        assertEquals(0, p.englishIndex)
    }

    @Test
    fun `hel：中文整段成立时不抢首选，英文排第二`() {
        val p = plan("hel")
        assertEquals("help", p.english)
        assertEquals(1, p.englishIndex)
        assertEquals("荷兰", engine.candidates("hel", 1).first().text)
    }

    @Test
    fun `拼音整段强命中：不出英文`() {
        for (q in listOf("you", "women", "can", "he", "me", "to", "go", "no", "so", "an", "we", "do")) {
            assertNull("$q 不该出英文", plan(q).english)
        }
    }

    @Test
    fun `两个字母只认整词：th 不出 the`() {
        assertNull(plan("th").english)
        assertEquals("is", plan("is").english) // 中文没有候选，英文整词排第一
        assertEquals("it", plan("it").english)
    }

    @Test
    fun `game：只有零碎中文候选，英文排第一`() {
        val p = plan("game")
        assertEquals("game", p.english)
        assertEquals(0, p.englishIndex)
    }

    @Test
    fun `emoji：首选词整段吃满才出，且只有一个`() {
        val p = plan("kaixin")
        assertNull("kaixin 不是英文词", p.english)
        assertEquals("😄", p.emoji)

        assertEquals("🙏", plan("xiexie").emoji)
        assertNull("我们 没有 emoji 映射", plan("women").emoji)

        // 还在组句：首选 开心 只吃 6 个字母，剩余 d 未处理
        assertNull("kaixind 不该抢着出 emoji", plan("kaixind").emoji)
    }

    @Test
    fun `空输入与超短输入不出附加候选`() {
        assertTrue(plan("").isEmpty)
        assertTrue(plan("k").isEmpty)
    }
}