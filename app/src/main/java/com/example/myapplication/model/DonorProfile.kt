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
    val donatedAge60To64: Boolean = false,
    /** 자주 헌혈하는 지역, as a short province name ("서울"): the 헌혈의 집 tab lists its centers first. */
    val preferredProvince: String? = null
)

fun DonorProfile.currentAge(today: LocalDate = LocalDate.now()): Int? =
    birthDate?.let { Period.between(it, today).years }
