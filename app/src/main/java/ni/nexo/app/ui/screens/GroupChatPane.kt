package ni.nexo.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ni.nexo.app.data.ChatMessage
import ni.nexo.app.data.GroupSummary
import ni.nexo.app.data.MessageKind
import ni.nexo.app.data.NexoRepository
import ni.nexo.app.ui.media.VoiceNoteRecorder
import ni.nexo.app.ui.theme.NexoCyan
import ni.nexo.app.ui.theme.NexoMuted
import ni.nexo.app.ui.theme.NexoNight
import ni.nexo.app.ui.theme.NexoNightSoft
import ni.nexo.app.ui.theme.NexoPink
import ni.nexo.app.ui.theme.NexoPurple

@Composable
fun GroupChatPane(
    group: GroupSummary,
    repository: NexoRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceNoteRecorder(context) }
    val listState = rememberLazyListState()
    var messages by remember(group.id) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var draft by remember(group.id) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var attachments by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun uploadAndSend(bytes: ByteArray, mime: String, fileName: String, kind: MessageKind, durationMs: Long? = null) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val path = repository.uploadGroupMedia(group.id, bytes, mime, fileName)
                repository.sendGroupMessage(
                    groupId = group.id,
                    text = draft.trim(),
                    kind = kind,
                    mediaPath = path,
                    mediaMime = mime,
                    mediaSizeBytes = bytes.size.toLong(),
                    durationMs = durationMs
                )
                draft = ""
            } catch (t: Throwable) {
                error = t.message ?: "No pudimos enviar el archivo."
            } finally {
                busy = false
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("No pudimos leer la foto.")
                uploadAndSend(bytes, mime, uri.lastPathSegment ?: "foto.jpg", MessageKind.Image)
            }.onFailure { error = it.message }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val mime = context.contentResolver.getType(uri) ?: "video/mp4"
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("No pudimos leer el video.")
                uploadAndSend(bytes, mime, uri.lastPathSegment ?: "video.mp4", MessageKind.Video)
            }.onFailure { error = it.message }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("No pudimos leer el archivo.")
                uploadAndSend(bytes, mime, uri.lastPathSegment ?: "archivo", MessageKind.Document)
            }.onFailure { error = it.message }
        }
    }

    fun startRecording() {
        runCatching {
            recorder.start()
            recording = true
        }.onFailure { error = it.message ?: "No pudimos iniciar el micrófono." }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else error = "NEXO necesita permiso de micrófono para notas de voz."
    }

    fun toggleRecording() {
        if (!recording) {
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startRecording()
            } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            val result = runCatching { recorder.stop() }.getOrNull()
            recording = false
            if (result != null) {
                uploadAndSend(result.bytes, "audio/mp4", result.fileName, MessageKind.Audio, result.durationMs)
            }
        }
    }

    DisposableEffect(Unit) { onDispose { recorder.cancel() } }

    LaunchedEffect(group.id, repository) {
        try {
            repository.observeGroupMessages(group.id).collectLatest { messages = it }
        } catch (t: Throwable) {
            error = t.message ?: "No pudimos cargar el grupo."
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().background(NexoNight)) {
        Surface(color = NexoNightSoft.copy(alpha = 0.98f)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
                Surface(shape = CircleShape, color = NexoPurple, modifier = Modifier.size(42.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(group.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(group.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Text("${group.memberCount} miembros", color = NexoCyan, fontSize = 11.sp)
                }
            }
        }

        error?.let {
            Text(it, color = NexoPink, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(10.dp))
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        color = if (message.fromMe) Color(0xFF6334D8) else Color(0xFF1A1D35),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(0.82f)
                    ) {
                        Column(Modifier.padding(11.dp)) {
                            if (!message.fromMe) {
                                Text(message.senderName ?: "Miembro", color = NexoCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(3.dp))
                            }
                            GroupMediaContent(message)
                            if (message.text.isNotBlank()) {
                                if (message.mediaPath != null) Spacer(Modifier.height(5.dp))
                                Text(message.text, color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        Surface(color = NexoNightSoft.copy(alpha = 0.98f)) {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(4000) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Mensaje al grupo") },
                    maxLines = 4,
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = NexoCyan,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
                        focusedPlaceholderColor = NexoMuted,
                        unfocusedPlaceholderColor = NexoMuted,
                        cursorColor = NexoCyan
                    ),
                    trailingIcon = {
                        Row {
                            androidx.compose.foundation.layout.Box {
                                IconButton(onClick = { attachments = true }) {
                                    Icon(Icons.Rounded.AttachFile, contentDescription = "Adjuntar", tint = NexoMuted)
                                }
                                DropdownMenu(expanded = attachments, onDismissRequest = { attachments = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Foto") },
                                        leadingIcon = { Icon(Icons.Rounded.Image, null) },
                                        onClick = { attachments = false; imagePicker.launch("image/*") }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Video") },
                                        leadingIcon = { Icon(Icons.Rounded.Videocam, null) },
                                        onClick = { attachments = false; videoPicker.launch("video/*") }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Cualquier archivo") },
                                        leadingIcon = { Icon(Icons.Rounded.Description, null) },
                                        onClick = { attachments = false; filePicker.launch("*/*") }
                                    )
                                }
                            }
                        }
                    }
                )
                Spacer(Modifier.width(7.dp))
                Surface(
                    modifier = Modifier.size(50.dp),
                    shape = CircleShape,
                    color = if (recording) NexoPink else if (draft.isNotBlank()) NexoCyan else NexoPurple
                ) {
                    IconButton(
                        enabled = !busy,
                        onClick = {
                            if (draft.isNotBlank()) {
                                val text = draft.trim()
                                busy = true
                                scope.launch {
                                    runCatching { repository.sendGroupMessage(group.id, text) }
                                        .onSuccess { draft = "" }
                                        .onFailure { error = it.message }
                                    busy = false
                                }
                            } else toggleRecording()
                        }
                    ) {
                        Icon(
                            if (draft.isNotBlank()) Icons.Rounded.Send else Icons.Rounded.Mic,
                            contentDescription = if (draft.isNotBlank()) "Enviar" else "Nota de voz",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupMediaContent(message: ChatMessage) {
    when (message.kind) {
        MessageKind.Image -> {
            if (!message.mediaUrl.isNullOrBlank()) {
                AsyncImage(
                    model = message.mediaUrl,
                    contentDescription = "Foto",
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                    contentScale = ContentScale.Crop
                )
            } else GroupMediaPlaceholder("Foto")
        }
        MessageKind.Video -> GroupMediaPlaceholder("Video")
        MessageKind.Audio -> GroupMediaPlaceholder("Nota de voz")
        MessageKind.Document -> GroupMediaPlaceholder(message.mediaMime ?: "Archivo")
        else -> Unit
    }
}

@Composable
private fun GroupMediaPlaceholder(label: String) {
    Surface(color = Color.Black.copy(alpha = 0.16f), shape = RoundedCornerShape(12.dp)) {
        Text(
            label,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        )
    }
}