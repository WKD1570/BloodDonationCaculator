package com.example.myapplication.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.MedicationHistoryRepository
import com.example.myapplication.data.ProhibitedDrugRepository
import com.example.myapplication.domain.PROHIBITED_DRUGS
import com.example.myapplication.domain.nextEligibleDonationDate
import com.example.myapplication.domain.searchDrugs
import com.example.myapplication.model.RestrictedDrug
import com.example.myapplication.model.toMedicationRecord
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The outcome of applying a drug's restriction period to the date it was taken. */
sealed interface DonationEligibility {
    /** Donation is allowed again from [date] onwards. */
    data class EligibleFrom(val date: LocalDate) : DonationEligibility

    /** The drug carries a lifetime restriction, so there is no eligible date to show. */
    data object PermanentlyProhibited : DonationEligibility
}

data class ProhibitedDrugSearchUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val results: List<RestrictedDrug> = emptyList(),
    val selectedDrug: RestrictedDrug? = null,
    val medicationDate: LocalDate? = null,
    val eligibility: DonationEligibility? = null,
    /** Set when the bundled asset could not be read and the compiled-in list is being used. */
    val dataWarning: String? = null
)

class ProhibitedDrugSearchViewModel(
    application: Application,
    private val repository: ProhibitedDrugRepository,
    private val medicationRepository: MedicationHistoryRepository
) : AndroidViewModel(application) {

    // AndroidViewModelFactory looks for a constructor taking exactly (Application), so keep this
    // no-frills one alongside the injectable primary constructor used by tests.
    constructor(application: Application) : this(
        application,
        ProhibitedDrugRepository(application),
        MedicationHistoryRepository(application)
    )

    private val _uiState = MutableStateFlow(ProhibitedDrugSearchUiState())
    val uiState: StateFlow<ProhibitedDrugSearchUiState> = _uiState.asStateFlow()

    private var allDrugs: List<RestrictedDrug> = emptyList()

    /**
     * Row id of the history entry saved for the current selection, or 0 when nothing is saved yet.
     * Re-picking the date for the same drug updates that row instead of piling up duplicates.
     */
    private var savedRecordId: Long = 0

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val (drugs, warning) = try {
                repository.loadDrugs() to null
            } catch (e: Exception) {
                // The asset ships inside the APK, so a failure here is a packaging bug rather than
                // something the user can fix. Fall back to the compiled-in list so the calculator
                // still works, but say so instead of silently serving different data.
                PROHIBITED_DRUGS to "약물 목록 파일을 읽지 못해 기본 목록을 사용합니다."
            }
            allDrugs = drugs
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    dataWarning = warning,
                    results = searchDrugs(state.query, drugs)
                )
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query, results = searchDrugs(query, allDrugs)) }
    }

    /** Selecting a drug clears any previously entered date so a stale result is never shown. */
    fun onDrugSelected(drug: RestrictedDrug) {
        savedRecordId = 0
        _uiState.update {
            it.copy(selectedDrug = drug, medicationDate = null, eligibility = null)
        }
    }

    /**
     * Records the intake date and persists it to the medication history, which is what feeds the
     * integrated next-eligible-date calculation on the status screen.
     */
    fun onMedicationDateSelected(date: LocalDate) {
        val drug = _uiState.value.selectedDrug ?: return
        _uiState.update { it.copy(medicationDate = date, eligibility = eligibilityFor(drug, date)) }

        viewModelScope.launch {
            val stored = medicationRepository.save(
                drug.toMedicationRecord(date).copy(id = savedRecordId)
            )
            // Only adopt the id from the first insert: on the update path the returned row id is
            // not guaranteed to be meaningful, and overwriting a good id with it would orphan the
            // row and start duplicating on the next date change.
            if (savedRecordId == 0L) savedRecordId = stored
        }
    }

    fun clearSelection() {
        savedRecordId = 0
        _uiState.update {
            it.copy(selectedDrug = null, medicationDate = null, eligibility = null)
        }
    }

    fun clearQuery() {
        onQueryChange("")
    }

    private fun eligibilityFor(drug: RestrictedDrug, date: LocalDate): DonationEligibility =
        nextEligibleDonationDate(drug, date)
            ?.let(DonationEligibility::EligibleFrom)
            ?: DonationEligibility.PermanentlyProhibited
}
