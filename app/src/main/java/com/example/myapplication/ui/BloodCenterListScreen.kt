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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.example.myapplication.domain.sectionPlaces
import com.example.myapplication.model.BloodCenter
import com.example.myapplication.model.DonationPlaceKind
import com.example.myapplication.model.DonationType

/** List key of the 다른 지역 heading that follows the 자주 헌혈하는 지역's centers. */
internal const val OTHERS_HEADING_KEY = "others-heading"

/** List key of the 즐겨찾기 heading over the starred places. */
internal const val FAVORITES_HEADING_KEY = "favorites-heading"

/**
 * 헌혈 장소: a 헌혈의 집 / 헌혈카페 toggle over two lists - every 헌혈의 집 from 대한적십자사's list,
 * or every 헌혈카페 from 한마음혈액원's site - each narrowed by province and machine chips and search.
 * Each row counts the place's 성분헌혈 machines; tapping it opens the models, its phone number (and a
 * 헌혈카페's hours and parking), 전화하기 and 지도에서 보기, which hand off to the phone's dialer and
 * map app - the app itself makes no network calls.
 *
 * The ☆ on a row stars the place. Starred places that match the chips and search come first, under
 * 즐겨찾기, in either list and whatever province is picked.
 *
 * [preferredProvince] is 마이페이지's 자주 헌혈하는 지역. Its chip comes right after 전체, and under
 * 전체 its centers are listed next under their own heading, then 다른 지역. It's taken as a
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
    val sections = remember(state.visible, state.province, preferredProvince, state.favorites) {
        sectionPlaces(state.visible, state.province, preferredProvince, state.favorites)
    }
    val listState = rememberLazyListState()
    // The other kind's list starts from its top, not wherever this one was scrolled to.
    LaunchedEffect(state.kind) { listState.scrollToItem(0) }

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
        // The toggle, search and chips stay put while the list scrolls underneath.
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
        ) {
            KindToggle(
                counts = state.kindCounts,
                selected = state.kind,
                onSelect = {
                    focusManager.clearFocus()
                    viewModel.onKindSelect(it)
                }
            )
            Spacer(Modifier.height(10.dp))
            SearchField(
                query = state.query,
                placeholder = when (state.kind) {
                    DonationPlaceKind.CENTER -> "센터 이름·지역 검색 (예: 강남, 서면)"
                    DonationPlaceKind.CAFE -> "카페 이름·지역 검색 (예: 홍대, 수원)"
                },
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
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            fun centerRows(centers: List<BloodCenter>) = items(centers, key = { it.id }) { center ->
                CenterRow(
                    center = center,
                    expanded = center.id == state.expandedId,
                    favorite = center.id in state.favorites,
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.onCenterClick(center)
                    },
                    onFavoriteToggle = { viewModel.onFavoriteToggle(center) },
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
                    if (sections.favorites.isNotEmpty()) {
                        item(key = FAVORITES_HEADING_KEY) { SectionHeading("★ 즐겨찾기") }
                        centerRows(sections.favorites)
                    }
                    if (sections.preferred.isNotEmpty()) {
                        item(key = "preferred-heading") { SectionHeading("$preferredProvince (자주 헌혈하는 지역)") }
                        centerRows(sections.preferred)
                        if (sections.others.isNotEmpty()) item(key = OTHERS_HEADING_KEY) { SectionHeading("다른 지역") }
                    } else if (sections.favorites.isNotEmpty() && sections.others.isNotEmpty()) {
                        item(key = "rest-heading") { SectionHeading("다른 장소") }
                    }
                    centerRows(sections.others)
                    item(key = "source") {
                        Text(
                            when (state.kind) {
                                DonationPlaceKind.CENTER -> buildString {
                                    append("헌혈의 집: 공공데이터포털 「대한적십자사_헌혈의집 정보」")
                                    state.asOf?.let { append(" (${it} 기준)") }
                                    // The machines come from a donor community's post, credited to its author.
                                    if (state.machines.isNotEmpty()) {
                                        append("\n성분헌혈 기기: 헌혈 마이너 갤러리 헤마토필리아님의 「헌혈의집 성분채혈기기 현황」")
                                        state.machinesAsOf?.let { append(" (${it} 기준)") }
                                    }
                                    append("\n운영 시간·휴무일과 성분헌혈 기기는 바뀔 수 있으니 방문 전에 전화로 확인해 주세요.")
                                }
                                // The cafes' pages were last edited years apart, so the date is when they were read.
                                DonationPlaceKind.CAFE -> buildString {
                                    append("헌혈카페: 한마음혈액원 홈페이지 「헌혈카페 찾기」")
                                    state.asOf?.let { append(" (${it} 수집)") }
                                    append("\n헌혈카페는 혈소판 채혈 기기만 공개해 혈장 기기는 정보 없음으로 표시해요.")
                                    append("\n운영 시간·주차와 기기는 바뀔 수 있으니 방문 전에 전화로 확인해 주세요.")
                                }
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

/**
 * 헌혈의 집 | 헌혈카페, each with how many places it has: a two-way switch above everything else,
 * since the search and chips below apply to whichever list it picks.
 */
