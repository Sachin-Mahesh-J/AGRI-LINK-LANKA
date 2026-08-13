package com.example.agriscout.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val ColorErrorDark = Color(0xFFFFB4AB)
private val ColorErrorContainerDark = Color(0xFF93000A)
private val ColorErrorContainerLight = Color(0xFFFFDAD6)

private val DarkColorScheme = darkColorScheme(
    primary = FieldGreen80,
    onPrimary = DeepCanopy,
    primaryContainer = DarkLeafContainer,
    onPrimaryContainer = PaleLeaf,
    secondary = LeafGreen80,
    onSecondary = DeepCanopy,
    secondaryContainer = SoilBrown40,
    onSecondaryContainer = CreamSurface,
    tertiary = HarvestGold80,
    onTertiary = DeepCanopy,
    tertiaryContainer = SoilBrown40,
    onTertiaryContainer = HarvestGold80,
    error = ColorErrorDark,
    onError = DeepCanopy,
    errorContainer = ColorErrorContainerDark,
    onErrorContainer = CreamSurface,
    background = DeepCanopy,
    onBackground = PaleLeaf,
    surface = DarkFieldSurface,
    onSurface = PaleLeaf,
    surfaceContainer = DarkLeafContainer,
    surfaceContainerLow = DeepCanopy,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceVariant = DarkLeafContainer,
    onSurfaceVariant = SoilBrown80,
    outline = SoilBrown80,
    outlineVariant = SoilBrown40
)

private val LightColorScheme = lightColorScheme(
    primary = FieldGreen40,
    onPrimary = CreamSurface,
    primaryContainer = PaleLeaf,
    onPrimaryContainer = DeepCanopy,
    secondary = LeafGreen40,
    onSecondary = DeepCanopy,
    secondaryContainer = SoilBrown80,
    onSecondaryContainer = DeepCanopy,
    tertiary = HarvestGold40,
    onTertiary = CreamSurface,
    tertiaryContainer = HarvestGold80,
    onTertiaryContainer = DeepCanopy,
    error = ErrorSeed,
    onError = CreamSurface,
    errorContainer = ColorErrorContainerLight,
    onErrorContainer = ErrorSeed,
    background = CreamSurface,
    onBackground = DeepCanopy,
    surface = CreamSurface,
    onSurface = DeepCanopy,
    surfaceContainer = PaleLeaf,
    surfaceContainerLow = MistGreen,
    surfaceContainerHigh = Color(0xFFE0EDD9),
    surfaceVariant = PaleLeaf,
    onSurfaceVariant = SoilBrown40,
    outline = ClayOutline,
    outlineVariant = SoilBrown80
)

private val AgriScoutShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun AgriScoutTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep a consistent agriculture palette for coursework screenshots and demos.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AgriScoutShapes,
        content = content
    )
}