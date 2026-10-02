@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.myapplication.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myapplication.data.certificatePhotoUriFor
import com.example.myapplication.data.createCertificatePhotoFile
import com.example.myapplication.data.GUIDE_FRAME_ASPECT_RATIO
import com.example.myapplication.data.GUIDE_FRAME_WIDTH_FRACTION
import com.example.myapplication.data.recognizeCertificateInfo
import com.example.myapplication.domain.ANNUAL_LIMIT_ML
import com.example.myapplication.domain.BloodDonationInfo
import com.example.myapplication.domain.IntegratedNextResult
import com.example.myapplication.domain.Reason
import com.example.myapplication.domain.TYPE_INFO
import com.example.myapplication.domain.WHOLE_BLOOD_DIAGNOSTIC_DRAW_ML
import com.example.myapplication.domain.WHOLE_BLOOD_STATED_VOLUMES_ML
import com.example.myapplication.domain.donationTypeEnum
import com.example.myapplication.domain.drawnVolumeMl
import com.example.myapplication.domain.earliestEligibleDate
import com.example.myapplication.domain.checkPhysicalEligibility
import com.example.myapplication.domain.fmt
import com.example.myapplication.domain.fmtShort
import com.example.myapplication.domain.statusStyle
import com.example.myapplication.domain.typeSubtitle
import com.example.myapplication.domain.volumeMl
import com.example.myapplication.domain.wholeBloodStatedVolumeMl
import com.example.myapplication.domain.withinAnnualWindow
import com.example.myapplication.model.DonationRecord
import com.example.myapplication.model.DonationType
import com.example.myapplication.model.DonorProfile
import com.example.myapplication.model.OTHER_DONATION_TYPES
import com.example.myapplication.model.REGULAR_DONATION_TYPES
import com.example.myapplication.model.MedicationRecord
import com.example.myapplication.model.Sex
import com.example.myapplication.ui.theme.LocalDarkTheme
import com.example.myapplication.model.currentAge
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

private enum class Screen(val label: String, val icon: String) {
    STATUS("현황", "🩸"),
    INPUT("헌혈 입력", "📝"),
    MYPAGE("마이페이지", "🧍")
}

private sealed interface RecordFormMode {
    /** [types] are the ones the form offers: the regular three from 헌혈 기록, or the 기타 two. */
    data class Add(val types: List<DonationType>) : RecordFormMode
    data class Edit(val record: DonationRecord) : RecordFormMode
}

// Design tokens: neutral slate background/text with an indigo accent, pastel semantic status
// colors. Each token is a @Composable property so it can switch between the light and dark slate
// scale, while still being usable as a bare identifier everywhere below (Compose resolves the
// getter at each call site since all call sites are themselves composable). They read
// LocalDarkTheme rather than isSystemInDarkTheme() so the in-app theme switch overrides the
// device setting.
internal val PageBg: Color
    @Composable get() = if (LocalDarkTheme.current) Color(0xFF0B1220) else Color(0xFFF8FAFC)
internal val CardBg: Color
    @Composable get() = if (LocalDarkTheme.current) Color(0xFF1E293B) else Color(0xFFFFFFFF)
internal val CardBorder: Color
    @Composable get() = if (LocalDarkTheme.current) Color(0xFF334155) else Color(0xFFE2E8F0)
internal val TextPrimary: Color
    @Composable get() = if (LocalDarkTheme.current) Color(0xFFF8FAFC) else Color(0xFF0F172A)
internal val TextSecondary: Color
    @Composable get() = if (LocalDarkTheme.current) Color(0xFF94A3B8) else Color(0xFF64748B)
internal val TextTertiary: Color
    @Composable get() = if (LocalDarkTheme.current) Color(0xFF64748B) else Color(0xFF94A3B8)
internal val InputBg: Color
    @Composable get() = if (LocalDarkTheme.current) Color(0xFF0F172A) else Color(0xFFF8FAFC)
internal val InputBgHover: Color
    @Composable get() = if (LocalDarkTheme.current) Color(0xFF334155) else Color(0xFFF1F5F9)
internal val InputBorder: Color
    @Composable get() = if (LocalDarkTheme.current) Color(0xFF334155) else Color(0xFFE2E8F0)
internal val DividerColor: Color
    @Composable get() = if (LocalDarkTheme.current) Color(0xFF334155) else Color(0xFFF1F5F9)
internal val Accent: Color
    @Composable get() = if (LocalDarkTheme.current) Color(0xFF6366F1) else Color(0xFF4F46E5)
internal val AccentHover: Color
    @Composable get() = if (LocalDarkTheme.current) Color(0xFF818CF8) else Color(0xFF4338CA)
internal val ErrorText: Color
    @Composable get() = if (LocalDarkTheme.current) Color(0xFFF43F5E) else Color(0xFFE11D48)
internal val SuccessColor: Color
    @Composable get() = if (LocalDarkTheme.current) Color(0xFF10B981) else Color(0xFF059669)
internal val CalendarDayText: Color
    @Composable get() = if (LocalDarkTheme.current) Color(0xFFCBD5E1) else Color(0xFF334155)
internal val ProgressTrack: Color
    @Composable get() = if (LocalDarkTheme.current) Color(0xFF334155) else Color(0xFFE2E8F0)
internal val CardShape = RoundedCornerShape(20.dp)

private val CUSTOM_CENTER_OPTION = "직접 입력"

private val BLOOD_CENTERS = listOf(
    "서울남부혈액원",
    "서울북부혈액원",
    "인천혈액원",
    "경기혈액원",
    "강원혈액원",
    "대전세종충남혈액원",
    "충북혈액원",
    "광주전남혈액원",
    "전북혈액원",
    "대구경북혈액원",
    "부산혈액원",
    "울산혈액원",
    "경남혈액원",
    "제주혈액원"
)

