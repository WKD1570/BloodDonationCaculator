package com.example.myapplication.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.domain.fmt
import com.example.myapplication.model.MedicationRecord
import com.example.myapplication.model.eligibleFrom
import com.example.myapplication.model.isPermanentlyRestricted

/**
 * The saved 약물 복용 이력 on 의료 정보. Entries are written by
 * [ProhibitedDrugSearchScreen] when the user supplies an intake date, and feed the integrated
 * next-eligible-date on the status screen - so deleting one here changes that date.
 */
@Composable
fun MedicationHistoryCard(
    medications: List<MedicationRecord>,
    onDelete: (MedicationRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "🗂️ 약물 복용 이력",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (medications.isEmpty()) {
                    "저장된 복용 이력이 없어요."
                } else {
                    "${medications.size}건 저장됨 · 다음 헌혈 가능일 계산에 반영됩니다."
                },
                fontSize = 11.sp,
                color = TextTertiary,
                lineHeight = 16.sp
            )

            if (medications.isEmpty()) return@Column

            Spacer(Modifier.height(12.dp))
            medications.forEachIndexed { index, record ->
                MedicationHistoryRow(record, onDelete = { onDelete(record) })
                if (index != medications.lastIndex) HorizontalDivider(color = DividerColor)
            }
        }
    }
}

@Composable
private fun MedicationHistoryRow(record: MedicationRecord, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                record.ingredientName,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                "복용일 ${fmt(record.intakeDate)}",
                fontSize = 11.sp,
                color = TextTertiary
            )
            val eligibleFrom = record.eligibleFrom
            if (record.isPermanentlyRestricted || eligibleFrom == null) {
                Text(
                    "영구 헌혈 금지",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ErrorText
                )
            } else {
                Text(
                    "${record.restrictionDays}일 제한 · ${fmt(eligibleFrom)}부터 가능",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
        TextButton(onClick = onDelete) {
            Text("삭제", color = ErrorText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
