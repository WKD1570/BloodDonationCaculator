package com.example.myapplication.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.BLOOD_CAFES_ASSET
import com.example.myapplication.data.BLOOD_CENTERS_ASSET
import com.example.myapplication.data.BloodCenterDataException
import com.example.myapplication.data.BloodCenterList
import com.example.myapplication.data.BloodCenterRepository
import com.example.myapplication.data.FavoritePlaceStore
import com.example.myapplication.domain.MachineFilter
import com.example.myapplication.domain.filterCenters
import com.example.myapplication.domain.narrowingMachineCounts
import com.example.myapplication.domain.provinceCounts
import com.example.myapplication.model.BloodCenter
import com.example.myapplication.model.DonationPlaceKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BloodCenterListUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    /** Which list is showing: 헌혈의 집 or 헌혈카페. */
    val kind: DonationPlaceKind = DonationPlaceKind.CENTER,
    /** How many places each kind has, for the toggle. A kind that failed to load is missing. */
    val kindCounts: Map<DonationPlaceKind, Int> = emptyMap(),
    /** Every place of [kind]. */
    val centers: List<BloodCenter> = emptyList(),
    /** Province chips: each province with its number of places. */
    val provinces: List<Pair<String, Int>> = emptyList(),
    /** Machine chips: 혈소판 가능 and each model with how many places have it - only those that narrow the list. */
    val machines: List<Pair<MachineFilter, Int>> = emptyList(),
    /** Date of the source file ("2026.07.01"), shown under the list. */
    val asOf: String? = null,
    /** Date of the machines CSV ("2026.10.14"), shown under the list with its credit. */
    val machinesAsOf: String? = null,
    val query: String = "",
    /** Null means 전체. */
    val province: String? = null,
    /** Null means no machine chip is picked. */
    val machine: MachineFilter? = null,
    val expandedId: String? = null,
    /** [centers] narrowed by [province], [machine] and [query]. */
    val visible: List<BloodCenter> = emptyList(),
    /** Ids of the starred places, of either kind; listed first under 즐겨찾기. */
    val favorites: Set<String> = emptySet()
)

/**
 * Backs the 헌혈 장소 tab: the bundled 헌혈의 집 and 헌혈카페 lists, one at a time, filtered by
 * province, machine and search text. Held at activity scope so the kind, the search and the open
 * row survive switching tabs.
 */
class BloodCenterListViewModel(application: Application) : AndroidViewModel(application) {

    private val favoriteStore = FavoritePlaceStore.get(application)

    private val repositories = mapOf(
        DonationPlaceKind.CENTER to BloodCenterRepository(application, BLOOD_CENTERS_ASSET),
        DonationPlaceKind.CAFE to BloodCenterRepository(application, BLOOD_CAFES_ASSET)
    )

    /** Each kind's list once loaded. One that failed is missing, and the other still shows. */
    private var lists: Map<DonationPlaceKind, BloodCenterList> = emptyMap()

    private val _uiState = MutableStateFlow(BloodCenterListUiState())
    val uiState: StateFlow<BloodCenterListUiState> = _uiState.asStateFlow()

    init {
        // A restored backup replaces the stars too, so they're followed rather than read once.
        viewModelScope.launch {
            favoriteStore.ids.collect { ids -> _uiState.update { it.copy(favorites = ids) } }
        }
        viewModelScope.launch {
            val loaded = mutableMapOf<DonationPlaceKind, BloodCenterList>()
            repositories.forEach { (kind, repository) ->
                try {
                    loaded[kind] = repository.load()
                } catch (e: BloodCenterDataException) {
                    // Left out: its toggle shows no count, and picking it shows the error.
                }
            }
            lists = loaded
            _uiState.update {
                it.copy(isLoading = false, kindCounts = loaded.mapValues { (_, list) -> list.centers.size }).showing(it.kind)
            }
        }
    }

    /**
     * Switches between 헌혈의 집 and 헌혈카페. The search and the province carry over - "수원" means
     * the same in both - unless the other kind has nothing in that province. The machine chip and
     * the open row don't, since the chips and rows differ.
     */
    fun onKindSelect(kind: DonationPlaceKind) {
        _uiState.update { if (it.kind == kind) it else it.showing(kind) }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query).refiltered() }
    }

    /** Picking the chip that's already selected goes back to 전체. */
    fun onProvinceSelect(province: String?) {
        _uiState.update { it.copy(province = if (province == it.province) null else province).refiltered() }
    }

    /** Picking the chip that's already selected clears it, like null does. */
    fun onMachineSelect(machine: MachineFilter?) {
        _uiState.update { it.copy(machine = if (machine == it.machine) null else machine).refiltered() }
    }

    /** One row open at a time; tapping the open row closes it. */
    fun onCenterClick(center: BloodCenter) {
        _uiState.update { it.copy(expandedId = if (it.expandedId == center.id) null else center.id) }
    }

    /** Stars [center], or unstars it. Saved right away; the list follows through [favoriteStore]. */
    fun onFavoriteToggle(center: BloodCenter) = favoriteStore.toggle(center.id)

    /** This state switched to [kind]'s list. While the lists are still loading, only the kind changes. */
    private fun BloodCenterListUiState.showing(kind: DonationPlaceKind): BloodCenterListUiState {
        if (isLoading) return copy(kind = kind)
        val list = lists[kind] ?: return copy(
            kind = kind,
            error = "${kind.label} 목록을 불러오지 못했어요.",
            centers = emptyList(),
            provinces = emptyList(),
            machines = emptyList(),
            asOf = null,
            machinesAsOf = null,
            province = null,
            machine = null,
            expandedId = null,
            visible = emptyList()
        )
        val provinces = provinceCounts(list.centers)
        return copy(
            kind = kind,
            error = null,
            centers = list.centers,
            provinces = provinces,
            machines = narrowingMachineCounts(list.centers),
            asOf = list.asOf,
            machinesAsOf = list.machinesAsOf,
            province = province?.takeIf { picked -> provinces.any { it.first == picked } },
            machine = null,
            expandedId = null
        ).refiltered()
    }

    private fun BloodCenterListUiState.refiltered() =
        copy(visible = filterCenters(centers, query, province, machine))
}
