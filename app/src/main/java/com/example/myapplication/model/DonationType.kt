package com.example.myapplication.model

import java.time.Period

/**
 * @param minInterval How long a donation of this type blocks the next donation of any type.
 * @param isOther Recorded in the separate 기타 card instead of 헌혈 기록: donations the app tracks for
 * how they restrict the regular three, but doesn't plan for.
 */
enum class DonationType(val defaultVolumeMl: Int, val minInterval: Period, val isOther: Boolean = false) {
    WHOLE_BLOOD(430, Period.ofDays(56)),
    PLASMA(45, Period.ofDays(14)),
    PLATELET(90, Period.ofDays(14)),
    WHITE_BLOOD_CELL(90, Period.ofDays(14), isOther = true),

    /** The volume drawn isn't known, so the minimum of 30mL is counted. */
    STEM_CELL(30, Period.ofMonths(6), isOther = true)
}

/** 전혈/혈장/혈소판: recorded in 헌혈 기록 and planned from 현황. */
val REGULAR_DONATION_TYPES: List<DonationType> = DonationType.entries.filterNot { it.isOther }

/** 백혈구성분헌혈/조혈모세포 기증: recorded in the 기타 card. */
val OTHER_DONATION_TYPES: List<DonationType> = DonationType.entries.filter { it.isOther }
