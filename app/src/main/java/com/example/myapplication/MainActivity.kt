package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.ui.BloodDonationCalculatorScreen
import com.example.myapplication.ui.ThemeViewModel
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val themeMode by themeViewModel.themeMode.collectAsState()
            // SYSTEM defers to the device here; LIGHT/DARK ignore it outright.
            val darkTheme = themeMode.isDark(isSystemInDarkTheme())

            // Re-apply edge-to-edge whenever the resolved theme flips, so the status and
            // navigation bar icons stay legible against the app's own background rather than
            // against whatever the device theme was at launch.
            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = systemBarStyle(darkTheme),
                    navigationBarStyle = systemBarStyle(darkTheme)
                )
            }

            MyApplicationTheme(darkTheme = darkTheme) {
                BloodDonationCalculatorScreen(
                    onToggleTheme = themeViewModel::setDarkTheme
                )
            }
        }
    }
}

/** Transparent bars either way; only the icon contrast differs. */
private fun systemBarStyle(darkTheme: Boolean): SystemBarStyle =
    if (darkTheme) {
        SystemBarStyle.dark(Color.TRANSPARENT)
    } else {
        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    }
