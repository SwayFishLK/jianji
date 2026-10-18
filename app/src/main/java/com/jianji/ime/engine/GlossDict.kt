package com.jianji.ime.engine

import java.io.InputStream

/**
 * 二进制释义表（assets/gloss-en.bin、gloss-ja.bin，生成器见 tools/dictgen/GlossGen.java）。
 *
 * 一次只载入一种学习语言（青简同款约定）。词按 UTF-8 字节序升序，查询即整块二分；
 * 显示串在查询时现拼：义项按「词性 + 空格 + 译文」、多条用「 · 」连接，
 * 日文译文把假名对到汉字段上（`開発する|かいはつする` → `v. 開発(かいはつ)する`）。
 */
class GlossDict private constructor(
    private val word: ByteArray,
    private val wordOff: IntArray,
    private val senseStart: IntArray,
    private val text: ByteArray,
    private val textOff: IntArray,
    private val textLen: ShortArray,
    private val readOff: IntArray,
    private val readLen: ShortArray,
    private val pos: ByteArray,
) {

    val size: Int get() = wordOff.size - 1

    /** 词 → 显示用译文；没有该词返回 null。结果做小缓存（网格面板与候选栏会重复问）。 */
    fun annotationFor(w: String): String? {
        cache[w]?.let { return it }
        val q = w.toByteArray(Charsets.UTF_8)
        var lo = 0
        var hi = size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = compare(word, wordOff[mid], wordOff[mid + 1], q)
            if (c < 0) lo = mid + 1 else if (c > 0) hi = mid - 1 else {
                val s = format(mid)
                if (s != null) {
                    if (cache.size >= 4096) cache.clear()
                    cache[w] = s
                }
                return s
            }
        }
        return null
    }

    private val cache = HashMap<String, String>()

    private fun format(i: Int): String? {
        val sb = StringBuilder()
        for (s in senseStart[i] until senseStart[i + 1]) {
            if (sb.isNotEmpty()) sb.append(" · ")
            val p = pos[s].toInt()
            if (p in 1 until POS_ABBREV.size) sb.append(POS_ABBREV[p]).append(' ')
            val t = stringAt(textOff[s], textLen[s].toInt())
            val rl = readLen[s].toInt()
            if (rl > 0) sb.append(furigana(t, stringAt(readOff[s], rl))) else sb.append(t)
        }
        return if (sb.isEmpty()) null else sb.toString()
    }

    private fun stringAt(off: Int, len: Int): String = String(text, off, len, Charsets.UTF_8)

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
        /** 词性缩写（与 GlossGen.java 的 POS_ABBREV 一一对应） */
        private val POS_ABBREV =
            arrayOf("", "n.", "v.", "adj.", "adv.", "pron.", "prep.", "conj.", "num.", "m.", "part.", "int.", "phr.")

        fun load(stream: InputStream): GlossDict {
            val b = stream.readBytes()
            require(
                b.size > 40 &&
                    b[0] == 'J'.code.toByte() && b[1] == 'J'.code.toByte() &&
                    b[2] == 'G'.code.toByte() && b[3] == '1'.code.toByte(),
            ) { "gloss.bin 格式不对" }
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
            val senseCount = int()
            val wordLen = int()
            val textLen = int()

            val word = b.copyOfRange(p, p + wordLen); p += wordLen
            val wordOff = IntArray(count + 1) { int() }
            val senseStart = IntArray(count + 1) { int() }
            val text = b.copyOfRange(p, p + textLen); p += textLen
            val textOff = IntArray(senseCount)
            val textLenArr = ShortArray(senseCount)
            val readOff = IntArray(senseCount)
            val readLenArr = ShortArray(senseCount)
            val pos = ByteArray(senseCount)
            for (i in 0 until senseCount) {
                pos[i] = b[p]; p += 1
                textOff[i] = ((b[p].toInt() and 0xff) shl 24) or
                    ((b[p + 1].toInt() and 0xff) shl 16) or
                    ((b[p + 2].toInt() and 0xff) shl 8) or
                    (b[p + 3].toInt() and 0xff); p += 4
                textLenArr[i] = (((b[p].toInt() and 0xff) shl 8) or (b[p + 1].toInt() and 0xff)).toShort(); p += 2
                readOff[i] = ((b[p].toInt() and 0xff) shl 24) or
                    ((b[p + 1].toInt() and 0xff) shl 16) or
                    ((b[p + 2].toInt() and 0xff) shl 8) or
                    (b[p + 3].toInt() and 0xff); p += 4
                readLenArr[i] = (((b[p].toInt() and 0xff) shl 8) or (b[p + 1].toInt() and 0xff)).toShort(); p += 2
            }
            return GlossDict(word, wordOff, senseStart, text, textOff, textLenArr, readOff, readLenArr, pos)
        }

        // —————— 振假名（对齐青简 qingjian_core::candidate::furigana） ——————

        /** `開発する` + `かいはつする` → `開発(かいはつ)する`；对不上时整体注在后面。 */
        private fun furigana(text: String, reading: String): String {
            val runs = splitRuns(text)
            if (!runs.any { it.second }) return text
            val readingChars = reading.map { toHiragana(it) }
            val out = arrayOfNulls<String>(runs.size)
            return if (align(runs, 0, readingChars, 0, out)) {
                val sb = StringBuilder()
                runs.forEachIndexed { i, (run, _) ->
                    sb.append(run)
                    out[i]?.let { sb.append('(').append(it).append(')') }
                }
                sb.toString()
            } else {
                "$text($reading)"
            }
        }

        /** 连续汉字一段、连续非汉字一段：(段, 是否汉字) */
        private fun splitRuns(text: String): List<Pair<String, Boolean>> {
            val runs = ArrayList<Pair<String, Boolean>>()
            for (c in text) {
                val kanji = isKanji(c)
                val last = runs.lastOrNull()
                if (last != null && last.second == kanji) {
                    runs[runs.size - 1] = last.first + c to kanji
                } else {
                    runs.add(c.toString() to kanji)
                }
            }
            return runs
        }

        /**
         * 回溯对齐：非汉字段必须与读音对应位置逐字相同（片假名先统一成平假名）；
         * 汉字段取到下一个锚点之间的假名、至少一个；最后一段汉字吃掉剩余全部。
         */
        private fun align(
            runs: List<Pair<String, Boolean>>,
            index: Int,
            reading: List<Char>,
            position: Int,
            out: Array<String?>,
        ): Boolean {
            if (index == runs.size) return position == reading.size
            val (run, kanji) = runs[index]
            if (!kanji) {
                val chars = run.map { toHiragana(it) }
                if (position + chars.size <= reading.size &&
                    reading.subList(position, position + chars.size) == chars
                ) {
                    out[index] = null
                    if (align(runs, index + 1, reading, position + chars.size, out)) return true
                }
                return false
            }
            if (index == runs.size - 1) {
                if (position < reading.size) {
                    out[index] = reading.subList(position, reading.size).joinToString("")
                    return true
                }
                return false
            }
            for (end in position + 1..reading.size) {
                out[index] = reading.subList(position, end).joinToString("")
                if (align(runs, index + 1, reading, end, out)) return true
            }
            out[index] = null
            return false
        }

        /** 汉字（含 々 〆），与青简 is_kanji 一致 */
        private fun isKanji(c: Char): Boolean =
            c in '\u4e00'..'\u9fff' || c in '\u3400'..'\u4dbf' || c == '々' || c == '〆'

        /** 片假名统一成平假名（U+30A1–U+30F6 减 0x60），长音符等原样 */
        private fun toHiragana(c: Char): Char =
            if (c in '\u30a1'..'\u30f6') (c.code - 0x60).toChar() else c
    }
}
