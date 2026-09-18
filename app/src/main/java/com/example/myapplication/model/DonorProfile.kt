package com.example.myapplication.model

import java.time.LocalDate
import java.time.Period

enum class Sex { MALE, FEMALE }

data class DonorProfile(
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val sex: Sex? = null,
    val name: String? = null,
    val birthDate: LocalDate? = null,
    val donatedAge60To64: Boolean = false
)

fun DonorProfile.currentAge(today: LocalDate = LocalDate.now()): Int? =
    birthDate?.let { Period.between(it, today).years }
