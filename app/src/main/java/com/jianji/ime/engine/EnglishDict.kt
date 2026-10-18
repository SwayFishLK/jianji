package com.jianji.ime.engine

import java.io.InputStream
import java.util.Locale

/**
 * 二进制英文词表（assets/english.bin，生成器见 tools/dictgen/EnglishGen.java）。
 *
 * 全部小写词按 UTF-8 字节序升序：查询 = 整块二分定位前缀起点 + 向后扫描，
 * 命中的词按词频（wordfreq Zipf ×1000）降序返回前 limit 个（英文模式候选联想）。
 */
class EnglishDict private constructor(
    private val word: ByteArray,
    private val wordOff: IntArray,
    private val freq: IntArray,
) {

    val size: Int get() = wordOff.size - 1

    /** 前缀联想：词频降序返回最多 limit 个词；没有命中返回空表 */
    fun suggest(prefix: String, limit: Int): List<String> {
        if (prefix.isEmpty() || limit <= 0 || size == 0) return emptyList()
        val q = prefix.lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8)

        var lo = 0
        var hi = size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (compare(word, wordOff[mid], wordOff[mid + 1], q) < 0) lo = mid + 1 else hi = mid
        }

        var packed: LongArray? = null
        var n = 0
        var i = lo
        while (i < size) {
            val a = wordOff[i]
            val b = wordOff[i + 1]
            if (b - a < q.size || compare(word, a, a + q.size, q) != 0) break
            if (packed == null) packed = LongArray(64)
            else if (n == packed.size) packed = packed.copyOf(n * 2)
            packed[n++] = (freq[i].toLong() shl 32) or i.toLong()
            i++
        }
        if (packed == null) return emptyList()

        java.util.Arrays.sort(packed, 0, n)
        val out = ArrayList<String>(minOf(limit, n))
        var k = n - 1
        while (k >= 0 && out.size < limit) {
            out.add(wordAt((packed[k] and 0xffffffffL).toInt()))
            k--
        }
        return out
    }

    private fun wordAt(i: Int): String =
        String(word, wordOff[i], wordOff[i + 1] - wordOff[i], Charsets.UTF_8)

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
        fun load(stream: InputStream): EnglishDict {
            val b = stream.readBytes()
            require(
                b.size > 12 &&
                    b[0] == 'J'.code.toByte() && b[1] == 'J'.code.toByte() &&
                    b[2] == 'E'.code.toByte() && b[3] == '1'.code.toByte(),
            ) { "english.bin 格式不对" }
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
            val blobLen = int()
            val word = b.copyOfRange(p, p + blobLen); p += blobLen
            val wordOff = IntArray(count + 1) { int() }
            val freq = IntArray(count) { int() }
            return EnglishDict(word, wordOff, freq)
        }
    }
}
