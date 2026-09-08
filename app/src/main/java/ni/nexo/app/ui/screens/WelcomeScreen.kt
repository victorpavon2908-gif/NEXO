package ni.nexo.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ni.nexo.app.ui.components.NexoBackdrop
import ni.nexo.app.ui.components.NexoGradientButton
import ni.nexo.app.ui.components.NexoGlassCard
import ni.nexo.app.ui.components.NexoLogoMark
import ni.nexo.app.ui.components.NexoWordmark
import ni.nexo.app.ui.theme.NexoCyan
import ni.nexo.app.ui.theme.NexoMuted
import ni.nexo.app.ui.theme.NexoPink
import ni.nexo.app.ui.theme.NexoPurple

@Composable
fun WelcomeScreen(
    onStart: () -> Unit,
    onLogin: () -> Unit
) {
    NexoBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            NexoLogoMark(Modifier.size(112.dp))
            Spacer(Modifier.height(14.dp))
            NexoWordmark()
            Spacer(Modifier.height(26.dp))

            Text(
                text = "Más que matches.",
                color = Color.White,
                fontSize = 31.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Conocé personas reales sin regalar tu privacidad desde el primer segundo.",
                color = NexoMuted,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))
            NexoGlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 17.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Feature("Privado", NexoCyan)
                    Feature("Real", NexoPink)
                    Feature("+18", NexoPurple)
                }
            }

            Spacer(Modifier.height(30.dp))
            NexoGradientButton(
                text = "Crear mi cuenta",
                onClick = onStart,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("Ya tengo cuenta", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = "Privacidad · Control · Conexiones con propósito",
                color = NexoMuted.copy(alpha = 0.8f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun Feature(label: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("●", color = accent, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
