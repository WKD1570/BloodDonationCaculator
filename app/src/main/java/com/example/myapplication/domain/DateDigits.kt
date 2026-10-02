package com.example.myapplication.domain

import java.time.LocalDate

// Dates are typed as plain digits (YYYYMMDD) and only shown with slashes, so a keystroke never lands
// on the wrong side of a separator.

/** The date 8 typed digits spell out, or null while incomplete or when no such date exists (20260231). */
fun parseDateDigits(digits: String): LocalDate? {
    if (digits.length != 8 || !digits.all { it.isDigit() }) return null
    return runCatching {
        LocalDate.of(digits.take(4).toInt(), digits.substring(4, 6).toInt(), digits.substring(6).toInt())
    }.getOrNull()
}

/** How typed digits read in a YYYY/MM/DD field so far: "202609" -> "2026/09". */
fun formatDateDigits(digits: String): String = buildString {
    append(digits.take(4))
    if (digits.length > 4) append('/').append(digits.substring(4, minOf(6, digits.length)))
    if (digits.length > 6) append('/').append(digits.substring(6, minOf(8, digits.length)))
}

/** The digits a YYYY/MM/DD field holds for this date: 2026-09-03 -> "20260903". */
fun LocalDate.toDateDigits(): String =
    year.toString().padStart(4, '0') + monthValue.toString().padStart(2, '0') + dayOfMonth.toString().padStart(2, '0')
