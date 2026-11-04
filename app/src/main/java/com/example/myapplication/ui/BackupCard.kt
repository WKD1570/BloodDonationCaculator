package com.example.myapplication.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.BackupFile
import com.example.myapplication.data.backupFileName
import com.example.myapplication.data.backupSummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** What a file manager may call a .json file; some only know it as generic bytes or text. */
private val BACKUP_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

private val exportedAtFormat = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm")

/**
 * 백업·복원 on 마이페이지: writes everything the donor entered to a file they pick with the system
 * picker, and restores one after asking - restoring replaces what's in the app. The file isn't
 * encrypted, so the card says plainly what's in it.
 */
@Composable
internal fun BackupCard(
    state: BackupUiState,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(onExport)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImport)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("💾 백업·복원", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "헌혈·약·감염병·체류 기록과 헌혈 계획, 마이페이지 정보, 즐겨찾기를 파일 하나로 저장해요. " +
                    "폰을 바꿀 때 새 폰에서 불러오면 그대로 이어서 쓸 수 있어요.",
                fontSize = 11.sp,
                color = TextTertiary,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BackupButton("파일로 내보내기", enabled = !state.busy, modifier = Modifier.weight(1f)) {
                    exportLauncher.launch(backupFileName(LocalDate.now()))
                }
                BackupButton("파일에서 불러오기", enabled = !state.busy, modifier = Modifier.weight(1f)) {
                    importLauncher.launch(BACKUP_MIME_TYPES)
                }
            }
            state.message?.let { message ->
                Spacer(Modifier.height(10.dp))
                Text(
                    message,
                    fontSize = 12.sp,
                    color = if (state.isError) ErrorText else SuccessColor,
                    lineHeight = 17.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "⚠️ 백업 파일에는 이름·생년월일·헌혈증 번호와 약·감염병·체류 기록이 암호화되지 않은 채 들어 있어요. " +
                    "믿을 수 있는 곳에만 보관하고, 다른 사람에게 보내지 마세요.",
                fontSize = 10.sp,
                color = TextTertiary,
                lineHeight = 15.sp
            )
        }
    }

    state.pending?.let { file ->
        AlertDialog(
            onDismissRequest = onCancelRestore,
            title = { Text("백업을 불러올까요?", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = { Text(restoreText(file), fontSize = 13.sp, lineHeight = 19.sp) },
            confirmButton = { TextButton(onClick = onConfirmRestore) { Text("불러오기", color = ErrorText) } },
            dismissButton = { TextButton(onClick = onCancelRestore) { Text("취소") } }
        )
    }
}

@Composable
private fun BackupButton(label: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, InputBorder),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
        modifier = modifier
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun restoreText(file: BackupFile): String = buildString {
    append("지금 앱에 있는 기록과 마이페이지 정보를 모두 지우고 이 파일의 내용으로 바꿔요. ")
    append("되돌릴 수 없으니, 필요하면 먼저 지금 기록을 내보내 두세요.\n\n")
    file.exportedAt?.let { append("내보낸 때: ${it.format(exportedAtFormat)}\n") }
    append(backupSummary(file.backup))
    if (file.skipped > 0) append("\n\n읽을 수 없는 항목 ${file.skipped}개는 건너뛰어요.")
}
