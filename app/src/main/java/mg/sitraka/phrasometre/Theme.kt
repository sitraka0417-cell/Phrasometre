package mg.sitraka.phrasometre

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat

private val Clair = lightColorScheme(
    primary = Color(0xFF0E6B68),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFEDEA),
    onPrimaryContainer = Color(0xFF00201F),
    secondary = Color(0xFF4A6362),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E6),
    onSecondaryContainer = Color(0xFF05201F),
    tertiary = Color(0xFF8A5100),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB0),
    onTertiaryContainer = Color(0xFF2B1700),
    background = Color(0xFFF5F7F6),
    onBackground = Color(0xFF171D1C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171D1C),
    surfaceVariant = Color(0xFFDDE5E3),
    onSurfaceVariant = Color(0xFF3F4948),
    outline = Color(0xFF6F7978),
    outlineVariant = Color(0xFFC4CCCA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F4F3),
    surfaceContainer = Color(0xFFEAEFEE),
    surfaceContainerHigh = Color(0xFFE4E9E8),
    surfaceContainerHighest = Color(0xFFDEE4E2)
)

private val Sombre = darkColorScheme(
    primary = Color(0xFF7FD6D1),
    onPrimary = Color(0xFF003735),
    primaryContainer = Color(0xFF00504D),
    onPrimaryContainer = Color(0xFFCFEDEA),
    secondary = Color(0xFFB1CCCA),
    onSecondary = Color(0xFF1C3534),
    secondaryContainer = Color(0xFF334B4A),
    onSecondaryContainer = Color(0xFFCCE8E6),
    tertiary = Color(0xFFFFB95C),
    onTertiary = Color(0xFF472A00),
    tertiaryContainer = Color(0xFF5E3A00),
    onTertiaryContainer = Color(0xFFFFDDB0),
    background = Color(0xFF0E1413),
    onBackground = Color(0xFFDEE4E2),
    surface = Color(0xFF151C1B),
    onSurface = Color(0xFFDEE4E2),
    surfaceVariant = Color(0xFF3F4948),
    onSurfaceVariant = Color(0xFFBEC9C7),
    outline = Color(0xFF899392),
    outlineVariant = Color(0xFF3F4948),
    surfaceContainerLowest = Color(0xFF0A0F0F),
    surfaceContainerLow = Color(0xFF131A19),
    surfaceContainer = Color(0xFF182020),
    surfaceContainerHigh = Color(0xFF212928),
    surfaceContainerHighest = Color(0xFF2B3332)
)

// Thème « Crépuscule » : violet profond et corail
private val Special = darkColorScheme(
    primary = Color(0xFFD0A8FF),
    onPrimary = Color(0xFF3A1B66),
    primaryContainer = Color(0xFF52318A),
    onPrimaryContainer = Color(0xFFEBDCFF),
    secondary = Color(0xFFCDBDE6),
    onSecondary = Color(0xFF342A4A),
    secondaryContainer = Color(0xFF4B4062),
    onSecondaryContainer = Color(0xFFE9DDFF),
    tertiary = Color(0xFFFFB38A),
    onTertiary = Color(0xFF512300),
    tertiaryContainer = Color(0xFF7A3B1E),
    onTertiaryContainer = Color(0xFFFFDBCB),
    background = Color(0xFF150F26),
    onBackground = Color(0xFFE8E0F5),
    surface = Color(0xFF1F1738),
    onSurface = Color(0xFFE8E0F5),
    surfaceVariant = Color(0xFF3A2F57),
    onSurfaceVariant = Color(0xFFCDC3E0),
    outline = Color(0xFF968CAD),
    outlineVariant = Color(0xFF3F3460),
    surfaceContainerLowest = Color(0xFF100A1F),
    surfaceContainerLow = Color(0xFF1B1431),
    surfaceContainer = Color(0xFF221A3B),
    surfaceContainerHigh = Color(0xFF2B2246),
    surfaceContainerHighest = Color(0xFF352B52)
)

private val Typo = Typography().let {
    it.copy(
        headlineMedium = it.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = it.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = it.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = it.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    )
}

@Composable
fun estSombre(): Boolean = MaterialTheme.colorScheme.background.let {
    (0.299f * it.red + 0.587f * it.green + 0.114f * it.blue) < 0.5f
}

@Composable
fun PhrasometreTheme(theme: String, echelle: Float, content: @Composable () -> Unit) {
    val systemeSombre = isSystemInDarkTheme()
    val sombre = when (theme) {
        "clair" -> false
        "sombre", "special" -> true
        else -> systemeSombre
    }
    val schema = when (theme) {
        "clair" -> Clair
        "sombre" -> Sombre
        "special" -> Special
        else -> if (systemeSombre) Sombre else Clair
    }
    val vue = LocalView.current
    if (!vue.isInEditMode) {
        SideEffect {
            val fenetre = (vue.context as? Activity)?.window
            if (fenetre != null) {
                val c = WindowCompat.getInsetsController(fenetre, vue)
                c.isAppearanceLightStatusBars = !sombre
                c.isAppearanceLightNavigationBars = !sombre
            }
        }
    }
    val dens = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(dens.density, dens.fontScale * echelle)) {
        MaterialTheme(colorScheme = schema, typography = Typo, content = content)
    }
}
