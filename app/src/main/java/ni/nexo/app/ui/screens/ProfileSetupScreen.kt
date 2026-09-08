package ni.nexo.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ni.nexo.app.data.LocalUserProfile
import ni.nexo.app.ui.components.ProfilePhoto

@Composable
fun ProfileSetupScreen(
    initial: LocalUserProfile,
    backendConfigured: Boolean,
    busy: Boolean = false,
    message: String? = null,
    onBack: () -> Unit,
    onUploadPhoto: suspend (ByteArray, String) -> String,
    onContinue: (LocalUserProfile) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember(initial.name) { mutableStateOf(initial.name) }
    var age by remember(initial.age) { mutableStateOf(initial.age) }
    var city by remember(initial.city) { mutableStateOf(initial.city) }
    var bio by remember(initial.bio) { mutableStateOf(initial.bio) }
    var intention by remember(initial.intention) { mutableStateOf(initial.intention) }
    var interests by remember(initial.interests) { mutableStateOf(initial.interests.joinToString(", ")) }
    var photoUrl by remember(initial.photoUrl) { mutableStateOf(initial.photoUrl) }
    var photoBusy by remember { mutableStateOf(false) }
    var photoMessage by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !photoBusy) {
            scope.launch {
                photoBusy = true
                photoMessage = null
                try {
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("No pudimos leer esa fotografía.")
                    require(bytes.size <= 5 * 1024 * 1024) { "La foto debe pesar como máximo 5 MB." }
                    photoUrl = onUploadPhoto(bytes, mimeType)
                    photoMessage = "Foto cargada correctamente."
                } catch (error: Exception) {
                    photoMessage = error.message ?: "No pudimos subir la foto."
                } finally {
                    photoBusy = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 30.dp)
    ) {
        Text("Tu perfil", fontSize = 32.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text("Mostremos lo suficiente para conectar, sin pedir ubicación exacta ni datos innecesarios.")
        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProfilePhoto(
                photoUrl = photoUrl,
                name = name,
                modifier = Modifier.size(118.dp)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { photoPicker.launch("image/*") },
                enabled = backendConfigured && !busy && !photoBusy
            ) {
                Text(if (photoBusy) "Subiendo foto..." else if (photoUrl.isNullOrBlank()) "Agregar foto" else "Cambiar foto")
            }
            if (!backendConfigured) {
                Text("Las fotos reales se activan al conectar Supabase.", fontSize = 12.sp)
            }
            if (!photoMessage.isNullOrBlank()) {
                Text(photoMessage!!, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(20.dp))
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
                        interests = interests.split(',').map(String::trim).filter(String::isNotBlank).distinct().take(12),
                        photoUrl = photoUrl
                    )
                )
            },
            enabled = !busy && !photoBusy && name.isNotBlank() && age.toIntOrNull()?.let { it in 18..120 } == true,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(if (busy) "Guardando..." else "Guardar y entrar a NEXO", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onBack,
            enabled = !busy && !photoBusy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Volver")
        }
    }
}
