package com.jianji.ime.engine

import java.io.DataInputStream
import java.io.InputStream

/**
 * 表情表（M4）：青简 catalog 表情行 → emoji.bin（"JJM1"）。
 * 分类（常用 / 颜文字 / 笑脸 / …），面板网格直接使用。
 * 纯 JVM，可单元测试。
 */
class EmojiDict private constructor(val cats: List<Cat>) {

    data class Cat(val name: String, val items: List<String>)

    val size: Int = cats.sumOf { it.items.size }

    companion object {
        fun load(input: InputStream): EmojiDict = DataInputStream(input.buffered(1 shl 16)).use { ins ->
            val magic = ByteArray(4)
            ins.readFully(magic)
            require(magic[0] == 'J'.code.toByte() && magic[1] == 'J'.code.toByte() &&
                magic[2] == 'M'.code.toByte() && magic[3] == '1'.code.toByte()) { "bad emoji.bin magic" }
            val n = ins.readInt()
            val cats = ArrayList<Cat>(n)
            repeat(n) {
                val nameLen = ins.readInt()
                val name = ByteArray(nameLen).also(ins::readFully).toString(Charsets.UTF_8)
                val count = ins.readInt()
                val items = ArrayList<String>(count)
                repeat(count) {
                    val len = ins.readInt()
                    items.add(ByteArray(len).also(ins::readFully).toString(Charsets.UTF_8))
                }
                cats.add(Cat(name, items))
            }
            EmojiDict(cats)
        }
    }
}
