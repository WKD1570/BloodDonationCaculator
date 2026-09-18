package com.example.myapplication.data

import android.content.Context
import com.example.myapplication.model.MedicationRecord
import kotlinx.coroutines.flow.Flow

/** Read/write access to the saved 약물 복용 이력, backed by Room. */
class MedicationHistoryRepository(private val dao: MedicationHistoryDao) {

    constructor(context: Context) : this(AppDatabase.getInstance(context).medicationHistoryDao())

    /** Emits the full history, newest intake first, and re-emits on every write. */
    val medications: Flow<List<MedicationRecord>> = dao.observeAll()

    suspend fun getAll(): List<MedicationRecord> = dao.getAll()

    /**
     * Inserts a new entry, or replaces the existing row when [record] carries a non-zero id.
     * Returns the row id of the stored entry.
     */
    suspend fun save(record: MedicationRecord): Long = dao.upsert(record)

    suspend fun delete(record: MedicationRecord) = dao.delete(record)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun clear() = dao.clear()
}
