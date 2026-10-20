package com.example.myapplication.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.domain.MachineFilter
import com.example.myapplication.domain.TYPE_INFO
import com.example.myapplication.domain.machineSummary
import com.example.myapplication.domain.mapQueryOf
import com.example.myapplication.domain.modelList
import com.example.myapplication.domain.preferredFirst
import com.example.myapplication.domain.splitPreferred
import com.example.myapplication.model.BloodCenter
import com.example.myapplication.model.DonationType

/** List key of the 다른 지역 heading that follows the 자주 헌혈하는 지역's centers. */
internal const val OTHERS_HEADING_KEY = "others-heading"

/**
 * 헌혈의 집 목록: every center from 대한적십자사's list, narrowed by province and machine chips and
 * search. Each row counts the center's 성분헌혈 machines; tapping it opens the models, its phone
 * number, 전화하기 and 지도에서 보기, which hand off to the phone's dialer and map app - the app
 * itself makes no network calls.
 *
 * [preferredProvince] is 마이페이지's 자주 헌혈하는 지역. Its chip comes right after 전체, and under
 * 전체 its centers are listed first under their own heading, then 다른 지역. It's taken as a
 * parameter rather than held in [viewModel], so a change on 마이페이지 shows from the first frame.
 *
 * The list is lazy and scrolls on its own, so the root screen hosts it outside its page scroll.
 */
@Composable
fun BloodCenterListScreen(
    preferredProvince: String?,
    modifier: Modifier = Modifier,
    viewModel: BloodCenterListViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val chips = remember(state.provinces, preferredProvince) { preferredFirst(state.provinces, preferredProvince) }
    val (preferred, others) = remember(state.visible, state.province, preferredProvince) {
        splitPreferred(state.visible, state.province, preferredProvince)
    }

    // Back undoes the narrowing a step at a time - search, then machine, then province - before
    // it leaves the app.
    BackHandler(enabled = state.query.isNotEmpty() || state.machine != null || state.province != null) {
        when {
            state.query.isNotEmpty() -> viewModel.onQueryChange("")
            state.machine != null -> viewModel.onMachineSelect(null)
            else -> viewModel.onProvinceSelect(null)
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Search and chips stay put while the list scrolls underneath.
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
        ) {
            SearchField(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = { focusManager.clearFocus() }
            )
            if (state.provinces.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                ProvinceChips(
                    provinces = chips,
                    total = state.centers.size,
                    selected = state.province,
                    onSelect = {
                        focusManager.clearFocus()
                        viewModel.onProvinceSelect(it)
                    }
                )
            }
            if (state.machines.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                MachineChips(
                    machines = state.machines,
                    selected = state.machine,
                    onSelect = {
                        focusManager.clearFocus()
                        viewModel.onMachineSelect(it)
                    }
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 640.dp)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            fun centerRows(centers: List<BloodCenter>) = items(centers, key = { it.id }) { center ->
                CenterRow(
                    center = center,
                    expanded = center.id == state.expandedId,
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.onCenterClick(center)
                    },
                    onCall = { dial(context, center.phone) },
                    onShowOnMap = { showOnMap(context, center) }
                )
            }

            when {
                state.isLoading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent)
                    }
                }

                state.error != null -> item(key = "error") {
                    Message(state.error.orEmpty(), color = ErrorText)
                }

                state.visible.isEmpty() -> item(key = "empty") {
                    Message("검색 결과가 없어요. 다른 지역·기기를 고르거나 검색어를 바꿔 보세요.")
                }

                else -> {
                    item(key = "count") {
                        Text(
                            "${state.province ?: "전국"} ${state.visible.size}곳" + state.machine?.let { " · ${it.label}" }.orEmpty(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    if (preferred.isNotEmpty()) {
                        item(key = "preferred-heading") { SectionHeading("$preferredProvince (자주 헌혈하는 지역)") }
                        centerRows(preferred)
                        if (others.isNotEmpty()) item(key = OTHERS_HEADING_KEY) { SectionHeading("다른 지역") }
                    }
                    centerRows(others)
                    item(key = "source") {
                        Text(
                            buildString {
                                append("헌혈의 집: 공공데이터포털 「대한적십자사_헌혈의집 정보」")
                                state.asOf?.let { append(" (${it} 기준)") }
                                // The machines come from a donor community's post, credited to its author.
                                if (state.machines.isNotEmpty()) {
                                    append("\n성분헌혈 기기: 헌혈 마이너 갤러리 헤마토필리아님의 「헌혈의집 성분채혈기기 현황」")
                                    state.machinesAsOf?.let { append(" (${it} 기준)") }
                                }
                                append("\n운영 시간·휴무일과 성분헌혈 기기는 바뀔 수 있으니 방문 전에 전화로 확인해 주세요.")
                            },
                            fontSize = 10.sp,
                            color = TextTertiary,
                            lineHeight = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        placeholder = { Text("센터 이름·지역 검색 (예: 강남, 서면)", fontSize = 13.sp, color = TextTertiary) },
        leadingIcon = { Text("🔍", fontSize = 15.sp) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                Text(
                    "✕",
                    fontSize = 14.sp,
                    color = TextTertiary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onQueryChange("") }
                        .padding(10.dp)
                )
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = CardBg,
            unfocusedContainerColor = CardBg,
            focusedBorderColor = Accent,
            unfocusedBorderColor = CardBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = Accent
        )
    )
}

@Composable
private fun ProvinceChips(
    provinces: List<Pair<String, Int>>,
    total: Int,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ChoiceChip("전체", active = selected == null, onClick = { onSelect(null) }, count = total)
        provinces.forEach { (province, count) ->
            ChoiceChip(province, active = selected == province, onClick = { onSelect(province) }, count = count)
        }
    }
}

/** 혈소판 가능 and each model, every chip counting the centers that have it. */
@Composable
private fun MachineChips(
    machines: List<Pair<MachineFilter, Int>>,
    selected: MachineFilter?,
    onSelect: (MachineFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        machines.forEach { (machine, count) ->
            ChoiceChip(machine.label, active = selected == machine, onClick = { onSelect(machine) }, count = count)
        }
    }
}

/**
 * A pill chip for the province and machine rows here and 마이페이지's [PreferredProvinceCard], which
 * has no [count] to show.
 */
@Composable
internal fun ChoiceChip(label: String, active: Boolean, onClick: () -> Unit, count: Int? = null) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (active) Accent else CardBg)
            .border(1.dp, if (active) Accent else CardBorder, shape)
            .selectable(selected = active, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) Color.White else TextPrimary
        )
        if (count != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                count.toString(),
                fontSize = 11.sp,
                color = if (active) Color.White.copy(alpha = 0.8f) else TextTertiary
            )
        }
    }
}

