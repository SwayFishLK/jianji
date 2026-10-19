package com.jianji.ime.engine

import java.io.InputStream

/**
 * 二进制词库（assets/dict.bin，生成器见 tools/dictgen/DictGen.java）。
 *
 * 数据全部以原始数组常驻内存：查询路径只做整数运算与区间二分/扫描，
 * 不为词条建对象索引；只在产生候选时切出字符串。
 *
 * 两大有序视图：
 *  - 主表（按全拼连写升序，同拼音按词频降序）：全拼前缀查询
 *  - 简拼索引（按首字母串升序，同键按词频降序）：简拼查询
 */
class JianDict private constructor(
    private val pinyin: ByteArray,
    private val pinyinOff: IntArray,
    private val word: ByteArray,
    private val wordOff: IntArray,
    private val freq: IntArray,
    private val initials: ByteArray,
    private val initialsOff: IntArray,
    private val initialsIdx: IntArray,
    val syllableSet: HashSet<String>,
) {

    val size: Int get() = freq.size

    /** 最长音节长度（查询切分用） */
    val maxSyllableLen: Int = syllableSet.maxOfOrNull { it.length } ?: 6

    fun wordAt(i: Int): String = String(word, wordOff[i], wordOff[i + 1] - wordOff[i], Charsets.UTF_8)

    fun pinyinAt(i: Int): String = String(pinyin, pinyinOff[i], pinyinOff[i + 1] - pinyinOff[i], Charsets.US_ASCII)

    fun freqAt(i: Int): Int = freq[i]

    /** 词条 i 全拼连写的长度（字符数） */
    fun pinyinLenAt(i: Int): Int = pinyinOff[i + 1] - pinyinOff[i]

    // —————— 主表（全拼升序） ——————

    /** 第一个 全拼 >= q 的主表下标 */
    fun lowerBoundFull(q: String): Int = lowerBound(pinyin, pinyinOff, q)

    fun startsWithFull(i: Int, q: String): Boolean = startsWith(pinyin, pinyinOff[i], pinyinOff[i + 1], q)

    // —————— 简拼索引 ——————

    /** 第一个 简拼 >= q 的简拼序下标 */
    fun lowerBoundInitials(q: String): Int = lowerBound(initials, initialsOff, q)

    fun startsWithInitials(j: Int, q: String): Boolean = startsWith(initials, initialsOff[j], initialsOff[j + 1], q)

    fun compareInitialsAt(j: Int, q: String): Int = compare(initials, initialsOff[j], initialsOff[j + 1], q)

    /** 简拼序下标 j → 主表词条下标 */
    fun entryOfInitials(j: Int): Int = initialsIdx[j]

    // —————— 连写拼音 → 音节切分（自造短语用：wutiaowu → wu|tiao|wu） ——————

    /**
     * 把一段连写拼音按「恰好 syllables 个音节」切开（自造短语命中时按音节逐段比）。
     * 长音节优先、回溯找可行切法；切不开（非法音节 / 音节数对不上）返回 null，
     * 调用方退回「只按整段拼音匹配」。syllables 用短语字数（汉字一字一音节）。
     */
    fun syllablesOf(pinyin: String, syllables: Int): List<String>? {
        val n = pinyin.length
        if (syllables <= 0 || n < syllables) return null
        val out = ArrayList<String>(syllables)
        fun dfs(pos: Int, need: Int): Boolean {
            if (need == 0) return pos == n
            val maxLen = minOf(maxSyllableLen, n - pos - (need - 1))
            for (l in maxLen downTo 1) {
                val syl = pinyin.substring(pos, pos + l)
                if (syl !in syllableSet) continue
                out.add(syl)
                if (dfs(pos + l, need - 1)) return true
                out.removeAt(out.size - 1)
            }
            return false
        }
        return if (dfs(0, syllables)) out else null
    }

    // —————— 内部 ——————

    private fun compare(blob: ByteArray, off: Int, end: Int, q: String): Int {
        val len = end - off
        val m = minOf(len, q.length)
        for (k in 0 until m) {
            val d = (blob[off + k].toInt() and 0xff) - q[k].code
            if (d != 0) return d
        }
        return len - q.length
    }

    private fun startsWith(blob: ByteArray, off: Int, end: Int, q: String): Boolean {
        if (end - off < q.length) return false
        for (k in q.indices) {
            if ((blob[off + k].toInt() and 0xff) != q[k].code) return false
        }
        return true
    }

    private fun lowerBound(blob: ByteArray, offs: IntArray, q: String): Int {
        var lo = 0
        var hi = offs.size - 2
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (compare(blob, offs[mid], offs[mid + 1], q) < 0) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        fun load(stream: InputStream): JianDict {
            val b = stream.readBytes()
            require(
                b.size > 40 &&
                    b[0] == 'J'.code.toByte() && b[1] == 'J'.code.toByte() &&
                    b[2] == 'D'.code.toByte() && b[3] == '1'.code.toByte(),
            ) { "dict.bin 格式不对" }
            var p = 4
            fun int(): Int {
                val v = ((b[p].toInt() and 0xff) shl 24) or
                    ((b[p + 1].toInt() and 0xff) shl 16) or
                    ((b[p + 2].toInt() and 0xff) shl 8) or
                    (b[p + 3].toInt() and 0xff)
                p += 4
                return v
            }

            val count = int()
            val syllableCount = int()
            val pinyinLen = int()
            val wordLen = int()
            val initialsLen = int()
            val syllablesLen = int()

            val pinyin = b.copyOfRange(p, p + pinyinLen); p += pinyinLen
            val pinyinOff = IntArray(count + 1) { int() }
            val word = b.copyOfRange(p, p + wordLen); p += wordLen
            val wordOff = IntArray(count + 1) { int() }
            val freq = IntArray(count) { int() }
            val initials = b.copyOfRange(p, p + initialsLen); p += initialsLen
            val initialsOff = IntArray(count + 1) { int() }
            val initialsIdx = IntArray(count) { int() }
            val syllablesBlob = b.copyOfRange(p, p + syllablesLen); p += syllablesLen
            val syllablesOff = IntArray(syllableCount + 1) { int() }

            val syllableSet = HashSet<String>(syllableCount * 2)
            for (i in 0 until syllableCount) {
                syllableSet.add(
                    String(syllablesBlob, syllablesOff[i], syllablesOff[i + 1] - syllablesOff[i], Charsets.US_ASCII),
                )
            }
            return JianDict(pinyin, pinyinOff, word, wordOff, freq, initials, initialsOff, initialsIdx, syllableSet)
        }
    }
}
