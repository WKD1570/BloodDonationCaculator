package com.example.myapplication.model

/** The two kinds of place the 헌혈 장소 tab lists, each from its own source. */
enum class DonationPlaceKind(val label: String) {
    /** 대한적십자사's 헌혈의 집 (`blood_centers.json`). */
    CENTER("헌혈의 집"),

    /** 한마음혈액원's 헌혈카페 (`blood_cafes.json`). */
    CAFE("헌혈카페")
}

/**
 * One 헌혈의 집 from 대한적십자사's open data (`blood_centers.json`), or one 헌혈카페 from
 * 한마음혈액원's site (`blood_cafes.json`) - both come in the same shape.
 *
 * @param bloodBank The 혈액원 it belongs to, without the suffix ("서울중앙", "한마음").
 * @param type The funding category as published (국고, 자체, 원내, 국고대학 or 자체대학), or 헌혈카페.
 * @param machines Its 성분헌혈 machines, or null when the machines list doesn't cover it.
 * @param hours Opening hours as published, one line per rule. Only 헌혈카페 publish them.
 * @param parking Parking as published. Only 헌혈카페 publish it.
 */
data class BloodCenter(
    val bloodBank: String,
    val name: String,
    val type: String,
    val address: String,
    val phone: String,
    val machines: CenterMachines? = null,
    val hours: String? = null,
    val parking: String? = null
) {
    /** Names repeat across 혈액원 only in principle, but the pair is what the data keys on. */
    val id: String get() = "$bloodBank/$name"

    val bloodBankLabel: String get() = if (bloodBank.isBlank()) "" else "${bloodBank}혈액원"

    /**
     * What a donor might want to know from [type]. 국고/자체 is about who funds the center and
     * means nothing to a visitor, so only "inside the 혈액원" and "on a campus" are surfaced -
     * the latter because campus centers tend to follow the academic calendar.
     */
    val placeLabel: String?
        get() = when {
            type == "원내" -> "혈액원 내"
            type.endsWith("대학") -> "대학 내"
            else -> null
        }
}

/**
 * The 성분헌혈 machines at one place: each model with how many it has, in the order the source
 * gives them. Only models it has are listed, so an empty [platelet] means 혈소판 can't be given there.
 *
 * @param plasma Null when the source doesn't say: 헌혈카페 publish only their 혈소판 machines, so
 * their 혈장 machines are unknown rather than none.
 */
data class CenterMachines(
    val plasma: Map<String, Int>?,
    val platelet: Map<String, Int>
)