@Composable
internal fun rememberHoverColor(interactionSource: MutableInteractionSource, base: Color, hover: Color): State<Color> {
    val isHovered by interactionSource.collectIsHoveredAsState()
    return animateColorAsState(if (isHovered) hover else base, animationSpec = tween(200), label = "hoverColor")
}

@Composable
fun BloodDonationCalculatorScreen(
    onToggleTheme: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DonationViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val profile = state.profile
    val records = state.records
    val today = state.today

    var selectedScreen by remember { mutableStateOf(Screen.STATUS) }
    var formMode by remember { mutableStateOf<RecordFormMode?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = PageBg,
        topBar = { AppTopBar(onToggleTheme = onToggleTheme) },
        bottomBar = { BottomNavBar(selectedScreen, onSelect = { selectedScreen = it }) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                    when (selectedScreen) {
                        Screen.STATUS -> StatusScreen(
                            records = records,
                            medications = state.medications,
                            today = today,
                            nextByType = state.nextByType
                        )
                        Screen.INPUT -> InputScreen(
                            records = records,
                            onAddClick = { types -> formMode = RecordFormMode.Add(types) },
                            onRecordClick = { record -> formMode = RecordFormMode.Edit(record) }
                        )
                        Screen.MYPAGE -> MyPageScreen(
                            profile = profile,
                            medications = state.medications,
                            onProfileChange = viewModel::updateProfile,
                            onDeleteMedication = viewModel::deleteMedication
                        )
                    }
                }
            }
        }
    }

    formMode?.let { mode ->
        val editing = (mode as? RecordFormMode.Edit)?.record
        RecordFormDialog(
            initial = editing,
            records = records,
            types = when (mode) {
                is RecordFormMode.Add -> mode.types
                is RecordFormMode.Edit -> if (mode.record.type.isOther) OTHER_DONATION_TYPES else REGULAR_DONATION_TYPES
            },
            onDismiss = { formMode = null },
            onSave = { record ->
                if (editing == null) viewModel.addRecord(record) else viewModel.updateRecord(record)
                formMode = null
            },
            onDelete = editing?.let {
                {
                    viewModel.deleteRecord(it.id)
                    formMode = null
                }
            }
        )
    }
}

private fun formatNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

@Composable
internal fun PhysicalProfileCard(
    profile: DonorProfile,
    onProfileChange: (DonorProfile) -> Unit
) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("🧍 헌혈 자격 확인", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "생년월일·체중·신장·성별을 입력하면 신체 기준(대한적십자사) 충족 여부를 확인합니다.",
                fontSize = 11.sp,
                color = TextTertiary,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ProfileTextField(
                    label = "이름",
                    value = profile.name ?: "",
                    placeholder = "선택사항",
                    onValueChange = { text -> onProfileChange(profile.copy(name = text.takeIf { it.isNotBlank() })) },
                    modifier = Modifier.weight(0.8f)
                )
                BirthDateField(
                    label = "생년월일",
                    date = profile.birthDate,
                    onDateChange = { date -> onProfileChange(profile.copy(birthDate = date)) },
                    modifier = Modifier.weight(1.2f)
                )
            }
            profile.currentAge()?.let { age ->
                Spacer(Modifier.height(4.dp))
                Text("만 ${age}세로 계산됨", fontSize = 11.sp, color = TextTertiary)
                if (age >= 65) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable {
                                onProfileChange(profile.copy(donatedAge60To64 = !profile.donatedAge60To64))
                            }
                    ) {
                        Checkbox(
                            checked = profile.donatedAge60To64,
                            onCheckedChange = { checked -> onProfileChange(profile.copy(donatedAge60To64 = checked)) }
                        )
                        Text("60~64세에 헌혈한 경험이 있어요", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ProfileNumberField(
                    label = "체중",
                    suffix = "kg",
                    initialValue = profile.weightKg?.let { formatNumber(it) } ?: "",
                    onValueChange = { text ->
                        onProfileChange(profile.copy(weightKg = text.toDoubleOrNull()))
                    },
                    modifier = Modifier.weight(1f)
                )
                ProfileNumberField(
                    label = "신장",
                    suffix = "cm",
                    initialValue = profile.heightCm?.let { formatNumber(it) } ?: "",
                    onValueChange = { text ->
                        onProfileChange(profile.copy(heightCm = text.toDoubleOrNull()))
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SexToggleButton("남성", profile.sex == Sex.MALE, Modifier.weight(1f)) {
                    onProfileChange(profile.copy(sex = Sex.MALE))
                }
                SexToggleButton("여성", profile.sex == Sex.FEMALE, Modifier.weight(1f)) {
                    onProfileChange(profile.copy(sex = Sex.FEMALE))
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = DividerColor)
            Spacer(Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                REGULAR_DONATION_TYPES.forEach { type ->
                    val info = TYPE_INFO.getValue(type)
                    val result = checkPhysicalEligibility(profile, type)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(9.dp).clip(CircleShape).background(info.color))
                                Spacer(Modifier.width(8.dp))
                                Text(info.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (result.isEligible) SuccessColor else ErrorText)
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    if (result.isEligible) "가능" else "불가",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (result.reasons.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                result.reasons.forEach { ReasonChip(Reason(it, ErrorText)) }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "※ 나이·체중·신장·성별 기준만 확인하며, 혈색소·혈압 등 문진 결과는 반영하지 않습니다.",
                fontSize = 10.sp,
                color = TextTertiary,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun ProfileNumberField(
    label: String,
    suffix: String,
    initialValue: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Local text state is intentionally decoupled from the parsed Double in the caller's
    // profile: parsing every keystroke (e.g. toDoubleOrNull on "65.") would reject transient
    // input while the user is still mid-decimal and wipe what they typed.
    var text by remember { mutableStateOf(initialValue) }
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { new ->
                text = new
                onValueChange(new)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            suffix = { Text(suffix, fontSize = 11.sp, color = TextTertiary) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}

@Composable
private fun ProfileTextField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Fully controlled, no local buffer: `value` is the single source of truth, so both user
    // typing and externally-set values (e.g. OCR autofill) display correctly. An earlier version
    // buffered this in its own `remember(value) { mutableStateOf(value) }`, but since `value` is
    // itself echoed back by this same field's onValueChange on every keystroke, that key changed
    // every character and broke live typing (especially Korean IME composition).
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(placeholder, fontSize = 12.sp, color = TextTertiary) }
        )
    }
}

@Composable
private fun FieldLabel(text: String, required: Boolean) {
    Row {
        Text(text, fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
        if (required) Text(" *", fontSize = 11.sp, color = ErrorText, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun ErrorHint(text: String) {
    Text(text, fontSize = 10.sp, color = ErrorText, modifier = Modifier.padding(top = 3.dp))
}

@Composable
private fun BirthDateField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        FieldLabel(label, required = false)
        Spacer(Modifier.height(4.dp))
        // The profile keeps its last complete birth date while a new one is being typed.
        DateInputField(date = date, onDateChange = { it?.let(onDateChange) }, showCalendar = false)
    }
}

@Composable
private fun SexToggleButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val base = if (selected) Accent else InputBg
    val hover = if (selected) AccentHover else InputBgHover
    val bg by rememberHoverColor(interactionSource, base, hover)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.5.dp, if (selected) Accent else InputBorder, RoundedCornerShape(10.dp))
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color.White else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** One option in a row of mutually exclusive choices, outlined and tinted with [color]/[selectedBg] while selected. */
@Composable
private fun ColoredToggleButton(
    label: String,
    selected: Boolean,
    color: Color,
    selectedBg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val bg by rememberHoverColor(interactionSource, if (selected) selectedBg else InputBg, if (selected) selectedBg else InputBgHover)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.5.dp, if (selected) color else InputBorder, RoundedCornerShape(10.dp))
            .hoverable(interactionSource)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) color else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun TypeToggleRow(types: List<DonationType>, selected: DonationType?, onSelect: (DonationType) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().selectableGroup()) {
        types.forEach { type ->
            val info = TYPE_INFO.getValue(type)
            ColoredToggleButton(
                label = info.label,
                selected = selected == type,
                color = info.color,
                selectedBg = info.bg,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(type) }
            )
        }
    }
}

/**
 * The 320mL/400mL picker shown under [TypeToggleRow] while whole blood is selected. Buttons are
 * labeled with the amount stated on the certificate, but [selectedDrawnMl] and [onSelect] use the
 * drawn volume stored in [DonationRecord.donatedVolumeMl], which adds the diagnostic draw.
 */
@Composable
private fun WholeBloodVolumeToggleRow(selectedDrawnMl: Int?, onSelect: (Int) -> Unit) {
    val info = TYPE_INFO.getValue(DonationType.WHOLE_BLOOD)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().selectableGroup()) {
        WHOLE_BLOOD_STATED_VOLUMES_ML.forEach { statedMl ->
            val drawnMl = statedMl + WHOLE_BLOOD_DIAGNOSTIC_DRAW_ML
            ColoredToggleButton(
                label = "${statedMl}mL",
                selected = selectedDrawnMl == drawnMl,
                color = info.color,
                selectedBg = info.bg,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(drawnMl) }
            )
        }
    }
}

