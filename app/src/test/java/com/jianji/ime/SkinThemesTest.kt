package com.jianji.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinThemesTest {

    @Test
    fun hasFiveUniqueThemes() {
        val ids = SkinThemes.all.map { it.id }
        assertEquals(5, ids.size)
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(SkinThemes.all.any { it.id == SkinThemes.DEFAULT_ID })
    }

    @Test
    fun fourGlassAndOneClassic() {
        assertEquals(4, SkinThemes.all.count { !it.classic })
        assertEquals(1, SkinThemes.all.count { it.classic })
        assertTrue(SkinThemes.all.none { it.light })
        assertEquals("ink", SkinThemes.all.single { it.classic }.id)
    }

    @Test
    fun classicKeyColorsAreDarkened() {
        val ink = SkinThemes.byId("ink")
        assertTrue(ink.keyBg < 0xFF2A2E37.toInt())
        assertTrue(ink.keyPressed < 0xFF3A4150.toInt())
        assertTrue(ink.keyFnBg < 0xFF232730.toInt())
    }

    @Test
    fun unknownThemeFallsBackToDefault() {
        assertSame(SkinThemes.byId(SkinThemes.DEFAULT_ID), SkinThemes.byId(null))
        assertSame(SkinThemes.byId(SkinThemes.DEFAULT_ID), SkinThemes.byId("legacy-glass"))
    }
}