package com.example.myapplication.domain

import com.example.myapplication.model.DonationType
import com.example.myapplication.model.REGULAR_DONATION_TYPES
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** The reminders are checked once a day, from this time of the morning. */
val REMINDER_CHECK_TIME: LocalTime = LocalTime.of(9, 0)

/** The first [REMINDER_CHECK_TIME] after [now]: later today, or tomorrow once it has passed. */
fun nextReminderCheck(now: ZonedDateTime): ZonedDateTime {
    val today = now.toLocalDate().atTime(REMINDER_CHECK_TIME).atZone(now.zone)
    return if (today.isAfter(now)) today else today.plusDays(1)
}

/** What a check keeps for the next one, so it says each thing once. */
data class ReminderMemory(
    /** The regular types that could be given at the last check. */
    val eligibleTypes: Set<DonationType>,
    /** The planned day last reminded about, so a second check on the same day doesn't repeat it. */
    val remindedPlanDate: LocalDate?
)

/** One planned donation as the reminders need it, from a [PlanCheck]. */
data class PlannedStatus(val type: DonationType, val date: LocalDate, val allowedFrom: LocalDate?) {
    val isAllowed: Boolean get() = allowedFrom != null && !date.isBefore(allowedFrom)
}

sealed interface Reminder {
    /** [types] - in 현황's order - couldn't be given at the last check and can now. */
    data class NowEligible(val types: List<DonationType>) : Reminder

    /** [plan] is for tomorrow, and it may no longer be allowed. */
    data class PlanTomorrow(val plan: PlannedStatus) : Reminder
}

data class ReminderCheck(val reminders: List<Reminder>, val memory: ReminderMemory)

/**
 * The day's reminders, and what to remember for tomorrow's check.
 *
 * A type is reminded about when it turns possible - comparing with the last check rather than
 * looking for a date, so a check missed while the phone was off still reminds the next morning,
 * and a type that has long been possible doesn't remind every day. [nextDates] is
 * [nextEligibleByType] reduced to its dates, which take the 헌혈 간격 and the 약·감염병·체류
 * restrictions together; null is a lifetime ban.
 *
 * With no [previous] - the check run when reminders are turned on - nothing is reminded and only
 * the memory is set, since everything would otherwise look newly possible.
 */
fun checkReminders(
    today: LocalDate,
    nextDates: Map<DonationType, LocalDate?>,
    plan: List<PlannedStatus>,
    previous: ReminderMemory?
): ReminderCheck {
    val eligible = REGULAR_DONATION_TYPES.filter { type -> nextDates[type]?.let { !it.isAfter(today) } == true }
    val tomorrow = today.plusDays(1)
    if (previous == null) return ReminderCheck(emptyList(), ReminderMemory(eligible.toSet(), remindedPlanDate = null))

    val reminders = mutableListOf<Reminder>()
    val newlyEligible = eligible.filterNot { it in previous.eligibleTypes }
    if (newlyEligible.isNotEmpty()) reminders += Reminder.NowEligible(newlyEligible)

    val plannedTomorrow = plan.filter { it.date == tomorrow }
    if (previous.remindedPlanDate != tomorrow) plannedTomorrow.forEach { reminders += Reminder.PlanTomorrow(it) }

    val remindedPlanDate = if (plannedTomorrow.isNotEmpty()) tomorrow else previous.remindedPlanDate
    return ReminderCheck(reminders, ReminderMemory(eligible.toSet(), remindedPlanDate))
}

/** A notification's two lines. */
data class ReminderText(val title: String, val body: String)

private val shortDate = DateTimeFormatter.ofPattern("M월 d일")

/**
 * What [reminder] says, naming each type with [label] - 현황's "혈장성분헌혈" and so on, which all
 * end in 헌혈, so they all take 을.
 */
fun reminderText(reminder: Reminder, label: (DonationType) -> String): ReminderText = when (reminder) {
    is Reminder.NowEligible -> ReminderText(
        title = "이제 ${reminder.types.joinToString("·") { label(it) }}을 할 수 있어요",
        body = "헌혈 간격과 약·감염병·체류 제한이 모두 지났어요. 헌혈 장소 탭에서 가까운 곳을 찾아보세요."
    )
    is Reminder.PlanTomorrow -> {
        val plan = reminder.plan
        val type = label(plan.type)
        when {
            plan.isAllowed -> ReminderText(
                title = "내일 $type 계획이 있어요",
                body = "방문 전에 헌혈 장소의 운영 시간을 확인해 보세요."
            )
            plan.allowedFrom != null -> ReminderText(
                title = "내일 계획한 ${type}을 할 수 없어요",
                body = "지금 기록으로는 ${plan.allowedFrom.format(shortDate)}부터 할 수 있어요. 헌혈 계획에서 날짜를 바꿔 보세요."
            )
            else -> ReminderText(
                title = "내일 계획한 ${type}을 할 수 없어요",
                body = "영구 헌혈 금지에 해당하는 기록이 있어요. 의료 정보 탭을 확인해 보세요."
            )
        }
    }
}
