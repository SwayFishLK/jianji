package com.jianji.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinThemesTest {

    @Test
    fun hasEightUniqueThemes() {
        val ids = SkinThemes.all.map { it.id }
        assertEquals(8, ids.size)
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(SkinThemes.all.any { it.id == SkinThemes.DEFAULT_ID })
    }

    @Test
    fun fiveDarkAndThreeLight() {
        assertEquals(5, SkinThemes.all.count { !it.light })
        assertEquals(3, SkinThemes.all.count { it.light })
    }

    @Test
    fun unknownThemeFallsBackToDefault() {
        assertSame(SkinThemes.byId(SkinThemes.DEFAULT_ID), SkinThemes.byId(null))
        assertSame(SkinThemes.byId(SkinThemes.DEFAULT_ID), SkinThemes.byId("legacy-glass"))
    }
}