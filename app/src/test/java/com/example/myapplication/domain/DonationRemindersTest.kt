package com.example.myapplication.domain

import com.example.myapplication.model.DonationType
import com.example.myapplication.model.DonationType.PLASMA
import com.example.myapplication.model.DonationType.PLATELET
import com.example.myapplication.model.DonationType.WHOLE_BLOOD
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DonationRemindersTest {

    private fun d(text: String): LocalDate = LocalDate.parse(text)

    private val today = d("2026-11-03")
    private val label = mapOf(WHOLE_BLOOD to "전혈헌혈", PLASMA to "혈장성분헌혈", PLATELET to "혈소판성분헌혈")::getValue

    private fun dates(whole: String?, plasma: String?, platelet: String?) =
        mapOf(WHOLE_BLOOD to whole?.let(::d), PLASMA to plasma?.let(::d), PLATELET to platelet?.let(::d))

    private val nothingYet = ReminderMemory(eligibleTypes = emptySet(), remindedPlanDate = null)

    @Test
    fun `the check runs at 9 in the morning, later today or else tomorrow`() {
        val seoul = ZoneId.of("Asia/Seoul")
        fun at(text: String) = ZonedDateTime.of(java.time.LocalDateTime.parse(text), seoul)
        assertEquals(at("2026-11-03T09:00"), nextReminderCheck(at("2026-11-03T07:30")))
        assertEquals(at("2026-11-04T09:00"), nextReminderCheck(at("2026-11-03T09:00")))
        assertEquals(at("2026-11-04T09:00"), nextReminderCheck(at("2026-11-03T21:15")))
        assertEquals(at("2027-01-01T09:00"), nextReminderCheck(at("2026-12-31T23:59")))
    }

    @Test
    fun `turning reminders on only notes how things stand`() {
        val check = checkReminders(today, dates("2026-11-03", "2026-10-01", "2026-12-01"), emptyList(), previous = null)
        assertEquals(emptyList<Reminder>(), check.reminders)
        assertEquals(setOf(WHOLE_BLOOD, PLASMA), check.memory.eligibleTypes)
    }

    @Test
    fun `a type is reminded about the day it turns possible, and only then`() {
        val first = checkReminders(today, dates("2026-11-03", "2026-11-03", "2026-11-10"), emptyList(), nothingYet)
        assertEquals(listOf(Reminder.NowEligible(listOf(WHOLE_BLOOD, PLASMA))), first.reminders)

        val nextDay = checkReminders(today.plusDays(1), dates("2026-11-04", "2026-11-04", "2026-11-10"), emptyList(), first.memory)
        assertEquals(emptyList<Reminder>(), nextDay.reminders)
    }

    @Test
    fun `a check missed while the phone was off still reminds the next morning`() {
        val wasWaiting = ReminderMemory(eligibleTypes = setOf(WHOLE_BLOOD), remindedPlanDate = null)
        // 혈장 turned possible yesterday; today's date is floored at today, as nextEligibleByType does.
        val check = checkReminders(today, dates("2026-11-03", "2026-11-03", "2026-12-01"), emptyList(), wasWaiting)
        assertEquals(listOf(Reminder.NowEligible(listOf(PLASMA))), check.reminders)
    }

    @Test
    fun `donating again and waiting out the interval reminds again`() {
        val allPossible = ReminderMemory(setOf(WHOLE_BLOOD, PLASMA, PLATELET), null)
        val afterPlasma = checkReminders(today, dates("2026-12-29", "2026-11-17", "2026-11-17"), emptyList(), allPossible)
        assertEquals(emptyList<Reminder>(), afterPlasma.reminders)
        assertEquals(emptySet<DonationType>(), afterPlasma.memory.eligibleTypes)

        val twoWeeksOn = checkReminders(d("2026-11-17"), dates("2026-12-29", "2026-11-17", "2026-11-17"), emptyList(), afterPlasma.memory)
        assertEquals(listOf(Reminder.NowEligible(listOf(PLASMA, PLATELET))), twoWeeksOn.reminders)
    }

    @Test
    fun `a lifetime ban never reminds`() {
        val check = checkReminders(today, dates(null, null, null), emptyList(), nothingYet)
        assertEquals(emptyList<Reminder>(), check.reminders)
        assertEquals(emptySet<DonationType>(), check.memory.eligibleTypes)
    }

    @Test
    fun `tomorrow's plan is reminded about once`() {
        val plan = listOf(
            PlannedStatus(PLASMA, d("2026-11-04"), allowedFrom = d("2026-11-04")),
            PlannedStatus(PLATELET, d("2026-11-20"), allowedFrom = d("2026-11-18"))
        )
        val waiting = dates("2026-12-01", "2026-11-04", "2026-11-04")
        val morning = checkReminders(today, waiting, plan, nothingYet)
        assertEquals(listOf(Reminder.PlanTomorrow(plan[0])), morning.reminders)
        assertEquals(d("2026-11-04"), morning.memory.remindedPlanDate)

        // The same day again - an alarm put back after a reboot - says nothing new.
        assertEquals(emptyList<Reminder>(), checkReminders(today, waiting, plan, morning.memory).reminders)
    }

    @Test
    fun `the reminders read naturally`() {
        assertEquals(
            ReminderText(
                "이제 전혈헌혈·혈장성분헌혈을 할 수 있어요",
                "헌혈 간격과 약·질환·시술·접종·체류 제한이 모두 지났어요. 헌혈 장소 탭에서 가까운 곳을 찾아보세요."
            ),
            reminderText(Reminder.NowEligible(listOf(WHOLE_BLOOD, PLASMA)), label)
        )
        assertEquals(
            "내일 혈장성분헌혈 계획이 있어요",
            reminderText(Reminder.PlanTomorrow(PlannedStatus(PLASMA, d("2026-11-04"), d("2026-11-01"))), label).title
        )
        val tooSoon = reminderText(Reminder.PlanTomorrow(PlannedStatus(PLATELET, d("2026-11-04"), d("2026-11-09"))), label)
        assertEquals("내일 계획한 혈소판성분헌혈을 할 수 없어요", tooSoon.title)
        assertEquals("지금 기록으로는 11월 9일부터 할 수 있어요. 헌혈 계획에서 날짜를 바꿔 보세요.", tooSoon.body)
        val banned = reminderText(Reminder.PlanTomorrow(PlannedStatus(PLATELET, d("2026-11-04"), null)), label)
        assertEquals("내일 계획한 혈소판성분헌혈을 할 수 없어요", banned.title)
    }
}
