package ni.nexo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ni.nexo.app.data.CallLifecycleService
import ni.nexo.app.data.CallRecord
import ni.nexo.app.data.CallState
import ni.nexo.app.data.CallType
import ni.nexo.app.data.NexoRepository
import ni.nexo.app.data.PersonProfile
import ni.nexo.app.ui.components.ProfilePhoto
import ni.nexo.app.ui.theme.NexoCyan
import ni.nexo.app.ui.theme.NexoMuted
import ni.nexo.app.ui.theme.NexoPink
import ni.nexo.app.ui.theme.NexoPurple

private const val RING_TIMEOUT_MS = 30_000L
private const val REMOTE_STATE_POLL_MS = 650L

@Composable
fun CallScreen(
    person: PersonProfile,
    initialCall: CallRecord,
    repository: NexoRepository,
    onFinished: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val lifecycle = remember { CallLifecycleService() }
    var state by remember(initialCall.id) { mutableStateOf(initialCall.state) }
    var muted by remember { mutableStateOf(false) }
    var videoEnabled by remember { mutableStateOf(initialCall.type == CallType.Video) }
    var ending by remember { mutableStateOf(false) }

    // Demo mode keeps the previous simulated flow. Real mode is driven by the
    // state persisted in Supabase so remote reject/end is reflected here.
    LaunchedEffect(initialCall.id, repository.configured) {
        if (!repository.configured) {
            delay(850)
            state = CallState.Connecting
            delay(700)
            state = CallState.Connected
            return@LaunchedEffect
        }

        while (!ending && state in setOf(CallState.Ringing, CallState.Connecting, CallState.Connected)) {
            val remote = runCatching {
                repository.loadCalls().firstOrNull { it.id == initialCall.id }
            }.getOrNull()

            if (remote != null && remote.state != state) {
                state = remote.state
                if (state in setOf(
                        CallState.Declined,
                        CallState.Missed,
                        CallState.Ended,
                        CallState.Failed
                    )
                ) {
                    ending = true
                    delay(550)
                    onFinished()
                    break
                }
            }
            delay(REMOTE_STATE_POLL_MS)
        }
    }

    // If nobody answers an outgoing call, persist it as missed. This avoids a
    // call remaining forever in "ringing" when the remote device never answers.
    LaunchedEffect(initialCall.id, initialCall.outgoing, repository.configured) {
        if (!repository.configured || !initialCall.outgoing || state != CallState.Ringing) return@LaunchedEffect
        delay(RING_TIMEOUT_MS)
        if (!ending && state == CallState.Ringing) {
            ending = true
            runCatching {
                if (lifecycle.available) lifecycle.markMissed(initialCall.id)
                else repository.endCall(initialCall.id)
            }
            state = CallState.Missed
            delay(700)
            onFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF050714), Color(0xFF17113C), Color(0xFF10091E))
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 42.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(36.dp))
            ProfilePhoto(
                photoUrl = person.photoUrl,
                name = person.name,
                modifier = Modifier.size(132.dp),
                shape = CircleShape,
                backgroundColor = NexoPurple,
                textColor = Color.White
            )
            Spacer(Modifier.height(20.dp))
            Text(person.name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(7.dp))
            Text(
                text = when (state) {
                    CallState.Ringing -> "Llamando…"
                    CallState.Connecting -> "Conectando de forma segura…"
                    CallState.Connected -> "Conectado"
                    CallState.Declined -> "Llamada rechazada"
                    CallState.Missed -> "Sin respuesta"
                    CallState.Ended -> "Llamada finalizada"
                    CallState.Failed -> "No se pudo conectar"
                },
                color = if (state == CallState.Connected) NexoCyan else NexoMuted,
                fontSize = 15.sp
            )

            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControl(
                    active = muted,
                    onClick = { muted = !muted },
                    activeIcon = Icons.Rounded.MicOff,
                    inactiveIcon = Icons.Rounded.Mic,
                    label = if (muted) "Activar" else "Silenciar"
                )
                if (initialCall.type == CallType.Video) {
                    CallControl(
                        active = !videoEnabled,
                        onClick = { videoEnabled = !videoEnabled },
                        activeIcon = Icons.Rounded.VideocamOff,
                        inactiveIcon = Icons.Rounded.Videocam,
                        label = if (videoEnabled) "Cámara" else "Sin video"
                    )
                }
                Surface(color = NexoPink, shape = CircleShape, modifier = Modifier.size(66.dp)) {
                    IconButton(
                        onClick = {
                            if (!ending) {
                                ending = true
                                scope.launch {
                                    runCatching {
                                        if (lifecycle.available) lifecycle.end(initialCall.id)
                                        else repository.endCall(initialCall.id)
                                    }
                                    state = CallState.Ended
                                    delay(250)
                                    onFinished()
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.CallEnd, contentDescription = "Colgar", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun CallControl(
    active: Boolean,
    onClick: () -> Unit,
    activeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    inactiveIcon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = if (active) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f),
            shape = CircleShape,
            modifier = Modifier.size(58.dp)
        ) {
            IconButton(onClick = onClick) {
                Icon(if (active) activeIcon else inactiveIcon, contentDescription = label, tint = Color.White)
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(label, color = NexoMuted, fontSize = 10.sp)
    }
}
