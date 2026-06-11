package de.pixel.clashreminders.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary          = md_theme_light_primary,
    onPrimary        = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    secondary        = md_theme_light_secondary,
    background       = md_theme_light_background,
    surface          = md_theme_light_surface,
    error            = md_theme_light_error,
)

private val DarkColorScheme = darkColorScheme(
    primary          = md_theme_dark_primary,
    onPrimary        = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    secondary        = md_theme_dark_secondary,
    background       = md_theme_dark_background,
    surface          = md_theme_dark_surface,
    error            = md_theme_dark_error,
)

@Composable
fun ClashRemindersTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content,
    )
}
