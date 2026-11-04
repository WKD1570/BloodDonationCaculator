package com.example.myapplication.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import java.io.IOException
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A backup file is a few hundred KB at most; anything far bigger isn't one, so it isn't read in. */
private const val MAX_BACKUP_BYTES = 5 * 1024 * 1024

/**
 * Writes [AppBackup]s to, and reads them from, files the donor picks with the system file picker -
 * on the phone, on a memory card or in a cloud drive app. Nothing goes over the network from here.
 */
class BackupRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db get() = AppDatabase.getInstance(appContext)

    suspend fun write(uri: Uri, backup: AppBackup, exportedAt: LocalDateTime) = withContext(Dispatchers.IO) {
        val bytes = encodeBackup(backup, exportedAt).toByteArray(Charsets.UTF_8)
        val stream = appContext.contentResolver.openOutputStream(uri) ?: throw IOException("Could not open $uri")
        stream.use { it.write(bytes) }
    }

    suspend fun read(uri: Uri): BackupFile = withContext(Dispatchers.IO) {
        val stream = appContext.contentResolver.openInputStream(uri) ?: throw IOException("Could not open $uri")
        val bytes = stream.use { it.readNBytesCompat(MAX_BACKUP_BYTES + 1) }
        if (bytes.size > MAX_BACKUP_BYTES) throw BackupFormatException("백업 파일이 아니에요. 파일이 너무 커요.")
        // A file saved again by a text editor can start with a byte-order mark, which JSON doesn't allow.
        decodeBackup(bytes.toString(Charsets.UTF_8).removePrefix("﻿"))
    }

    /**
     * Replaces the 약·감염병·체류 이력 with [backup]'s in one transaction - all of it or none - and
     * the starred places. 마이페이지, 헌혈 기록 and 헌혈 계획 are the ViewModel's to save, since it
     * holds them in memory.
     */
    suspend fun replaceHistory(backup: AppBackup) {
        val medications = db.medicationHistoryDao()
        val health = db.healthHistoryDao()
        db.withTransaction {
            medications.clear()
            medications.insertAll(backup.medications)
            health.clearDiseases()
            health.insertDiseases(backup.diseases)
            health.clearStays()
            health.insertStays(backup.stays)
        }
        FavoritePlaceStore.get(appContext).replaceAll(backup.favoritePlaces)
    }
}

/** InputStream.readNBytes is API 33+; this reads up to [limit] bytes on any version. */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