@Composable
private fun KindToggle(
    counts: Map<DonationPlaceKind, Int>,
    selected: DonationPlaceKind,
    onSelect: (DonationPlaceKind) -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardBg)
            .border(1.dp, CardBorder, shape)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DonationPlaceKind.entries.forEach { kind ->
            val active = kind == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) Accent else Color.Transparent)
                    .selectable(selected = active, onClick = { onSelect(kind) }, role = Role.Tab)
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    kind.label,
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) Color.White else TextPrimary
                )
                counts[kind]?.let { count ->
                    Spacer(Modifier.width(5.dp))
                    Text(
                        count.toString(),
                        fontSize = 12.sp,
                        color = if (active) Color.White.copy(alpha = 0.8f) else TextTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, placeholder: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        placeholder = { Text(placeholder, fontSize = 13.sp, color = TextTertiary) },
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
    favorite: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
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
                // 10 rather than 12 top and bottom: the star's touch target makes the first line taller.
                .padding(horizontal = 16.dp, vertical = 10.dp)
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
                Spacer(Modifier.width(4.dp))
                FavoriteStar(name = center.name, favorite = favorite, onToggle = onFavoriteToggle)
                Spacer(Modifier.width(2.dp))
                Text(if (expanded) "▴" else "▾", fontSize = 12.sp, color = TextTertiary)
            }
            Spacer(Modifier.height(1.dp))
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
                center.hours?.let { DetailLine("🕘", it) }
                center.parking?.let { DetailLine("🅿️", it) }
                // Machine lines unless it has none at all; 헌혈카페 get a 혈장 line of 정보 없음.
                center.machines?.takeIf { it.plasma == null || it.plasma.isNotEmpty() || it.platelet.isNotEmpty() }?.let { machines ->
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

/**
 * ★ or ☆ beside a place's name: a tap stars it without opening the row. Read out as a checkbox
 * named after the place, so a screen reader says what's being starred.
 */
@Composable
private fun FavoriteStar(name: String, favorite: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .toggleable(value = favorite, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = "$name 즐겨찾기" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (favorite) "★" else "☆",
            fontSize = 18.sp,
            color = if (favorite) FavoriteColor else TextTertiary,
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
}

/** Amber, the color people expect of a filled star; legible on both the light and dark card. */
private val FavoriteColor = Color(0xFFF59E0B)

/** An icon and its text, the icon level with the first line when the text runs to several (hours). */
@Composable
private fun DetailLine(icon: String, text: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Text(icon, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.width(22.dp))
        Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = TextPrimary)
    }
}

/** One kind's models in an open row, its dot in that 헌혈's color from 현황. Null [models] reads 정보 없음. */
@Composable
private fun MachineLine(type: DonationType, kind: String, models: Map<String, Int>?) {
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
        Text(modelList(models), fontSize = 13.sp, color = if (models.isNullOrEmpty()) TextTertiary else TextPrimary)
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
