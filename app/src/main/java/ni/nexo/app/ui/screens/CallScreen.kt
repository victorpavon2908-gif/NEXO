package ni.nexo.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.Ringtone
import android.media.RingtoneManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ni.nexo.app.data.CallLifecycleService
import ni.nexo.app.data.CallRecord
import ni.nexo.app.data.CallState
import ni.nexo.app.data.CallType
import ni.nexo.app.data.NexoRepository
import ni.nexo.app.data.PersonProfile
import ni.nexo.app.ui.calls.WebRtcCallEngine
import ni.nexo.app.ui.components.ProfilePhoto
import ni.nexo.app.ui.theme.NexoCyan
import ni.nexo.app.ui.theme.NexoMuted
import ni.nexo.app.ui.theme.NexoPink
import ni.nexo.app.ui.theme.NexoPurple
import org.webrtc.SurfaceViewRenderer

private const val RING_TIMEOUT_MS = 30_000L
private const val SIGNAL_POLL_MS = 350L

private enum class MediaPermissionPurpose { Outgoing, Incoming }

@Composable
fun CallScreen(
    person: PersonProfile,
    initialCall: CallRecord,
    repository: NexoRepository,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = remember { CallLifecycleService() }
    var state by remember(initialCall.id) { mutableStateOf(initialCall.state) }
    var muted by remember(initialCall.id) { mutableStateOf(false) }
    var videoEnabled by remember(initialCall.id) { mutableStateOf(initialCall.type == CallType.Video) }
    var ending by remember(initialCall.id) { mutableStateOf(false) }
    var engine by remember(initialCall.id) { mutableStateOf<WebRtcCallEngine?>(null) }
    var lastSignalId by remember(initialCall.id) { mutableLongStateOf(0L) }
    var permissionPurpose by remember(initialCall.id) { mutableStateOf<MediaPermissionPurpose?>(null) }
    var failureText by remember(initialCall.id) { mutableStateOf<String?>(null) }

    val ringtone: Ringtone? = remember(initialCall.id) {
        runCatching {
            RingtoneManager.getRingtone(
                context,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            )
        }.getOrNull()
    }

    fun finishTerminal(next: CallState, delayMs: Long = 450L) {
        if (ending) return
        ending = true
        ringtone?.stop()
        engine?.close()
        engine = null
        state = next
        scope.launch {
            delay(delayMs)
            onFinished()
        }
    }

    fun failCall(message: String) {
        failureText = message
        scope.launch {
            runCatching { repository.endCall(initialCall.id) }
            finishTerminal(CallState.Failed, 900L)
        }
    }

    fun startEngine() {
        if (engine != null || ending) return
        runCatching {
            WebRtcCallEngine(
                context = context,
                repository = repository,
                call = initialCall.copy(state = state),
                listener = object : WebRtcCallEngine.Listener {
                    override fun onConnected() {
                        scope.launch {
                            if (!ending && state != CallState.Connected) {
                                state = CallState.Connected
                                runCatching { repository.markCallConnected(initialCall.id) }
                            }
                        }
                    }

                    override fun onRemoteHangup() {
                        scope.launch {
                            if (!ending) finishTerminal(CallState.Ended)
                        }
                    }

                    override fun onConnectionFailed(message: String) {
                        scope.launch { if (!ending) failCall(message) }
                    }
                }
            )
        }.onSuccess { created ->
            engine = created
            created.start()
        }.onFailure { error ->
            failCall(error.message ?: "No pudimos iniciar el audio/video.")
        }
    }

    fun requiredPermissionsGranted(): Boolean {
        val audio = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val camera = initialCall.type != CallType.Video ||
            context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        return audio && camera
    }

    fun acceptIncomingAndStart() {
        if (ending) return
        scope.launch {
            runCatching { repository.acceptCall(initialCall.id) }
                .onSuccess {
                    ringtone?.stop()
                    state = CallState.Connecting
                    startEngine()
                }
                .onFailure { error -> failCall(error.message ?: "No pudimos aceptar la llamada.") }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val audioGranted = result[Manifest.permission.RECORD_AUDIO] == true ||
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cameraGranted = initialCall.type != CallType.Video ||
            result[Manifest.permission.CAMERA] == true ||
            context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val purpose = permissionPurpose
        permissionPurpose = null
        if (!audioGranted || !cameraGranted) {
            failCall("NEXO necesita permiso de micrófono${if (initialCall.type == CallType.Video) " y cámara" else ""} para esta llamada.")
        } else {
            when (purpose) {
                MediaPermissionPurpose.Outgoing -> startEngine()
                MediaPermissionPurpose.Incoming -> acceptIncomingAndStart()
                null -> Unit
            }
        }
    }

    fun requestPermissionsFor(purpose: MediaPermissionPurpose) {
        if (requiredPermissionsGranted()) {
            when (purpose) {
                MediaPermissionPurpose.Outgoing -> startEngine()
                MediaPermissionPurpose.Incoming -> acceptIncomingAndStart()
            }
            return
        }
        permissionPurpose = purpose
        val permissions = if (initialCall.type == CallType.Video) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
        permissionLauncher.launch(permissions)
    }

    // Arranca el motor inmediatamente en una llamada saliente. En una llamada
    // entrante se espera a que el usuario pulse aceptar.
    LaunchedEffect(initialCall.id) {
        if (!repository.configured) {
            delay(700)
            state = CallState.Connecting
            delay(700)
            state = CallState.Connected
        } else if (initialCall.outgoing) {
            requestPermissionsFor(MediaPermissionPurpose.Outgoing)
        }
    }

    // Timbre local para llamada entrante mientras siga en ringing.
    LaunchedEffect(state, initialCall.outgoing) {
        if (!initialCall.outgoing && state == CallState.Ringing && !ending) {
            runCatching { ringtone?.play() }
        } else {
            runCatching { ringtone?.stop() }
        }
    }

    // Estado remoto + señales WebRTC. Un intervalo corto evita depender de una
    // sesión de Realtime perfecta y sigue funcionando tras microcortes de red.
    LaunchedEffect(initialCall.id, repository.configured) {
        if (!repository.configured) return@LaunchedEffect
        while (!ending) {
            val remote = runCatching {
                repository.loadCalls().firstOrNull { it.id == initialCall.id }
            }.getOrNull()

            if (remote != null && remote.state != state) {
                state = remote.state
                if (state in setOf(CallState.Declined, CallState.Missed, CallState.Ended, CallState.Failed)) {
                    failureText = when (state) {
                        CallState.Declined -> "La otra persona rechazó la llamada."
                        CallState.Missed -> "No hubo respuesta."
                        CallState.Failed -> "No se pudo establecer la llamada."
                        else -> null
                    }
                    finishTerminal(state, 700L)
                    break
                }
            }

            val activeEngine = engine
            if (activeEngine != null) {
                val signals = runCatching {
                    repository.loadCallSignals(initialCall.id, lastSignalId)
                }.getOrDefault(emptyList())
                signals.forEach { signal ->
                    if (signal.id > lastSignalId) lastSignalId = signal.id
                    activeEngine.handleSignal(signal)
                }
            }
            delay(SIGNAL_POLL_MS)
        }
    }

    // Una llamada saliente que no obtiene respuesta se convierte en perdida.
    LaunchedEffect(initialCall.id, initialCall.outgoing, repository.configured) {
        if (!repository.configured || !initialCall.outgoing) return@LaunchedEffect
        delay(RING_TIMEOUT_MS)
        if (!ending && state == CallState.Ringing) {
            runCatching {
                if (lifecycle.available) lifecycle.markMissed(initialCall.id)
                else repository.endCall(initialCall.id)
            }
            finishTerminal(CallState.Missed, 800L)
        }
    }

    DisposableEffect(initialCall.id) {
        onDispose {
            runCatching { ringtone?.stop() }
            engine?.close()
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
        if (initialCall.type == CallType.Video && engine != null && state != CallState.Ringing) {
            AndroidView(
                factory = { viewContext ->
                    SurfaceViewRenderer(viewContext).also { engine?.attachRemoteRenderer(it) }
                },
                modifier = Modifier.fillMaxSize()
            )
            AndroidView(
                factory = { viewContext ->
                    SurfaceViewRenderer(viewContext).also { engine?.attachLocalRenderer(it) }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 54.dp, end = 18.dp)
                    .width(118.dp)
                    .height(168.dp)
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 42.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(if (initialCall.type == CallType.Video && engine != null) 12.dp else 36.dp))
            if (initialCall.type != CallType.Video || engine == null || state == CallState.Ringing) {
                ProfilePhoto(
                    photoUrl = person.photoUrl,
                    name = person.name,
                    modifier = Modifier.size(132.dp),
                    shape = CircleShape,
                    backgroundColor = NexoPurple,
                    textColor = Color.White
                )
                Spacer(Modifier.height(20.dp))
            } else {
                Spacer(Modifier.height(170.dp))
            }

            Text(person.name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(7.dp))
            Text(
                text = failureText ?: when (state) {
                    CallState.Ringing -> if (initialCall.outgoing) "Llamando…" else "Llamada entrante"
                    CallState.Connecting -> "Conectando de forma segura…"
                    CallState.Connected -> "Conectado · cifrado WebRTC"
                    CallState.Declined -> "Llamada rechazada"
                    CallState.Missed -> "Sin respuesta"
                    CallState.Ended -> "Llamada finalizada"
                    CallState.Failed -> "No se pudo conectar"
                },
                color = if (state == CallState.Connected) NexoCyan else NexoMuted,
                fontSize = 15.sp
            )

            Spacer(Modifier.weight(1f))

            if (!initialCall.outgoing && state == CallState.Ringing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IncomingAction(
                        color = NexoPink,
                        icon = Icons.Rounded.CallEnd,
                        label = "Rechazar",
                        onClick = {
                            if (!ending) {
                                ending = true
                                ringtone?.stop()
                                scope.launch {
                                    runCatching { repository.declineCall(initialCall.id) }
                                    state = CallState.Declined
                                    delay(350)
                                    onFinished()
                                }
                            }
                        }
                    )
                    IncomingAction(
                        color = NexoCyan,
                        icon = if (initialCall.type == CallType.Video) Icons.Rounded.Videocam else Icons.Rounded.Call,
                        label = "Aceptar",
                        onClick = { requestPermissionsFor(MediaPermissionPurpose.Incoming) }
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallControl(
                        active = muted,
                        onClick = {
                            muted = !muted
                            engine?.setMuted(muted)
                        },
                        activeIcon = Icons.Rounded.MicOff,
                        inactiveIcon = Icons.Rounded.Mic,
                        label = if (muted) "Activar" else "Silenciar"
                    )
                    if (initialCall.type == CallType.Video) {
                        CallControl(
                            active = !videoEnabled,
                            onClick = {
                                videoEnabled = !videoEnabled
                                engine?.setVideoEnabled(videoEnabled)
                            },
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
                                    ringtone?.stop()
                                    engine?.close()
                                    engine = null
                                    scope.launch {
                                        runCatching { repository.endCall(initialCall.id) }
                                        state = CallState.Ended
                                        delay(250)
                                        onFinished()
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Rounded.CallEnd,
                                contentDescription = "Colgar",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun IncomingAction(
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(color = color, shape = CircleShape, modifier = Modifier.size(70.dp)) {
            IconButton(onClick = onClick) {
                Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Surface(color = Color.Black.copy(alpha = 0.20f), shape = RoundedCornerShape(50)) {
            Text(label, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
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
