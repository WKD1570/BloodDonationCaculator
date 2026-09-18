package com.example.myapplication.model

/** The user's theme preference, which may defer to the device or override it outright. */
enum class ThemeMode {
    /** Follow the device setting. The state the app starts in until the user picks a side. */
    SYSTEM,
    LIGHT,
    DARK;

    fun isDark(systemInDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDarkTheme
        LIGHT -> false
        DARK -> true
    }

    companion object {
        /**
         * Maps a toggle position onto an explicit mode. Flipping the switch always commits to
         * LIGHT or DARK rather than returning to SYSTEM, so the choice survives the device
         * later switching itself.
         */
        fun forDarkSelection(dark: Boolean): ThemeMode = if (dark) DARK else LIGHT
    }
}