@Composable
private fun CenterRow(
    center: BloodCenter,
    expanded: Boolean,
    onClick: () -> Unit,
    onCall: () -> Unit,
    onShowOnMap: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, if (expanded) Accent else CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .animateContentSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        center.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    center.placeLabel?.let { label ->
                        Spacer(Modifier.width(6.dp))
                        Text(
                            label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(InputBgHover)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(if (expanded) "▴" else "▾", fontSize = 12.sp, color = TextTertiary)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                center.address,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 17.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(machineSummary(center.machines), fontSize = 11.sp, color = TextTertiary)

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                if (center.phone.isNotBlank()) DetailLine("☎", center.phone)
                if (center.bloodBankLabel.isNotBlank()) DetailLine("🏥", center.bloodBankLabel)
                center.machines?.takeIf { it.plasma.isNotEmpty() || it.platelet.isNotEmpty() }?.let { machines ->
                    MachineLine(DonationType.PLASMA, "혈장", machines.plasma)
                    MachineLine(DonationType.PLATELET, "혈소판", machines.platelet)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (center.phone.isNotBlank()) {
                        OutlinedButton(
                            onClick = onCall,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, InputBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("전화하기", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Button(
                        onClick = onShowOnMap,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("지도에서 보기", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(icon: String, text: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 12.sp, modifier = Modifier.width(22.dp))
        Text(text, fontSize = 13.sp, color = TextPrimary)
    }
}

/** One kind's models in an open row, its dot in that 헌혈's color from 현황. */
@Composable
private fun MachineLine(type: DonationType, kind: String, models: Map<String, Int>) {
    Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(22.dp)) {
            Box(
                Modifier
                    .padding(start = 2.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(TYPE_INFO.getValue(type).color)
            )
        }
        Text(kind, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.width(48.dp))
        Text(modelList(models), fontSize = 13.sp, color = if (models.isEmpty()) TextTertiary else TextPrimary)
    }
}

/** Heads one part of 전체 when it's split by 자주 헌혈하는 지역: the label, then a hairline to the edge. */
@Composable
private fun SectionHeading(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(CardBorder)
        )
    }
}

@Composable
private fun Message(text: String, color: Color = TextTertiary) {
    Text(
        text,
        fontSize = 13.sp,
        color = color,
        lineHeight = 18.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 16.dp)
    )
}

private fun dial(context: Context, phone: String) {
    context.startOrToast(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.filter { it.isDigit() || it == '-' }}")))
}

/**
 * Kakao Map's public search link: opens the Kakao Map app when it's installed and the mobile web
 * map otherwise, so it works on any phone, and 길찾기 is one tap away from there.
 */
private fun showOnMap(context: Context, center: BloodCenter) {
    val url = "https://map.kakao.com/link/search/${Uri.encode(mapQueryOf(center))}"
    context.startOrToast(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private fun Context.startOrToast(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "열 수 있는 앱이 없어요.", Toast.LENGTH_SHORT).show()
    }
}
