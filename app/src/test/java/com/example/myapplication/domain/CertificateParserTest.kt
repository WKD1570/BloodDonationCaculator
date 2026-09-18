package com.example.myapplication.domain

import com.example.myapplication.model.DonationType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CertificateParserTest {

    @Test
    fun `extracts certificate number in dash-separated format`() {
        val result = parseBloodDonationInfo("증서번호 12-26-112916-01")
        assertEquals("12-26-112916-01", result.certNumber)
    }

    @Test
    fun `extracts donation date as LocalDate`() {
        val result = parseBloodDonationInfo("헌혈일자 2026. 7. 21.")
        assertEquals(LocalDate.of(2026, 7, 21), result.donationDate)
    }

    @Test
    fun `extracts raw donation type text including volume`() {
        val result = parseBloodDonationInfo("헌혈종류 전혈 400mL")
        assertEquals("전혈 400mL", result.donationType)
    }

    @Test
    fun `extracts blood center name with phone number`() {
        val result = parseBloodDonationInfo("대구경북혈액원(053 605 5662)")
        assertEquals("대구경북혈액원(053 605 5662)", result.centerName)
    }

    @Test
    fun `does not extract name or birth date - only the 4 supported fields exist on the data class`() {
        val result = parseBloodDonationInfo(
            """
            성명: 장영록
            생년월일: 2001. 12. 6.
            헌혈종류: 전혈 400mL
            헌혈일자: 2026. 7. 21.
            증서번호: 12-26-112916-01
            대구경북혈액원(053 605 5662)
            """.trimIndent()
        )
        assertEquals("12-26-112916-01", result.certNumber)
        assertEquals("전혈 400mL", result.donationType)
        assertEquals(LocalDate.of(2026, 7, 21), result.donationDate)
        assertEquals("대구경북혈액원(053 605 5662)", result.centerName)
    }

    @Test
    fun `parses a full certificate block modeled on a real donation certificate`() {
        val result = parseBloodDonationInfo(
            """
            헌혈증서
            증서번호 12-26-112916-01
            성명 장영록
            생년월일 2001. 12. 6.
            헌혈종류 전혈 400mL
            헌혈일자 2026. 7. 21.
            대구경북혈액원(053 605 5662)
            """.trimIndent()
        )
        assertEquals("12-26-112916-01", result.certNumber)
        assertEquals("전혈 400mL", result.donationType)
        assertEquals(LocalDate.of(2026, 7, 21), result.donationDate)
        assertEquals("대구경북혈액원(053 605 5662)", result.centerName)
    }

    @Test
    fun `parses text transcribed from the actual reference certificate photo (20260916_005524_jpg)`() {
        // Labels on the real photo are colon-separated ("성명: 장영록"), unlike the earlier
        // space-separated modeled test, and the center name has no "헌혈장소" label at all.
        val result = parseBloodDonationInfo(
            """
            헌혈증서
            증서번호: 12-26-112916-01
            성명: 장영록
            생년월일: 2001. 12. 6.
            성별: 남
            헌혈종류: 전혈 400mL
            사랑의 헌혈에 동참하여 생명 나눔을 몸소 실천하신 귀하에게
            깊은 존경과 감사의 마음을 담아 이 증서를 드립니다.
            헌혈일자: 2026. 7. 21.
            대구경북혈액원(053 605 5662)
            보건복지부장관
            """.trimIndent()
        )
        assertEquals("12-26-112916-01", result.certNumber)
        assertEquals("전혈 400mL", result.donationType)
        assertEquals(LocalDate.of(2026, 7, 21), result.donationDate)
        assertEquals("대구경북혈액원(053 605 5662)", result.centerName)
        assertEquals(DonationType.WHOLE_BLOOD, result.donationTypeEnum())
        assertEquals(430, result.drawnVolumeMl())
    }

    @Test
    fun `extracts stated volume from donation type text`() {
        assertEquals(400, BloodDonationInfo(donationType = "전혈 400mL").statedVolumeMl())
    }

    @Test
    fun `adds 30ml diagnostic draw to a standard whole blood donation`() {
        assertEquals(430, BloodDonationInfo(donationType = "전혈 400mL").drawnVolumeMl())
    }

    @Test
    fun `adds 30ml diagnostic draw to a reduced whole blood donation`() {
        assertEquals(350, BloodDonationInfo(donationType = "전혈 320mL").drawnVolumeMl())
    }

    @Test
    fun `does not add the diagnostic draw for non whole blood types`() {
        assertEquals(600, BloodDonationInfo(donationType = "혈장성분헌혈 600mL").drawnVolumeMl())
    }

    @Test
    fun `drawnVolumeMl is null when no stated volume could be parsed`() {
        assertNull(BloodDonationInfo(donationType = "전혈헌혈").drawnVolumeMl())
    }

    @Test
    fun `unrecognized text yields all null fields without crashing`() {
        val result = parseBloodDonationInfo("아무 의미 없는 텍스트 블록입니다")
        assertNull(result.certNumber)
        assertNull(result.donationType)
        assertNull(result.donationDate)
        assertNull(result.centerName)
    }

    @Test
    fun `empty text yields all null fields`() {
        val result = parseBloodDonationInfo("")
        assertNull(result.certNumber)
        assertNull(result.donationType)
    }

    @Test
    fun `maps whole blood donation type text to enum`() {
        assertEquals(DonationType.WHOLE_BLOOD, BloodDonationInfo(donationType = "전혈 400mL").donationTypeEnum())
    }

    @Test
    fun `maps plasma donation type text to enum`() {
        assertEquals(DonationType.PLASMA, BloodDonationInfo(donationType = "혈장성분헌혈").donationTypeEnum())
    }

    @Test
    fun `maps platelet donation type text to enum`() {
        assertEquals(DonationType.PLATELET, BloodDonationInfo(donationType = "혈소판성분헌혈").donationTypeEnum())
    }

    @Test
    fun `unrecognized donation type text maps to null enum`() {
        assertNull(BloodDonationInfo(donationType = "알수없음").donationTypeEnum())
    }
}
