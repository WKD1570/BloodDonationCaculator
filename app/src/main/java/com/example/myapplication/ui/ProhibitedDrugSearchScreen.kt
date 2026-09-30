@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.domain.fmt
import com.example.myapplication.model.RestrictedDrug
import java.time.LocalDate

/**
 * Offline search over the bundled 헌혈 금지 약물 list. Picking a drug asks when it was taken and
 * shows the resulting 헌혈 가능일, or a permanent-restriction notice for lifetime bans.
 *
 * The result list is a [LazyColumn] with a capped height because this card is hosted inside a
 * vertically scrolling parent, where an unbounded lazy list would fail to measure.
 */
@Composable
fun ProhibitedDrugSearchScreen(
    modifier: Modifier = Modifier,
    viewModel: ProhibitedDrugSearchViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }

    // Opening the picker is a consequence of selecting a drug, not of the tap itself, so that
    // restoring a selection (rotation, process death) does not pop the dialog again.
    LaunchedEffect(state.selectedDrug) {
        if (state.selectedDrug != null && state.medicationDate == null) showDatePicker = true
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "💊 약물 복용 이력 확인",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "헌혈 제한 약물을 검색하고 복용일을 입력하면 헌혈 가능일을 알려드려요.",
                fontSize = 11.sp,
                color = TextTertiary,
                lineHeight = 16.sp
            )

            state.dataWarning?.let { warning ->
                Spacer(Modifier.height(8.dp))
                Text(warning, fontSize = 11.sp, color = ErrorText, lineHeight = 16.sp)
            }

            Spacer(Modifier.height(14.dp))

            // Bound to a local so the null check below smart-casts; `state` is a delegated
            // property and would need a non-null assertion instead.
            val selected = state.selectedDrug
            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Accent)
                }

                selected == null -> SearchSection(
                    query = state.query,
                    results = state.results,
                    onQueryChange = viewModel::onQueryChange,
                    onDrugClick = viewModel::onDrugSelected
                )

                else -> SelectedDrugSection(
                    drug = selected,
                    medicationDate = state.medicationDate,
                    eligibility = state.eligibility,
                    onChangeDateClick = { showDatePicker = true },
                    onResetClick = {
                        viewModel.clearSelection()
                        viewModel.clearQuery()
                    }
                )
            }
        }
    }

    if (showDatePicker && state.selectedDrug != null) {
        MedicationDateDialog(
            initialDate = state.medicationDate,
            onDismiss = {
                showDatePicker = false
                // Backing out without answering leaves no drug selected, so the user lands back
                // on the search field rather than on a half-filled result card.
                if (state.medicationDate == null) viewModel.clearSelection()
            },
            onConfirm = {
                viewModel.onMedicationDateSelected(it)
                showDatePicker = false
            }
        )
    }
}

@Composable
private fun SearchSection(
    query: String,
    results: List<RestrictedDrug>,
    onQueryChange: (String) -> Unit,
    onDrugClick: (RestrictedDrug) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = {
            Text("약물명을 검색하세요 (예: 아스피린)", fontSize = 12.sp, color = TextTertiary)
        }
    )

    if (query.isBlank()) return

    Spacer(Modifier.height(8.dp))
    if (results.isEmpty()) {
        Text("검색 결과가 없어요", fontSize = 12.sp, color = TextTertiary)
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, InputBorder, RoundedCornerShape(10.dp))
    ) {
        itemsIndexed(
            items = results,
            key = { _, drug -> "${drug.ingredientName}|${drug.representativeBrands}" }
        ) { index, drug ->
            DrugRow(drug, onClick = { onDrugClick(drug) })
            if (index != results.lastIndex) HorizontalDivider(color = DividerColor)
        }
    }
}

@Composable
private fun DrugRow(drug: RestrictedDrug, onClick: () -> Unit) {
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
        Text(
            drug.ingredientName,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        if (drug.representativeBrands.isNotBlank()) {
            // Some ingredients list dozens of brands (피나스테라이드 alone has ~70), which would
            // turn a single result row into a wall of text. Two lines is enough to recognise a
            // match; the full list is not what the user is scanning for here.
            Text(
                drug.representativeBrands,
                fontSize = 11.sp,
                color = TextTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (drug.category.isNotBlank()) {
            Text(drug.category, fontSize = 10.sp, color = TextTertiary)
        }
    }
}

@Composable
private fun SelectedDrugSection(
    drug: RestrictedDrug,
    medicationDate: LocalDate?,
    eligibility: DonationEligibility?,
    onChangeDateClick: () -> Unit,
    onResetClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                drug.ingredientName,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(drug.restrictionText, fontSize = 11.sp, color = TextTertiary, lineHeight = 16.sp)
        }
        TextButton(onClick = onResetClick) {
            Text("다시 검색", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    Spacer(Modifier.height(14.dp))
    Text("복용일", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
    Spacer(Modifier.height(4.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(InputBg)
            .border(1.dp, InputBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onChangeDateClick)
            .padding(horizontal = 12.dp, vertical = 16.dp)
    ) {
        Text(
            medicationDate?.let { fmt(it) } ?: "날짜 선택",
            fontSize = 14.sp,
            color = if (medicationDate != null) TextPrimary else TextTertiary
        )
    }

    when (eligibility) {
        null -> Unit

        is DonationEligibility.EligibleFrom -> {
            Spacer(Modifier.height(10.dp))
            Text(
                "이 날짜부터 헌혈이 가능해요: ${fmt(eligibility.date)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = SuccessColor
            )
            Text(drug.restrictionText, fontSize = 11.sp, color = TextTertiary, lineHeight = 16.sp)
        }

        DonationEligibility.PermanentlyProhibited -> {
            Spacer(Modifier.height(10.dp))
            Text(
                "이 약물은 복용 시 영구적으로 헌혈이 제한돼요.",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = ErrorText
            )
            Text(drug.restrictionText, fontSize = 11.sp, color = TextTertiary, lineHeight = 16.sp)
        }
    }
}

@Composable
internal fun MedicationDateDialog(
    initialDate: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    // Starts on today, which is usually the answer; the field rejects future dates.
    var date by remember { mutableStateOf<LocalDate?>(initialDate ?: LocalDate.now()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("이 약을 언제 복용하셨나요?", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        },
        text = { DateInputField(date = date, onDateChange = { date = it }) },
        confirmButton = {
            TextButton(enabled = date != null, onClick = { date?.let(onConfirm) }) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}
