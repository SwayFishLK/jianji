package com.jianji.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 释义 / 等级表的 JVM 直测（读真实 assets，`gradle :app:testDebugUnitTest`）：
 *  - 上划译文用「可输入形态」：第一条义项、去词性、去振假名注音；
 *  - 生词角标：英语 CEFR（B2 起标）/ 日语 JLPT（N2 起标）。
 */
class GlossAndLevelTest {

    private fun asset(name: String): File =
        listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
            .firstOrNull { it.exists() } ?: error("找不到 assets/$name（工作目录 ${File(".").absolutePath}）")

    private val en: GlossDict by lazy { GlossDict.load(asset("gloss-en.bin").inputStream()) }
    private val ja: GlossDict by lazy { GlossDict.load(asset("gloss-ja.bin").inputStream()) }
    private val levelsEn: LevelDict by lazy { LevelDict.load(asset("levels-en.bin").inputStream()) }
    private val levelsJa: LevelDict by lazy { LevelDict.load(asset("levels-ja.bin").inputStream()) }

    @Test
    fun `可输入形态：第一条义项、去词性去注音`() {
        val cases = listOf("你好", "开发", "吃饭", "今天", "什么", "谢谢", "再见", "中国")
        for (w in cases) {
            println("en $w 形态=${en.inputFormFor(w)} / 注记=${en.annotationFor(w)}")
            println("ja $w 形态=${ja.inputFormFor(w)} / 注记=${ja.annotationFor(w)}")
        }
        assertEquals("hello", en.inputFormFor("你好"))
        // 形态不含词性前缀
        val f = en.inputFormFor("你好")
        assertTrue("不该带词性：$f", f != null && !f.contains("."))
        assertNull(en.inputFormFor("这个词肯定不在表里"))
    }

    @Test
    fun `生词等级：入门不标、高级才标`() {
        println("en hello=${levelsEn.levelOf("hello")} develop=${levelsEn.levelOf("develop")}")
        println("en 生词标记 hello=${levelsEn.markFor("hello")} develop=${levelsEn.markFor("develop")}")
        assertEquals("hello 应为 A1（下标 0）", 0, levelsEn.levelOf("hello"))
        assertNull("A1 不该标生词", levelsEn.markFor("hello"))

        println("ja 開発=${levelsJa.levelOf("開発")} かかる=${levelsJa.levelOf("かかる")}")
        assertEquals("開発 应为 N1", "N1", levelsJa.markFor("開発"))
        assertEquals("かかる 应为 N5（下标 0）", 0, levelsJa.levelOf("かかる"))
        assertNull("N5 不该标生词", levelsJa.markFor("かかる"))
    }
}
