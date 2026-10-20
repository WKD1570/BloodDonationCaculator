package com.example.myapplication.domain

import com.example.myapplication.model.BloodCenter
import com.example.myapplication.model.CenterMachines
import org.junit.Assert.assertEquals
import org.junit.Test

class BloodCenterMachinesTest {

    private fun center(
        name: String,
        plasma: Map<String, Int> = emptyMap(),
        platelet: Map<String, Int> = emptyMap(),
        address: String = "서울 중구 한강대로 405"
    ) = BloodCenter("서울중앙", name, "자체", address, "02-000-0000", CenterMachines(plasma, platelet))

    private val central = center("중앙센터", plasma = mapOf("Nexsys" to 1, "Aurora" to 2), platelet = mapOf("Amicus" to 1, "Trima" to 1))
    private val station = center("서울역센터", plasma = mapOf("PCS2" to 2), platelet = mapOf("Trima" to 1))
    private val sindorim = center("신도림테크노마트센터", plasma = mapOf("PCS2" to 1))
    private val jamsil = center("잠실역센터")
    private val ujangsan = BloodCenter("서울중앙", "우장산역센터", "자체", "서울 강서구 강서로45길 5", "02-000-0000", machines = null)
    private val all = listOf(central, station, sindorim, jamsil, ujangsan)

    @Test
    fun `혈소판 가능 keeps only centers with a 혈소판 machine`() {
        assertEquals(listOf(central, station), filterCenters(all, "", null, MachineFilter.AnyPlatelet))
    }

    @Test
    fun `a model chip keeps centers with that model, of either kind`() {
        assertEquals(listOf(central, station), filterCenters(all, "", null, MachineFilter.Model("Trima")))
        assertEquals(listOf(station, sindorim), filterCenters(all, "", null, MachineFilter.Model("PCS2")))
    }

    @Test
    fun `the machine filter combines with province and search`() {
        val seomyeon = center("서면센터", platelet = mapOf("Trima" to 2), address = "부산 부산진구 중앙대로 698")
        val jeonpo = center("전포센터", plasma = mapOf("PCS2" to 1), address = "부산 부산진구 전포대로 168")
        val centers = all + seomyeon + jeonpo

        assertEquals(listOf(seomyeon), filterCenters(centers, "", "부산", MachineFilter.Model("Trima")))
        // "역" also finds 잠실역센터 and 우장산역센터, which have no Trima.
        assertEquals(listOf(station), filterCenters(centers, "역", null, MachineFilter.Model("Trima")))
        assertEquals(centers, filterCenters(centers, "", null, null))
    }

    @Test
    fun `chips lead with 혈소판 가능, then 혈소판 models, then 혈장, the most widespread first`() {
        assertEquals(
            listOf(
                MachineFilter.AnyPlatelet to 2,
                MachineFilter.Model("Trima") to 2,
                MachineFilter.Model("Amicus") to 1,
                MachineFilter.Model("PCS2") to 2,
                MachineFilter.Model("Aurora") to 1, // ties with Nexsys on centers, has more machines
                MachineFilter.Model("Nexsys") to 1
            ),
            machineCounts(all)
        )
    }

    @Test
    fun `혈소판 가능 is left out when no center has a 혈소판 machine, and every chip without data`() {
        assertEquals(listOf(MachineFilter.Model("PCS2") to 1), machineCounts(listOf(sindorim, jamsil)))
        assertEquals(emptyList<Pair<MachineFilter, Int>>(), machineCounts(listOf(ujangsan)))
    }

    @Test
    fun `the row line counts each kind, with 없음 for a kind the center lacks`() {
        assertEquals("혈장 3 · 혈소판 2", machineSummary(central.machines))
        assertEquals("혈장 1 · 혈소판 없음", machineSummary(sindorim.machines))
        assertEquals("혈장 없음 · 혈소판 1", machineSummary(CenterMachines(emptyMap(), mapOf("Trima" to 1))))
        assertEquals("성분헌혈 기기 없음", machineSummary(jamsil.machines))
        assertEquals("기기 정보 없음", machineSummary(ujangsan.machines))
    }

    @Test
    fun `the open row lists each model with its count`() {
        assertEquals("Nexsys 1 · Aurora 2", modelList(mapOf("Nexsys" to 1, "Aurora" to 2)))
        assertEquals("없음", modelList(emptyMap()))
    }
}