@Composable
private fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val bg by rememberHoverColor(interactionSource, Accent, AccentHover)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ScanCertificateButton(isScanning: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val bg by rememberHoverColor(interactionSource, InputBg, InputBgHover)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, InputBorder, RoundedCornerShape(10.dp))
            .hoverable(interactionSource)
            .clickable(enabled = !isScanning, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isScanning) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Accent)
            Spacer(Modifier.width(8.dp))
            Text("인식 중…", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        } else {
            Text("📷", fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text("사진으로 채우기", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
    }
}

@Composable
private fun CenterField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var customMode by remember { mutableStateOf(value.isNotBlank() && value !in BLOOD_CENTERS) }
    // Only flips custom mode ON in response to an externally-set value (e.g. OCR autofill)
    // that isn't a known center; never flips it back off, since clearing the field to type a
    // custom entry (value becomes "") must keep custom mode on.
    LaunchedEffect(value) {
        if (value.isNotBlank() && value !in BLOOD_CENTERS) customMode = true
    }
    val interactionSource = remember { MutableInteractionSource() }
    val bg by rememberHoverColor(interactionSource, InputBg, InputBgHover)

    Column(modifier) {
        FieldLabel("헌혈장소", required = false)
        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .border(1.dp, InputBorder, RoundedCornerShape(10.dp))
                    .hoverable(interactionSource)
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when {
                        customMode -> CUSTOM_CENTER_OPTION
                        value.isNotBlank() -> value
                        else -> "선택 또는 직접 입력 (선택사항)"
                    },
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = if (customMode || value.isNotBlank()) TextPrimary else TextTertiary,
                    modifier = Modifier.weight(1f)
                )
                Text(if (expanded) "▲" else "▾", fontSize = 11.sp, color = TextTertiary)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("선택 안 함", color = TextTertiary) },
                    onClick = {
                        customMode = false
                        onValueChange("")
                        expanded = false
                    }
                )
                HorizontalDivider(color = DividerColor)
                BLOOD_CENTERS.forEach { center ->
                    DropdownMenuItem(
                        text = { Text(center) },
                        onClick = {
                            customMode = false
                            onValueChange(center)
                            expanded = false
                        }
                    )
                }
                HorizontalDivider(color = DividerColor)
                DropdownMenuItem(
                    text = { Text(CUSTOM_CENTER_OPTION, color = Accent, fontWeight = FontWeight.SemiBold) },
                    onClick = {
                        customMode = true
                        onValueChange("")
                        expanded = false
                    }
                )
            }
        }
        if (customMode) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("헌혈 장소명을 입력하세요", fontSize = 12.sp, color = TextTertiary) }
            )
        }
    }
}

