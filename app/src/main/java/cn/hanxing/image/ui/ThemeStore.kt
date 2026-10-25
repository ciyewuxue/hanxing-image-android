package cn.hanxing.image.ui

import android.content.Context

/** 主题偏好：强调色 + 深浅模式，不涉及密钥，明文存即可。 */
class ThemeStore(context: Context) {

    private val prefs = context.getSharedPreferences("hanxing_theme", Context.MODE_PRIVATE)

    fun load(): ThemeSettings = ThemeSettings(
        accent = Accent.of(prefs.getString(KEY_ACCENT, null)),
        mode = ThemeMode.of(prefs.getString(KEY_MODE, null)),
    )

    fun save(settings: ThemeSettings) {
        prefs.edit()
            .putString(KEY_ACCENT, settings.accent.name)
            .putString(KEY_MODE, settings.mode.name)
            .apply()
    }

    private companion object {
        const val KEY_ACCENT = "accent"
        const val KEY_MODE = "mode"
    }
}
