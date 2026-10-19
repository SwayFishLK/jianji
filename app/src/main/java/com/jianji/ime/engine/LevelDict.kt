package com.jianji.ime.engine

import java.io.InputStream

/**
 * 生词等级表（assets/levels-en.bin 英语 CEFR / levels-ja.bin 日语 JLPT，
 * 生成器见 tools/dictgen/LevelGen.java）。
 *
 * 词 → 等级下标（从易到难：A1..C2 / N5..N1）；词按 UTF-8 字节序升序，查询即整块二分。
 * 候选右上角的生词角标（等级 ≥ [THRESHOLD] 才显示）从这里取。
 */
class LevelDict private constructor(
    private val names: Array<String>,
    private val word: ByteArray,
    private val wordOff: IntArray,
    private val level: ByteArray,
) {

    val size: Int get() = wordOff.size - 1

    /** 词 → 等级下标；没收录 → -1 */
    fun levelOf(w: String): Int {
        val q = w.toByteArray(Charsets.UTF_8)
        var lo = 0
        var hi = size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = compare(word, wordOff[mid], wordOff[mid + 1], q)
            if (c < 0) lo = mid + 1 else if (c > 0) hi = mid - 1 else return level[mid].toInt()
        }
        return -1
    }

    /** 生词角标文本（英语 B2 / 日语 N2 及以上）；不是生词或没收录 → null */
    fun markFor(w: String): String? {
        val i = levelOf(w)
        return if (i >= THRESHOLD && i < names.size) names[i] else null
    }

    /** blob[off, end) 与字节串 q 的无符号比较（字典序） */
    private fun compare(blob: ByteArray, off: Int, end: Int, q: ByteArray): Int {
        val len = end - off
        val m = minOf(len, q.size)
        for (k in 0 until m) {
            val d = (blob[off + k].toInt() and 0xff) - (q[k].toInt() and 0xff)
            if (d != 0) return d
        }
        return len - q.size
    }

    companion object {
        /** 生词门槛：第 3 档起 = 英语 B2 / 日语 N2 */
        const val THRESHOLD = 3

        fun load(stream: InputStream): LevelDict {
            val b = stream.readBytes()
            require(
                b.size > 16 &&
                    b[0] == 'J'.code.toByte() && b[1] == 'J'.code.toByte() &&
                    b[2] == 'L'.code.toByte() && b[3] == '1'.code.toByte(),
            ) { "levels.bin 格式不对" }
            var p = 4
            fun int(): Int {
                val v = ((b[p].toInt() and 0xff) shl 24) or
                    ((b[p + 1].toInt() and 0xff) shl 16) or
                    ((b[p + 2].toInt() and 0xff) shl 8) or
                    (b[p + 3].toInt() and 0xff)
                p += 4
                return v
            }

            val levelCount = int()
            val count = int()
            val wordLen = int()
            val names = Array(levelCount) {
                val l = b[p].toInt() and 0xff
                p += 1
                val s = String(b, p, l, Charsets.UTF_8)
                p += l
                s
            }
            val word = b.copyOfRange(p, p + wordLen); p += wordLen
            val wordOff = IntArray(count + 1) { int() }
            val level = b.copyOfRange(p, p + count)
            return LevelDict(names, word, wordOff, level)
        }
    }
}
