package com.example.myapplication.domain

import com.example.myapplication.model.DonationType
import java.time.LocalDate

data class BloodDonationInfo(
    val certNumber: String? = null,
    val donationType: String? = null,
    val donationDate: LocalDate? = null,
    val centerName: String? = null
)

private val CERT_NUMBER_REGEX = Regex("""\d{2}-\d{2}-\d{6}-\d{2}""")
private val DONATION_DATE_REGEX = Regex("""헌혈일자?\D*(\d{4})\D+(\d{1,2})\D+(\d{1,2})""")
private val DONATION_TYPE_REGEX = Regex("""헌혈종류\s*[:：]?\s*([^\n]+)""")
private val CENTER_NAME_REGEX = Regex("""[가-힣]+혈액원\s*\([\d\s-]+\)""")
private val STATED_VOLUME_REGEX = Regex("""(\d+)\s*mL""")

private fun MatchResult.toLocalDateOrNull(): LocalDate? {
    val (year, month, day) = destructured
    return runCatching { LocalDate.of(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()
}

/**
 * Extracts blood-donation-certificate fields from OCR'd text (ML Kit's `Text.text`) using regex
 * patterns matched against a real 대한적십자사 헌혈증서 layout. Certificates vary by blood
 * center, so each field is matched independently and left null (never guessed) when its pattern
 * isn't found. Only 헌혈종류/헌혈일/증서번호/헌혈장소 are extracted - name and birth date are
 * always left to manual entry, since OCR must not write into those fields.
 */
fun parseBloodDonationInfo(text: String): BloodDonationInfo = BloodDonationInfo(
    certNumber = CERT_NUMBER_REGEX.find(text)?.value,
    donationType = DONATION_TYPE_REGEX.find(text)?.groupValues?.get(1)?.trim(),
    donationDate = DONATION_DATE_REGEX.find(text)?.toLocalDateOrNull(),
    centerName = CENTER_NAME_REGEX.find(text)?.value
)

/** Maps the certificate's raw donation-type text (e.g. "전혈 400mL") to this app's [DonationType]. */
fun BloodDonationInfo.donationTypeEnum(): DonationType? = when {
    donationType == null -> null
    donationType.contains("전혈") -> DonationType.WHOLE_BLOOD
    donationType.contains("혈장") -> DonationType.PLASMA
    donationType.contains("혈소판") -> DonationType.PLATELET
    else -> null
}

/** The volume figure printed on the certificate (e.g. 400 from "전혈 400mL"), before any diagnostic draw is added. */
fun BloodDonationInfo.statedVolumeMl(): Int? =
    donationType?.let { STATED_VOLUME_REGEX.find(it)?.groupValues?.get(1)?.toIntOrNull() }

/**
 * The actual volume drawn, i.e. [statedVolumeMl] plus the extra 30mL diagnostic-testing draw for
 * whole blood donations (e.g. a stated 400mL whole blood donation actually draws 430mL). Other
 * donation types draw exactly the stated amount. Null when the stated volume couldn't be parsed.
 */
fun BloodDonationInfo.drawnVolumeMl(): Int? = statedVolumeMl()?.let { stated ->
    if (donationTypeEnum() == DonationType.WHOLE_BLOOD) stated + WHOLE_BLOOD_DIAGNOSTIC_DRAW_ML else stated
}
