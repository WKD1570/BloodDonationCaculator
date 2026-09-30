package com.example.myapplication.domain

import androidx.compose.ui.graphics.Color
import com.example.myapplication.model.DonationRecord
import com.example.myapplication.model.DonationType
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

const val WHOLE_BLOOD_ANNUAL_LIMIT = 5
const val PLATELET_ANNUAL_LIMIT = 24
const val ANNUAL_LIMIT_ML = 2160

/**
 * How long a donation keeps counting toward the annual count/volume limits, matching the +366일
 * expiry [calcNext] uses: a donation is still counted on the 365th day after it, and no longer
 * counted once 366 days have passed.
 */
const val ANNUAL_WINDOW_DAYS = 366L

data class TypeInfo(
    val type: DonationType,
    val label: String,
    val color: Color,
    val bg: Color,
    val border: Color
) {
    val volumeMl: Int get() = type.defaultVolumeMl
    val gapDays: Int get() = type.minIntervalDays
    val yearMax: Int?
        get() = when (type) {
            DonationType.WHOLE_BLOOD -> WHOLE_BLOOD_ANNUAL_LIMIT
            DonationType.PLATELET -> PLATELET_ANNUAL_LIMIT
            DonationType.PLASMA -> null
        }
}

val TYPE_INFO: Map<DonationType, TypeInfo> = mapOf(
    DonationType.WHOLE_BLOOD to TypeInfo(DonationType.WHOLE_BLOOD, "전혈헌혈", Color(0xFFDC2626), Color(0xFFFEF2F2), Color(0xFFFCA5A5)),
    DonationType.PLASMA to TypeInfo(DonationType.PLASMA, "혈장성분헌혈", Color(0xFFEAB308), Color(0xFFFEFCE8), Color(0xFFFDE68A)),
    DonationType.PLATELET to TypeInfo(DonationType.PLATELET, "혈소판성분헌혈", Color(0xFF38BDF8), Color(0xFFF0F9FF), Color(0xFFBAE6FD))
)

fun typeSubtitle(type: DonationType): String {
    val info = TYPE_INFO.getValue(type)
    val weeks = info.gapDays / 7
    val yearText = info.yearMax?.let { "연 ${it}회" } ?: "횟수제한 없음"
    return "1회 ${info.volumeMl}mL · 간격 ${weeks}주 · $yearText"
}

/** 대한적십자사 기준: 전혈헌혈은 만 16~17세는 350mL, 만 18세 이상은 430mL을 채혈한다. */
fun actualVolumeMl(type: DonationType, age: Int?): Int =
    if (type == DonationType.WHOLE_BLOOD && age != null && age in 16..17) 350 else type.defaultVolumeMl

private fun DonationRecord.ageAtDonation(): Int? = birthDate?.let { Period.between(it, date).years }

/** Uses the certificate-stated [DonationRecord.donatedVolumeMl] when known, otherwise the age-based default. */
fun DonationRecord.volumeMl(): Int = donatedVolumeMl ?: actualVolumeMl(type, ageAtDonation())

/**
 * The records that still count toward the annual limits as of [today] — i.e. the donations from
 * the past year ([ANNUAL_WINDOW_DAYS]). Donations older than that have expired and no longer
 * consume any of the annual 2,160mL allowance.
 */
fun List<DonationRecord>.withinAnnualWindow(today: LocalDate): List<DonationRecord> =
    filter { it.date.plusDays(ANNUAL_WINDOW_DAYS).isAfter(today) }

data class DDay(val text: String, val color: Color)

data class StatusStyle(val label: String, val color: Color)

data class Reason(val text: String, val color: Color)

data class NextResult(val nextDate: LocalDate, val dd: DDay, val reasons: List<Reason>)

private val longFormatter = DateTimeFormatter.ofPattern("yyyy'년' M'월' d'일'")
private val shortFormatter = DateTimeFormatter.ofPattern("M'월' d'일'")

fun fmt(date: LocalDate): String = date.format(longFormatter)
fun fmtShort(date: LocalDate): String = date.format(shortFormatter)

fun dday(date: LocalDate, today: LocalDate): DDay {
    val diff = java.time.temporal.ChronoUnit.DAYS.between(today, date)
    return when {
        diff > 0 -> DDay("D-$diff", Color(0xFF475569))
        diff == 0L -> DDay("D-Day · 오늘!", Color(0xFF4F46E5))
        else -> DDay("이미 가능", Color(0xFF059669))
    }
}

