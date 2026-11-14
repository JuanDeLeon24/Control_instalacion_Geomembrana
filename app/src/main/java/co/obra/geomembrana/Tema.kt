package co.obra.geomembrana

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val AzulOscuro = Color(0xFF002F5C)
val AzulClaro = Color(0xFFA0BEE7)
val Amarillo = Color(0xFFF2B705)   // color de la geomembrana en los gráficos

/** Colores de estado con buen contraste en ambos modos. */
@Immutable
data class ColoresEstado(val ok: Color, val okFondo: Color, val aviso: Color, val avisoFondo: Color, val mal: Color, val malFondo: Color)

private val EstadoClaro = ColoresEstado(
    ok = Color(0xFF0B6B2E), okFondo = Color(0xFFDDF3E3),
    aviso = Color(0xFF7A4D00), avisoFondo = Color(0xFFFFF0C2),
    mal = Color(0xFFA4161A), malFondo = Color(0xFFFCE1E1)
)
private val EstadoOscuro = ColoresEstado(
    ok = Color(0xFF7EE2A0), okFondo = Color(0xFF123B22),
    aviso = Color(0xFFFFD166), avisoFondo = Color(0xFF3F3005),
    mal = Color(0xFFFF8A8A), malFondo = Color(0xFF4A1414)
)

val LocalEstado = staticCompositionLocalOf { EstadoClaro }

// Claro: pensado para luz solar directa (texto casi negro sobre blanco, alto contraste)
private val Claro = lightColorScheme(
    primary = AzulOscuro, onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4F7), onPrimaryContainer = Color(0xFF001D3A),
    secondary = Color(0xFF8A6400), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE8A3), onSecondaryContainer = Color(0xFF2B1F00),
    background = Color(0xFFF1F4F8), onBackground = Color(0xFF0D1218),
    surface = Color.White, onSurface = Color(0xFF0D1218),
    surfaceVariant = Color(0xFFE3E9F1), onSurfaceVariant = Color(0xFF2E3A47),
    outline = Color(0xFF5B6B7C), error = Color(0xFFA4161A)
)

// Oscuro: para el interior del túnel (no encandila, sigue siendo legible)
private val Oscuro = darkColorScheme(
    primary = Color(0xFF9CC3F5), onPrimary = Color(0xFF00213F),
    primaryContainer = Color(0xFF173A60), onPrimaryContainer = Color(0xFFD6E4F7),
    secondary = Amarillo, onSecondary = Color(0xFF2B1F00),
    secondaryContainer = Color(0xFF4A3800), onSecondaryContainer = Color(0xFFFFE8A3),
    background = Color(0xFF0B1118), onBackground = Color(0xFFE8EEF5),
    surface = Color(0xFF141C26), onSurface = Color(0xFFE8EEF5),
    surfaceVariant = Color(0xFF1F2A36), onSurfaceVariant = Color(0xFFC3CEDA),
    outline = Color(0xFF8796A8), error = Color(0xFFFF8A8A)
)

@Composable
fun TemaObra(modo: ModoTema, content: @Composable () -> Unit) {
    val oscuro = when (modo) {
        ModoTema.SISTEMA -> isSystemInDarkTheme()
        ModoTema.CLARO -> false
        ModoTema.OSCURO -> true
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalEstado provides if (oscuro) EstadoOscuro else EstadoClaro) {
        MaterialTheme(colorScheme = if (oscuro) Oscuro else Claro, content = content)
    }
}
