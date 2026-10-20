package com.jianji.ime.engine

import java.io.DataInputStream
import java.io.InputStream

/**
 * 中文词 → 一个相关 emoji（assets/emoji-map.bin，生成器见 tools/dictgen/EmojiMapGen.java）。
 *
 * 数据来源：青简 emoji-zh.tsv（Unicode CLDR zh annotations，Unicode License v3）；
 * 每个词只保留一个 emoji（常用面板里更靠前的优先，另有少量人工覆盖）。
 * 运行时候选栏只在「首选词整段吃满输入」时查一次，拿不到就什么都不出。
 *
 * 二进制布局（大端）：
 *   char[4] "JJX1" | int 词条数 | 每条：int 词长 + 词（UTF-8）| int emoji 长 + emoji（UTF-8）
 */
class EmojiMap private constructor(private val map: HashMap<String, String>) {

    val size: Int get() = map.size

    fun emojiFor(word: String): String? = map[word]

    companion object {
        fun load(input: InputStream): EmojiMap =
            DataInputStream(input.buffered(1 shl 16)).use { ins ->
                val magic = ByteArray(4)
                ins.readFully(magic)
                require(
                    magic[0] == 'J'.code.toByte() && magic[1] == 'J'.code.toByte() &&
                        magic[2] == 'X'.code.toByte() && magic[3] == '1'.code.toByte(),
                ) { "bad emoji-map.bin magic" }
                val n = ins.readInt()
                val map = HashMap<String, String>(n * 2)
                repeat(n) {
                    val wl = ins.readInt()
                    val w = ByteArray(wl).also(ins::readFully).toString(Charsets.UTF_8)
                    val el = ins.readInt()
                    val e = ByteArray(el).also(ins::readFully).toString(Charsets.UTF_8)
                    if (w.isNotEmpty() && e.isNotEmpty()) map[w] = e
                }
                EmojiMap(map)
            }
    }
}