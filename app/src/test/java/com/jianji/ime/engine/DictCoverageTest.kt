package com.jianji.ime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 词库覆盖回归：v0.4 并入白霜拼音后，近十年流行语/新名词要能查到，
 * 同时老词不能丢。直接读真实 assets/dict.bin，`gradle :app:testDebugUnitTest` 跑。
 */
class DictCoverageTest {

    private val dict: JianDict by lazy {
        val f = listOf(File("src/main/assets/dict.bin"), File("app/src/main/assets/dict.bin"))
            .firstOrNull { it.exists() } ?: error("找不到 dict.bin（工作目录 ${File(".").absolutePath}）")
        JianDict.load(f.inputStream())
    }

    private val modern = setOf(
        "五条悟", "绝绝子", "摆烂", "内卷", "破防", "元宇宙", "多模态", "具身智能",
        "显眼包", "搭子", "情绪价值", "松弛感", "淄博烧烤", "村超", "特种兵旅游",
        "雪糕刺客", "电子榨菜", "双减", "新质生产力", "低空经济", "银发经济",
        "数智化", "智能体", "人形机器人", "预制菜", "直播带货", "短视频",
        "碳中和", "双碳", "新能源", "充电桩", "网约车", "共享单车", "健康码",
        "方舱", "弹窗", "破圈", "塌房", "社死", "美拉德", "围炉煮茶", "陆冲",
        "桨板", "黑神话悟空", "鸿蒙", "原生鸿蒙", "大模型", "生成式人工智能",
    )

    private val old = setOf("你好", "中国", "开心", "我们", "喜欢", "世界", "人民", "输入法")

    @Test
    fun `词库规模与新旧词覆盖`() {
        assertTrue("词库应达到 35 万条以上，实际 ${dict.size}", dict.size >= 350_000)

        val want = modern + old
        val found = HashSet<String>(want.size)
        for (i in 0 until dict.size) {
            val w = dict.wordAt(i)
            if (w in want) found.add(w)
        }
        assertEquals("缺失词条", emptySet<String>(), want - found)
    }

    @Test
    fun `干净学习状态下新词全拼排第一`() {
        val e = FlatPinyinEngine(dict)
        val cases = mapOf(
            "wutiaowu" to "五条悟",
            "dazi" to "搭子",
            "jushenzhineng" to "具身智能",
            "neijuan" to "内卷",
            "bailan" to "摆烂",
            "pofang" to "破防",
            "damoxing" to "大模型",
            "xianyanbao" to "显眼包",
            "ziboshaokao" to "淄博烧烤",
            "gongxiangdanche" to "共享单车",
            "heishenhuawukong" to "黑神话悟空",
        )
        for ((q, want) in cases) {
            val top = e.candidates(q, 10).first()
            assertEquals("$q 首选", want, top.text)
        }
    }

    @Test
    fun `现代热词词频已抬起`() {
        val want = mapOf(
            "大模型" to 5_000,
            "搭子" to 5_000,
            "具身智能" to 5_000,
            "共享单车" to 5_000,
            "黑神话悟空" to 5_000,
            "五条悟" to 3_000,
            "淄博烧烤" to 5_000,
        )
        val best = HashMap<String, Int>()
        for (i in 0 until dict.size) {
            val w = dict.wordAt(i)
            val f = want[w] ?: continue
            if (f > (best[w] ?: 0)) best[w] = f
        }
        for ((w, min) in want) {
            assertTrue("$w 词频应 ≥ $min，实际 ${best[w]}", (best[w] ?: 0) >= min)
        }
    }
}