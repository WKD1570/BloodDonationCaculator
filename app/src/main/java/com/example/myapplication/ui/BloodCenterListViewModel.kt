package com.example.myapplication.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.BloodCenterDataException
import com.example.myapplication.data.BloodCenterRepository
import com.example.myapplication.domain.filterCenters
import com.example.myapplication.domain.provinceCounts
import com.example.myapplication.model.BloodCenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BloodCenterListUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val centers: List<BloodCenter> = emptyList(),
    /** Province chips: each province with its number of centers. */
    val provinces: List<Pair<String, Int>> = emptyList(),
    /** Date of the source CSV ("2026.07.01"), shown under the list. */
    val asOf: String? = null,
    val query: String = "",
    /** Null means 전체. */
    val province: String? = null,
    val expandedId: String? = null,
    /** [centers] narrowed by [province] and [query]. */
    val visible: List<BloodCenter> = emptyList()
)

/**
 * Backs the 헌혈의 집 tab: the bundled list of centers, filtered by province and search text.
 * Held at activity scope so the search and the open row survive switching tabs.
 */
class BloodCenterListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BloodCenterRepository(application)

    private val _uiState = MutableStateFlow(BloodCenterListUiState())
    val uiState: StateFlow<BloodCenterListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = try {
                val list = repository.load()
                BloodCenterListUiState(
                    isLoading = false,
                    centers = list.centers,
                    provinces = provinceCounts(list.centers),
                    asOf = list.asOf,
                    visible = list.centers
                )
            } catch (e: BloodCenterDataException) {
                BloodCenterListUiState(isLoading = false, error = "헌혈의 집 목록을 불러오지 못했어요.")
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query).refiltered() }
    }

    /** Picking the chip that's already selected goes back to 전체. */
    fun onProvinceSelect(province: String?) {
        _uiState.update { it.copy(province = if (province == it.province) null else province).refiltered() }
    }

    /** One row open at a time; tapping the open row closes it. */
    fun onCenterClick(center: BloodCenter) {
        _uiState.update { it.copy(expandedId = if (it.expandedId == center.id) null else center.id) }
    }

    private fun BloodCenterListUiState.refiltered() =
        copy(visible = filterCenters(centers, query, province))
}
