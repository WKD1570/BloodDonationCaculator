package com.example.myapplication.data

import com.example.myapplication.model.DiseaseRecord
import com.example.myapplication.model.DonationRecord
import com.example.myapplication.model.DonationType
import com.example.myapplication.model.DonorProfile
import com.example.myapplication.model.MedicationRecord
import com.example.myapplication.model.PlannedDonation
import com.example.myapplication.model.Sex
import com.example.myapplication.model.StayRecord
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBackupTest {

    private fun d(text: String): LocalDate = LocalDate.parse(text)

    private val full = AppBackup(
        profile = DonorProfile(
            weightKg = 68.5, heightCm = 172.0, sex = Sex.MALE, name = "홍길동",
            birthDate = d("1999-03-01"), donatedAge60To64 = false, preferredProvince = "서울"
        ),
        donations = listOf(
            DonationRecord(1L, DonationType.WHOLE_BLOOD, d("2026-05-02"), d("1999-03-01"), "12-34-567890", "홍길동", Sex.MALE, "서울역센터", 430),
            DonationRecord(2L, DonationType.PLASMA, d("2026-09-20"), centerName = "홍대점 \"Y'Z\"|파이프")
        ),
        plan = listOf(PlannedDonation(3L, DonationType.PLATELET, d("2026-11-20"))),
        medications = listOf(MedicationRecord(7, "아스피린", "아스피린, 아스트릭스", d("2026-10-30"), 3, "복용 후 3일")),
        diseases = listOf(DiseaseRecord(4, "말라리아", d("2024-01-15"))),
        stays = listOf(StayRecord(5, "태국", d("2026-01-02"), d("2026-01-09"), offshoreOnly = false, visitedRiskArea = false)),
        favoritePlaces = setOf("한마음/홍대점", "서울중앙/서울역센터")
    )

    @Test
    fun `a backup reads back exactly as it was written`() {
        val text = encodeBackup(full, LocalDateTime.parse("2026-11-03T13:50:12.345"))
        val file = decodeBackup(text)
        assertEquals(full, file.backup)
        assertEquals(LocalDateTime.parse("2026-11-03T13:50:12"), file.exportedAt)
        assertEquals(0, file.skipped)
        assertTrue(text.contains("\"format\": \"$BACKUP_FORMAT\""))
        assertTrue("Korean stays readable in the file", text.contains("홍길동"))
    }

    @Test
    fun `an empty app makes a backup that reads back empty`() {
        val empty = AppBackup(DonorProfile(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptySet())
        assertEquals(empty, decodeBackup(encodeBackup(empty, LocalDateTime.parse("2026-11-03T09:00:00"))).backup)
        assertEquals("저장된 기록 없음", backupSummary(empty))
    }

    @Test
    fun `the summary counts what there is`() {
        assertEquals("헌혈 기록 2건 · 헌혈 계획 1건 · 약 복용 1건 · 질환·시술·접종 1건 · 체류 1건 · 즐겨찾기 2곳", backupSummary(full))
        assertEquals("헌혈 기록 2건", backupSummary(full.copy(plan = emptyList(), medications = emptyList(), diseases = emptyList(), stays = emptyList(), favoritePlaces = emptySet())))
    }

    @Test
    fun `the file name carries the date`() {
        assertEquals("헌혈기록_백업_20261103.json", backupFileName(d("2026-11-03")))
    }

    @Test
    fun `other files are refused with a message the donor can act on`() {
        val notJson = assertThrows(BackupFormatException::class.java) { decodeBackup("헌혈 기록") }
        assertTrue(notJson.message!!.contains("읽을 수 없어요"))
        val otherJson = assertThrows(BackupFormatException::class.java) { decodeBackup("""{"centers": []}""") }
        assertEquals("이 앱에서 내보낸 백업 파일이 아니에요.", otherJson.message)
        assertThrows(BackupFormatException::class.java) { decodeBackup("") }
        assertThrows(BackupFormatException::class.java) { decodeBackup("""{"format": "$BACKUP_FORMAT"}""") }
    }

    @Test
    fun `a backup from a newer app is refused, an older one read`() {
        val newer = assertThrows(BackupFormatException::class.java) {
            decodeBackup("""{"format": "$BACKUP_FORMAT", "version": ${BACKUP_VERSION + 1}}""")
        }
        assertTrue(newer.message!!.contains("업데이트"))
        val file = decodeBackup("""{"format": "$BACKUP_FORMAT", "version": 1}""")
        assertEquals(emptyList<DonationRecord>(), file.backup.donations)
        assertNull(file.exportedAt)
    }

    @Test
    fun `unreadable or repeated entries are skipped and counted, the rest kept`() {
        val file = decodeBackup(
            """
            {"format": "$BACKUP_FORMAT", "version": 1, "exportedAt": "어제",
             "profile": {"name": "  ", "sex": "OTHER", "weightKg": -3, "birthDate": "1999-02-30", "preferredProvince": " 부산 "},
             "donations": [
               {"id": 1, "type": "PLASMA", "date": "2026-09-20"},
               {"id": 2, "type": "BLOOD", "date": "2026-09-21"},
               {"id": 3, "type": "PLASMA"},
               {"id": 1, "type": "PLATELET", "date": "2026-10-01"},
               null
             ],
             "plan": [{"id": 8, "type": "PLASMA", "date": "2026-11-20"}, {"id": 9, "type": "PLATELET", "date": "2026-11-20"}],
             "medications": [{"id": 0, "ingredientName": "아스피린", "intakeDate": "2026-10-30", "restrictionDays": 3},
                             {"id": 0, "ingredientName": "피나스테라이드", "intakeDate": "2026-10-01", "restrictionDays": 30},
                             {"ingredientName": "이소트레티노인", "intakeDate": "2026-10-01"}],
             "stays": [{"id": 5, "regionName": "태국", "startDate": "2026-01-09", "endDate": "2026-01-02"}],
             "favoritePlaces": ["한마음/홍대점", " ", null]}
            """.trimIndent()
        )
        val backup = file.backup
        assertEquals(listOf(1L), backup.donations.map { it.id })
        assertEquals(listOf(8L), backup.plan.map { it.id })
        // Room fills in id 0, so two of them aren't a repeat; a missing restrictionDays is unreadable.
        assertEquals(listOf("아스피린", "피나스테라이드"), backup.medications.map { it.ingredientName })
        assertEquals(emptyList<StayRecord>(), backup.stays)
        assertEquals(setOf("한마음/홍대점"), backup.favoritePlaces)
        assertEquals(DonorProfile(preferredProvince = "부산"), backup.profile)
        assertNull(file.exportedAt)
        // Type BLOOD, no date, repeated id 1, null, the second plan on 11-20, 이소트레티노인, the backwards stay.
        assertEquals(7, file.skipped)
    }

    @Test
    fun `a stay keeps its defaults when the file leaves them out`() {
        val file = decodeBackup(
            """{"format": "$BACKUP_FORMAT", "version": 1,
                "stays": [{"id": 5, "regionName": "강원 철원군", "startDate": "2026-07-01", "endDate": "2026-07-02"}]}"""
        )
        val stay = file.backup.stays.single()
        assertEquals(false, stay.offshoreOnly)
        assertEquals(true, stay.visitedRiskArea)
    }
}
