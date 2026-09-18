package com.example.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `SYSTEM follows the device setting in both directions`() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemInDarkTheme = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemInDarkTheme = false))
    }

    @Test
    fun `DARK stays dark even when the device is light`() {
        assertTrue(ThemeMode.DARK.isDark(systemInDarkTheme = false))
    }

    @Test
    fun `LIGHT stays light even when the device is dark`() {
        assertFalse(ThemeMode.LIGHT.isDark(systemInDarkTheme = true))
    }

    @Test
    fun `toggling commits to an explicit mode rather than returning to SYSTEM`() {
        assertEquals(ThemeMode.DARK, ThemeMode.forDarkSelection(dark = true))
        assertEquals(ThemeMode.LIGHT, ThemeMode.forDarkSelection(dark = false))
    }
}
