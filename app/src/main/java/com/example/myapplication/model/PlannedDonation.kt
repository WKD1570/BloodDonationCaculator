package com.example.myapplication.model

import java.time.LocalDate

/** A donation planned for a future day on the 헌혈 계획 page - at most one a day. */
data class PlannedDonation(val id: Long, val type: DonationType, val date: LocalDate)
