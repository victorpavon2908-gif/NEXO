package ni.nexo.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ni.nexo.app.data.LocalUserProfile
import ni.nexo.app.ui.components.NexoBackdrop
import ni.nexo.app.ui.components.NexoGradientButton
import ni.nexo.app.ui.components.ProfilePhoto
import ni.nexo.app.ui.theme.NexoCyan
import ni.nexo.app.ui.theme.NexoMuted
import ni.nexo.app.ui.theme.NexoNightSoft
import ni.nexo.app.ui.theme.NexoPurple

@Composable
fun ProfileScreen(
    profile: LocalUserProfile,
    backendConfigured: Boolean,
    plusActive: Boolean,
    onEdit: () -> Unit,
    onSettings: () -> Unit,
    onSafety: () -> Unit,
    onDiscoverySettings: () -> Unit,
    onPlus: () -> Unit,
    onLogout: () -> Unit
) {
    NexoBackdrop {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mi NEXO", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                if (plusActive) {
                    Surface(color = NexoPurple.copy(alpha = 0.40f), shape = RoundedCornerShape(50)) {
                        Text("PLUS", color = NexoCyan, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                    }
                    Spacer(Modifier.size(8.dp))
                }
                Surface(color = NexoNightSoft.copy(alpha = 0.86f), shape = CircleShape) {
                    androidx.compose.material3.IconButton(onClick = onSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Configuración", tint = NexoCyan)
                    }
                }
            }
            Text(
                if (backendConfigured) "Cuenta conectada · sincronización segura" else "Modo demo local",
                color = if (backendConfigured) NexoCyan else NexoMuted,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(18.dp))
            Surface(
                color = NexoNightSoft.copy(alpha = 0.84f),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ProfilePhoto(
                        photoUrl = profile.photoUrl,
                        name = profile.name,
                        modifier = Modifier.size(116.dp),
                        shape = CircleShape,
                        backgroundColor = NexoPurple,
                        textColor = Color.White
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(profile.name.ifBlank { "Tu perfil" }, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                    Text(
                        listOfNotNull(profile.age.takeIf { it.isNotBlank() }?.let { "$it años" }, profile.city.takeIf { it.isNotBlank() }).joinToString(" · "),
                        color = NexoMuted
                    )
                    if (profile.bio.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Text(profile.bio, color = Color.White.copy(alpha = 0.90f), lineHeight = 20.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Busca", color = NexoCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(profile.intention, color = Color.White, fontWeight = FontWeight.SemiBold)
                    if (profile.interests.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(profile.interests.joinToString("  •  "), color = NexoMuted, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Surface(
                color = Color(0xFF102737).copy(alpha = 0.72f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = NexoCyan)
                    Spacer(Modifier.size(10.dp))
                    Column {
                        Text("Privacidad primero", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "Ubicación exacta, controles de lectura, presencia, llamadas y mensajes temporales son configurables.",
                            color = NexoMuted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            NexoGradientButton(
                text = "Editar perfil",
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onSettings,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Privacidad y configuración")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onSafety,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = NexoCyan)
                Spacer(Modifier.size(8.dp))
                Text("Centro de Cita Segura")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onDiscoverySettings,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Preferencias de descubrimiento")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onPlus,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Rounded.Diamond, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Conocer NEXO Plus")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("Cerrar sesión")
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}
