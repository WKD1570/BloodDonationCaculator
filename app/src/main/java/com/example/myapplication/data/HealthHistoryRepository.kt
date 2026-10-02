package com.example.myapplication.data

import android.content.Context
import com.example.myapplication.model.DiseaseRecord
import com.example.myapplication.model.StayRecord
import kotlinx.coroutines.flow.Flow

/** Read/write access to the saved 감염병 이력 and 체류 이력, backed by Room. */
class HealthHistoryRepository(private val dao: HealthHistoryDao) {

    constructor(context: Context) : this(AppDatabase.getInstance(context).healthHistoryDao())

    val diseases: Flow<List<DiseaseRecord>> = dao.observeDiseases()

    val stays: Flow<List<StayRecord>> = dao.observeStays()

    suspend fun saveDisease(record: DiseaseRecord): Long = dao.upsertDisease(record)

    suspend fun saveStay(record: StayRecord): Long = dao.upsertStay(record)

    suspend fun saveStays(records: List<StayRecord>) = dao.upsertStays(records)

    suspend fun deleteDisease(record: DiseaseRecord) = dao.deleteDisease(record)

    suspend fun deleteStay(record: StayRecord) = dao.deleteStay(record)
}
