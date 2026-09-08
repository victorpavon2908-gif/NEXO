package ni.nexo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NexoPurple = Color(0xFF6C4DFF)
val NexoBlue = Color(0xFF2979FF)
val NexoPink = Color(0xFFFF4D8D)
val NexoInk = Color(0xFF17152A)
val NexoSurface = Color(0xFFF8F7FC)

private val NexoColorScheme = lightColorScheme(
    primary = NexoPurple,
    secondary = NexoBlue,
    tertiary = NexoPink,
    background = Color.White,
    surface = NexoSurface,
    onPrimary = Color.White,
    onBackground = NexoInk,
    onSurface = NexoInk
)

@Composable
fun NexoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NexoColorScheme,
        content = content
    )
}
