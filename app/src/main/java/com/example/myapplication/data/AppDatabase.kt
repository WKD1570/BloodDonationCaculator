package com.example.myapplication.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.myapplication.model.DiseaseRecord
import com.example.myapplication.model.MedicationRecord
import com.example.myapplication.model.StayRecord

@Database(
    entities = [MedicationRecord::class, DiseaseRecord::class, StayRecord::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(LocalDateConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun medicationHistoryDao(): MedicationHistoryDao

    abstract fun healthHistoryDao(): HealthHistoryDao

    companion object {
        private const val DB_NAME = "donation.db"

        /** Adds 감염병 이력 and 체류 이력, keeping the medication history already saved. */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `disease_history` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`diseaseName` TEXT NOT NULL, " +
                        "`treatmentEndDate` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stay_history` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`regionName` TEXT NOT NULL, " +
                        "`startDate` TEXT NOT NULL, " +
                        "`endDate` TEXT NOT NULL, " +
                        "`offshoreOnly` INTEGER NOT NULL, " +
                        "`visitedRiskArea` INTEGER NOT NULL)"
                )
            }
        }

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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
