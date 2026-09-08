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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ni.nexo.app.data.LocalUserProfile

@Composable
fun ProfileScreen(
    profile: LocalUserProfile,
    onEdit: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Text("Mi perfil", fontSize = 30.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(20.dp))
        Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp)) {
                Text(profile.name.ifBlank { "Tu perfil" }, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                if (profile.age.isNotBlank()) Text("${profile.age} años")
                Text(profile.city.ifBlank { "Nicaragua" })
                Spacer(Modifier.height(12.dp))
                Text("Busca", fontWeight = FontWeight.Bold)
                Text(profile.intention)
                Spacer(Modifier.height(14.dp))
                Text("Privacidad", fontWeight = FontWeight.Bold)
                Text("La ubicación exacta y tus conversaciones no se mostrarán públicamente.")
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Editar perfil")
        }
    }
}
