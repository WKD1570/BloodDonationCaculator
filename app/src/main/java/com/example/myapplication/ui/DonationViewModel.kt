package com.example.myapplication.ui

import android.app.Application
import android.database.SQLException
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.AppBackup
import com.example.myapplication.data.BackupFile
import com.example.myapplication.data.BackupFormatException
import com.example.myapplication.data.BackupRepository
import com.example.myapplication.data.FavoritePlaceStore
import com.example.myapplication.data.HealthHistoryRepository
import com.example.myapplication.data.InfectionRuleDataException
import com.example.myapplication.data.InfectionRuleRepository
import com.example.myapplication.data.MedicationHistoryRepository
import com.example.myapplication.data.backupSummary
import com.example.myapplication.data.loadDonationRecords
import com.example.myapplication.data.loadDonorProfile
import com.example.myapplication.data.loadPlannedDonations
import com.example.myapplication.data.saveDonationRecords
import com.example.myapplication.data.saveDonorProfile
import com.example.myapplication.data.savePlannedDonations
import com.example.myapplication.domain.HealthRestriction
import com.example.myapplication.domain.IntegratedNextResult
import com.example.myapplication.domain.PlanCheck
import com.example.myapplication.domain.checkPlan
import com.example.myapplication.domain.fillPlan
import com.example.myapplication.domain.healthRestrictions
import com.example.myapplication.domain.nextEligibleByType
import com.example.myapplication.domain.planWithout
import com.example.myapplication.domain.upcomingPlan
import com.example.myapplication.model.DiseaseRecord
import com.example.myapplication.model.DonationRecord
import com.example.myapplication.model.DonationType
import com.example.myapplication.model.DonorProfile
import com.example.myapplication.model.InfectionRules
import com.example.myapplication.model.MedicationRecord
import com.example.myapplication.model.PlannedDonation
import com.example.myapplication.model.StayRecord
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DonationUiState(
    val isLoading: Boolean = true,
    val profile: DonorProfile = DonorProfile(),
    val records: List<DonationRecord> = emptyList(),
    val medications: List<MedicationRecord> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val health: HealthHistoryState = HealthHistoryState(),
    /** Next eligible date per type, with donation, medication *and* health history combined. */
    val nextByType: Map<DonationType, IntegratedNextResult> = emptyMap(),
    /** 헌혈 계획: each planned donation from today on, in date order, with whether it's allowed. */
    val plan: List<PlanCheck> = emptyList()
)

/**
 * The 감염병·체류 이력 and the restrictions it carries. [rules] is null until the bundled
 * `blood_donation_rules.xml` has loaded - or for good when it can't, with [rulesWarning] saying so.
 */
data class HealthHistoryState(
    val rules: InfectionRules? = null,
    val rulesWarning: String? = null,
    val diseases: List<DiseaseRecord> = emptyList(),
    val stays: List<StayRecord> = emptyList(),
    val restrictions: List<HealthRestriction> = emptyList()
)

/** The 백업·복원 card on 마이페이지. */
data class BackupUiState(
    /** A file is being written or read; the buttons wait. */
    val busy: Boolean = false,
    /** What the last export or restore came to, shown under the buttons. */
    val message: String? = null,
    val isError: Boolean = false,
    /** A file read and waiting for the donor to confirm replacing everything with it. */
    val pending: BackupFile? = null
)

/**
 * Owns the donor profile, the donation history, the medication history and the 감염병·체류
 * history, and derives the integrated next-eligible-date from all of them. It also writes and
 * restores the backup file, since restoring replaces what it holds.
 *
 * Profile and donation records still live in [com.example.myapplication.data.DonationStateStore]
 * (SharedPreferences); medication and 감염병·체류 history live in Room. Because the Room DAO exposes a Flow, a
 * medication saved from the drug-search screen reaches this ViewModel without either side holding
 * a reference to the other.
 */
