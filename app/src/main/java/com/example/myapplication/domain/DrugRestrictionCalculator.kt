package com.example.myapplication.domain

import com.example.myapplication.model.RestrictedDrug
import com.example.myapplication.model.isPermanentlyRestricted
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Source: 대한적십자사 헌혈 제한 의약품 목록 (사용자 제공). Update this list if the official
 * guidance changes.
 */
val PROHIBITED_DRUGS: List<RestrictedDrug> = listOf(
    RestrictedDrug(
        category = "기타 약물",
        ingredientName = "아스피린",
        representativeBrands = "아스피린",
        restrictionDays = 3,
        restrictionText = "복용 후 3일간 헌혈 금지 (감기 치료 목적 경구 복용 포함)"
    ),
    RestrictedDrug(
        category = "기타 약물 및 주사제",
        ingredientName = "항생제/스테로이드/보톡스/클로피도그렐",
        representativeBrands = "항생제(주사), 스테로이드제(주사), 보톡스(주사), 클로피도그렐 등",
        restrictionDays = 7,
        restrictionText = "투여 후 1주간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "기타 약물",
        ingredientName = "티클로피딘",
        representativeBrands = "티클로피딘",
        restrictionDays = 14,
        restrictionText = "복용 후 2주간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "전립선비대증 치료제",
        ingredientName = "피나스테라이드",
        representativeBrands = "프로스카 등",
        restrictionDays = 28,
        restrictionText = "복용 후 4주간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "탈모증 치료제",
        ingredientName = "피나스테라이드",
        representativeBrands = "프로페시아 등",
        restrictionDays = 28,
        restrictionText = "복용 후 4주간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "여드름 치료제",
        ingredientName = "이소트레티노인",
        representativeBrands = "로아큐탄, 아크날, 뉴티논 등",
        restrictionDays = 28,
        restrictionText = "복용 후 4주간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "손 습진치료제",
        ingredientName = "알리트레티노인",
        representativeBrands = "알리톡",
        restrictionDays = 30,
        restrictionText = "복용 후 1개월간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "항악성종양/나성결절홍반 치료제",
        ingredientName = "탈리도마이드",
        representativeBrands = "세엘진탈리도마이드, 탈로마 등",
        restrictionDays = 30,
        restrictionText = "복용 후 1개월간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "전립선비대증 치료제",
        ingredientName = "두타스테라이드",
        representativeBrands = "아보다트, 아보스타 등",
        restrictionDays = 180,
        restrictionText = "복용 후 6개월간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "기타 주사제",
        ingredientName = "태반주사제/혈액응고인자",
        representativeBrands = "태반주사제, 혈액응고인자",
        restrictionDays = 365,
        restrictionText = "투여 후 1년간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "항악성종양 치료제",
        ingredientName = "비스모데깁",
        representativeBrands = "에리벳지",
        restrictionDays = 730,
        restrictionText = "복용 후 24개월간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "건선 치료제",
        ingredientName = "아시트레틴",
        representativeBrands = "네오티가손, 소리아탄",
        restrictionDays = 1095,
        restrictionText = "복용 후 3년간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "건선 치료제",
        ingredientName = "에트레티네이트",
        representativeBrands = "티가손, 타가손, 테지손",
        restrictionDays = -1,
        restrictionText = "복용 시 영구적으로 헌혈 금지"
    ),
    RestrictedDrug(
        category = "기타 약물 및 주사제",
        ingredientName = "사람뇌하수체 유래 성장호르몬 등",
        representativeBrands = "사람뇌하수체 유래 성장호르몬, 소에서 추출한 인슐린, 면역억제제 등",
        restrictionDays = -1,
        restrictionText = "투여 시 영구적으로 헌혈 금지"
    )
)

/**
 * Adds [amount] of [unit] to [date] via [LocalDate.plus], which resolves calendar irregularities
 * itself - e.g. adding months clamps to the shorter target month (Jan 31 + 1 month -> Feb 28, or
 * Feb 29 in a leap year) and adding days always lands on the correct calendar date across leap
 * years and month-length changes.
 */
fun addRestrictionPeriod(date: LocalDate, amount: Long, unit: ChronoUnit): LocalDate =
    date.plus(amount, unit)

/** Returns null when [drug] is a permanent restriction - there is no eligible date. */
fun nextEligibleDonationDate(drug: RestrictedDrug, medicationDate: LocalDate): LocalDate? =
    if (drug.isPermanentlyRestricted) null
    else addRestrictionPeriod(medicationDate, drug.restrictionDays.toLong(), ChronoUnit.DAYS)

/** Case-insensitive match against ingredient name or brand names; blank query returns nothing. */
fun searchDrugs(query: String, drugs: List<RestrictedDrug> = PROHIBITED_DRUGS): List<RestrictedDrug> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()
    return drugs.filter {
        it.ingredientName.contains(trimmed, ignoreCase = true) ||
            it.representativeBrands.contains(trimmed, ignoreCase = true)
    }
}
