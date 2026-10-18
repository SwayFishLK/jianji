package com.jianji.ime.engine

/**
 * 一个候选词。
 *
 * @param text 词面（M3 起带译文）
 * @param score 排序分（越高越靠前）
 * @param consumed 吃掉的拼音字母数：小于输入长度时上屏后剩余拼音继续组句
 *   （部分上屏，如 nihaom 的「你好」= 5，上屏后剩 m）；≥ 输入长度表示整段吃掉。
 */
data class Candidate(val text: String, val score: Float, val consumed: Int = Int.MAX_VALUE)

/**
 * 拼音引擎接口：纯 Kotlin 实现，接口化以便将来整体替换
 * （如 Rust Core / librime 等）。
 */
interface PinyinEngine {

    /**
     * 查询候选，按分数降序排列。
     * @param query 已输入的小写字母串（实现自行忽略无意义字符）
     * @param limit 返回上限
     */
    fun candidates(query: String, limit: Int = 100): List<Candidate>
}
