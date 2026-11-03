@file:OptIn(ExperimentalLayoutApi::class)

package com.example.myapplication.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.domain.PROVINCE_ORDER

/**
 * 자주 헌혈하는 지역 on 마이페이지: one province, whose 헌혈의 집 and 헌혈카페 the 헌혈 장소 tab lists
 * first under 전체 and whose chip it moves up front. Tapping the picked one again clears it.
 */
@Composable
internal fun PreferredProvinceCard(
    selected: String?,
    onSelect: (String?) -> Unit,
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
            Text("📍 자주 헌혈하는 지역", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                if (selected == null) {
                    "고른 지역의 헌혈의 집·헌혈카페를 헌혈 장소 탭에서 먼저 보여드려요."
                } else {
                    "헌혈 장소 탭에서 $selected 지역을 먼저 보여드려요.\n한 번 더 누르면 해제돼요."
                },
                fontSize = 11.sp,
                color = TextTertiary,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PROVINCE_ORDER.forEach { province ->
                    ChoiceChip(
                        label = province,
                        active = province == selected,
                        onClick = { onSelect(if (province == selected) null else province) }
                    )
                }
            }
        }
    }
}
