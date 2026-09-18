package com.example.myapplication.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.loadThemeMode
import com.example.myapplication.data.saveThemeMode
import com.example.myapplication.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds the app-wide theme preference. Scoped to the activity rather than to a screen, because the
 * value has to be known above [com.example.myapplication.ui.theme.MyApplicationTheme].
 */
class ThemeViewModel(application: Application) : AndroidViewModel(application) {

    // Read synchronously rather than in a coroutine: the very first frame needs the saved theme,
    // and loading it asynchronously would paint that frame in the wrong one and flash on launch.
    private val _themeMode = MutableStateFlow(loadThemeMode(application))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setDarkTheme(dark: Boolean) {
        val mode = ThemeMode.forDarkSelection(dark)
        _themeMode.value = mode
        viewModelScope.launch(Dispatchers.IO) { saveThemeMode(getApplication(), mode) }
    }
}
