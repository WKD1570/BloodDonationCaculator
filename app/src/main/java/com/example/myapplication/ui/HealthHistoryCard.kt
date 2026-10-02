@file:OptIn(ExperimentalLayoutApi::class)

package com.example.myapplication.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.domain.HealthRestriction
import com.example.myapplication.domain.HistorySource
import com.example.myapplication.domain.diseaseRestriction
import com.example.myapplication.domain.fmt
import com.example.myapplication.domain.healthRestrictions
import com.example.myapplication.domain.isDomesticStay
import com.example.myapplication.domain.label
import com.example.myapplication.domain.overseasStayRegion
import com.example.myapplication.domain.searchStayRegions
import com.example.myapplication.domain.stayRegions
import com.example.myapplication.model.DeferralPeriod
import com.example.myapplication.model.DiseaseRecord
import com.example.myapplication.model.DiseaseRule
import com.example.myapplication.model.DonationType
import com.example.myapplication.model.InfectionRules
import com.example.myapplication.model.REGULAR_DONATION_TYPES
import com.example.myapplication.model.StayRecord
import com.example.myapplication.model.StayRegion
import com.example.myapplication.model.StayRegionKind
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private enum class HealthDialog { DISEASE, STAY }

/** What an unsaved stay is identified by in the dialog's preview, so it can't collide with a saved row. */
private const val DRAFT_STAY_ID = -1L

private val TYPE_SHORT_LABELS = mapOf(
    DonationType.WHOLE_BLOOD to "전혈",
    DonationType.PLATELET to "혈소판",
    DonationType.PLASMA to "혈장"
)

/**
 * The saved 감염병 이력 and 체류 이력 on 의료 정보, with what each one restricts. Entries feed the
 * integrated next-eligible-date on the status screen, so adding or deleting one here moves it.
 */
@Composable
internal fun HealthHistoryCard(
    health: HealthHistoryState,
    today: LocalDate,
    onSaveDisease: (DiseaseRecord) -> Unit,
    onDeleteDisease: (DiseaseRecord) -> Unit,
    onSaveStay: (StayRecord) -> Unit,
    onDeleteStay: (StayRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    var dialog by remember { mutableStateOf<HealthDialog?>(null) }
    val rules = health.rules
    val regions = remember(rules) { rules?.let(::stayRegions).orEmpty() }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("🦠 감염병·체류 이력", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "앓았던 감염병이나 해외 방문·말라리아·vCJD 위험지역 체류를 입력하면 헌혈 제한기간과 가능한 헌혈 종류를 " +
                    "계산해 다음 헌혈 가능일에 반영해요.",
                fontSize = 11.sp,
                color = TextTertiary,
                lineHeight = 16.sp
            )

            health.rulesWarning?.let { warning ->
                Spacer(Modifier.height(8.dp))
                Text(warning, fontSize = 11.sp, color = ErrorText, lineHeight = 16.sp)
            }
            if (rules == null) {
                if (health.rulesWarning == null) {
                    Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent)
                    }
                }
                return@Column
            }

            Spacer(Modifier.height(14.dp))
            HistorySectionHeader("감염병 이력", onAdd = { dialog = HealthDialog.DISEASE })
            if (health.diseases.isEmpty()) EmptyHistoryText("저장된 감염병 이력이 없어요.")
            health.diseases.forEachIndexed { index, record ->
                HistoryRow(
                    title = record.diseaseName,
                    subtitle = "치료 종료일 ${fmt(record.treatmentEndDate)}",
                    known = rules.diseases.any { it.name == record.diseaseName },
                    restrictions = health.restrictions.filter { HistorySource.Disease(record.id) in it.sources },
                    today = today,
                    onDelete = { onDeleteDisease(record) }
                )
                if (index != health.diseases.lastIndex) HorizontalDivider(color = DividerColor)
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = CardBorder)
            Spacer(Modifier.height(12.dp))

            HistorySectionHeader("체류 이력", onAdd = { dialog = HealthDialog.STAY })
            if (health.stays.isEmpty()) EmptyHistoryText("저장된 체류 이력이 없어요.")
            health.stays.forEachIndexed { index, record ->
                HistoryRow(
                    title = record.regionName,
                    subtitle = stayPeriodText(record),
                    known = regions.any { it.name == record.regionName } || !isDomesticStay(record.regionName, rules),
                    restrictions = health.restrictions.filter { HistorySource.Stay(record.id) in it.sources },
                    today = today,
                    onDelete = { onDeleteStay(record) }
                )
                if (index != health.stays.lastIndex) HorizontalDivider(color = DividerColor)
            }
        }
    }

    if (rules != null) {
        when (dialog) {
            HealthDialog.DISEASE -> DiseaseHistoryDialog(
                rules = rules,
                today = today,
                onDismiss = { dialog = null },
                onSave = {
                    onSaveDisease(it)
                    dialog = null
                }
            )

            HealthDialog.STAY -> StayHistoryDialog(
                rules = rules,
                regions = regions,
                savedStays = health.stays,
                today = today,
                onDismiss = { dialog = null },
                onSave = {
                    onSaveStay(it)
                    dialog = null
                }
            )

            null -> Unit
        }
    }
}

