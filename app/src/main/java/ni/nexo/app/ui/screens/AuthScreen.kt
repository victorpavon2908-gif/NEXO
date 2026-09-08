package ni.nexo.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ni.nexo.app.ui.theme.NexoPurple

@Composable
fun AuthScreen(
    startInRegisterMode: Boolean,
    backendConfigured: Boolean,
    busy: Boolean,
    message: String?,
    onBack: () -> Unit,
    onSubmit: (register: Boolean, email: String, password: String) -> Unit
) {
    var registerMode by remember(startInRegisterMode) { mutableStateOf(startInRegisterMode) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Text(
            text = if (registerMode) "Crear cuenta" else "Volver a NEXO",
            fontSize = 32.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (backendConfigured) "Tu cuenta se conectará a Supabase de forma segura."
            else "Modo demo: agregá las variables de Supabase para activar cuentas reales.",
            color = if (backendConfigured) Color(0xFF247A4D) else Color(0xFF8A6500)
        )
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Correo") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Contraseña") },
            supportingText = { Text("Mínimo 6 caracteres para el MVP") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )

        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(message, color = NexoPurple)
        }

        Spacer(Modifier.height(22.dp))
        Button(
            onClick = { onSubmit(registerMode, email, password) },
            enabled = !busy && email.contains("@") && password.length >= 6,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                when {
                    busy -> "Conectando..."
                    registerMode -> "Crear mi cuenta"
                    else -> "Iniciar sesión"
                },
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = {
                registerMode = !registerMode
                password = ""
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (registerMode) "Ya tengo cuenta" else "Quiero crear una cuenta")
        }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = onBack,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Volver")
        }
    }
}
