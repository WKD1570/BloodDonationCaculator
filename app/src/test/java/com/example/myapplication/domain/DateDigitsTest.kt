package com.example.myapplication.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DateDigitsTest {

    @Test
    fun `eight typed digits spell out the date`() {
        assertEquals(LocalDate.of(2026, 9, 30), parseDateDigits("20260930"))
    }

    @Test
    fun `a date that doesn't exist parses to null`() {
        assertNull(parseDateDigits("20260231"))
    }

    @Test
    fun `an incomplete date parses to null`() {
        assertNull(parseDateDigits("2026093"))
    }

    @Test
    fun `typed digits read as YYYY slash MM slash DD as they're typed`() {
        assertEquals("2026", formatDateDigits("2026"))
        assertEquals("2026/0", formatDateDigits("20260"))
        assertEquals("2026/09", formatDateDigits("202609"))
        assertEquals("2026/09/3", formatDateDigits("2026093"))
        assertEquals("2026/09/30", formatDateDigits("20260930"))
    }

    @Test
    fun `a date turns back into its zero-padded digits`() {
        assertEquals("20260903", LocalDate.of(2026, 9, 3).toDateDigits())
    }
}
