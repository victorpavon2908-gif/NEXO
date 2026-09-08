package ni.nexo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import ni.nexo.app.ui.theme.NexoPurple

@Composable
fun ProfilePhoto(
    photoUrl: String?,
    name: String,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    backgroundColor: Color = NexoPurple.copy(alpha = 0.14f),
    textColor: Color = NexoPurple
) {
    Box(
        modifier = modifier.clip(shape).background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "Foto de ${name.ifBlank { "perfil" }}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = name.trim().take(1).ifBlank { "N" }.uppercase(),
                color = textColor,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}