internal fun stayPeriodText(stay: StayRecord): String {
    val nights = ChronoUnit.DAYS.between(stay.startDate, stay.endDate)
    return "${fmt(stay.startDate)} ~ ${fmt(stay.endDate)} · ${nights}박 ${nights + 1}일"
}

@Composable
private fun HistorySectionHeader(title: String, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        TextButton(onClick = onAdd) {
            Text("+ 추가", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyHistoryText(text: String) {
    Text(text, fontSize = 11.sp, color = TextTertiary, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun HistoryRow(
    title: String,
    subtitle: String,
    known: Boolean,
    restrictions: List<HealthRestriction>,
    today: LocalDate,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(subtitle, fontSize = 11.sp, color = TextTertiary)
            when {
                !known -> Text("현재 기준에 없는 항목이라 반영하지 않아요", fontSize = 11.sp, color = TextTertiary)
                restrictions.isEmpty() -> Text("헌혈 제한 없음", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SuccessColor)
                else -> restrictions.forEach { RestrictionOutcome(it, today) }
            }
        }
        TextButton(onClick = onDelete) {
            Text("삭제", color = ErrorText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** When [restriction] ends, and which donation types it leaves open. */
@Composable
internal fun RestrictionOutcome(restriction: HealthRestriction, today: LocalDate) {
    val eligibleFrom = restriction.eligibleFrom
    when {
        eligibleFrom == null -> Text(
            "영구 헌혈 금지 · ${restriction.title}",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = ErrorText
        )

        restriction.isActive(today) -> Text(
            "${restriction.periodLabel} · ${fmt(eligibleFrom)}부터 가능",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary
        )

        else -> Text("제한 종료 · ${fmt(eligibleFrom)}부터 가능", fontSize = 11.sp, color = SuccessColor)
    }
    if (restriction.isActive(today)) {
        Spacer(Modifier.height(4.dp))
        DonationTypeChips(restriction)
    }
    Text(restriction.detail, fontSize = 10.sp, color = TextTertiary, lineHeight = 14.sp)
}

/** One chip per regular donation type: 불가 for the ones [restriction] blocks, 가능 for the rest. */
@Composable
private fun DonationTypeChips(restriction: HealthRestriction) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        REGULAR_DONATION_TYPES.forEach { type ->
            val blocked = restriction.blocks(type)
            TypeChip(
                text = "${TYPE_SHORT_LABELS.getValue(type)} ${if (blocked) "불가" else "가능"}",
                color = if (blocked) ErrorText else SuccessColor
            )
        }
    }
}

@Composable
private fun TypeChip(text: String, color: Color) {
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/**
 * 현황's summary of the 감염병·체류 restrictions still in force: what each blocks and until when.
 * Shows nothing when none are.
 */
@Composable
internal fun HealthRestrictionsCard(restrictions: List<HealthRestriction>, today: LocalDate) {
    val active = restrictions.filter { it.isActive(today) }.sortedBy { it.eligibleFrom ?: LocalDate.MAX }
    if (active.isEmpty()) return

    Text("🦠 감염병·체류 제한", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    Spacer(Modifier.height(10.dp))
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            active.forEachIndexed { index, restriction ->
                Column(Modifier.padding(vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            restriction.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        val eligibleFrom = restriction.eligibleFrom
                        Text(
                            if (eligibleFrom == null) "영구 제한" else "${fmt(eligibleFrom)}부터",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (eligibleFrom == null) ErrorText else TextPrimary
                        )
                    }
                    Text(restriction.detail, fontSize = 11.sp, color = TextTertiary, lineHeight = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    DonationTypeChips(restriction)
                }
                if (index != active.lastIndex) HorizontalDivider(color = DividerColor)
            }
        }
    }
}

@Composable
private fun SelectableRow(title: String, subtitle: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val bg by rememberHoverColor(interactionSource, CardBg, InputBgHover)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Text(subtitle, fontSize = 11.sp, color = TextTertiary)
    }
}

/** A capped-height list, since it's shown inside a dialog that can't grow past the screen. */
@Composable
private fun <T> PickList(items: List<T>, key: (T) -> String, row: @Composable (T) -> Unit) {
    if (items.isEmpty()) {
        Text("검색 결과가 없어요", fontSize = 12.sp, color = TextTertiary, modifier = Modifier.padding(top = 8.dp))
        return
    }
    Spacer(Modifier.height(8.dp))
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, InputBorder, RoundedCornerShape(10.dp))
    ) {
        itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
            row(item)
            if (index != items.lastIndex) HorizontalDivider(color = DividerColor)
        }
    }
}

@Composable
private fun SelectedHeader(title: String, subtitle: String, onChange: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(subtitle, fontSize = 11.sp, color = TextTertiary, lineHeight = 16.sp)
        }
        TextButton(onClick = onChange) {
            Text("다시 선택", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DialogFieldLabel(text: String) {
    Spacer(Modifier.height(12.dp))
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
    Spacer(Modifier.height(4.dp))
}

/** What saving would mean, worked out before the user commits to it. */
@Composable
private fun RestrictionPreview(restrictions: List<HealthRestriction>, today: LocalDate) {
    Spacer(Modifier.height(14.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(InputBg)
            .padding(12.dp)
    ) {
        Text("계산 결과", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        if (restrictions.isEmpty()) {
            Text("헌혈 제한 없음", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SuccessColor)
        } else {
            restrictions.forEach { RestrictionOutcome(it, today) }
        }
    }
}

private val DiseaseRule.periodDescription: String
    get() = when (deferral) {
        DeferralPeriod.Permanent -> "영구 헌혈 금지"
        DeferralPeriod.UntilTreatmentEnds -> "치료 종료 시까지 헌혈 금지"
        is DeferralPeriod.After -> "치료 종료 후 $periodText 헌혈 금지"
    }

@Composable
internal fun DiseaseHistoryDialog(
    rules: InfectionRules,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (DiseaseRecord) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<DiseaseRule?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("감염병 이력 추가", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary) },
        text = {
            val rule = selected
            if (rule == null) {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("감염병명을 검색하세요 (예: 말라리아)", fontSize = 12.sp, color = TextTertiary) }
                    )
                    val matches = rules.diseases.filter { it.name.contains(query.trim(), ignoreCase = true) }
                    PickList(matches, key = { it.name }) { disease ->
                        SelectableRow(disease.name, disease.periodDescription, onClick = { selected = disease })
                    }
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SelectedHeader(rule.name, rule.periodDescription, onChange = { selected = null })
                    DialogFieldLabel("치료 종료일")
                    DateInputField(date = endDate, onDateChange = { endDate = it })
                    endDate?.let { end ->
                        RestrictionPreview(
                            listOfNotNull(diseaseRestriction(DiseaseRecord(diseaseName = rule.name, treatmentEndDate = end), rules)),
                            today
                        )
                    }
                }
            }
        },
        confirmButton = {
            val rule = selected
            val end = endDate
            TextButton(
                enabled = rule != null && end != null,
                onClick = { if (rule != null && end != null) onSave(DiseaseRecord(diseaseName = rule.name, treatmentEndDate = end)) }
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun TabButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Accent else InputBg)
            .border(1.dp, if (selected) Accent else InputBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else TextSecondary
        )
    }
}

@Composable
private fun CheckboxRow(checked: Boolean, text: String, hint: String?, onCheckedChange: (Boolean) -> Unit) {
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(Modifier.weight(1f)) {
            Text(text, fontSize = 12.sp, color = TextPrimary, lineHeight = 16.sp)
            hint?.let { Text(it, fontSize = 10.sp, color = TextTertiary, lineHeight = 14.sp) }
        }
    }
}

@Composable
internal fun StayHistoryDialog(
    rules: InfectionRules,
    regions: List<StayRegion>,
    savedStays: List<StayRecord>,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (StayRecord) -> Unit
) {
    var domestic by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<StayRegion?>(null) }
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var offshoreOnly by remember { mutableStateOf(false) }
    var visitedRiskArea by remember { mutableStateOf(true) }

    val region = selected
    val start = startDate
    val end = endDate
    val datesInOrder = start != null && end != null && !end.isBefore(start)
    val draft = if (region != null && start != null && end != null && datesInOrder) {
        StayRecord(
            id = DRAFT_STAY_ID,
            regionName = region.name,
            startDate = start,
            endDate = end,
            offshoreOnly = offshoreOnly && region.kind == StayRegionKind.DOMESTIC_MALARIA,
            visitedRiskArea = visitedRiskArea || !region.partialRegion
        )
    } else {
        null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("체류 이력 추가", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary) },
        text = {
            if (region == null) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TabButton("국외", selected = !domestic, modifier = Modifier.weight(1f)) {
                            domestic = false
                            query = ""
                        }
                        TabButton("국내", selected = domestic, modifier = Modifier.weight(1f)) {
                            domestic = true
                            query = ""
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text(
                                if (domestic) "지역을 검색하세요 (예: 파주)" else "국가를 검색하세요 (예: 태국, 영국)",
                                fontSize = 12.sp,
                                color = TextTertiary
                            )
                        }
                    )
                    val listed = searchStayRegions(query, regions.filter { it.isDomestic == domestic })
                    // A country no rule lists can still be entered by name: 해외 방문 itself restricts.
                    val typed = if (domestic || regions.any { it.name.equals(query.trim(), ignoreCase = true) }) {
                        null
                    } else {
                        overseasStayRegion(query, rules)
                    }
                    val matches = listed + listOfNotNull(typed)
                    PickList(matches, key = { it.name }) { candidate ->
                        SelectableRow(candidate.name, "${candidate.group} · ${candidate.detail}", onClick = { selected = candidate })
                    }
                    if (!domestic) {
                        Text(
                            "목록에 없는 국가도 이름을 입력해 추가할 수 있어요. 대한민국 외 모든 국가는 " +
                                "귀국 후 ${rules.overseasTravel.restriction.deferral.label()}간 헌혈이 제한돼요.",
                            fontSize = 10.sp,
                            color = TextTertiary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SelectedHeader(region.name, "${region.group} · ${region.detail}", onChange = { selected = null })
                    DialogFieldLabel("체류 시작일")
                    DateInputField(date = startDate, onDateChange = { startDate = it })
                    DialogFieldLabel("체류 종료일 (떠난 날)")
                    DateInputField(date = endDate, onDateChange = { endDate = it })
                    if (start != null && end != null && end.isBefore(start)) {
                        ErrorHint("종료일이 시작일보다 빨라요")
                    }

                    if (region.kind == StayRegionKind.DOMESTIC_MALARIA) {
                        CheckboxRow(
                            checked = offshoreOnly,
                            text = "${rules.domesticMalaria.offshoreConditionText}에서만 숙박했어요",
                            hint = "선박 근무 등. 육지나 연안에서 숙박했다면 체크하지 마세요.",
                            onCheckedChange = { offshoreOnly = it }
                        )
                    }
                    if (region.partialRegion) {
                        CheckboxRow(
                            checked = visitedRiskArea,
                            text = "말라리아 위험지역을 방문했어요",
                            hint = "이 국가는 일부 지역만 위험지역이에요. 확실하지 않으면 체크해 두세요.",
                            onCheckedChange = { visitedRiskArea = it }
                        )
                    }

                    draft?.let {
                        // Evaluated together with the saved stays: vCJD time adds up across trips.
                        val preview = healthRestrictions(emptyList(), savedStays + it, rules)
                            .filter { restriction -> HistorySource.Stay(DRAFT_STAY_ID) in restriction.sources }
                        RestrictionPreview(preview, today)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = draft != null,
                onClick = { draft?.let { onSave(it.copy(id = 0)) } }
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}
