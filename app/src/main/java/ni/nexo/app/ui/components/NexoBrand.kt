package ni.nexo.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ni.nexo.app.ui.theme.NexoBlue
import ni.nexo.app.ui.theme.NexoCyan
import ni.nexo.app.ui.theme.NexoNight
import ni.nexo.app.ui.theme.NexoPink
import ni.nexo.app.ui.theme.NexoPurple

val NexoBrandGradient = Brush.linearGradient(
    listOf(NexoCyan, NexoBlue, NexoPurple, NexoPink)
)

@Composable
fun NexoBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF050716),
                        Color(0xFF11133A),
                        Color(0xFF0A0B22),
                        Color(0xFF160A2A)
                    )
                )
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(NexoBlue.copy(alpha = 0.28f), Color.Transparent)
                ),
                radius = size.minDimension * 0.58f,
                center = Offset(size.width * 0.02f, size.height * 0.12f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(NexoPink.copy(alpha = 0.24f), Color.Transparent)
                ),
                radius = size.minDimension * 0.64f,
                center = Offset(size.width * 0.96f, size.height * 0.24f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(NexoPurple.copy(alpha = 0.22f), Color.Transparent)
                ),
                radius = size.minDimension * 0.75f,
                center = Offset(size.width * 0.62f, size.height * 0.96f)
            )
        }
        content()
    }
}

@Composable
fun NexoLogoMark(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val edge = size.minDimension
        val inset = edge * 0.035f
        val corner = edge * 0.25f

        drawRoundRect(
            brush = NexoBrandGradient,
            cornerRadius = CornerRadius(corner, corner)
        )
        drawRoundRect(
            color = NexoNight.copy(alpha = 0.97f),
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2f, size.height - inset * 2f),
            cornerRadius = CornerRadius(corner - inset, corner - inset)
        )

        val stroke = edge * 0.115f
        val leftX = size.width * 0.34f
        val rightX = size.width * 0.66f
        val topY = size.height * 0.37f
        val bottomY = size.height * 0.68f

        drawCircle(
            brush = Brush.linearGradient(listOf(NexoCyan, NexoBlue, NexoPurple)),
            radius = edge * 0.062f,
            center = Offset(leftX, size.height * 0.25f)
        )
        drawCircle(
            brush = Brush.linearGradient(listOf(NexoPurple, NexoPink)),
            radius = edge * 0.062f,
            center = Offset(rightX, size.height * 0.25f)
        )

        drawLine(
            brush = Brush.linearGradient(listOf(NexoCyan, NexoBlue, NexoPurple)),
            start = Offset(leftX, topY),
            end = Offset(leftX, bottomY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            brush = NexoBrandGradient,
            start = Offset(leftX, topY),
            end = Offset(rightX, bottomY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            brush = Brush.linearGradient(listOf(NexoPurple, NexoPink)),
            start = Offset(rightX, topY),
            end = Offset(rightX, bottomY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun NexoWordmark(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val size = if (compact) 22.sp else 36.sp
        val spacing = if (compact) 1.sp else 3.sp
        Text("NE", color = Color.White, fontSize = size, fontWeight = FontWeight.Black, letterSpacing = spacing)
        Text("X", color = NexoCyan, fontSize = size, fontWeight = FontWeight.Black, letterSpacing = spacing)
        Text("O", color = Color.White, fontSize = size, fontWeight = FontWeight.Black, letterSpacing = spacing)
    }
}

@Composable
fun NexoGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 30,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xCC11142F))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        NexoCyan.copy(alpha = 0.55f),
                        NexoPurple.copy(alpha = 0.48f),
                        NexoPink.copy(alpha = 0.55f)
                    )
                ),
                shape = shape
            )
            .padding(1.dp)
    ) {
        content()
    }
}

@Composable
fun NexoGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 56.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(NexoBrandGradient)
            .clickable(enabled = enabled && !busy, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = text,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}
