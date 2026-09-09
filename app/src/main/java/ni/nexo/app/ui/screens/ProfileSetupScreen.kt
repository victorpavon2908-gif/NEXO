package ni.nexo.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ni.nexo.app.data.LocalUserProfile
import ni.nexo.app.ui.components.NexoBackdrop
import ni.nexo.app.ui.components.NexoGradientButton
import ni.nexo.app.ui.components.NexoWordmark
import ni.nexo.app.ui.components.ProfilePhoto
import ni.nexo.app.ui.theme.NexoCyan
import ni.nexo.app.ui.theme.NexoMuted
import ni.nexo.app.ui.theme.NexoNightSoft
import ni.nexo.app.ui.theme.NexoPink
import ni.nexo.app.ui.theme.NexoPurple
import ni.nexo.app.ui.userFacingError

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

    val profileProgress = listOf(
        name.isNotBlank(),
        age.toIntOrNull()?.let { it in 18..120 } == true,
        city.isNotBlank(),
        bio.isNotBlank(),
        interests.isNotBlank()
    ).count { it } / 5f

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
                    photoMessage = userFacingError(error, "No pudimos subir la foto.")
                } finally {
                    photoBusy = false
                }
            }
        }
    }

    NexoBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, enabled = !busy && !photoBusy) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
                NexoWordmark(compact = true)
                Spacer(Modifier.weight(1f))
                Text("${(profileProgress * 100).toInt()}%", color = NexoCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text("Tu perfil", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text("Mostrá quién sos sin compartir ubicación exacta ni datos innecesarios.", color = NexoMuted, lineHeight = 20.sp)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(5.dp).background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))) {
                Box(
                    Modifier
                        .fillMaxWidth(profileProgress.coerceIn(0f, 1f))
                        .height(5.dp)
                        .background(Brush.horizontalGradient(listOf(NexoCyan, NexoPurple, NexoPink)), RoundedCornerShape(50))
                )
            }

            Spacer(Modifier.height(20.dp))
            Surface(color = NexoNightSoft.copy(alpha = 0.78f), shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ProfilePhoto(photoUrl = photoUrl, name = name, modifier = Modifier.size(116.dp))
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { photoPicker.launch("image/*") },
                        enabled = backendConfigured && !busy && !photoBusy
                    ) {
                        Text(if (photoBusy) "Subiendo…" else if (photoUrl.isNullOrBlank()) "Agregar foto" else "Cambiar foto")
                    }
                    if (!backendConfigured) Text("La foto se activa al conectar Supabase.", color = NexoMuted, fontSize = 11.sp)
                    photoMessage?.let { Text(it, color = NexoCyan, fontSize = 11.sp) }
                }
            }

            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nombre") },
                supportingText = { Text("Así aparecerás ante otras personas") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = age,
                onValueChange = { age = it.filter(Char::isDigit).take(3) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Edad") },
                supportingText = { Text("NEXO es exclusivamente para mayores de 18 años") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = city,
                onValueChange = { city = it.take(100) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ciudad") },
                supportingText = { Text("Nunca pedimos tu dirección exacta") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it.take(500) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Sobre vos") },
                supportingText = { Text("${bio.length}/500") },
                minLines = 3,
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = intention,
                onValueChange = { intention = it.take(120) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("¿Qué buscás?") },
                supportingText = { Text("Sé claro para conectar con la persona correcta") },
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = interests,
                onValueChange = { interests = it.take(240) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Intereses separados por coma") },
                supportingText = { Text("Ej.: café, música, viajes") },
                shape = RoundedCornerShape(16.dp)
            )

            message?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = NexoCyan, fontSize = 12.sp)
            }

            Spacer(Modifier.height(22.dp))
            NexoGradientButton(
                text = if (busy) "Guardando…" else "Guardar y continuar",
                onClick = {
                    onContinue(
                        LocalUserProfile(
                            name = name.trim(),
                            age = age,
                            city = city.trim(),
                            bio = bio.trim(),
                            intention = intention.ifBlank { "Conocer a alguien de verdad" }.trim(),
                            interests = interests.split(',').map(String::trim).filter(String::isNotBlank).distinct().take(12),
                            photoUrl = photoUrl
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && !photoBusy && name.isNotBlank() && city.isNotBlank() &&
                    age.toIntOrNull()?.let { it in 18..120 } == true,
                busy = busy
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Podés cambiar todo después desde tu perfil.",
                color = NexoMuted,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}
