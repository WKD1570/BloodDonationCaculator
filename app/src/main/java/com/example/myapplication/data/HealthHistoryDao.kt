package com.example.myapplication.data

import androidx.room.Dao
import androidx.room.Delete
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
}
