package com.example.myapplication.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.domain.HealthRestriction
import com.example.myapplication.domain.HistorySource
import com.example.myapplication.domain.IntegratedNextResult
import com.example.myapplication.domain.TYPE_INFO
import com.example.myapplication.domain.VcjdExposure
import com.example.myapplication.domain.dday
import com.example.myapplication.domain.fmt
import com.example.myapplication.domain.healthRestrictions
import com.example.myapplication.domain.nextEligibleByType
import com.example.myapplication.domain.vcjdExposures
import com.example.myapplication.model.DonationRecord
import com.example.myapplication.model.DonationType
import com.example.myapplication.model.InfectionRules
import com.example.myapplication.model.MedicationRecord
import com.example.myapplication.model.StayRecord
import java.time.LocalDate

/**
 * Imported stays are evaluated before they're saved under ids at or below this, so they can be
 * told apart from saved stays (positive ids) in [HealthRestriction.sources].
 */
private const val IMPORTED_ID_BASE = -1_000L

/** What the imported Timeline means once combined with everything already on record. */
private class TimelineAnalysis(
    /**
     * Every imported stay: the saved copy when it's already in 체류 이력, otherwise the stay under an
     * [IMPORTED_ID_BASE] id.
     */
    val stays: List<StayRecord>,
    /** Restrictions at least one imported stay contributes to. */
    val restrictions: List<HealthRestriction>,
    /** The imported stays those restrictions come from - the ones worth saving. */
    val staysToSave: List<StayRecord>,
    val unrestrictedStays: Int,
    val nextByType: Map<DonationType, IntegratedNextResult>,
    val vcjd: List<VcjdExposure>
)

private fun StayRecord.key() = Triple(regionName, startDate, endDate)

private fun analyze(
    done: TimelineImportState.Done,
    health: HealthHistoryState,
    rules: InfectionRules,
    records: List<DonationRecord>,
    medications: List<MedicationRecord>,
    today: LocalDate
): TimelineAnalysis {
    // Matching an imported stay to its saved copy keeps the results in place once they're saved.
    val savedByKey = health.stays.associateBy { it.key() }
    val imported = done.stays.map { it.toStayRecord() }.distinctBy { it.key() }
    val stays = imported.mapIndexed { index, stay -> savedByKey[stay.key()] ?: stay.copy(id = IMPORTED_ID_BASE - index) }
    val newStays = stays.filter { it.id <= IMPORTED_ID_BASE }
    val importedSources = stays.map { HistorySource.Stay(it.id) }.toSet()

    // Saved and imported stays together: vCJD time adds up across both.
    val all = healthRestrictions(health.diseases, health.stays + newStays, rules)
    val restrictions = all.filter { restriction -> restriction.sources.any { it in importedSources } }
    val contributing = restrictions.flatMap { it.sources }.toSet()

    return TimelineAnalysis(
        stays = stays,
        restrictions = restrictions,
        staysToSave = newStays.filter { HistorySource.Stay(it.id) in contributing },
        unrestrictedStays = stays.count { HistorySource.Stay(it.id) !in contributing },
        nextByType = nextEligibleByType(records, medications, today, all),
        vcjd = vcjdExposures(imported, rules)
    )
}

/**
 * 의료 정보's Google Maps Timeline import: pick an export, see which stays in restricted regions it
 * contains and when each donation type is possible again, then save those stays to 체류 이력.
 */
