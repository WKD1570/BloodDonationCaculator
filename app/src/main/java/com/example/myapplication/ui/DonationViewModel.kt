package com.example.myapplication.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.MedicationHistoryRepository
import com.example.myapplication.data.loadDonationRecords
import com.example.myapplication.data.loadDonorProfile
import com.example.myapplication.data.saveDonationRecords
import com.example.myapplication.data.saveDonorProfile
import com.example.myapplication.domain.IntegratedNextResult
import com.example.myapplication.domain.integratedNextEligible
import com.example.myapplication.model.DonationRecord
import com.example.myapplication.model.DonationType
import com.example.myapplication.model.DonorProfile
import com.example.myapplication.model.MedicationRecord
import com.example.myapplication.model.currentAge
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DonationUiState(
    val isLoading: Boolean = true,
    val profile: DonorProfile = DonorProfile(),
    val records: List<DonationRecord> = emptyList(),
    val medications: List<MedicationRecord> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    /** Next eligible date per type, with donation *and* medication history already combined. */
    val nextByType: Map<DonationType, IntegratedNextResult> = emptyMap()
)

/**
 * Owns the donor profile, the donation history and the medication history, and derives the
 * integrated next-eligible-date from all three.
 *
 * Profile and donation records still live in [com.example.myapplication.data.DonationStateStore]
 * (SharedPreferences); medication history lives in Room. Because the Room DAO exposes a Flow, a
 * medication saved from the drug-search screen reaches this ViewModel without either side holding
 * a reference to the other.
 */
class DonationViewModel(
    application: Application,
    private val medicationRepository: MedicationHistoryRepository
) : AndroidViewModel(application) {

    // AndroidViewModelFactory resolves the (Application) constructor; the two-arg one is for tests.
    constructor(application: Application) :
        this(application, MedicationHistoryRepository(application))

    private val context get() = getApplication<Application>()

    private val profile = MutableStateFlow(DonorProfile())
    private val records = MutableStateFlow<List<DonationRecord>>(emptyList())
    private val isLoading = MutableStateFlow(true)

    /** Captured once so the calendar and D-day labels do not shift mid-session. */
    private val today: LocalDate = LocalDate.now()

    val uiState: StateFlow<DonationUiState> = combine(
        profile,
        records,
        medicationRepository.medications,
        isLoading
    ) { profile, records, medications, loading ->
        DonationUiState(
            isLoading = loading,
            profile = profile,
            records = records,
            medications = medications,
            today = today,
            nextByType = DonationType.entries.associateWith { type ->
                integratedNextEligible(
                    type = type,
                    records = records,
                    medications = medications,
                    today = today,
                    donorAge = profile.currentAge(today)
                )
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DonationUiState(today = today)
    )

    init {
        viewModelScope.launch {
            // loadDonationRecords needs the profile: it uses it to fill in the fields that the
            // one-time legacy-format migration cannot recover from the old records themselves.
            val loadedProfile = withContext(Dispatchers.IO) { loadDonorProfile(context) }
            val loadedRecords = withContext(Dispatchers.IO) { loadDonationRecords(context, loadedProfile) }
            profile.value = loadedProfile
            records.value = loadedRecords
            isLoading.value = false
        }
    }

    fun updateProfile(updated: DonorProfile) {
        profile.value = updated
        viewModelScope.launch(Dispatchers.IO) { saveDonorProfile(context, updated) }
    }

    fun addRecord(record: DonationRecord) = persistRecords(records.value + record)

    fun updateRecord(record: DonationRecord) =
        persistRecords(records.value.map { if (it.id == record.id) record else it })

    fun deleteRecord(id: Long) = persistRecords(records.value.filterNot { it.id == id })

    private fun persistRecords(updated: List<DonationRecord>) {
        records.value = updated
        viewModelScope.launch(Dispatchers.IO) { saveDonationRecords(context, updated) }
    }

    fun deleteMedication(record: MedicationRecord) {
        viewModelScope.launch { medicationRepository.delete(record) }
    }
}
