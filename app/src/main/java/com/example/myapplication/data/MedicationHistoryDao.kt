package com.example.myapplication.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.example.myapplication.model.MedicationRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationHistoryDao {

    /** Newest intake first; `id` breaks ties so same-day entries keep a stable order. */
    @Query("SELECT * FROM medication_history ORDER BY intakeDate DESC, id DESC")
    fun observeAll(): Flow<List<MedicationRecord>>

    @Query("SELECT * FROM medication_history ORDER BY intakeDate DESC, id DESC")
    suspend fun getAll(): List<MedicationRecord>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: MedicationRecord): Long

    /** Inserts a new row, or replaces the existing one when [MedicationRecord.id] is non-zero. */
    @Upsert
    suspend fun upsert(record: MedicationRecord): Long

    @Delete
    suspend fun delete(record: MedicationRecord)

    @Query("DELETE FROM medication_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM medication_history")
    suspend fun clear()
}
