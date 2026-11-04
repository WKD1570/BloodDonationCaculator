package com.example.myapplication.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.DrugProductDataException
import com.example.myapplication.data.DrugProductList
import com.example.myapplication.data.DrugProductRepository
import com.example.myapplication.data.MedicationHistoryRepository
import com.example.myapplication.data.ProhibitedDrugRepository
import com.example.myapplication.domain.PROHIBITED_DRUGS
import com.example.myapplication.domain.ProductIndex
import com.example.myapplication.domain.ProductMatch
import com.example.myapplication.domain.nextEligibleDonationDate
import com.example.myapplication.domain.searchDrugs
import com.example.myapplication.model.RestrictedDrug
import com.example.myapplication.model.toMedicationRecord
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    /**
     * Products from the product dictionary matching the query, each checked against the official
     * list - so a product name finds its ingredient's restriction, or shows it isn't listed.
     */
    val productResults: List<ProductMatch> = emptyList(),
    /** Where 상품명 search comes from ("의약품 226종 · 의약품 목록(2026.07.20)"); null when unavailable. */
    val productSource: String? = null,
    val selectedDrug: RestrictedDrug? = null,
    val medicationDate: LocalDate? = null,
    val eligibility: DonationEligibility? = null,
    /** Set when the bundled asset could not be read and the compiled-in list is being used. */
    val dataWarning: String? = null
)

class ProhibitedDrugSearchViewModel(
    application: Application,
    private val repository: ProhibitedDrugRepository,
    private val medicationRepository: MedicationHistoryRepository,
    private val productRepository: DrugProductRepository = DrugProductRepository(application)
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
    /** The product dictionary, checked against the list once (it holds a few thousand products). */
    private var productIndex: ProductIndex = ProductIndex.EMPTY

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
            // The product dictionary only widens search. Without it, ingredient search still works,
            // so a missing file just means no 상품명 results rather than a warning.
            val productList: DrugProductList? = try {
                productRepository.load()
            } catch (e: DrugProductDataException) {
                null
            }
            allDrugs = drugs
            productIndex = withContext(Dispatchers.Default) { ProductIndex(productList?.products.orEmpty(), drugs) }
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    dataWarning = warning,
                    results = searchDrugs(state.query, drugs),
                    productResults = productIndex.search(state.query),
                    productSource = productList?.let { list ->
                        "의약품 ${list.products.size}종" +
                            (list.sourceNote?.let { " · $it" } ?: list.asOf?.let { " ($it 자료)" } ?: "")
                    }
                )
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update {
            it.copy(
                query = query,
                results = searchDrugs(query, allDrugs),
                productResults = productIndex.search(query)
            )
        }
    }

    /**
     * A restricted product leads to its official entry, with the same 복용일 flow as searching it.
     * When the entry depends on why it was taken, the search shows that ingredient's entries
     * instead, so the donor picks the one that fits.
     */
    fun onProductSelected(match: ProductMatch) {
        val choices = match.purposeChoices
        if (choices.isNotEmpty()) onQueryChange(choices.first().ingredientName)
        else match.strictestRestriction?.let(::onDrugSelected)
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
