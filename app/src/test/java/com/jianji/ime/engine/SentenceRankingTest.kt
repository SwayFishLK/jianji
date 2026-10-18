package com.jianji.ime.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 引擎排序回归测试台（JVM 直跑，读真实 assets/dict.bin；`gradle :app:testDebugUnitTest`）。
 *
 *  - 每例打印候选前列（带分），肉眼确认手感；
 *  - 关键用例断言首选：词语手感 + 整句（M3.6）都要守住；
 *  - `数据查证`打印词库里的词频，调参前先确认数据。
 * 调 FlatPinyinEngine 常数后先跑它，再上机。
 */
class SentenceRankingTest {

    private val dict: JianDict by lazy {
        val f = listOf(File("src/main/assets/dict.bin"), File("app/src/main/assets/dict.bin"))
            .firstOrNull { it.exists() } ?: error("找不到 dict.bin（工作目录 ${File(".").absolutePath}）")
        JianDict.load(f.inputStream())
    }

    private val engine = FlatPinyinEngine(dict)

    private fun show(q: String, n: Int = 8): List<Candidate> =
        engine.candidates(q, n).also { cs ->
            println("候选 $q -> " + cs.joinToString(" / ") { "${it.text}:${"%.2f".format(it.score)}(${it.consumed})" })
        }

    private fun check(title: String, cases: List<Pair<String, String>>) {
        val failures = ArrayList<String>()
        for ((q, expect) in cases) {
            val got = show(q)
            if (got.firstOrNull()?.text != expect) {
                failures.add("$q 期望首选「$expect」，实际「${got.firstOrNull()?.text}」")
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** 词库里「全拼正好等于 pinyin」的词（词=freq），看数据用 */
    private fun fullMatches(pinyin: String): String {
        var i = dict.lowerBoundFull(pinyin)
        val l = ArrayList<String>()
        var taken = 0
        while (i < dict.size && taken < 40 &&
            dict.pinyinLenAt(i) == pinyin.length && dict.startsWithFull(i, pinyin)
        ) {
            l.add("${dict.wordAt(i)}=${dict.freqAt(i)}")
            i++
            taken++
        }
        return if (l.isEmpty()) "（无）" else l.joinToString(", ")
    }

    @Test
    fun `整句首选`() {
        check(
            "整句",
            listOf(
                "woxiangshuruyijuhua" to "我想输入一句话",
                "nihaoma" to "你好吗",
                "jintiantianqihenhao" to "今天天气很好",
                "womenyiqichifanba" to "我们一起吃饭吧",
                "nihaozaijian" to "你好再见",
                "nizaiganma" to "你在干嘛",
                "mingtianjian" to "明天见",
            ),
        )
    }

    @Test
    fun `词语手感回归`() {
        check(
            "词语",
            listOf(
                "nihao" to "你好",
                "kaifazhe" to "开发者",
                "shuru" to "输入",
                "zaijian" to "再见",
                "xiexie" to "谢谢",
                "woaini" to "我爱你",
            ),
        )
    }

    @Test
    fun `特殊行为`() {
        // 部分上屏：nihaom 首选「你好」且只吃 5 个字母（剩余 m 留组句）
        val c = show("nihaom", 10)
        val first = c.firstOrNull()
        assertTrue("nihaom 首选应为「你好」且 consumed=5，实际 $first", first?.text == "你好" && first.consumed == 5)

        // 简拼：nhm 要有「你好吗」方向
        val nhm = show("nhm", 10)
        assertTrue("nhm 应含「你好吗」，实际 ${nhm.map { it.text }}", nhm.any { it.text == "你好吗" })

        // 大长串无解时的回退（跑通即可）
        assertTrue(engine.candidates("jjjjj", 6).isNotEmpty())
        show("jjjjj", 6)
    }

    @Test
    fun `数据查证`() {
        val qs = listOf(
            "你在" to "nizai", "干嘛" to "ganma", "你在干嘛" to "nizaiganma",
            "明天见" to "mingtianjian", "明天" to "mingtian",
            "再见" to "zaijian", "在家" to "zaijia", "在" to "zai", "呢" to "ne",
            "很好" to "henhao", "今天天气" to "jintiantianqi", "今天" to "jintian", "天气" to "tianqi",
            "一句话" to "yijuhua", "输入" to "shuru", "我想" to "woxiang",
            "你好" to "nihao", "你好吗" to "nihaoma", "我爱你" to "woaini",
        )
        for ((w, py) in qs) println("词库 $w($py) -> " + fullMatches(py))
    }
}
