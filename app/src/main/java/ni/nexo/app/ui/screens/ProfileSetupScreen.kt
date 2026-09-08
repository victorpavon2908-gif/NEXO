package ni.nexo.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onBack: () -> Unit,
    onContinue: (LocalUserProfile) -> Unit
) {
    var name by remember(initial.name) { mutableStateOf(initial.name) }
    var age by remember(initial.age) { mutableStateOf(initial.age) }
    var city by remember(initial.city) { mutableStateOf(initial.city) }
    var intention by remember(initial.intention) { mutableStateOf(initial.intention) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 30.dp)
    ) {
        Text("Tu perfil", fontSize = 32.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text("Mostremos lo suficiente para conectar, sin pedir datos que no hacen falta.")
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Nombre") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = age,
            onValueChange = { age = it.filter(Char::isDigit).take(2) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Edad") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = city,
            onValueChange = { city = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ciudad") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = intention,
            onValueChange = { intention = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("¿Qué buscas?") },
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(28.dp))

        Button(
            onClick = {
                onContinue(
                    LocalUserProfile(
                        name = name.ifBlank { "Tú" },
                        age = age,
                        city = city.ifBlank { "Nicaragua" },
                        intention = intention.ifBlank { "Conocer a alguien de verdad" }
                    )
                )
            },
            enabled = name.isNotBlank() && age.toIntOrNull()?.let { it >= 18 } == true,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Entrar a Nexo", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Volver")
        }
    }
}
