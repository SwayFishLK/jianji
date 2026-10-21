package com.jianji.ime.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** @ 后候选用的邮箱后缀表：本地静态、去重、后缀格式。 */
class EmailSuffixesTest {

    @Test
    fun commonSuffixesAreUsable() {
        assertTrue(EmailSuffixes.common.size >= 10)
        assertEquals(EmailSuffixes.common.size, EmailSuffixes.common.distinct().size)
        assertTrue(EmailSuffixes.common.all { it.contains('.') && !it.startsWith('@') })
        assertTrue(EmailSuffixes.common.containsAll(listOf("qq.com", "163.com", "gmail.com", "outlook.com")))
    }

    @Test
    fun symbolVariantAddsAt() {
        assertEquals(EmailSuffixes.common.map { "@$it" }, EmailSuffixes.symbols)
        assertTrue(EmailSuffixes.symbols.all { it.startsWith('@') && it.count { c -> c == '@' } == 1 })
    }
}