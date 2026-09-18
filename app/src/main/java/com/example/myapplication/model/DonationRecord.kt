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
     * diagnostic-testing draw for whole blood donations. Null when the record was entered
     * manually (without a scanned certificate), in which case [volumeMl] falls back to the
     * age-based default.
     */
    val donatedVolumeMl: Int? = null
)
