package ni.nexo.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ni.nexo.app.data.NexoRepository
import ni.nexo.app.data.PrivacySettings
import ni.nexo.app.ui.components.NexoBackdrop
import ni.nexo.app.ui.components.NexoGradientButton
import ni.nexo.app.ui.theme.NexoCyan
import ni.nexo.app.ui.theme.NexoMuted
import ni.nexo.app.ui.theme.NexoNightSoft
import ni.nexo.app.ui.theme.NexoPurple

@Composable
fun SettingsScreen(
    repository: NexoRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(PrivacySettings()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(repository) {
        settings = runCatching { repository.loadPrivacySettings() }.getOrDefault(PrivacySettings())
        loading = false
    }

    NexoBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
                Text("Configuración", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            }
            Text(
                "Tu privacidad y tu experiencia son configurables. Estos ajustes se sincronizan con tu cuenta cuando Supabase está conectado.",
                color = NexoMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(18.dp))
            SettingsSection("Privacidad", Icons.Rounded.Lock) {
                SettingSwitch("Confirmaciones de lectura", "Mostrar y recibir ✓✓ de leído", settings.readReceipts) {
                    settings = settings.copy(readReceipts = it)
                }
                SettingSwitch("Mostrar en línea", "Permitir que tus matches vean cuando estás activo", settings.showOnline) {
                    settings = settings.copy(showOnline = it)
                }
                SettingSwitch("Última conexión", "Mostrar cuándo usaste NEXO por última vez", settings.showLastSeen) {
                    settings = settings.copy(showLastSeen = it)
                }
                SettingSwitch("Permitir llamadas", "Tus matches pueden iniciar llamadas de voz o video", settings.allowCalls) {
                    settings = settings.copy(allowCalls = it)
                }
                SettingSwitch("Respuestas a estados", "Permitir respuestas privadas a tus novedades", settings.allowStatusReplies) {
                    settings = settings.copy(allowStatusReplies = it)
                }
            }

            Spacer(Modifier.height(14.dp))
            SettingsSection("Notificaciones", Icons.Rounded.Notifications) {
                SettingSwitch("Notificaciones", "Avisos de nuevos mensajes, matches y llamadas", settings.notificationsEnabled) {
                    settings = settings.copy(notificationsEnabled = it)
                }
                SettingSwitch("Vista previa", "Mostrar el contenido del mensaje en la notificación", settings.notificationPreview) {
                    settings = settings.copy(notificationPreview = it)
                }
            }

            Spacer(Modifier.height(14.dp))
            SettingsSection("Mensajes temporales", Icons.Rounded.Security) {
                Text(
                    "Elegí cuánto tiempo conservar los mensajes nuevos de una conversación.",
                    color = NexoMuted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                val timers = listOf(
                    0 to "Desactivados",
                    3600 to "1 hora",
                    86400 to "24 horas",
                    604800 to "7 días",
                    7776000 to "90 días"
                )
                timers.forEach { (seconds, label) ->
                    val selected = settings.disappearingSeconds == seconds
                    Surface(
                        color = if (selected) NexoPurple.copy(alpha = 0.30f) else Color.Transparent,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { settings = settings.copy(disappearingSeconds = seconds) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, color = Color.White, modifier = Modifier.weight(1f))
                            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = NexoCyan)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            SettingsSection("Estado técnico", Icons.Rounded.Security) {
                TechRow("Supabase / cuenta", if (repository.configured) "Conectado" else "Modo demo")
                TechRow("Chat persistente + Realtime", if (repository.configured) "Listo al aplicar migraciones" else "Simulado localmente")
                TechRow("Google / Facebook", "Requiere habilitar proveedores")
                TechRow("Push FCM", "Requiere google-services.json")
                TechRow("Voz / video WebRTC", "Señalización lista · falta STUN/TURN + motor WebRTC")
                TechRow("Cifrado E2E", "Estructura preparada · protocolo criptográfico pendiente")
            }

            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = NexoCyan, fontSize = 13.sp)
            }

            Spacer(Modifier.height(18.dp))
            NexoGradientButton(
                text = if (loading) "Cargando…" else "Guardar configuración",
                onClick = {
                    if (!saving && !loading) {
                        saving = true
                        scope.launch {
                            runCatching { repository.savePrivacySettings(settings) }
                                .onSuccess { message = "Configuración guardada." }
                                .onFailure { message = it.message ?: "No pudimos guardar la configuración." }
                            saving = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
                busy = saving
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        color = NexoNightSoft.copy(alpha = 0.82f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = NexoCyan)
                Spacer(Modifier.padding(4.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = NexoMuted, fontSize = 11.sp, lineHeight = 16.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TechRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(0.45f))
        Text(value, color = NexoMuted, fontSize = 12.sp, modifier = Modifier.weight(0.55f))
    }
}