package com.example.myapplication.domain

import com.example.myapplication.model.DonationType
import com.example.myapplication.model.DonorProfile
import com.example.myapplication.model.Sex
import com.example.myapplication.model.currentAge
import kotlin.math.roundToInt

data class PhysicalEligibility(val isEligible: Boolean, val reasons: List<String>)

/**
 * 대한적십자사 기준: multi-type (component) donation requires a predicted blood volume - from the
 * 성인예측 혈량표, [predictedBloodVolume] - of at least this.
 */
private const val MIN_BLOOD_VOLUME_FOR_APHERESIS_ML = 4000

/** Null for the 기타 types, which have no age criteria here (and aren't screened on 마이페이지). */
private fun ageRange(type: DonationType): IntRange? = when (type) {
    DonationType.WHOLE_BLOOD -> 16..69
    DonationType.PLASMA -> 17..69
    DonationType.PLATELET -> 17..59
    DonationType.WHITE_BLOOD_CELL, DonationType.STEM_CELL -> null
}

private fun minWeightKg(sex: Sex): Double = when (sex) {
    Sex.MALE -> 50.0
    Sex.FEMALE -> 45.0
}

/**
 * Checks age/weight/height eligibility for one donation type against 대한적십자사 reference
 * criteria. This does not cover criteria a self-reported profile can't capture (hemoglobin,
 * blood pressure, recent illness, etc.) — it only screens the physical intake fields.
 */
fun checkPhysicalEligibility(profile: DonorProfile, type: DonationType): PhysicalEligibility {
    val age = profile.currentAge()
    val weight = profile.weightKg
    val height = profile.heightCm
    val sex = profile.sex

    if (age == null || weight == null || height == null || sex == null) {
        return PhysicalEligibility(false, listOf("생년월일, 체중, 신장, 성별을 모두 입력해주세요"))
    }

    val reasons = mutableListOf<String>()

    ageRange(type)?.let { range ->
        if (age !in range) {
            reasons.add("나이 기준 미충족 (${range.first}~${range.last}세, 입력 ${age}세)")
        } else if (age >= 65 && !profile.donatedAge60To64) {
            reasons.add("65세 이상은 60~64세 사이 헌혈 경험이 있어야 헌혈 가능합니다")
        }
    }

    val minWeight = minWeightKg(sex)
    if (weight < minWeight) {
        reasons.add("체중 기준 미충족 (${minWeight.roundToInt()}kg 이상 필요, 입력 ${weight}kg)")
    }

    if (type != DonationType.WHOLE_BLOOD) {
        val predicted = predictedBloodVolume(height, weight, sex)
        if (predicted != null && predicted.volumeMl < MIN_BLOOD_VOLUME_FOR_APHERESIS_ML) {
            reasons.add(
                "예측 혈량 부족으로 성분헌혈 제한 (예측 ${"%,d".format(predicted.volumeMl)}mL, " +
                    "${"%,d".format(MIN_BLOOD_VOLUME_FOR_APHERESIS_ML)}mL 이상 필요)"
            )
        }
    }

    return PhysicalEligibility(reasons.isEmpty(), reasons)
}
