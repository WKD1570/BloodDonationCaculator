@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.myapplication.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.domain.formatDateDigits
import com.example.myapplication.domain.parseDateDigits
import com.example.myapplication.domain.toDateDigits
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A date typed as YYYY/MM/DD whatever the phone's language - Material's date picker would take it in
 * the phone's own format instead (MM/DD/YYYY in English). Reports the date once the 8 digits make a
 * real date that isn't in the future, and null otherwise. With [showCalendar], a 📅 button also offers
 * a calendar - for picking only, so the phone's typed format never shows up there either.
 */
@Composable
internal fun DateInputField(
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    showCalendar: Boolean = true
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(date?.toDateDigits().orEmpty())) }
    var showCalendarDialog by remember { mutableStateOf(false) }

    // Follow a date set from outside the field (e.g. a certificate scan), but not the field's own null
    // report for a half-typed date - resetting to it would wipe what's being typed.
    LaunchedEffect(date) {
        val digits = date?.toDateDigits()
        if (digits != null && digits != fieldValue.text) fieldValue = TextFieldValue(digits, TextRange(digits.length))
    }

    val digits = fieldValue.text
    val parsed = parseDateDigits(digits)
    val error = when {
        digits.length < 8 -> null
        parsed == null -> "유효한 날짜가 아니에요"
        parsed.isAfter(LocalDate.now()) -> "오늘 이후 날짜는 입력할 수 없어요"
        else -> null
    }

    Column(modifier) {
        OutlinedTextField(
            value = fieldValue,
            onValueChange = { new ->
                val raw = new.text.filter { it.isDigit() }.take(8)
                fieldValue = new.copy(text = raw, selection = TextRange(raw.length))
                onDateChange(parseDateDigits(raw)?.takeUnless { it.isAfter(LocalDate.now()) })
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("YYYY/MM/DD", fontSize = 12.sp, color = TextTertiary) },
            trailingIcon = if (showCalendar) {
                {
                    Text(
                        "📅",
                        fontSize = 16.sp,
                        modifier = Modifier
                            .semantics { contentDescription = "달력에서 선택" }
                            .clickable(role = Role.Button) { showCalendarDialog = true }
                            .padding(12.dp)
                    )
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = DateDigitsVisualTransformation
        )
        error?.let { ErrorHint(it) }
    }

    if (showCalendarDialog) {
        CalendarDialog(
            initial = parsed?.takeUnless { it.isAfter(LocalDate.now()) },
            onDismiss = { showCalendarDialog = false },
            onPick = { picked ->
                val pickedDigits = picked.toDateDigits()
                fieldValue = TextFieldValue(pickedDigits, TextRange(pickedDigits.length))
                onDateChange(picked)
                showCalendarDialog = false
            }
        )
    }
}

@Composable
private fun CalendarDialog(initial: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = NotInTheFuture,
        initialDisplayMode = DisplayMode.Picker
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                }
            ) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    ) {
        // No switch to Material's typed input: typing happens in the YYYY/MM/DD field.
        DatePicker(state = state, showModeToggle = false)
    }
}

private val NotInTheFuture = object : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(LocalDate.now())

    override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
}

// Keeps the field's actual text as plain digits and only shows the slashes, so typed keystrokes
// never get reordered by separators being inserted mid-string.
private val DateDigitsVisualTransformation = VisualTransformation { text ->
    TransformedText(AnnotatedString(formatDateDigits(text.text)), DateDigitsOffsetMapping)
}

private val DateDigitsOffsetMapping = object : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int = when {
        offset <= 4 -> offset
        offset <= 6 -> offset + 1
        else -> offset + 2
    }

    override fun transformedToOriginal(offset: Int): Int = when {
        offset <= 4 -> offset
        offset <= 7 -> offset - 1
        else -> offset - 2
    }
}
