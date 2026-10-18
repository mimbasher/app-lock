package com.turboo.applock.data

/** How the app picks between the light and dark colour schemes. */
enum class ThemeMode {
    /** Follow the device setting. The default. */
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}
