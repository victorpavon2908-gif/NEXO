package ni.nexo.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ni.nexo.app.data.LocalUserProfile

@Composable
fun ProfileScreen(
    profile: LocalUserProfile,
    backendConfigured: Boolean,
    onEdit: () -> Unit,
    onLogout: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Text("Mi perfil", fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text(
            if (backendConfigured) "Cuenta conectada · Supabase" else "Modo demo local",
            color = if (backendConfigured) Color(0xFF247A4D) else Color(0xFF8A6500)
        )
        Spacer(Modifier.height(18.dp))
        Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp)) {
                Text(profile.name.ifBlank { "Tu perfil" }, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                if (profile.age.isNotBlank()) Text("${profile.age} años")
                Text(profile.city.ifBlank { "Nicaragua" })
                if (profile.bio.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(profile.bio)
                }
                Spacer(Modifier.height(12.dp))
                Text("Busca", fontWeight = FontWeight.Bold)
                Text(profile.intention)
                if (profile.interests.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Intereses", fontWeight = FontWeight.Bold)
                    Text(profile.interests.joinToString(" • "))
                }
                Spacer(Modifier.height(14.dp))
                Text("Privacidad", fontWeight = FontWeight.Bold)
                Text("Tu ubicación exacta y tus conversaciones no forman parte del perfil público.")
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Editar perfil")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Cerrar sesión")
        }
    }
}