class DonationViewModel(
    application: Application,
    private val medicationRepository: MedicationHistoryRepository,
    private val healthRepository: HealthHistoryRepository = HealthHistoryRepository(application),
    private val ruleRepository: InfectionRuleRepository = InfectionRuleRepository(application),
    private val backupRepository: BackupRepository = BackupRepository(application)
) : AndroidViewModel(application) {

    // AndroidViewModelFactory resolves the (Application) constructor; the two-arg one is for tests.
    constructor(application: Application) :
        this(application, MedicationHistoryRepository(application))

    private val context get() = getApplication<Application>()

    private val profile = MutableStateFlow(DonorProfile())
    private val records = MutableStateFlow<List<DonationRecord>>(emptyList())
    private val plan = MutableStateFlow<List<PlannedDonation>>(emptyList())
    private var lastPlanId = 0L
    private val isLoading = MutableStateFlow(true)
    private val rules = MutableStateFlow<InfectionRules?>(null)
    private val rulesWarning = MutableStateFlow<String?>(null)

    private val _backupState = MutableStateFlow(BackupUiState())
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    /** Captured once so the calendar and D-day labels do not shift mid-session. */
    private val today: LocalDate = LocalDate.now()

    private val health = combine(
        rules,
        rulesWarning,
        healthRepository.diseases,
        healthRepository.stays
    ) { rules, warning, diseases, stays ->
        HealthHistoryState(
            rules = rules,
            rulesWarning = warning,
            diseases = diseases,
            stays = stays,
            restrictions = rules?.let { healthRestrictions(diseases, stays, it) }.orEmpty()
        )
    }

    private val recordsAndPlan = combine(records, plan) { records, plan -> records to plan }

    val uiState: StateFlow<DonationUiState> = combine(
        profile,
        recordsAndPlan,
        medicationRepository.medications,
        health,
        isLoading
    ) { profile, (records, plan), medications, health, loading ->
        DonationUiState(
            isLoading = loading,
            profile = profile,
            records = records,
            medications = medications,
            today = today,
            health = health,
            nextByType = nextEligibleByType(records, medications, today, health.restrictions),
            plan = checkPlan(plan, records, medications, health.restrictions)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DonationUiState(today = today)
    )

    init {
        viewModelScope.launch {
            try {
                rules.value = ruleRepository.loadRules()
            } catch (e: InfectionRuleDataException) {
                rulesWarning.value = "감염병·체류 기준을 불러오지 못해 다음 헌혈 가능일에 반영하지 못했어요."
            }
        }
        viewModelScope.launch {
            // loadDonationRecords needs the profile: it uses it to fill in the fields that the
            // one-time legacy-format migration cannot recover from the old records themselves.
            val loadedProfile = withContext(Dispatchers.IO) { loadDonorProfile(context) }
            val loadedRecords = withContext(Dispatchers.IO) { loadDonationRecords(context, loadedProfile) }
            val savedPlan = withContext(Dispatchers.IO) { loadPlannedDonations(context) }
            profile.value = loadedProfile
            records.value = loadedRecords
            lastPlanId = savedPlan.maxOfOrNull { it.id } ?: 0L
            val upcoming = upcomingPlan(savedPlan, today)
            if (upcoming.size == savedPlan.size) plan.value = upcoming else persistPlan(upcoming)
            isLoading.value = false
        }
    }

    fun updateProfile(updated: DonorProfile) {
        profile.value = updated
        viewModelScope.launch(Dispatchers.IO) { saveDonorProfile(context, updated) }
    }

    fun addRecord(record: DonationRecord) {
        persistRecords(records.value + record)
        dropPlannedFor(record)
    }

    fun updateRecord(record: DonationRecord) {
        persistRecords(records.value.map { if (it.id == record.id) record else it })
        dropPlannedFor(record)
    }

    fun deleteRecord(id: Long) = persistRecords(records.value.filterNot { it.id == id })

    private fun persistRecords(updated: List<DonationRecord>) {
        records.value = updated
        viewModelScope.launch(Dispatchers.IO) { saveDonationRecords(context, updated) }
    }

    /** A donation made as planned takes its planned day and type off the plan. */
    private fun dropPlannedFor(record: DonationRecord) {
        val remaining = planWithout(plan.value, record)
        if (remaining.size != plan.value.size) persistPlan(remaining)
    }

    /** Tapping a day on 헌혈 계획: plans [type] there, or takes off what's already planned that day. */
    fun togglePlannedDay(date: LocalDate, type: DonationType) {
        if (date.isBefore(today)) return
        val current = plan.value
        persistPlan(
            if (current.any { it.date == date }) current.filterNot { it.date == date }
            else current + PlannedDonation(newPlanId(), type, date)
        )
    }

    fun removePlanned(id: Long) = persistPlan(plan.value.filterNot { it.id == id })

    fun clearPlan() = persistPlan(emptyList())

    /** 가능한 날짜로 채우기: [type] on every earliest day it fits from today through [months] months on. */
    fun fillEarliest(type: DonationType, months: Long) {
        val state = uiState.value
        persistPlan(
            fillPlan(
                type, today, today.plusMonths(months), plan.value,
                records.value, state.medications, state.health.restrictions
            ) { newPlanId() }
        )
    }

    /** Unique even when [fillEarliest] makes several within the same millisecond. */
    private fun newPlanId(): Long {
        lastPlanId = maxOf(lastPlanId + 1, System.currentTimeMillis())
        return lastPlanId
    }

    private fun persistPlan(updated: List<PlannedDonation>) {
        plan.value = updated
        viewModelScope.launch(Dispatchers.IO) { savePlannedDonations(context, updated) }
    }

    fun deleteMedication(record: MedicationRecord) {
        viewModelScope.launch { medicationRepository.delete(record) }
    }

    fun saveDisease(record: DiseaseRecord) {
        viewModelScope.launch { healthRepository.saveDisease(record) }
    }

    fun deleteDisease(record: DiseaseRecord) {
        viewModelScope.launch { healthRepository.deleteDisease(record) }
    }

    fun saveStay(record: StayRecord) {
        viewModelScope.launch { healthRepository.saveStay(record) }
    }

    fun saveStays(records: List<StayRecord>) {
        viewModelScope.launch { healthRepository.saveStays(records) }
    }

    fun deleteStay(record: StayRecord) {
        viewModelScope.launch { healthRepository.deleteStay(record) }
    }

    /**
     * Writes everything the donor entered to [uri], a file they just created with the system picker.
     * Waits for the first load, so an export tapped right at launch can't come out empty.
     */
    fun exportBackup(uri: Uri) {
        if (_backupState.value.busy) return
        _backupState.value = BackupUiState(busy = true)
        viewModelScope.launch {
            isLoading.first { loading -> !loading }
            _backupState.value = try {
                val backup = AppBackup(
                    profile = profile.value,
                    donations = records.value,
                    plan = plan.value,
                    medications = medicationRepository.getAll(),
                    diseases = healthRepository.getAllDiseases(),
                    stays = healthRepository.getAllStays(),
                    favoritePlaces = FavoritePlaceStore.get(context).ids.value
                )
                backupRepository.write(uri, backup, LocalDateTime.now())
                BackupUiState(message = "백업 파일을 저장했어요. ${backupSummary(backup)}")
            } catch (e: IOException) {
                BackupUiState(message = "파일을 저장하지 못했어요. 다른 위치를 골라 다시 해 보세요.", isError = true)
            } catch (e: SecurityException) {
                BackupUiState(message = "파일을 저장하지 못했어요. 다른 위치를 골라 다시 해 보세요.", isError = true)
            }
        }
    }

    /** Reads [uri] and, when it's a backup, holds it in [BackupUiState.pending] for the donor to confirm. */
    fun readBackup(uri: Uri) {
        if (_backupState.value.busy) return
        _backupState.value = BackupUiState(busy = true)
        viewModelScope.launch {
            _backupState.value = try {
                BackupUiState(pending = backupRepository.read(uri))
            } catch (e: BackupFormatException) {
                BackupUiState(message = e.message, isError = true)
            } catch (e: IOException) {
                BackupUiState(message = "파일을 열지 못했어요.", isError = true)
            } catch (e: SecurityException) {
                BackupUiState(message = "파일을 열지 못했어요.", isError = true)
            }
        }
    }

    fun cancelRestore() {
        _backupState.value = BackupUiState()
    }

    /**
     * Replaces everything with the pending backup: the Room history first, in one transaction, so a
     * failure there leaves the app as it was; then 마이페이지, 헌혈 기록 and 헌혈 계획.
     */
    fun confirmRestore() {
        val file = _backupState.value.pending ?: return
        _backupState.value = BackupUiState(busy = true)
        viewModelScope.launch {
            isLoading.first { loading -> !loading }
            val backup = file.backup
            _backupState.value = try {
                backupRepository.replaceHistory(backup)
                withContext(Dispatchers.IO) {
                    saveDonorProfile(context, backup.profile)
                    saveDonationRecords(context, backup.donations)
                }
                profile.value = backup.profile
                records.value = backup.donations
                lastPlanId = maxOf(lastPlanId, backup.plan.maxOfOrNull { it.id } ?: 0L)
                persistPlan(upcomingPlan(backup.plan, today))
                BackupUiState(message = "백업을 불러왔어요. ${backupSummary(backup)}")
            } catch (e: SQLException) {
                BackupUiState(message = "백업을 불러오지 못했어요. 지금 기록은 그대로예요.", isError = true)
            }
        }
    }
}
