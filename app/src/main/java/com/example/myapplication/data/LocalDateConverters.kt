package com.example.myapplication.data

import androidx.room.TypeConverter
import java.time.LocalDate

/**
 * Stores dates as ISO-8601 text, matching how [DonationStateStore] already persists them. ISO
 * dates sort lexicographically in the same order they sort chronologically, so `ORDER BY
 * intakeDate` in SQL stays correct without a separate numeric column.
 */
class LocalDateConverters {
    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? = date?.toString()
}
