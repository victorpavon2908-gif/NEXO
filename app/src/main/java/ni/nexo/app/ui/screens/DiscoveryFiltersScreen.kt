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
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import ni.nexo.app.data.DiscoveryPreferences
import ni.nexo.app.data.NexoRepository
import ni.nexo.app.data.PremiumEntitlements
import ni.nexo.app.ui.components.NexoBackdrop
import ni.nexo.app.ui.components.NexoGradientButton
import ni.nexo.app.ui.theme.NexoCyan
import ni.nexo.app.ui.theme.NexoMuted
import ni.nexo.app.ui.theme.NexoNightSoft
import ni.nexo.app.ui.theme.NexoPurple
import ni.nexo.app.ui.userFacingError

@Composable
fun DiscoveryFiltersScreen(
    repository: NexoRepository,
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    onSaved: (DiscoveryPreferences) -> Unit
) {
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(DiscoveryPreferences()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var entitlements by remember { mutableStateOf(PremiumEntitlements()) }

    LaunchedEffect(repository) {
        settings = runCatching { repository.loadDiscoveryPreferences() }.getOrDefault(DiscoveryPreferences())
        entitlements = runCatching { repository.loadPremiumEntitlements() }.getOrDefault(PremiumEntitlements())
        loading = false
    }

    NexoBackdrop {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Volver", tint = Color.White) }
                Column {
                    Text("Tu descubrimiento", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text("Vos decidís a quién querés conocer", color = NexoMuted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            FilterCard("Rango de edad") {
                Text("${settings.minAge} a ${settings.maxAge} años", color = NexoCyan, fontWeight = FontWeight.Bold)
                Text("Edad mínima", color = NexoMuted, fontSize = 11.sp)
                Slider(
                    value = settings.minAge.toFloat(),
                    onValueChange = { settings = settings.copy(minAge = it.toInt().coerceAtMost(settings.maxAge)) },
                    valueRange = 18f..80f,
                    steps = 61
                )
                Text("Edad máxima", color = NexoMuted, fontSize = 11.sp)
                Slider(
                    value = settings.maxAge.toFloat(),
                    onValueChange = { settings = settings.copy(maxAge = it.toInt().coerceAtLeast(settings.minAge)) },
                    valueRange = 18f..80f,
                    steps = 61
                )
            }
            Spacer(Modifier.height(12.dp))
            FilterCard("Lugar e intención") {
                OutlinedTextField(
                    value = settings.city,
                    onValueChange = { settings = settings.copy(city = it.take(80)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = entitlements.advancedFilters,
                    label = { Text("Ciudad (opcional)") },
                    supportingText = { Text("La coincidencia es por ciudad, no por ubicación exacta") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(10.dp))
                listOf("Todas", "Relación seria", "Conocer y ver qué pasa", "Amistad").forEach { option ->
                    val selected = settings.intention == option
                    Surface(
                        color = if (selected) NexoPurple.copy(alpha = 0.34f) else Color.Transparent,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().clickable(enabled = entitlements.advancedFilters) {
                            settings = settings.copy(intention = option)
                        }
                    ) {
                        Text(option, color = if (selected) NexoCyan else Color.White, modifier = Modifier.padding(12.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            FilterCard("Disponibilidad") {
                FilterSwitch("Solo personas en línea · Plus", "Priorizá conversaciones que pueden empezar ahora.", settings.onlyOnline, entitlements.advancedFilters) {
                    settings = settings.copy(onlyOnline = it)
                }
                FilterSwitch("Pausar mi perfil", "No aparecerás en nuevos descubrimientos hasta reactivarlo.", settings.profilePaused) {
                    settings = settings.copy(profilePaused = it)
                }
            }
            if (!entitlements.advancedFilters) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = NexoPurple.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onUpgrade)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Filtros avanzados con NEXO Plus", color = NexoCyan, fontWeight = FontWeight.Bold)
                        Text("Ciudad, intención y personas en línea. El rango de edad y pausar tu perfil siguen gratis.", color = NexoMuted, fontSize = 11.sp)
                    }
                }
            }
            message?.let { Text(it, color = NexoCyan, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp)) }
            Spacer(Modifier.height(18.dp))
            NexoGradientButton(
                text = if (loading) "Cargando…" else "Aplicar filtros",
                onClick = {
                    saving = true
                    message = null
                    scope.launch {
                        val effective = if (entitlements.advancedFilters) settings else settings.copy(
                            city = "", intention = "Todas", onlyOnline = false
                        )
                        runCatching { repository.saveDiscoveryPreferences(effective) }
                            .onSuccess { onSaved(effective) }
                            .onFailure { message = userFacingError(it, "No pudimos guardar los filtros.") }
                        saving = false
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
private fun FilterCard(title: String, content: @Composable () -> Unit) {
    Surface(color = NexoNightSoft.copy(alpha = 0.84f), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FilterAlt, null, tint = NexoCyan)
                Spacer(Modifier.padding(4.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun FilterSwitch(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = NexoMuted, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
