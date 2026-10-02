package com.example.myapplication.model

import java.time.LocalDate

data class DonationRecord(
    val id: Long,
    val type: DonationType,
    val date: LocalDate,
    val birthDate: LocalDate? = null,
    val certNumber: String? = null,
    val name: String? = null,
    val sex: Sex? = null,
    val centerName: String? = null,
    /**
     * Actual drawn volume (mL) as stated on the certificate, already including the +30mL
     * diagnostic-testing draw for whole blood donations. For whole blood it only records which
     * amount was given (320 or 400mL): every whole blood donation counts as 430mL toward the annual
     * cap. Null when unknown, in which case [volumeMl] falls back to the type's standard volume.
     */
    val donatedVolumeMl: Int? = null
)
