package ni.nexo.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ni.nexo.app.data.LocalUserProfile

@Composable
fun ProfileSetupScreen(
    initial: LocalUserProfile,
    busy: Boolean = false,
    message: String? = null,
    onBack: () -> Unit,
    onContinue: (LocalUserProfile) -> Unit
) {
    var name by remember(initial.name) { mutableStateOf(initial.name) }
    var age by remember(initial.age) { mutableStateOf(initial.age) }
    var city by remember(initial.city) { mutableStateOf(initial.city) }
    var bio by remember(initial.bio) { mutableStateOf(initial.bio) }
    var intention by remember(initial.intention) { mutableStateOf(initial.intention) }
    var interests by remember(initial.interests) { mutableStateOf(initial.interests.joinToString(", ")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 30.dp)
    ) {
        Text("Tu perfil", fontSize = 32.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text("Mostremos lo suficiente para conectar, sin pedir ubicación exacta ni datos innecesarios.")
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(60) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nombre") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = age,
            onValueChange = { age = it.filter(Char::isDigit).take(3) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Edad") },
            supportingText = { Text("NEXO es exclusivamente para mayores de 18 años") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = city,
            onValueChange = { city = it.take(100) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ciudad") },
            supportingText = { Text("Nunca pedimos tu dirección exacta") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it.take(500) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Sobre vos") },
            minLines = 3,
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = intention,
            onValueChange = { intention = it.take(120) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("¿Qué buscás?") },
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = interests,
            onValueChange = { interests = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Intereses separados por coma") },
            supportingText = { Text("Ej.: café, música, viajes") },
            shape = RoundedCornerShape(16.dp)
        )

        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(message)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                onContinue(
                    LocalUserProfile(
                        name = name.trim(),
                        age = age,
                        city = city.ifBlank { "Nicaragua" }.trim(),
                        bio = bio.trim(),
                        intention = intention.ifBlank { "Conocer a alguien de verdad" }.trim(),
                        interests = interests.split(',').map(String::trim).filter(String::isNotBlank).distinct().take(12)
                    )
                )
            },
            enabled = !busy && name.isNotBlank() && age.toIntOrNull()?.let { it in 18..120 } == true,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(if (busy) "Guardando..." else "Guardar y entrar a NEXO", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onBack,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Volver")
        }
    }
}
