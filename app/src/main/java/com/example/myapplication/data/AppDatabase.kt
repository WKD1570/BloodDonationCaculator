package com.example.myapplication.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.myapplication.model.MedicationRecord

@Database(entities = [MedicationRecord::class], version = 1, exportSchema = false)
@TypeConverters(LocalDateConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun medicationHistoryDao(): MedicationHistoryDao

    companion object {
        private const val DB_NAME = "donation.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        DB_NAME
                    )
                    .build()
                    .also { instance = it }
            }
    }
}
