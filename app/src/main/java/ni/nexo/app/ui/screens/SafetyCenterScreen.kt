package ni.nexo.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import kotlinx.coroutines.launch
import ni.nexo.app.data.NexoRepository
import ni.nexo.app.data.SafeDatePlan
import ni.nexo.app.data.SafeDateState
import ni.nexo.app.ui.components.NexoBackdrop
import ni.nexo.app.ui.components.NexoGradientButton
import ni.nexo.app.ui.theme.NexoCyan
import ni.nexo.app.ui.theme.NexoMuted
import ni.nexo.app.ui.theme.NexoNightSoft
import ni.nexo.app.ui.theme.NexoPink
import ni.nexo.app.ui.theme.NexoPurple
import ni.nexo.app.ui.userFacingError

@Composable
fun SafetyCenterScreen(
    repository: NexoRepository,
    partnerId: String? = null,
    partnerNameInitial: String = "",
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var plans by remember { mutableStateOf<List<SafeDatePlan>>(emptyList()) }
    var partnerName by remember(partnerNameInitial) { mutableStateOf(partnerNameInitial) }
    var place by remember { mutableStateOf("") }
    var checkInAt by remember { mutableStateOf("") }
    var trustedName by remember { mutableStateOf("") }
    var trustedPhone by remember { mutableStateOf("") }
    var safetyCode by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() { plans = repository.loadSafeDatePlans() }
    LaunchedEffect(repository) { runCatching { refresh() } }

    fun sharePlan(plan: SafeDatePlan) {
        val body = "Cita segura NEXO: estaré con ${plan.partnerName} en ${plan.place}. Confirmaré que estoy bien a las ${plan.checkInAt}."
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(plan.trustedPhone)}")).putExtra("sms_body", body)
        runCatching { context.startActivity(intent) }.onFailure { message = "No encontramos una aplicación de mensajes." }
    }

    NexoBackdrop {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Volver", tint = Color.White) }
                Column {
                    Text("Cita Segura", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black)
                    Text("Prepará el encuentro y mantené el control", color = NexoCyan, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Surface(color = NexoPurple.copy(alpha = 0.23f), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Shield, null, tint = NexoCyan)
                    Spacer(Modifier.padding(5.dp))
                    Text("Elegí un lugar público, avisale a alguien de confianza y confirmá cuando estés bien.", color = Color.White, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Surface(color = NexoNightSoft.copy(alpha = 0.86f), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Planificar una cita", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    SafetyField(partnerName, { partnerName = it.take(60) }, "Persona")
                    SafetyField(place, { place = it.take(120) }, "Lugar público")
                    SafetyField(checkInAt, { checkInAt = it.take(40) }, "Hora para confirmar", "Ej.: hoy, 9:30 p. m.")
                    SafetyField(trustedName, { trustedName = it.take(60) }, "Contacto de confianza")
                    OutlinedTextField(
                        value = trustedPhone,
                        onValueChange = { trustedPhone = it.filter { char -> char.isDigit() || char == '+' }.take(16) },
                        modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                        label = { Text("Teléfono de confianza") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                    SafetyField(safetyCode, { safetyCode = it.take(20) }, "Código secreto", "Una palabra que solo ustedes conozcan")
                    Spacer(Modifier.height(14.dp))
                    NexoGradientButton(
                        text = "Guardar plan seguro",
                        onClick = {
                            saving = true
                            message = null
                            scope.launch {
                                val plan = SafeDatePlan(
                                    id = UUID.randomUUID().toString(), partnerId = partnerId, partnerName = partnerName.trim(),
                                    place = place.trim(), checkInAt = checkInAt.trim(), trustedName = trustedName.trim(),
                                    trustedPhone = trustedPhone.trim(), safetyCode = safetyCode.trim()
                                )
                                runCatching { repository.createSafeDatePlan(plan) }
                                    .onSuccess { saved ->
                                        refresh(); sharePlan(saved); place = ""; checkInAt = ""; safetyCode = ""
                                        message = "Plan guardado. Revisá el mensaje antes de enviarlo."
                                    }
                                    .onFailure { message = userFacingError(it, "No pudimos guardar el plan seguro.") }
                                saving = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(), busy = saving,
                        enabled = partnerName.isNotBlank() && place.isNotBlank() && checkInAt.isNotBlank() && trustedPhone.isNotBlank()
                    )
                }
            }
            message?.let { Text(it, color = NexoCyan, fontSize = 12.sp, modifier = Modifier.padding(vertical = 10.dp)) }
            if (plans.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("Tus planes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                plans.forEach { plan ->
                    Surface(color = NexoNightSoft.copy(alpha = 0.84f), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(top = 9.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("${plan.partnerName} · ${plan.place}", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Confirmación: ${plan.checkInAt} · Aviso a ${plan.trustedName.ifBlank { "contacto de confianza" }}", color = NexoMuted, fontSize = 11.sp)
                            Text(
                                when (plan.state) {
                                    SafeDateState.Planned -> "Pendiente de confirmación"
                                    SafeDateState.ConfirmedSafe -> "Confirmaste que estás bien"
                                    SafeDateState.Cancelled -> "Plan cancelado"
                                },
                                color = if (plan.state == SafeDateState.ConfirmedSafe) NexoCyan else NexoPink,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (plan.state == SafeDateState.Planned) {
                                Row(Modifier.fillMaxWidth().padding(top = 9.dp)) {
                                    Button(onClick = {
                                        scope.launch { repository.updateSafeDateState(plan.id, SafeDateState.ConfirmedSafe); refresh() }
                                    }, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Rounded.CheckCircle, null)
                                        Text(" Estoy bien")
                                    }
                                    Spacer(Modifier.padding(4.dp))
                                    OutlinedButton(onClick = { sharePlan(plan) }) {
                                        Icon(Icons.Rounded.Send, "Compartir")
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(plan.trustedPhone)}"))
                                        runCatching { context.startActivity(intent) }
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                ) {
                                    Icon(Icons.Rounded.Phone, null)
                                    Text(" Llamar a mi contacto")
                                }
                                TextButton(
                                    onClick = {
                                        scope.launch { repository.updateSafeDateState(plan.id, SafeDateState.Cancelled); refresh() }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Cancelar este plan", color = NexoMuted) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Cita Segura ayuda a prepararte, pero no reemplaza a los servicios de emergencia. NEXO nunca envía mensajes ni realiza llamadas sin tu confirmación.", color = NexoMuted, fontSize = 10.sp, lineHeight = 14.sp)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SafetyField(value: String, onChange: (String) -> Unit, label: String, hint: String? = null) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
        label = { Text(label) }, supportingText = hint?.let { { Text(it) } }, singleLine = true,
        shape = RoundedCornerShape(16.dp)
    )
}
