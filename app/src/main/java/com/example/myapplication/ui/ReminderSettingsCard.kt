package com.example.myapplication.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.reminder.DonationReminders
import kotlinx.coroutines.launch

/**
 * 헌혈 알림 on 마이페이지: one switch for the morning reminders. Turning it on asks for Android 13's
 * notification permission first; when notifications are refused or turned off for the app, the card
 * says so and opens the system setting, and picks up the answer when the donor comes back.
 */
@Composable
internal fun ReminderSettingsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(DonationReminders.isEnabled(context)) }
    var canNotify by remember { mutableStateOf(DonationReminders.canNotify(context)) }
    // The donor asked for reminders but notifications aren't allowed, so they're off for now.
    var blocked by remember { mutableStateOf(false) }

    fun turnOn() {
        enabled = true
        blocked = false
        scope.launch { DonationReminders.enable(context) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = DonationReminders.canNotify(context)
        if (granted) turnOn() else blocked = true
    }
    // Back from the system setting: on if they allowed notifications there after asking for reminders.
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        canNotify = DonationReminders.canNotify(context)
        if (blocked && canNotify) turnOn()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🔔 헌혈 알림", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "다시 헌혈할 수 있게 된 날과 헌혈 계획 전날, 아침 9시쯤 알려 드려요.",
                        fontSize = 11.sp,
                        color = TextTertiary,
                        lineHeight = 16.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        when {
                            !on -> {
                                DonationReminders.disable(context)
                                enabled = false
                                blocked = false
                            }
                            DonationReminders.needsPermission(context) ->
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            !DonationReminders.canNotify(context) -> {
                                canNotify = false
                                blocked = true
                            }
                            else -> turnOn()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CardBg,
                        checkedTrackColor = Accent,
                        checkedBorderColor = Accent,
                        uncheckedThumbColor = CardBg,
                        uncheckedTrackColor = InputBorder,
                        uncheckedBorderColor = InputBorder
                    ),
                    modifier = Modifier.semantics { contentDescription = "헌혈 알림" }
                )
            }
            if (blocked || (enabled && !canNotify)) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "이 앱의 알림이 꺼져 있어 알려 드릴 수 없어요. 설정에서 알림을 허용해 주세요.",
                    fontSize = 12.sp,
                    color = ErrorText,
                    lineHeight = 17.sp
                )
                TextButton(onClick = { settingsLauncher.launch(DonationReminders.notificationSettingsIntent(context)) }) {
                    Text("알림 설정 열기", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                }
            } else if (enabled) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "다음 헌혈 가능일에는 헌혈 간격과 약·질환·시술·접종·체류 제한이 모두 반영돼요. 계획한 날 헌혈을 할 수 없게 되면 그것도 알려 드려요.",
                    fontSize = 10.sp,
                    color = TextTertiary,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
