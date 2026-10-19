package com.example.myapplication.model

/**
 * One 헌혈의 집 from 대한적십자사's open data (`blood_centers.json`).
 *
 * @param bloodBank The 혈액원 it belongs to, without the suffix ("서울중앙").
 * @param type The funding category as published: 국고, 자체, 원내, 국고대학 or 자체대학.
 * @param machines Its 성분헌혈 machines, or null when the machines list doesn't cover it.
 */
data class BloodCenter(
    val bloodBank: String,
    val name: String,
    val type: String,
    val address: String,
    val phone: String,
    val machines: CenterMachines? = null
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
 * The 성분헌혈 machines at one 헌혈의 집: each model with how many the center has, in the order the
 * machines list gives them. Only models it has are listed, so an empty [platelet] means 혈소판 can't
 * be given there.
 */
data class CenterMachines(
    val plasma: Map<String, Int>,
    val platelet: Map<String, Int>
)