@Composable
internal fun RecordFormDialog(
    initial: DonationRecord?,
    records: List<DonationRecord>,
    types: List<DonationType>,
    onDismiss: () -> Unit,
    onSave: (DonationRecord) -> Unit,
    onDelete: (() -> Unit)?
) {
    var type by remember { mutableStateOf(initial?.type) }
    var donationDate by remember { mutableStateOf(initial?.date) }
    var certNumber by remember { mutableStateOf(initial?.certNumber ?: "") }
    var centerName by remember { mutableStateOf(initial?.centerName ?: "") }
    // A whole blood record opens on the amount it was saved with - or the standard 400mL for one
    // saved before the amount could be picked, which has no donatedVolumeMl - so the volume picker
    // always shows one selected.
    var donatedVolumeMl by remember {
        mutableStateOf(initial?.let { it.donatedVolumeMl ?: if (it.type == DonationType.WHOLE_BLOOD) it.type.defaultVolumeMl else null })
    }
    var showErrors by remember { mutableStateOf(false) }
    var restrictionError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isScanning by remember { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf<String?>(null) }
    var lastRawOcrText by remember { mutableStateOf<String?>(null) }
    var showRawOcrText by remember { mutableStateOf(false) }
    var showScanSourceMenu by remember { mutableStateOf(false) }
    var showCameraCapture by remember { mutableStateOf(false) }

    // Every type change - tapped or scanned - goes through here, so a volume picked or scanned for
    // the previous type (e.g. the +30mL whole-blood draw) is never saved on another type. Whole
    // blood starts on the standard 400mL, which the volume picker can change to 320mL.
    fun selectType(newType: DonationType) {
        if (newType != type) {
            donatedVolumeMl = if (newType == DonationType.WHOLE_BLOOD) DonationType.WHOLE_BLOOD.defaultVolumeMl else null
        }
        type = newType
        restrictionError = null
    }

    // Only 헌혈종류/헌혈일/증서번호/헌혈장소 are auto-filled from a scan - name and birth date
    // are always left for manual entry, per BloodDonationInfo no longer carrying those fields.
    fun applyParsedCertificate(parsed: BloodDonationInfo) {
        var appliedAny = false
        val missingFields = mutableListOf<String>()

        parsed.donationTypeEnum()?.let { selectType(it); appliedAny = true }
            ?: missingFields.add("헌혈 종류")
        parsed.donationDate?.let { donationDate = it; restrictionError = null; appliedAny = true }
            ?: missingFields.add("헌혈일")
        parsed.certNumber?.let { certNumber = it; appliedAny = true }
            ?: missingFields.add("증서번호")
        parsed.centerName?.let { centerName = it; appliedAny = true }
            ?: missingFields.add("헌혈장소")
        parsed.drawnVolumeMl()?.let { donatedVolumeMl = it; appliedAny = true }

        // A field silently staying blank (e.g. only the cert number was read) looks identical to a
        // fully successful scan unless we call out exactly which labels weren't found - so the user
        // knows to check those specific fields rather than assuming everything was filled in.
        scanMessage = when {
            !appliedAny -> "인식된 정보가 없어요. 직접 입력해주세요"
            missingFields.isNotEmpty() -> "${missingFields.joinToString(", ")}은(는) 인식하지 못했어요. 확인 후 입력해주세요"
            else -> null
        }
    }

    fun scanCertificateImage(uri: Uri, applyRoiCrop: Boolean) {
        isScanning = true
        scanMessage = null
        lastRawOcrText = null
        coroutineScope.launch {
            val result = runCatching { recognizeCertificateInfo(context, uri, applyRoiCrop) }.getOrNull()
            isScanning = false
            lastRawOcrText = result?.rawText
            if (result != null) applyParsedCertificate(result.info) else scanMessage = "이미지를 인식하지 못했어요. 다시 시도해주세요"
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            showCameraCapture = true
        } else {
            scanMessage = "카메라 권한이 필요해요"
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scanCertificateImage(uri, applyRoiCrop = false)
    }

    fun launchCertificateCamera() {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            showCameraCapture = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // The 기타 card's form: only its two types, and no photo scan - the scanner only reads
    // 전혈/혈장/혈소판 certificates.
    val isOtherForm = types.any { it.isOther }
    val recordNoun = if (isOtherForm) "기타 기록" else "헌혈 기록"

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = CardShape, color = CardBg, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    if (initial == null) "$recordNoun 추가" else "$recordNoun 수정",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(if (isOtherForm) "헌혈·기증 정보를 입력해주세요." else "헌혈증서에 적힌 정보를 입력해주세요.", fontSize = 12.sp, color = TextTertiary)
                Spacer(Modifier.height(14.dp))

                if (!isOtherForm) {
                    Box {
                        ScanCertificateButton(
                            isScanning = isScanning,
                            onClick = { showScanSourceMenu = true }
                        )
                        DropdownMenu(expanded = showScanSourceMenu, onDismissRequest = { showScanSourceMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("촬영") },
                                onClick = {
                                    showScanSourceMenu = false
                                    launchCertificateCamera()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("갤러리에서 선택") },
                                onClick = {
                                    showScanSourceMenu = false
                                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            )
                        }
                    }
                    scanMessage?.let { message ->
                        Spacer(Modifier.height(6.dp))
                        Text(message, fontSize = 11.sp, color = TextTertiary)
                    }
                    lastRawOcrText?.let { rawText ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (showRawOcrText) "인식된 원문 숨기기 ▲" else "인식된 원문 보기 ▼",
                            fontSize = 11.sp,
                            color = Accent,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { showRawOcrText = !showRawOcrText }
                        )
                        if (showRawOcrText) {
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(InputBg)
                                    .border(1.dp, InputBorder, RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                // Field values sometimes land in the wrong spot because this raw text
                                // isn't in the order the certificate prints it - this view exists so
                                // that mismatch is visible and reportable instead of silently guessed at.
                                Text(rawText, fontSize = 11.sp, color = TextSecondary, lineHeight = 16.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                }

                FieldLabel(if (isOtherForm) "종류" else "헌혈 종류", required = true)
                Spacer(Modifier.height(6.dp))
                TypeToggleRow(types = types, selected = type, onSelect = { selectType(it) })
                if (showErrors && type == null) ErrorHint(if (isOtherForm) "종류를 선택해주세요" else "헌혈 종류를 선택해주세요")

                if (type == DonationType.WHOLE_BLOOD) {
                    Spacer(Modifier.height(14.dp))
                    FieldLabel("헌혈량", required = true)
                    Spacer(Modifier.height(6.dp))
                    WholeBloodVolumeToggleRow(
                        selectedDrawnMl = donatedVolumeMl,
                        onSelect = { donatedVolumeMl = it; restrictionError = null }
                    )
                }

                Spacer(Modifier.height(14.dp))

                FieldLabel(if (isOtherForm) "헌혈·기증일" else "헌혈일", required = true)
                Spacer(Modifier.height(4.dp))
                DateInputField(date = donationDate, onDateChange = { donationDate = it; restrictionError = null })
                if (showErrors && donationDate == null) ErrorHint("필수 항목이에요")
                restrictionError?.let { ErrorHint(it) }

                // Kept out of the add form to minimize the fields a new record asks for up front -
                // a scan (above) can still silently populate them for later editing, and they're
                // always shown once a record exists so the user can fill them in from the edit screen.
                if (initial != null) {
                    Spacer(Modifier.height(14.dp))
                    ProfileTextField(label = "헌혈증서번호", value = certNumber, placeholder = "선택사항", onValueChange = { certNumber = it })

                    Spacer(Modifier.height(14.dp))
                    CenterField(value = centerName, onValueChange = { centerName = it })
                }

                Spacer(Modifier.height(22.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (onDelete != null) {
                        TextButton(onClick = { showDeleteConfirm = true }) {
                            Text("삭제", color = ErrorText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("취소", color = TextSecondary, fontSize = 13.sp) }
                    Spacer(Modifier.width(4.dp))
                    PrimaryButton(text = "저장") {
                        val t = type
                        val dd = donationDate
                        if (t == null || dd == null) {
                            showErrors = true
                        } else {
                            val otherRecords = records.filterNot { it.id == initial?.id }
                            val eligibleDate = earliestEligibleDate(t, otherRecords, dd)
                            if (dd.isBefore(eligibleDate)) {
                                restrictionError = "헌혈 제한기간이에요 (가능일: ${fmt(eligibleDate)})"
                            } else {
                                onSave(
                                    DonationRecord(
                                        id = initial?.id ?: System.currentTimeMillis(),
                                        type = t,
                                        date = dd,
                                        certNumber = certNumber.takeIf { it.isNotBlank() },
                                        centerName = centerName.takeIf { it.isNotBlank() },
                                        donatedVolumeMl = donatedVolumeMl
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("기록을 삭제할까요?") },
            text = { Text("삭제하면 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete?.invoke()
                }) { Text("삭제", color = ErrorText) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("취소") }
            }
        )
    }

    if (showCameraCapture) {
        CertificateCameraScreen(
            onCaptured = { uri ->
                showCameraCapture = false
                scanCertificateImage(uri, applyRoiCrop = true)
            },
            onDismiss = { showCameraCapture = false }
        )
    }
}

/**
 * Full-screen in-app camera capture: a live CameraX preview with the guide-frame overlay drawn
 * directly on top of it (matching [GUIDE_FRAME_WIDTH_FRACTION]/[GUIDE_FRAME_ASPECT_RATIO], which
 * [recognizeCertificateInfo] later crops the captured photo to), so what's shown here is exactly
 * what gets cropped.
 */
@Composable
private fun CertificateCameraScreen(onCaptured: (Uri) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var captureError by remember { mutableStateOf<String?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    // Unbinds the camera when this screen leaves composition (cancelled or captured) - otherwise
    // it stays bound to the Activity's lifecycle and keeps the camera hardware/indicator held.
    DisposableEffect(Unit) {
        onDispose { cameraProvider?.unbindAll() }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    val previewView = PreviewView(viewContext)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(viewContext)
                    cameraProviderFuture.addListener({
                        val provider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val capture = ImageCapture.Builder().build()
                        runCatching {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                capture
                            )
                        }.onSuccess {
                            imageCapture = capture
                            cameraProvider = provider
                        }.onFailure {
                            captureError = "카메라를 열지 못했어요"
                        }
                    }, mainExecutor)
                    previewView
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "헌혈증서를 아래 프레임 안에 맞춰 촬영해주세요",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(GUIDE_FRAME_WIDTH_FRACTION)
                        .aspectRatio(GUIDE_FRAME_ASPECT_RATIO)
                        .border(2.dp, Color.White, RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.weight(1f))
                captureError?.let { message ->
                    Text(message, color = ErrorText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("취소", color = Color.White) }
                    PrimaryButton(
                        text = if (isCapturing) "촬영 중..." else "촬영",
                        onClick = click@{
                            val capture = imageCapture ?: return@click
                            if (isCapturing) return@click
                            isCapturing = true
                            captureError = null
                            val file = createCertificatePhotoFile(context)
                            capture.takePicture(
                                ImageCapture.OutputFileOptions.Builder(file).build(),
                                mainExecutor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        isCapturing = false
                                        onCaptured(certificatePhotoUriFor(context, file))
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        isCapturing = false
                                        captureError = "촬영에 실패했어요. 다시 시도해주세요"
                                    }
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MyPageScreen(
    profile: DonorProfile,
    medications: List<MedicationRecord>,
    onProfileChange: (DonorProfile) -> Unit,
    onDeleteMedication: (MedicationRecord) -> Unit
) {
    PhysicalProfileCard(profile, onProfileChange = onProfileChange)
    Spacer(Modifier.height(14.dp))
    ProhibitedDrugSearchScreen()
    Spacer(Modifier.height(14.dp))
    MedicationHistoryCard(medications = medications, onDelete = onDeleteMedication)
}

/**
 * App-wide title bar with the light/dark switch.
 *
 * The switch position is read straight from [LocalDarkTheme] rather than being passed in, so it
 * always reflects what is actually on screen - including the initial "follow the device" state,
 * before the user has chosen a side.
 */
@Composable
private fun AppTopBar(onToggleTheme: (Boolean) -> Unit) {
    val isDark = LocalDarkTheme.current
    Column(modifier = Modifier.background(CardBg)) {
        TopAppBar(
            title = {
                Text(
                    "연간채혈량 계산기",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            actions = {
                Text(if (isDark) "🌙" else "☀️", fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Switch(
                    checked = isDark,
                    onCheckedChange = onToggleTheme,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CardBg,
                        checkedTrackColor = Accent,
                        checkedBorderColor = Accent,
                        uncheckedThumbColor = CardBg,
                        uncheckedTrackColor = InputBorder,
                        uncheckedBorderColor = InputBorder
                    ),
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .semantics { contentDescription = "다크 모드" }
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = CardBg,
                titleContentColor = TextPrimary
            )
        )
        HorizontalDivider(color = InputBorder)
    }
}

@Composable
private fun BottomNavBar(selected: Screen, onSelect: (Screen) -> Unit) {
    // The background fills the outer Column all the way to the physical bottom edge (the app
    // draws edge-to-edge), while navigationBarsPadding() on the Row keeps the tappable items
    // clear of the system navigation bar / gesture handle instead of being obscured by it.
    Column(modifier = Modifier.background(CardBg)) {
        HorizontalDivider(color = InputBorder)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Screen.entries.forEach { screen ->
                BottomNavItem(screen, screen == selected, Modifier.weight(1f)) { onSelect(screen) }
            }
        }
    }
}

@Composable
private fun BottomNavItem(screen: Screen, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val bg by rememberHoverColor(interactionSource, Color.Transparent, InputBgHover)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(screen.icon, fontSize = 18.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            screen.label,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) Accent else TextTertiary
        )
    }
}

@Composable
internal fun InputScreen(
    records: List<DonationRecord>,
    onAddClick: (types: List<DonationType>) -> Unit,
    onRecordClick: (DonationRecord) -> Unit
) {
    val (other, regular) = remember(records) { records.partition { it.type.isOther } }
    RecordListCard(
        title = "헌혈 기록",
        emptyMessage = "아직 등록된 헌혈 기록이 없어요.\n+ 추가 버튼으로 첫 기록을 남겨보세요.",
        records = regular,
        onAddClick = { onAddClick(REGULAR_DONATION_TYPES) },
        onRecordClick = onRecordClick
    )
    Spacer(Modifier.height(14.dp))
    RecordListCard(
        title = "기타",
        emptyMessage = "백혈구성분헌혈이나 조혈모세포 기증을 했다면\n+ 추가 버튼으로 남겨보세요.",
        records = other,
        onAddClick = { onAddClick(OTHER_DONATION_TYPES) },
        onRecordClick = onRecordClick
    )
    Spacer(Modifier.height(14.dp))
    LegendCard()
}

@Composable
internal fun RecordListCard(
    title: String,
    emptyMessage: String,
    records: List<DonationRecord>,
    onAddClick: () -> Unit,
    onRecordClick: (DonationRecord) -> Unit
) {
    // The years the user opened or closed. The newest year starts open and the rest closed, so this
    // holds only the exceptions - which keeps the newest year open by default as records change.
    var toggledYears by remember { mutableStateOf(emptySet<Int>()) }
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(2.dp))
                    Text("${records.size}건 등록됨", fontSize = 11.sp, color = TextTertiary)
                }
                PrimaryButton(text = "+ 추가", onClick = onAddClick)
            }

            if (records.isEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    emptyMessage,
                    fontSize = 13.sp,
                    color = TextTertiary,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp)
                )
            } else {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = DividerColor)
                // Newest year first, each year's records newest first.
                val byYear = remember(records) { records.sortedByDescending { it.date }.groupBy { it.date.year } }
                val newestYear = byYear.keys.first()
                byYear.entries.forEachIndexed { yearIndex, (year, yearRecords) ->
                    val open = (year == newestYear) != (year in toggledYears)
                    YearHeader(
                        year = year,
                        count = yearRecords.size,
                        open = open,
                        onClick = { toggledYears = if (year in toggledYears) toggledYears - year else toggledYears + year }
                    )
                    if (open) {
                        yearRecords.forEachIndexed { index, record ->
                            RecordRow(record = record, onClick = { onRecordClick(record) })
                            if (index != yearRecords.lastIndex) HorizontalDivider(color = DividerColor)
                        }
                    }
                    if (yearIndex != byYear.size - 1) HorizontalDivider(color = DividerColor)
                }
            }
        }
    }
}

/** A tappable "2026년 · 2건 ▲" header that opens or closes that year's records. */
@Composable
private fun YearHeader(year: Int, count: Int, open: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClickLabel = if (open) "접기" else "펼치기", onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${year}년 · ${count}건", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(if (open) "▲" else "▼", fontSize = 11.sp, color = TextTertiary)
    }
}

@Composable
private fun RecordRow(record: DonationRecord, onClick: () -> Unit) {
    val info = TYPE_INFO.getValue(record.type)
    val interactionSource = remember { MutableInteractionSource() }
    val bg by rememberHoverColor(interactionSource, Color.Transparent, InputBgHover)
    val subtitle = remember(record) {
        listOfNotNull(
            record.name?.let { "이름 $it" },
            record.birthDate?.let { "생년월일 ${fmt(it)}" },
            record.centerName,
            record.certNumber?.let { "증서 $it" }
        ).ifEmpty { listOf("추가 정보 없음") }.joinToString(" · ")
    }
    // Whole blood rows read like the certificate, with the amount picked in the form ("전혈헌혈 400mL").
    val title = record.wholeBloodStatedVolumeMl()?.let { "${info.label} ${it}mL" } ?: info.label
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(info.color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.width(8.dp))
                // The year is in the YearHeader above.
                Text(fmtShort(record.date), fontSize = 12.sp, color = TextSecondary)
            }
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 11.sp, color = TextTertiary, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Text("›", fontSize = 16.sp, color = TextTertiary)
    }
}

@Composable
private fun StatusScreen(
    records: List<DonationRecord>,
    medications: List<MedicationRecord>,
    today: LocalDate,
    nextByType: Map<DonationType, IntegratedNextResult>
) {
    CumulativeVolumeGauge(records, today)

    Spacer(Modifier.height(20.dp))

    NextDatesCard(records, medications, nextByType)

    Spacer(Modifier.height(20.dp))

    DonationCalendarCard(records, today, nextByType)
}

@Composable
private fun CumulativeVolumeGauge(records: List<DonationRecord>, today: LocalDate) {
    // The 2,160mL cap is an annual one, so only donations from the past year consume it - older
    // donations have expired and are excluded from both the total and the per-type bar.
    val recentRecords = records.withinAnnualWindow(today)
    val totalVol = recentRecords.sumOf { it.volumeMl() }
    val pct = (totalVol.toDouble() / ANNUAL_LIMIT_ML * 100).coerceAtMost(100.0)
    val style = statusStyle(pct)

    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "최근 1년 누적 채혈량",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$totalVol", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                        Spacer(Modifier.width(4.dp))
                        Text("/ 2,160 mL", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 5.dp))
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("제한 대비", fontSize = 11.sp, color = TextSecondary)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${"%.1f".format(pct)}%",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = style.color
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(style.color)
                            .padding(horizontal = 12.dp, vertical = 3.dp)
                    ) {
                        Text(style.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            VolumeProgressBar(recentRecords)

            Spacer(Modifier.height(4.dp))
            Text(
                "제한: 2,160 mL",
                fontSize = 10.sp,
                color = TextTertiary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
internal fun NextDatesCard(
    records: List<DonationRecord>,
    medications: List<MedicationRecord>,
    nextByType: Map<DonationType, IntegratedNextResult>
) {
    Text("📅 다음 헌혈 가능일", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    Spacer(Modifier.height(10.dp))

    if (records.isEmpty() && medications.isEmpty()) {
        Card(
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = CardBg),
            border = BorderStroke(1.dp, CardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Text(
                "헌혈 기록을 등록하면\n다음 헌혈 가능일을 계산합니다.",
                fontSize = 13.sp,
                color = TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp, horizontal = 20.dp)
            )
        }
    } else {
        Card(
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = CardBg),
            border = BorderStroke(1.dp, CardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                REGULAR_DONATION_TYPES.forEachIndexed { index, type ->
                    val result = nextByType[type] ?: return@forEachIndexed
                    NextDateRow(type, result)
                    if (index != REGULAR_DONATION_TYPES.lastIndex) {
                        HorizontalDivider(color = DividerColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun NextDateRow(type: DonationType, result: IntegratedNextResult) {
    val info = TYPE_INFO.getValue(type)
    Column(Modifier.padding(vertical = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(info.color))
                Spacer(Modifier.width(8.dp))
                Text(info.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }
            Column(horizontalAlignment = Alignment.End) {
                when (result) {
                    is IntegratedNextResult.Eligible -> {
                        Text(fmt(result.nextDate), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(result.dd.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = result.dd.color)
                    }

                    is IntegratedNextResult.PermanentlyProhibited -> {
                        Text("헌혈 불가", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ErrorText)
                        Text("영구 제한", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ErrorText)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (result.reasons.isEmpty()) {
                ReasonChip(Reason("모든 조건 충족 · 즉시 가능", SuccessColor))
            } else {
                result.reasons.forEach { ReasonChip(it) }
            }
        }
    }
}

@Composable
internal fun DonationCalendarCard(
    records: List<DonationRecord>,
    today: LocalDate,
    nextByType: Map<DonationType, IntegratedNextResult>
) {
    val donationTypesByDay = remember(records) {
        records.groupBy({ it.date }) { it.type }
    }
    // A permanently prohibited type has no next date, so it contributes no calendar marker.
    val nextTypesByDay = remember(nextByType) {
        nextByType.entries
            .mapNotNull { (type, result) ->
                (result as? IntegratedNextResult.Eligible)?.let { it.nextDate to type }
            }
            .groupBy({ (date, _) -> date }, { (_, type) -> type })
    }
    var displayedMonth by remember { mutableStateOf(YearMonth.from(today)) }

    Text("🗓️ 헌혈 캘린더", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    Spacer(Modifier.height(10.dp))

    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CalendarNavButton("‹") { displayedMonth = displayedMonth.minusMonths(1) }
                Text(
                    "${displayedMonth.year}년 ${displayedMonth.monthValue}월",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                CalendarNavButton("›") { displayedMonth = displayedMonth.plusMonths(1) }
            }

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth()) {
                listOf("일", "월", "화", "수", "목", "금", "토").forEach { d ->
                    Text(
                        d,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            val firstOfMonth = displayedMonth.atDay(1)
            val leading = firstOfMonth.dayOfWeek.value % 7
            val daysInMonth = displayedMonth.lengthOfMonth()
            val rows = (leading + daysInMonth + 6) / 7

            for (row in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val dayNum = row * 7 + col - leading + 1
                        Box(
                            modifier = Modifier.weight(1f).height(44.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (dayNum in 1..daysInMonth) {
                                val date = displayedMonth.atDay(dayNum)
                                CalendarDayCell(
                                    day = dayNum,
                                    isToday = date == today,
                                    donationTypes = donationTypesByDay[date].orEmpty(),
                                    nextTypes = nextTypesByDay[date].orEmpty()
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = DividerColor)
            Spacer(Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(TextSecondary))
                    Spacer(Modifier.width(6.dp))
                    Text("헌혈한 날", fontSize = 11.sp, color = TextSecondary)
                    Spacer(Modifier.width(14.dp))
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, TextSecondary, CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("다음 헌혈 가능일", fontSize = 11.sp, color = TextSecondary)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DonationType.entries.forEach { type ->
                        val info = TYPE_INFO.getValue(type)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(info.color))
                            Spacer(Modifier.width(5.dp))
                            Text(info.label.removeSuffix("헌혈"), fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: Int,
    isToday: Boolean,
    donationTypes: List<DonationType>,
    nextTypes: List<DonationType>
) {
    val bg = if (isToday) InputBgHover else Color.Transparent
    val textColor = if (isToday) TextPrimary else CalendarDayText
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(3.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(22.dp)) {
            if (nextTypes.isNotEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(1.5.dp, TYPE_INFO.getValue(nextTypes.first()).color, CircleShape)
                )
            }
            Text(
                "$day",
                fontSize = 12.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            donationTypes.take(3).forEach { type ->
                Box(Modifier.size(5.dp).clip(CircleShape).background(TYPE_INFO.getValue(type).color))
            }
        }
    }
}

@Composable
private fun CalendarNavButton(symbol: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val bg by rememberHoverColor(interactionSource, InputBg, InputBgHover)
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .hoverable(interactionSource)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
    }
}

@Composable
private fun VolumeProgressBar(recentRecords: List<DonationRecord>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(50))
            .background(ProgressTrack)
    ) {
        DonationType.entries.forEach { type ->
            val vol = recentRecords.filter { it.type == type }.sumOf { it.volumeMl() }
            val frac = (vol.toFloat() / ANNUAL_LIMIT_ML).coerceIn(0f, 1f)
            Box(
                Modifier
                    .weight(frac.coerceAtLeast(0.0001f))
                    .fillMaxHeight()
                    .background(TYPE_INFO.getValue(type).color)
            )
        }
        val used = recentRecords.sumOf { it.volumeMl() }
        val usedFrac = (used.toFloat() / ANNUAL_LIMIT_ML).coerceIn(0f, 1f)
        val remaining = (1f - usedFrac).coerceAtLeast(0.0001f)
        Box(Modifier.weight(remaining).fillMaxHeight())
    }
}

// Fixed per-column widths (rather than Row weight()) so cells like "조혈모세포 기증" always lay out
// on one line instead of wrapping awkwardly on narrow phone screens; sized generously for the
// longest real value in each column. horizontalScroll on the table is a safety net for very
// narrow screens or larger accessibility font scales, rather than clipping or wrapping.
private val LEGEND_TYPE_COL = 96.dp
private val LEGEND_VOLUME_COL = 84.dp
private val LEGEND_COUNT_COL = 56.dp
private val LEGEND_INTERVAL_COL = 76.dp

@Composable
private fun LegendCell(
    text: String,
    width: Dp,
    color: Color,
    bold: Boolean = false
) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.width(width)
    )
}

@Composable
internal fun LegendCard() {
    // Material3 Card wraps its content, and nothing inside this one stretches - the table is
    // built from fixed-width columns - so without fillMaxWidth the card collapses to the table's
    // intrinsic width (312.dp of columns + padding) and sits narrower than its sibling cards on
    // any screen wider than a small phone.
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text("연간 채혈 제한 기준", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(10.dp))

            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Column {
                    Row(Modifier.padding(bottom = 4.dp)) {
                        LegendCell("종류", LEGEND_TYPE_COL, TextSecondary, bold = true)
                        LegendCell("1회 채혈량", LEGEND_VOLUME_COL, TextSecondary, bold = true)
                        LegendCell("연간 횟수", LEGEND_COUNT_COL, TextSecondary, bold = true)
                        LegendCell("간격", LEGEND_INTERVAL_COL, TextSecondary, bold = true)
                    }
                    HorizontalDivider(color = DividerColor)

                    DonationType.entries.forEachIndexed { index, type ->
                        val info = TYPE_INFO.getValue(type)
                        Row(Modifier.padding(vertical = 6.dp)) {
                            LegendCell(info.label, LEGEND_TYPE_COL, TextPrimary)
                            LegendCell("${info.volumeMl}mL", LEGEND_VOLUME_COL, info.color, bold = true)
                            LegendCell(info.yearMax?.let { "${it}회" } ?: "없음", LEGEND_COUNT_COL, TextPrimary)
                            LegendCell(info.intervalLegendText, LEGEND_INTERVAL_COL, TextPrimary)
                        }
                        if (index != DonationType.entries.lastIndex) HorizontalDivider(color = DividerColor)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "※ 전혈헌혈은 320mL·400mL 모두 430mL로 계산합니다.",
                fontSize = 11.sp,
                color = TextTertiary,
                lineHeight = 17.sp
            )
            Text(
                "※ 조혈모세포 채혈량은 알 수 없어 최소 기준인 30mL로 설정했습니다.",
                fontSize = 11.sp,
                color = TextTertiary,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun ReasonChip(reason: Reason) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(reason.color))
        Spacer(Modifier.width(5.dp))
        Text(reason.text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = reason.color)
    }
}
