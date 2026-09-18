package com.example.myapplication.model

enum class DonationType(val defaultVolumeMl: Int, val minIntervalDays: Int) {
    WHOLE_BLOOD(430, 56),
    PLASMA(45, 14),
    PLATELET(90, 14)
}
