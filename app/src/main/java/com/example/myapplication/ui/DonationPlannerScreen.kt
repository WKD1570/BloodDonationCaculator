package com.example.myapplication.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.domain.ANNUAL_LIMIT_ML
import com.example.myapplication.domain.IntegratedNextResult
import com.example.myapplication.domain.PlanCheck
import com.example.myapplication.domain.TYPE_INFO
import com.example.myapplication.domain.fmtShort
import com.example.myapplication.domain.plannedAnnualVolume
import com.example.myapplication.model.DonationRecord
import com.example.myapplication.model.DonationType
import com.example.myapplication.model.REGULAR_DONATION_TYPES
import java.time.LocalDate
import java.time.YearMonth

/** 가능한 날짜로 채우기 periods, in months. */
private val FILL_PERIODS = listOf(1L to "1개월", 3L to "3개월", 6L to "6개월", 12L to "1년")

private val WEEKDAYS = listOf("월", "화", "수", "목", "금", "토", "일")

/** "10월 24일 (토)". */
private fun dayLabel(date: LocalDate) = "${fmtShort(date)} (${WEEKDAYS[date.dayOfWeek.value - 1]})"

/**
 * 헌혈 계획, opened from 더보기 under 현황's calendar: plan donations by tapping days, or fill in the
 * earliest days for a type, and see whether each would be allowed. Each is checked the way 현황
 * checks the next donation - records, medications and 감염병·체류 history - with every allowed
 * planned donation counting toward the ones after it.
 *
 * Stateless: [plan] is [DonationViewModel]'s, which saves it and marks it on 현황's calendar too.
 */
@Composable
internal fun DonationPlannerScreen(
    records: List<DonationRecord>,
    plan: List<PlanCheck>,
    nextByType: Map<DonationType, IntegratedNextResult>,
    today: LocalDate,
    onToggleDay: (LocalDate, DonationType) -> Unit,
    onRemove: (Long) -> Unit,
    onFill: (DonationType, Long) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var type by rememberSaveable { mutableStateOf(DonationType.WHOLE_BLOOD) }
    var months by rememberSaveable { mutableStateOf(3L) }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var confirmClear by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CalendarNavButton("‹", onBack)
            Spacer(Modifier.width(10.dp))
            Text("헌혈 계획", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "날짜를 누르면 고른 종류로 계획하고, 한 번 더 누르면 지워요. 헌혈 기록과 약물·감염병·체류 이력을 " +
                "반영해 가능한지 알려드려요.",
            fontSize = 11.sp,
            color = TextTertiary,
            lineHeight = 16.sp
        )

        Spacer(Modifier.height(14.dp))
        TypeToggleRow(REGULAR_DONATION_TYPES, type) { type = it }

        Spacer(Modifier.height(12.dp))
        PlannerCard {
            CalendarMonthGrid(month, { month = it }, today, records, nextByType, plan) { date -> onToggleDay(date, type) }
        }

        Spacer(Modifier.height(14.dp))
        PlannerCard {
            Text("⚡ 가능한 날짜로 채우기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "${TYPE_INFO.getValue(type).label}을 오늘부터 기간 끝까지 가능한 가장 빠른 날마다 넣어요. " +
                    "이미 넣은 계획은 그대로 둬요.",
                fontSize = 11.sp,
                color = TextTertiary,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FILL_PERIODS.forEach { (length, label) ->
                    ChoiceChip(label, active = months == length, onClick = { months = length })
                }
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton("가능한 날짜로 채우기", Modifier.fillMaxWidth()) { onFill(type, months) }
        }

        Spacer(Modifier.height(14.dp))
        PlannerCard {
            PlanList(plan, records, onRemove, onClearRequest = { confirmClear = true })
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("계획을 모두 지울까요?", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = { Text("헌혈 기록은 그대로 남아요.", fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClear()
                }) { Text("지우기", color = ErrorText) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("취소") } }
        )
    }
}

@Composable
private fun PlannerCard(content: @Composable () -> Unit) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) { content() }
    }
}

/** The plan in date order under a summary: what's allowed, by type, and the year's volume at its end. */
@Composable
private fun PlanList(
    plan: List<PlanCheck>,
    records: List<DonationRecord>,
    onRemove: (Long) -> Unit,
    onClearRequest: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("📋 계획 ${plan.size}회", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.weight(1f))
        if (plan.isNotEmpty()) {
            TextButton(onClick = onClearRequest) { Text("계획 모두 지우기", fontSize = 12.sp, color = TextSecondary) }
        }
    }
    if (plan.isEmpty()) {
        Text(
            "아직 계획이 없어요. 달력에서 날짜를 누르거나 위에서 채워 보세요.",
            fontSize = 11.sp,
            color = TextTertiary,
            lineHeight = 16.sp
        )
        return
    }

    val allowed = plan.count { it.isAllowed }
    val lastDay = plan.last().planned.date
    val volume = plannedAnnualVolume(plan, records, lastDay)
    Text(
        "가능 ${allowed}회 · 제한 ${plan.size - allowed}회 · " +
            REGULAR_DONATION_TYPES.mapNotNull { type ->
                plan.count { it.planned.type == type }.takeIf { it > 0 }?.let { "${TYPE_INFO.getValue(type).label.removeSuffix("헌혈")} $it" }
            }.joinToString(" · "),
        fontSize = 11.sp,
        color = TextSecondary,
        lineHeight = 16.sp
    )
    Text(
        "계획대로 하면 ${fmtShort(lastDay)} 기준 최근 1년 채혈량 ${"%,d".format(volume)} / ${"%,d".format(ANNUAL_LIMIT_ML)} mL",
        fontSize = 11.sp,
        color = TextSecondary,
        lineHeight = 16.sp
    )
    Spacer(Modifier.height(8.dp))
    plan.forEachIndexed { index, check ->
        if (index > 0) HorizontalDivider(color = DividerColor)
        PlanRow(check, onRemove = { onRemove(check.planned.id) })
    }
}

@Composable
private fun PlanRow(check: PlanCheck, onRemove: () -> Unit) {
    val info = TYPE_INFO.getValue(check.planned.type)
    val allowedFrom = check.allowedFrom
    val status = when {
        check.isAllowed -> "✓ 가능"
        allowedFrom == null -> "✗ 영구 헌혈 금지"
        else -> "✗ ${fmtShort(allowedFrom)}부터 가능"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(info.color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(dayLabel(check.planned.date), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(info.label, fontSize = 11.sp, color = TextSecondary)
            if (!check.isAllowed) {
                check.reasons.lastOrNull()?.let { reason ->
                    Text(reason.text, fontSize = 10.sp, color = reason.color, lineHeight = 14.sp)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            status,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (check.isAllowed) SuccessColor else ErrorText
        )
        Text(
            "×",
            fontSize = 16.sp,
            color = TextTertiary,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onRemove)
                .semantics { contentDescription = "계획 지우기" }
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