fun statusStyle(pct: Double): StatusStyle = when {
    pct <= 60 -> StatusStyle("여유", Color(0xFF059669))
    pct <= 85 -> StatusStyle("주의", Color(0xFFD97706))
    pct < 100 -> StatusStyle("임박", Color(0xFFEA580C))
    else -> StatusStyle("초과", Color(0xFFE11D48))
}

private fun sortedByDate(records: List<DonationRecord>): List<DonationRecord> = records.sortedBy { it.date }

/**
 * @param asOfDate The date the restriction rules are evaluated as of - only records dated at or
 * before this date can impose a constraint. Defaults to [today], which is correct whenever
 * [records] are all real historical donations (they can never postdate the real "today"). Pass an
 * explicit, earlier [asOfDate] when checking a specific candidate date - e.g. from
 * [earliestEligibleDate] - so a record dated *after* that candidate doesn't wrongly bind it with
 * a cooldown/count/volume constraint that (as of the candidate's date) hasn't happened yet.
 */
fun calcNext(
    type: DonationType,
    records: List<DonationRecord>,
    today: LocalDate,
    donorAge: Int? = null,
    asOfDate: LocalDate = today
): NextResult {
    val info = TYPE_INFO.getValue(type)
    val items = sortedByDate(records).filter { !it.date.isAfter(asOfDate) }
    val ofType = items.filter { it.type == type }
    val totalVol = items.sumOf { it.volumeMl() }
    val nextVolumeMl = actualVolumeMl(type, donorAge)

    var nextDate = today
    val reasons = mutableListOf<Reason>()

    // Interval rule: every past donation binds the next donation (of any type) using its OWN interval,
    // e.g. a whole blood donation imposes 56 days on the next plasma/platelet donation, and a plasma or
    // platelet donation likewise imposes its own 14 days on the next donation of any other type.
    if (items.isNotEmpty()) {
        val binding = items.maxBy { TYPE_INFO.getValue(it.type).let { i -> it.date.plusDays(i.gapDays.toLong()) } }
        val bindingInfo = TYPE_INFO.getValue(binding.type)
        val gapDate = binding.date.plusDays(bindingInfo.gapDays.toLong())
        if (gapDate.isAfter(today)) {
            if (gapDate.isAfter(nextDate)) nextDate = gapDate
            reasons.add(
                Reason(
                    "마지막 ${bindingInfo.label} 후 ${bindingInfo.gapDays}일 간격 필요 (${fmtShort(binding.date)})",
                    bindingInfo.color
                )
            )
        }
    }

    // Annual count-limit rule
    val yearMax = info.yearMax
    if (yearMax != null && ofType.size >= yearMax) {
        val oldest = ofType.first()
        val expDate = oldest.date.plusDays(366)
        if (expDate.isAfter(today)) {
            if (expDate.isAfter(nextDate)) nextDate = expDate
            reasons.add(
                Reason(
                    "연간 ${yearMax}회 한도 → 가장 오래된 헌혈 만료 후 가능 (${fmtShort(oldest.date)} → +365일)",
                    Color(0xFF64748B)
                )
            )
        }
    }

    // Annual cumulative-volume rule
    if (totalVol + nextVolumeMl > ANNUAL_LIMIT_ML) {
        val sorted = items.sortedBy { it.date }
        var runVol = totalVol
        for (h in sorted) {
            runVol -= h.volumeMl()
            if (runVol + nextVolumeMl <= ANNUAL_LIMIT_ML) {
                val expDate = h.date.plusDays(366)
                if (expDate.isAfter(today)) {
                    if (expDate.isAfter(nextDate)) nextDate = expDate
                    reasons.add(
                        Reason(
                            "연간 채혈량 2,160mL 초과 → ${fmtShort(h.date)} 헌혈 만료 후 ${nextVolumeMl}mL 여유 생김",
                            Color(0xFF475569)
                        )
                    )
                }
                break
            }
        }
    }

    return NextResult(nextDate, dday(nextDate, today), reasons)
}

private val DISTANT_PAST: LocalDate = LocalDate.of(1, 1, 1)

/**
 * Earliest date a [type] donation is allowed on [asOfDate], given [records] (which should exclude
 * the donation being placed/edited), ignoring today's date entirely — i.e. purely the
 * interval/annual-count/annual-volume constraints from [calcNext], with no "at least today" floor.
 * Only records at or before [asOfDate] can constrain it, so a record dated after [asOfDate] (e.g.
 * a later donation being backfilled before an earlier one) is correctly ignored.
 */
fun earliestEligibleDate(type: DonationType, records: List<DonationRecord>, ageAtDate: Int?, asOfDate: LocalDate): LocalDate =
    calcNext(type, records, DISTANT_PAST, ageAtDate, asOfDate).nextDate
