package ni.nexo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val NexoPurple = Color(0xFF7C4DFF)
val NexoViolet = Color(0xFF9B51FF)
val NexoBlue = Color(0xFF267BFF)
val NexoCyan = Color(0xFF20D5FF)
val NexoPink = Color(0xFFFF3DBB)
val NexoRose = Color(0xFFFF4F86)
val NexoNight = Color(0xFF07091A)
val NexoNightSoft = Color(0xFF10132D)
val NexoSurface = Color(0xFF151936)
val NexoSurfaceHigh = Color(0xFF20264A)
val NexoInk = Color(0xFFF7F5FF)
val NexoMuted = Color(0xFFAAAED0)
val NexoSuccess = Color(0xFF4CE6A6)

private val NexoColorScheme = darkColorScheme(
    primary = NexoPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2D225C),
    onPrimaryContainer = Color(0xFFE9E2FF),
    secondary = NexoCyan,
    onSecondary = NexoNight,
    tertiary = NexoPink,
    onTertiary = Color.White,
    background = NexoNight,
    onBackground = NexoInk,
    surface = NexoSurface,
    onSurface = NexoInk,
    surfaceVariant = NexoSurfaceHigh,
    onSurfaceVariant = NexoMuted,
    outline = Color(0xFF555B82),
    error = Color(0xFFFF6B7A)
)

private val NexoTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 36.sp,
        lineHeight = 42.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.8).sp
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Bold
    )
)

@Composable
fun NexoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NexoColorScheme,
        typography = NexoTypography,
        content = content
    )
}