@Composable
internal fun TimelineImportCard(
    health: HealthHistoryState,
    records: List<DonationRecord>,
    medications: List<MedicationRecord>,
    today: LocalDate,
    onSaveStays: (List<StayRecord>) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TimelineImportViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.import(uris)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("🗺️ Google 타임라인 가져오기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Google 지도 타임라인을 내보낸 파일을 선택하면 말라리아·vCJD 위험지역 체류를 찾아 헌혈 가능일을 계산해요.",
                fontSize = 11.sp,
                color = TextTertiary,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "🔒 파일은 이 기기 안에서만 처리되고 어디에도 전송되지 않아요.",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = SuccessColor,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "• Android: 설정 › 위치 › 타임라인 › 타임라인 데이터 내보내기 (Timeline.json)\n" +
                    "• iPhone: Google 지도 › 설정 › 개인 콘텐츠 › 타임라인 데이터 내보내기\n" +
                    "• Google 테이크아웃: Semantic Location History 폴더의 JSON 또는 ZIP",
                fontSize = 10.sp,
                color = TextTertiary,
                lineHeight = 15.sp
            )
            Spacer(Modifier.height(14.dp))

            when (val current = state) {
                TimelineImportState.Idle -> PickButton("타임라인 파일 선택") { picker.launch(arrayOf("*/*")) }

                TimelineImportState.Processing -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Accent, modifier = Modifier.padding(4.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("타임라인을 분석하고 있어요…", fontSize = 12.sp, color = TextSecondary)
                }

                is TimelineImportState.Failed -> {
                    Text(current.message, fontSize = 12.sp, color = ErrorText, lineHeight = 17.sp)
                    Spacer(Modifier.height(10.dp))
                    PickButton("다른 파일 선택") { picker.launch(arrayOf("*/*")) }
                }

                is TimelineImportState.Done -> {
                    val rules = health.rules
                    if (rules == null) {
                        Text(health.rulesWarning ?: "헌혈 기준을 불러오는 중이에요…", fontSize = 12.sp, color = TextTertiary)
                    } else {
                        val analysis = remember(current, health, records, medications, today) {
                            analyze(current, health, rules, records, medications, today)
                        }
                        TimelineResult(current, analysis, today, onSave = { onSaveStays(analysis.staysToSave.map { it.copy(id = 0) }) })
                    }
                    Spacer(Modifier.height(6.dp))
                    Row {
                        TextButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                            Text("다른 파일 선택", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        TextButton(onClick = viewModel::clear) {
                            Text("닫기", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(14.dp))
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
    Spacer(Modifier.height(6.dp))
}

private fun daysText(days: Long): String = when {
    days >= 365 -> "${days}일 (약 ${"%.1f".format(days / 365.25)}년)"
    days >= 30 -> "${days}일 (약 ${"%.1f".format(days / 30.44)}개월)"
    else -> "${days}일"
}

@Composable
private fun TimelineResult(
    done: TimelineImportState.Done,
    analysis: TimelineAnalysis,
    today: LocalDate,
    onSave: () -> Unit
) {
    val range = if (done.firstDate != null && done.lastDate != null) " (${fmt(done.firstDate)} ~ ${fmt(done.lastDate)})" else ""
    Text(
        "방문 기록 ${"%,d".format(done.visitCount)}건 분석$range",
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimary,
        lineHeight = 17.sp
    )
    Text(
        "위험지역 체류 ${done.stays.size}건 · 헌혈 제한 ${analysis.restrictions.size}건",
        fontSize = 11.sp,
        color = TextTertiary
    )

    SectionLabel("다음 헌혈 가능일 (가져온 체류 반영)")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(InputBg)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        analysis.nextByType.forEach { (type, result) ->
            val info = TYPE_INFO.getValue(type)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(info.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                when (result) {
                    is IntegratedNextResult.Eligible -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fmt(result.nextDate), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(Modifier.width(6.dp))
                        val dd = dday(result.nextDate, today)
                        Text(dd.text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = dd.color)
                    }

                    is IntegratedNextResult.PermanentlyProhibited ->
                        Text("영구 제한", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ErrorText)
                }
            }
        }
    }
    Text(
        "헌혈 기록·약물·감염병·저장된 체류 이력을 모두 반영한 날짜예요.",
        fontSize = 10.sp,
        color = TextTertiary,
        modifier = Modifier.padding(top = 4.dp)
    )

    if (analysis.restrictions.isNotEmpty()) {
        SectionLabel("헌혈을 제한하는 체류")
        analysis.restrictions.forEachIndexed { index, restriction ->
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(restriction.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                analysis.stays
                    .filter { HistorySource.Stay(it.id) in restriction.sources }
                    .forEach { Text(stayPeriodText(it), fontSize = 11.sp, color = TextTertiary) }
                RestrictionOutcome(restriction, today)
            }
            if (index != analysis.restrictions.lastIndex) HorizontalDivider(color = DividerColor)
        }
    }
    if (analysis.unrestrictedStays > 0) {
        Text(
            "당일 방문 등 기준에 해당하지 않는 위험지역 체류 ${analysis.unrestrictedStays}건은 제한이 없어요.",
            fontSize = 11.sp,
            color = TextTertiary,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }

    if (analysis.vcjd.isNotEmpty()) {
        SectionLabel("vCJD 지역 누적 체류")
        analysis.vcjd.forEach { exposure ->
            Text(
                "${exposure.country}: 총 ${daysText(exposure.totalDays)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                "${exposure.rule.targetPeriodText} 중 ${daysText(exposure.daysInPeriod)} · 기준 ${exposure.rule.minStayText}",
                fontSize = 11.sp,
                color = TextTertiary
            )
        }
        Text(
            "타임라인 기록은 대부분 2010년 이후부터 있어 vCJD 기준기간(1980~2001년) 체류는 확인하기 어려워요. " +
                "그 시기에 체류했다면 체류 이력에 직접 추가해 주세요.",
            fontSize = 10.sp,
            color = TextTertiary,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }

    if (done.failures.isNotEmpty()) {
        SectionLabel("읽지 않은 파일 ${done.failures.size}개")
        done.failures.take(5).forEach { (name, reason) ->
            Text("$name: $reason", fontSize = 10.sp, color = TextTertiary, lineHeight = 15.sp)
        }
    }

    Spacer(Modifier.height(14.dp))
    when {
        analysis.staysToSave.isNotEmpty() -> PickButton("체류 이력에 저장 (${analysis.staysToSave.size}건)", onSave)

        analysis.restrictions.isNotEmpty() -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("✓ 체류 이력에 반영되어 있어요", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SuccessColor)
        }

        else -> Text("저장할 제한 체류가 없어요.", fontSize = 12.sp, color = TextTertiary)
    }
}
