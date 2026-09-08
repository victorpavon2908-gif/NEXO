package ni.nexo.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ni.nexo.app.ui.components.NexoBackdrop
import ni.nexo.app.ui.components.NexoLogoMark
import ni.nexo.app.ui.components.NexoWordmark
import ni.nexo.app.ui.theme.NexoCyan
import ni.nexo.app.ui.theme.NexoMuted
import ni.nexo.app.ui.theme.NexoPink
import ni.nexo.app.ui.theme.NexoPurple

@Composable
fun SplashScreen() {
    val transition = rememberInfiniteTransition(label = "nexoSplash")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    NexoBackdrop {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            NexoLogoMark(Modifier.size(156.dp))
            Spacer(Modifier.height(22.dp))
            NexoWordmark()
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Conexiones reales. Privacidad real.",
                color = NexoMuted,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(56.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(NexoCyan, NexoPurple, NexoPink).forEachIndexed { index, color ->
                    Box(
                        modifier = Modifier
                            .size(if (index == 1) 10.dp else 8.dp)
                            .alpha(if (index == 1) pulse else 0.72f)
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawCircle(color = color)
                        }
                    }
                }
            }
        }
    }
}
