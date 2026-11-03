package com.example.myapplication.domain

import com.example.myapplication.model.BloodCenter
import com.example.myapplication.model.CenterMachines

/** A machine chip on the 헌혈 장소 tab: 혈소판 가능 - any 혈소판 machine - or one model of either kind. */
sealed interface MachineFilter {
    val label: String

    data object AnyPlatelet : MachineFilter {
        override val label = "혈소판 가능"
    }

    data class Model(val name: String) : MachineFilter {
        override val label: String get() = name
    }
}

/** Whether this center has what [filter] asks for. One the machines list doesn't cover never does. */
fun BloodCenter.has(filter: MachineFilter): Boolean {
    val machines = machines ?: return false
    return when (filter) {
        MachineFilter.AnyPlatelet -> machines.platelet.isNotEmpty()
        is MachineFilter.Model -> filter.name in machines.platelet || machines.plasma?.containsKey(filter.name) == true
    }
}

/**
 * The machine chips, each with how many of [centers] have it: 혈소판 가능 first, then the 혈소판
 * models, then the 혈장 ones, each kind with the model most centers have first - and on a tie, the
 * one with more machines in all. 혈장 gets no 가능 chip, since nearly every center has a 혈장 machine.
 */
fun machineCounts(centers: List<BloodCenter>): List<Pair<MachineFilter, Int>> {
    val known = centers.mapNotNull { it.machines }
    fun models(kind: (CenterMachines) -> Map<String, Int>): List<Pair<MachineFilter, Int>> =
        known.flatMap { kind(it).entries }
            .groupBy({ it.key }, { it.value })
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, List<Int>>> { it.value.size }.thenByDescending { it.value.sum() })
            .map { (model, counts) -> MachineFilter.Model(model) to counts.size }

    val withPlatelet = known.count { it.platelet.isNotEmpty() }
    val anyPlatelet = if (withPlatelet > 0) listOf(MachineFilter.AnyPlatelet to withPlatelet) else emptyList()
    return anyPlatelet + models { it.platelet } + models { it.plasma.orEmpty() }
}

/**
 * The chips worth showing for [centers]: those that narrow the list. A chip every place has - all
 * 19 헌혈카페 have Amicus, MCS and Trima - would select everything, so it's left out.
 */
fun narrowingMachineCounts(centers: List<BloodCenter>): List<Pair<MachineFilter, Int>> =
    machineCounts(centers).filter { (_, count) -> count < centers.size }

/**
 * The line under a center's address: "혈장 3 · 혈소판 2", with 없음 for a kind it has none of and
 * 정보 없음 for one its source doesn't report. A center with neither has 성분헌혈 기기 없음, and one
 * the machines list doesn't cover 기기 정보 없음.
 */
fun machineSummary(machines: CenterMachines?): String {
    if (machines == null) return "기기 정보 없음"
    val plasma = machines.plasma
    if (plasma != null && plasma.isEmpty() && machines.platelet.isEmpty()) return "성분헌혈 기기 없음"
    return "혈장 ${plasma?.let(::total) ?: "정보 없음"} · 혈소판 ${total(machines.platelet)}"
}

private fun total(models: Map<String, Int>): String = if (models.isEmpty()) "없음" else models.values.sum().toString()

/** One kind's models for a center's open row: "Nexsys 1 · Aurora 2", 없음, or 정보 없음 when unreported. */
fun modelList(models: Map<String, Int>?): String = when {
    models == null -> "정보 없음"
    models.isEmpty() -> "없음"
    else -> models.entries.joinToString(" · ") { (model, count) -> "$model $count" }
}
