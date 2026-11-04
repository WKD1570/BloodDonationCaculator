package com.example.myapplication.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.example.myapplication.model.DiseaseRecord
import com.example.myapplication.model.StayRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthHistoryDao {

    /** Most recent treatment first; `id` breaks ties so same-day entries keep a stable order. */
    @Query("SELECT * FROM disease_history ORDER BY treatmentEndDate DESC, id DESC")
    fun observeDiseases(): Flow<List<DiseaseRecord>>

    /** Most recent stay first. */
    @Query("SELECT * FROM stay_history ORDER BY endDate DESC, id DESC")
    fun observeStays(): Flow<List<StayRecord>>

    /** Inserts a new row, or replaces the existing one when [DiseaseRecord.id] is non-zero. */
    @Upsert
    suspend fun upsertDisease(record: DiseaseRecord): Long

    /** Inserts a new row, or replaces the existing one when [StayRecord.id] is non-zero. */
    @Upsert
    suspend fun upsertStay(record: StayRecord): Long

    @Upsert
    suspend fun upsertStays(records: List<StayRecord>)

    @Delete
    suspend fun deleteDisease(record: DiseaseRecord)

    @Delete
    suspend fun deleteStay(record: StayRecord)

    // One-shot reads for the backup file and the morning reminder check, which don't observe.

    @Query("SELECT * FROM disease_history ORDER BY treatmentEndDate DESC, id DESC")
    suspend fun getAllDiseases(): List<DiseaseRecord>

    @Query("SELECT * FROM stay_history ORDER BY endDate DESC, id DESC")
    suspend fun getAllStays(): List<StayRecord>

    // Restoring a backup: the rows keep their ids, so the inserts run on emptied tables.

    @Query("DELETE FROM disease_history")
    suspend fun clearDiseases()

    @Query("DELETE FROM stay_history")
    suspend fun clearStays()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDiseases(records: List<DiseaseRecord>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStays(records: List<StayRecord>)
}
