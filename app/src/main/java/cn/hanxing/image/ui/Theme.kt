package cn.hanxing.image.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** 强调色：只换主色与次色，深色底与浅色底各自配色。 */
enum class Accent(
    val label: String,
    val darkPrimary: Color,
    val lightPrimary: Color,
    val darkSecondary: Color,
    val lightSecondary: Color,
) {
    BLUE("星蓝", Color(0xFF8FB8FF), Color(0xFF2F63C8), Color(0xFFB9A7FF), Color(0xFF6446C8)),
    PURPLE("极光紫", Color(0xFFC6A8FF), Color(0xFF6B3FD4), Color(0xFF8FA8FF), Color(0xFF3A5BC0)),
    TEAL("青黛", Color(0xFF7FD6D0), Color(0xFF0E7A74), Color(0xFF9FC7E8), Color(0xFF2B6A9C)),
    GOLD("暖金", Color(0xFFF0C27B), Color(0xFF8F6412), Color(0xFFE8A28C), Color(0xFFA34F38));

    companion object {
        val all: List<Accent> = entries
        fun of(id: String?): Accent = all.firstOrNull { it.name == id } ?: BLUE
    }
}

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    DARK("深色"),
    LIGHT("浅色");

    companion object {
        val all: List<ThemeMode> = entries
        fun of(id: String?): ThemeMode = all.firstOrNull { it.name == id } ?: SYSTEM
    }
}

data class ThemeSettings(
    val accent: Accent = Accent.BLUE,
    val mode: ThemeMode = ThemeMode.SYSTEM,
)

private fun darkScheme(accent: Accent) = darkColorScheme(
    primary = accent.darkPrimary,
    onPrimary = Color(0xFF06111F),
    secondary = accent.darkSecondary,
    onSecondary = Color(0xFF120B24),
    background = Color(0xFF0A1020),
    onBackground = Color(0xFFE6ECFA),
    surface = Color(0xFF121A2B),
    onSurface = Color(0xFFE6ECFA),
    surfaceVariant = Color(0xFF1B2438),
    onSurfaceVariant = Color(0xFFA8B4CA),
    outline = Color(0xFF2C3A55),
    error = Color(0xFFFF8A8A),
    onError = Color(0xFF2A0808),
)

private fun lightScheme(accent: Accent) = lightColorScheme(
    primary = accent.lightPrimary,
    onPrimary = Color.White,
    secondary = accent.lightSecondary,
    onSecondary = Color.White,
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF121722),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121722),
    surfaceVariant = Color(0xFFE9EEF7),
    onSurfaceVariant = Color(0xFF55607A),
    outline = Color(0xFFC9D2E2),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

@Composable
fun HanxingTheme(settings: ThemeSettings, content: @Composable () -> Unit) {
    val dark = when (settings.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(
        colorScheme = if (dark) darkScheme(settings.accent) else lightScheme(settings.accent),
        typography = Typography(),
        content = content,
    )
    SystemBarsEffect()
}

/** 让状态栏、导航栏图标颜色跟着底色走。 */
@Composable
private fun SystemBarsEffect() {
    val view = LocalView.current
    if (view.isInEditMode) return
    val lightIcons = MaterialTheme.colorScheme.background.luminance() < 0.5f
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !lightIcons
            isAppearanceLightNavigationBars = !lightIcons
        }
    }
}
