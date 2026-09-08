package ni.nexo.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ni.nexo.app.BuildConfig
import ni.nexo.app.data.AccountDeletionService
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
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf(PrivacySettings()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        settings = settings.copy(notificationsEnabled = granted)
        message = if (granted) {
            "Notificaciones permitidas."
        } else {
            "Las notificaciones quedaron desactivadas. Podés habilitarlas después desde Android."
        }
    }

    fun updateNotifications(enabled: Boolean) {
        if (!enabled) {
            settings = settings.copy(notificationsEnabled = false)
            return
        }
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            settings = settings.copy(notificationsEnabled = true)
        }
    }

    LaunchedEffect(repository) {
        settings = runCatching { repository.loadPrivacySettings() }.getOrDefault(PrivacySettings())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            settings = settings.copy(notificationsEnabled = false)
        }
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
                Column {
                    Text("Configuración", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text("Privacidad, seguridad y experiencia", color = NexoMuted, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(18.dp))
            SettingsSection("Privacidad", Icons.Rounded.Lock) {
                SettingSwitch(
                    "Confirmaciones de lectura",
                    "Mostrar y recibir ✓✓ cuando un mensaje fue leído.",
                    settings.readReceipts
                ) { settings = settings.copy(readReceipts = it) }
                SettingSwitch(
                    "Mostrar en línea",
                    "Permitir que tus matches vean cuando estás activo.",
                    settings.showOnline
                ) { settings = settings.copy(showOnline = it) }
                SettingSwitch(
                    "Última conexión",
                    "Mostrar cuándo usaste NEXO por última vez.",
                    settings.showLastSeen
                ) { settings = settings.copy(showLastSeen = it) }
                SettingSwitch(
                    "Permitir llamadas",
                    "Tus matches pueden iniciar llamadas de voz o video.",
                    settings.allowCalls
                ) { settings = settings.copy(allowCalls = it) }
                SettingSwitch(
                    "Respuestas a novedades",
                    "Permitir respuestas privadas a tus estados de 24 horas.",
                    settings.allowStatusReplies
                ) { settings = settings.copy(allowStatusReplies = it) }
            }

            Spacer(Modifier.height(14.dp))
            SettingsSection("Notificaciones", Icons.Rounded.Notifications) {
                SettingSwitch(
                    "Notificaciones",
                    "Matches, mensajes y llamadas importantes.",
                    settings.notificationsEnabled,
                    ::updateNotifications
                )
                SettingSwitch(
                    "Vista previa del mensaje",
                    "Mostrar el contenido en la notificación.",
                    settings.notificationPreview,
                    enabled = settings.notificationsEnabled
                ) { settings = settings.copy(notificationPreview = it) }
                Text(
                    "NEXO ya prepara el canal de notificaciones del teléfono; la entrega remota se activa al conectar Firebase Cloud Messaging.",
                    color = NexoMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(Modifier.height(14.dp))
            SettingsSection("Mensajes temporales", Icons.Rounded.Security) {
                Text(
                    "Elegí cuánto tiempo conservar los mensajes nuevos. El temporizador se aplica a los mensajes enviados después de guardar.",
                    color = NexoMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
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
            SettingsSection("Seguridad", Icons.Rounded.Shield) {
                SecurityLine("Bloqueos", "Podés bloquear a una persona desde el menú de su chat.")
                SecurityLine("Reportes", "Los reportes quedan separados de tus chats y preparados para moderación.")
                SecurityLine("Ubicación", "NEXO no necesita mostrar tu ubicación exacta en el perfil.")
                SecurityLine("Cifrado", "La estructura está lista para conectar E2EE auditado; no se marca como activo antes de esa integración.")
            }

            Spacer(Modifier.height(14.dp))
            SettingsSection("Cuenta", Icons.Rounded.Info) {
                Text("NEXO ${BuildConfig.VERSION_NAME}", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Aplicación para mayores de 18 años.", color = NexoMuted, fontSize = 11.sp)
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = Color(0xFF40151D).copy(alpha = 0.82f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = Color(0xFFFF7186))
                            Spacer(Modifier.padding(4.dp))
                            Text("Eliminar mi cuenta", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Tu perfil se oculta inmediatamente y se inicia el proceso de borrado. En producción, el worker privado termina la limpieza de Auth y archivos.",
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 5.dp, bottom = 10.dp)
                        )
                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            enabled = !deleting,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(if (deleting) "Procesando…" else "Solicitar eliminación", color = Color(0xFFFF7186))
                        }
                    }
                }
            }

            if (!repository.configured) {
                Spacer(Modifier.height(14.dp))
                SettingsSection("Modo de prueba", Icons.Rounded.Security) {
                    TechRow("Base de datos", "Demo local")
                    TechRow("Mensajes", "Simulados y probables sin servidor")
                    TechRow("Conexiones externas", "Supabase · FCM · WebRTC · E2EE pendientes")
                }
            }

            message?.let {
                Spacer(Modifier.height(12.dp))
                Surface(color = NexoPurple.copy(alpha = 0.22f), shape = RoundedCornerShape(16.dp)) {
                    Text(
                        it,
                        color = NexoCyan,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                    )
                }
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
            Spacer(Modifier.height(28.dp))
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!deleting) showDeleteDialog = false },
            title = { Text("¿Eliminar tu cuenta?") },
            text = {
                Text(
                    "Tu perfil dejará de aparecer, se cerrarán tus matches y se iniciará el borrado de tus datos. Esta acción no debe usarse si solo querés cerrar sesión."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        scope.launch {
                            AccountDeletionService.request(repository)
                                .onSuccess {
                                    message = "Solicitud recibida. Cerrando tu cuenta…"
                                    delay(350)
                                    activity?.recreate()
                                }
                                .onFailure {
                                    deleting = false
                                    message = it.message ?: "No pudimos iniciar la eliminación de la cuenta."
                                }
                        }
                    }
                ) {
                    Text(if (deleting) "Eliminando…" else "Sí, eliminar", color = Color(0xFFD9344E))
                }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
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
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (enabled) Color.White else NexoMuted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Text(subtitle, color = NexoMuted, fontSize = 11.sp, lineHeight = 16.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SecurityLine(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = NexoMuted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun TechRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(0.43f))
        Text(value, color = NexoMuted, fontSize = 12.sp, modifier = Modifier.weight(0.57f))
    }
}
