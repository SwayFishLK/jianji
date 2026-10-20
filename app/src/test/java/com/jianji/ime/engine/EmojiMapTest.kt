package com.jianji.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** emoji-map.bin（词 → 一个 emoji）直测：常用词选得对、每条只有一个、没有空白。 */
class EmojiMapTest {

    private val map: EmojiMap by lazy {
        val f = listOf(File("src/main/assets/emoji-map.bin"), File("app/src/main/assets/emoji-map.bin"))
            .firstOrNull { it.exists() } ?: error("找不到 emoji-map.bin")
        EmojiMap.load(f.inputStream())
    }

    @Test
    fun `常用词映射`() {
        assertEquals("😄", map.emojiFor("开心"))
        assertEquals("😠", map.emojiFor("生气"))
        assertEquals("🥰", map.emojiFor("可爱"))
        assertEquals("🙏", map.emojiFor("谢谢"))
        assertEquals("🍚", map.emojiFor("吃饭"))
        assertEquals("🐱", map.emojiFor("猫"))
    }

    @Test
    fun `没有映射返回 null`() {
        assertNull(map.emojiFor("我们"))
        assertNull(map.emojiFor("你好吗"))
        assertNull(map.emojiFor("not-a-word"))
    }

    @Test
    fun `每条只有一个 emoji，词长 1~4`() {
        assertTrue("映射太少了：${map.size}", map.size > 4000)
        // 通过已知词抽查：emoji 里不能带空格（CLDR 原始数据是一行多个，生成时必须只留一个）
        for (w in listOf("开心", "高兴", "难过", "生日", "旅行", "周末", "工作", "电影", "音乐")) {
            val e = map.emojiFor(w)
            assertTrue("$w 缺映射", e != null)
            assertTrue("$w 的 emoji 有空白：$e", e!!.none { it.isWhitespace() })
            assertTrue("$w 词长不对", w.length in 1..4)
        }
    }
}