package com.example.myapplication.domain

import com.example.myapplication.model.RestrictedDrug
import com.example.myapplication.model.isPermanentlyRestricted
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Sources: 대한적십자사 헌혈 제한 의약품 목록 (사용자 제공), and 「헌혈기록카드 문진항목
 * 판정기준」 7차 개정판 (2021.12) 표1-1·표1-2 for the drugs that list leaves out. Restriction texts
 * are written for this app. 당일 is 1 day: donating is possible again the next day. Where the
 * period depends on why a drug was taken (메토트렉세이트, 항응고제), there is one entry per
 * purpose under the same ingredientName - see [ProductMatch.purposeChoices]. Kept in the same
 * order as `prohibited_drugs.json`; update both if the official guidance changes.
 */
val PROHIBITED_DRUGS: List<RestrictedDrug> = listOf(
    RestrictedDrug(
        category = "감기약·두통약",
        ingredientName = "감기약·두통약",
        representativeBrands = "종합감기약, 두통약 등",
        restrictionDays = 1,
        restrictionText = "복용 당일 헌혈 금지 (열·목감기 증상이 있었다면 3일)"
    ),
    RestrictedDrug(
        category = "해열진통소염제",
        ingredientName = "해열진통소염제",
        representativeBrands = "아세트아미노펜, 이부프로펜, 나프록센 등 먹는 약·주사 (아스피린·디피리다몰·인도메타신·피록시캄은 3일)",
        restrictionDays = 1,
        restrictionText = "투여 당일 헌혈 금지"
    ),
    RestrictedDrug(
        category = "근육이완제",
        ingredientName = "근육이완제",
        representativeBrands = "근육이완제 먹는 약·주사",
        restrictionDays = 1,
        restrictionText = "투여 당일 헌혈 금지"
    ),
    RestrictedDrug(
        category = "구충제",
        ingredientName = "구충제(예방 목적)",
        representativeBrands = "예방 목적으로 먹는 구충제 (디스토마약은 7일)",
        restrictionDays = 1,
        restrictionText = "복용 당일 헌혈 금지"
    ),
    RestrictedDrug(
        category = "동공확장제",
        ingredientName = "동공확장제",
        representativeBrands = "안과 검사 등에 쓰는 동공확장제",
        restrictionDays = 1,
        restrictionText = "사용 당일 헌혈 금지"
    ),
    RestrictedDrug(
        category = "말라리아 예방약",
        ingredientName = "말라리아 예방약",
        representativeBrands = "말라리아 예방약 (위험지역 방문에 따른 제한은 따로 적용)",
        restrictionDays = 1,
        restrictionText = "복용 당일 헌혈 금지"
    ),
    RestrictedDrug(
        category = "체중감량제",
        ingredientName = "펜터민/펜디메트라진",
        representativeBrands = "펜터민·펜디메트라진·암페타민 계열 식욕억제제",
        restrictionDays = 1,
        restrictionText = "복용 당일 헌혈 금지"
    ),
    RestrictedDrug(
        category = "기타 약물",
        ingredientName = "아스피린",
        representativeBrands = "아스피린",
        restrictionDays = 3,
        restrictionText = "복용 후 3일간 헌혈 금지 (감기 치료 목적 경구 복용 포함)"
    ),
    RestrictedDrug(
        category = "혈소판 기능에 영향을 주는 약",
        ingredientName = "디피리다몰",
        representativeBrands = "레드몰, 리다몰, 센타몰, 페가몰, 페르산틴 등",
        restrictionDays = 3,
        restrictionText = "복용 후 3일간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "혈소판 기능에 영향을 주는 약",
        ingredientName = "인도메타신",
        representativeBrands = "인테반, 류마메타신 등",
        restrictionDays = 3,
        restrictionText = "복용 후 3일간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "혈소판 기능에 영향을 주는 약",
        ingredientName = "피록시캄",
        representativeBrands = "피록시캄, 제로캄, 로감, 날시캄, 유캄, 로렉신, 로시덴, 뉴캄 등",
        restrictionDays = 3,
        restrictionText = "복용 후 3일간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "항생제",
        ingredientName = "항생제(감기 치료 목적)",
        representativeBrands = "감기 치료로 먹은 항생제",
        restrictionDays = 3,
        restrictionText = "복용 후 3일간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "기타 약물 및 주사제",
        ingredientName = "항생제/스테로이드/보톡스/클로피도그렐",
        representativeBrands = "항생제(주사), 스테로이드제(주사), 보톡스(주사), 클로피도그렐 등",
        restrictionDays = 7,
        restrictionText = "투여 후 1주간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "항생제",
        ingredientName = "항생제(먹는 약)",
        representativeBrands = "먹는 항생제, 쯔쯔가무시 예방약(독시사이클린) 포함",
        restrictionDays = 7,
        restrictionText = "복용 후 7일간 헌혈 금지 (감기 치료 목적이면 3일)"
    ),
    RestrictedDrug(
        category = "스테로이드제",
        ingredientName = "스테로이드제(먹는 약)",
        representativeBrands = "프레드니솔론, 덱사메타손 등 부신피질호르몬 먹는 약 (연고·점안액·스프레이는 제한 없음)",
        restrictionDays = 7,
        restrictionText = "복용 후 7일간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "구충제",
        ingredientName = "구충제(치료 목적)/프라지콴텔",
        representativeBrands = "치료 목적 구충제, 디스토마약(프라지콴텔)은 예방 목적이어도 포함",
        restrictionDays = 7,
        restrictionText = "복용 후 7일간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "주사제",
        ingredientName = "연골주사",
        representativeBrands = "히알루론산 관절 내 주사",
        restrictionDays = 7,
        restrictionText = "투여 후 7일간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "주사제",
        ingredientName = "인대강화주사",
        representativeBrands = "프롤로 주사(포도당·PRP 등), PDRN 주사",
        restrictionDays = 7,
        restrictionText = "투여 후 7일간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "주사제",
        ingredientName = "지방분해주사",
        representativeBrands = "지방분해 주사제",
        restrictionDays = 7,
        restrictionText = "투여 후 7일간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "체중감량제",
        ingredientName = "리라글루티드",
        representativeBrands = "삭센다",
        restrictionDays = 7,
        restrictionText = "투여 후 7일간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "켈로이드·비후성 반흔 치료제",
        ingredientName = "트라닐라스트",
        representativeBrands = "리자벤",
        restrictionDays = 7,
        restrictionText = "복용 후 7일간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "항진균제",
        ingredientName = "항진균제(표재성 감염)",
        representativeBrands = "백선·무좀·완선 등 피부·손발톱 진균증에 먹는 약 (연고·질정은 제한 없음)",
        restrictionDays = 7,
        restrictionText = "복용 종료 후 7일간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "항응고제",
        ingredientName = "와파린/헤파린/히루딘",
        representativeBrands = "예방 목적 투여 (기저질환이 헌혈 금지에 해당하지 않을 때)",
        restrictionDays = 7,
        restrictionText = "투여 중단 후 7일간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "줄기세포 주사",
        ingredientName = "줄기세포 주사(자가)",
        representativeBrands = "자기 줄기세포 주사",
        restrictionDays = 7,
        restrictionText = "투여 후 7일간 헌혈 금지"
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
        category = "항진균제",
        ingredientName = "항진균제(전신·심재성 감염)",
        representativeBrands = "장진균증 등 내부 장기 진균 감염 치료",
        restrictionDays = 28,
        restrictionText = "치료 종료 후 4주간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "건선 치료제",
        ingredientName = "메토트렉세이트",
        representativeBrands = "건선 치료 목적 복용",
        restrictionDays = 28,
        restrictionText = "복용 종료 후 4주간 헌혈 금지"
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
        category = "줄기세포 주사",
        ingredientName = "줄기세포 주사(관절경)",
        representativeBrands = "관절경으로 넣은 줄기세포 주사",
        restrictionDays = 30,
        restrictionText = "투여 후 1개월간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "결핵 치료제",
        ingredientName = "항결핵제(잠복결핵)",
        representativeBrands = "잠복결핵 치료로 먹은 항결핵제",
        restrictionDays = 30,
        restrictionText = "복용 중단 후 1개월간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "임상시험",
        ingredientName = "생물학적 동등성 시험(성분을 아는 경우)",
        representativeBrands = "복제약 비교 임상시험 참여 (그 성분의 제한이 8주보다 길면 그 기간)",
        restrictionDays = 56,
        restrictionText = "참여 후 8주간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "전립선비대증 치료제",
        ingredientName = "두타스테라이드",
        representativeBrands = "아보다트, 아보스타 등",
        restrictionDays = 180,
        restrictionText = "복용 후 6개월간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "골다공증 치료제",
        ingredientName = "데노수맙",
        representativeBrands = "프롤리아 등 데노수맙 주사",
        restrictionDays = 180,
        restrictionText = "투여 후 6개월간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "기타 주사제",
        ingredientName = "태반주사제/혈액응고인자",
        representativeBrands = "태반주사제, 혈액응고인자",
        restrictionDays = 365,
        restrictionText = "투여 후 1년간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "임상시험",
        ingredientName = "생물학적 동등성 시험(성분을 모르는 경우)",
        representativeBrands = "복제약 비교 임상시험 참여, 약 성분을 모를 때",
        restrictionDays = 365,
        restrictionText = "참여 후 1년간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "임상시험",
        ingredientName = "임상시험",
        representativeBrands = "신약·식품 등의 안전성·유효성 시험 참여 (질환이 있는 상태의 시험은 그 질환 기준)",
        restrictionDays = 365,
        restrictionText = "참여 후 1년간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "기타 주사제",
        ingredientName = "B형간염 면역글로불린",
        representativeBrands = "B형간염 면역글로불린 주사",
        restrictionDays = 365,
        restrictionText = "투여 후 1년간 헌혈 금지"
    ),
    RestrictedDrug(
        category = "줄기세포 주사",
        ingredientName = "줄기세포 주사(자가 여부 모름)",
        representativeBrands = "자기 줄기세포인지 확인할 수 없는 주사",
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
        category = "갑상선기능항진증 치료제",
        ingredientName = "프로필티오우라실/메티마졸",
        representativeBrands = "항갑상선제(PTU, MMI)",
        restrictionDays = 730,
        restrictionText = "복용 종료 후 2년간 헌혈 금지"
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
    ),
    RestrictedDrug(
        category = "항응고제",
        ingredientName = "와파린/헤파린/히루딘",
        representativeBrands = "쿠마딘 등, 정맥혈전증·관상동맥폐색증·폐색전증 등 치료 목적 투여",
        restrictionDays = -1,
        restrictionText = "치료 목적으로 투여 시 영구 헌혈 금지"
    ),
    RestrictedDrug(
        category = "항암제·자가면역질환 치료제",
        ingredientName = "메토트렉세이트",
        representativeBrands = "암·자가면역질환(류마티스 관절염 등) 치료 목적 복용",
        restrictionDays = -1,
        restrictionText = "치료 목적으로 복용 시 영구 헌혈 금지"
    ),
    RestrictedDrug(
        category = "기타 약물 및 주사제",
        ingredientName = "vCJD 위험지역 혈액 유래 진단시약",
        representativeBrands = "vCJD 위험지역에서 채혈한 혈액의 혈청으로 만든 진단시약",
        restrictionDays = -1,
        restrictionText = "투여 시 영구 헌혈 금지"
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

/**
 * Case-insensitive match against ingredient name, brand names or category - so "탈모" finds
 * 탈모증 치료제 and "항응고제" both of its entries. A blank query returns nothing.
 */
fun searchDrugs(query: String, drugs: List<RestrictedDrug> = PROHIBITED_DRUGS): List<RestrictedDrug> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()
    return drugs.filter {
        it.ingredientName.contains(trimmed, ignoreCase = true) ||
            it.representativeBrands.contains(trimmed, ignoreCase = true) ||
            it.category.contains(trimmed, ignoreCase = true)
    }
}
