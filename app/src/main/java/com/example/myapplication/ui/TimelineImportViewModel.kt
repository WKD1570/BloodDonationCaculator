package com.example.myapplication.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.RegionShapeDataException
import com.example.myapplication.data.RegionShapeRepository
import com.example.myapplication.data.readTimelineFile
import com.example.myapplication.domain.TimelineStay
import com.example.myapplication.domain.extractStays
import java.io.FileNotFoundException
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface TimelineImportState {
    data object Idle : TimelineImportState
    data object Processing : TimelineImportState
    data class Failed(val message: String) : TimelineImportState

    /**
     * @param stays Stays in restricted regions, whether or not they restrict on their own.
     * @param failures Files (or zip entries) that weren't Timeline data, with why.
     */
    data class Done(
        val visitCount: Int,
        val skippedVisits: Int,
        val firstDate: LocalDate?,
        val lastDate: LocalDate?,
        val stays: List<TimelineStay>,
        val failures: List<Pair<String, String>>
    ) : TimelineImportState
}

/**
 * Reads Google Maps Timeline exports the user picks and finds the stays in restricted regions.
 * Everything happens on the device: files are read through the system file picker's content URIs
 * and matched against bundled region boundaries, with no geocoding service or other network call.
 * The visits are only held in memory; what's kept is the 체류 이력 the user chooses to save.
 */
class TimelineImportViewModel(application: Application) : AndroidViewModel(application) {

    private val shapeRepository = RegionShapeRepository(application)

    private val _state = MutableStateFlow<TimelineImportState>(TimelineImportState.Idle)
    val state: StateFlow<TimelineImportState> = _state.asStateFlow()

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _state.value = TimelineImportState.Processing
        viewModelScope.launch {
            _state.value = try {
                withContext(Dispatchers.Default) { read(uris) }
            } catch (e: RegionShapeDataException) {
                TimelineImportState.Failed("지역 경계 데이터를 불러오지 못했어요.")
            }
        }
    }

    fun clear() {
        _state.value = TimelineImportState.Idle
    }

    private suspend fun read(uris: List<Uri>): TimelineImportState {
        val locator = shapeRepository.loadLocator()
        val resolver = getApplication<Application>().contentResolver
        val results = uris.map { uri ->
            val name = displayName(uri)
            try {
                readTimelineFile(name) { resolver.openInputStream(uri) ?: throw FileNotFoundException(name) }
            } catch (e: IOException) {
                return TimelineImportState.Failed("$name 파일을 열지 못했어요.")
            } catch (e: SecurityException) {
                return TimelineImportState.Failed("$name 파일에 접근할 권한이 없어요.")
            }
        }
        val visits = results.flatMap { it.visits }
        val failures = results.flatMap { it.failures }
        if (visits.isEmpty() && results.none { it.filesRead > 0 }) {
            return TimelineImportState.Failed(failures.firstOrNull()?.second ?: "타임라인 방문 기록을 찾지 못했어요.")
        }
        return TimelineImportState.Done(
            visitCount = visits.size,
            skippedVisits = results.sumOf { it.skippedVisits },
            firstDate = visits.minOfOrNull { it.start }?.toLocalDate(),
            lastDate = visits.maxOfOrNull { it.end }?.toLocalDate(),
            stays = extractStays(visits, locator::locate),
            failures = failures
        )
    }

    private fun displayName(uri: Uri): String =
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: uri.lastPathSegment
            ?: "파일"
}
