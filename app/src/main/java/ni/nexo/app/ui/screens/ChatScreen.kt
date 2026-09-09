package ni.nexo.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ni.nexo.app.data.ChatMessage
import ni.nexo.app.data.MessageKind
import ni.nexo.app.data.MessageStatus
import ni.nexo.app.data.NexoRepository
import ni.nexo.app.data.PersonProfile
import ni.nexo.app.ui.chat.ChatBubbleStyle
import ni.nexo.app.ui.chat.ChatPreferences
import ni.nexo.app.ui.chat.ChatPreferencesStore
import ni.nexo.app.ui.chat.ChatWallpaper
import ni.nexo.app.ui.components.ProfilePhoto
import ni.nexo.app.ui.media.VoiceNoteRecorder
import ni.nexo.app.ui.userFacingError
import ni.nexo.app.ui.theme.NexoCyan
import ni.nexo.app.ui.theme.NexoMuted
import ni.nexo.app.ui.theme.NexoNight
import ni.nexo.app.ui.theme.NexoNightSoft
import ni.nexo.app.ui.theme.NexoPink
import ni.nexo.app.ui.theme.NexoPurple

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    person: PersonProfile,
    repository: NexoRepository,
    onBack: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit,
    onBlocked: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val preferencesStore = remember { ChatPreferencesStore(context) }
    val voiceRecorder = remember { VoiceNoteRecorder(context) }

    var preferences by remember { mutableStateOf(preferencesStore.load()) }
    var messages by remember(person.id) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var draft by remember(person.id) { mutableStateOf("") }
    var loading by remember(person.id) { mutableStateOf(true) }
    var sending by remember(person.id) { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var errorMessage by remember(person.id) { mutableStateOf<String?>(null) }
    var replyTarget by remember(person.id) { mutableStateOf<ChatMessage?>(null) }
    var editingTarget by remember(person.id) { mutableStateOf<ChatMessage?>(null) }
    var actionTarget by remember(person.id) { mutableStateOf<ChatMessage?>(null) }
    var showEmojiRow by remember { mutableStateOf(false) }
    var showAttachments by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showPreferences by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun uploadAndSend(
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
        kind: MessageKind,
        caption: String = "",
        durationMs: Long? = null
    ) {
        if (sending) return
        sending = true
        scope.launch {
            try {
                val path = repository.uploadChatMedia(person.id, bytes, mimeType, fileName)
                repository.sendMessage(
                    targetUserId = person.id,
                    text = caption,
                    replyToText = replyTarget?.text,
                    replyToId = replyTarget?.id,
                    kind = kind,
                    mediaPath = path,
                    mediaMime = mimeType,
                    mediaSizeBytes = bytes.size.toLong(),
                    durationMs = durationMs
                )
                replyTarget = null
            } catch (error: Exception) {
                errorMessage = userFacingError(error, "No pudimos enviar el archivo.")
            } finally {
                sending = false
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("No pudimos leer la imagen.")
                    uploadAndSend(bytes, mime, uri.lastPathSegment ?: "imagen.jpg", MessageKind.Image, draft.trim())
                    draft = ""
                }.onFailure { errorMessage = userFacingError(it, "No pudimos preparar la imagen.") }
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val mime = context.contentResolver.getType(uri) ?: "video/mp4"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("No pudimos leer el video.")
                    uploadAndSend(bytes, mime, uri.lastPathSegment ?: "video.mp4", MessageKind.Video, draft.trim())
                    draft = ""
                }.onFailure { errorMessage = userFacingError(it, "No pudimos preparar el video.") }
            }
        }
    }

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("No pudimos leer el documento.")
                    uploadAndSend(bytes, mime, uri.lastPathSegment ?: "documento", MessageKind.Document, draft.trim())
                    draft = ""
                }.onFailure { errorMessage = userFacingError(it, "No pudimos preparar el documento.") }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
            uploadAndSend(output.toByteArray(), "image/jpeg", "camara-${System.currentTimeMillis()}.jpg", MessageKind.Image, draft.trim())
            draft = ""
        }
    }

    fun startRecording() {
        runCatching {
            voiceRecorder.start()
            recording = true
            toast("Grabando nota de voz… tocá de nuevo para enviar")
        }.onFailure { errorMessage = userFacingError(it, "No pudimos iniciar el micrófono.") }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else toast("NEXO necesita permiso de micrófono para notas de voz.")
    }

    fun toggleVoiceRecording() {
        if (!recording) {
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startRecording()
            } else {
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            val result = runCatching { voiceRecorder.stop() }.getOrNull()
            recording = false
            if (result != null) {
                uploadAndSend(
                    result.bytes,
                    "audio/mp4",
                    result.fileName,
                    MessageKind.Audio,
                    durationMs = result.durationMs
                )
            } else {
                toast("La nota fue demasiado corta.")
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { voiceRecorder.cancel() }
    }

    LaunchedEffect(person.id, repository) {
        loading = true
        errorMessage = null
        try {
            repository.observeMessages(person.id).collectLatest { fresh ->
                messages = fresh
                loading = false
                runCatching { repository.markConversationRead(person.id) }
            }
        } catch (error: Exception) {
            loading = false
            errorMessage = userFacingError(error, "No pudimos cargar la conversación.")
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex + 1)
    }

    fun savePreferences(updated: ChatPreferences) {
        preferences = updated
        preferencesStore.save(updated)
    }

    fun sendCurrentDraft() {
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        sending = true
        errorMessage = null
        scope.launch {
            try {
                val editing = editingTarget
                if (editing != null) {
                    repository.editMessage(editing.id, text)
                    editingTarget = null
                } else {
                    repository.sendMessage(
                        targetUserId = person.id,
                        text = text,
                        replyToText = replyTarget?.text,
                        replyToId = replyTarget?.id
                    )
                    replyTarget = null
                }
                draft = ""
                showEmojiRow = false
            } catch (error: Exception) {
                errorMessage = userFacingError(error, "No pudimos enviar el mensaje.")
            } finally {
                sending = false
            }
        }
    }

    val visibleMessages = remember(messages, searchQuery, searchMode) {
        if (!searchMode || searchQuery.isBlank()) messages
        else messages.filter { it.text.contains(searchQuery, ignoreCase = true) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(chatWallpaper(preferences.wallpaper))
            .imePadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = NexoNightSoft.copy(alpha = 0.96f), shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                    ProfilePhoto(
                        photoUrl = person.photoUrl,
                        name = person.name,
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        backgroundColor = NexoPurple,
                        textColor = Color.White
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(person.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (person.verified) {
                                Spacer(Modifier.width(4.dp))
                                Text("✓", color = NexoCyan, fontWeight = FontWeight.Black)
                            }
                        }
                        Text(
                            when {
                                !repository.configured -> "en línea · modo prueba"
                                person.phoneVerified -> "Número verificado · chat privado"
                                person.verified -> "Perfil verificado · chat privado"
                                else -> "Conversación privada"
                            },
                            color = NexoCyan,
                            fontSize = 10.sp
                        )
                    }
                    IconButton(onClick = onVideoCall) {
                        Icon(Icons.Rounded.Videocam, contentDescription = "Videollamada", tint = Color.White)
                    }
                    IconButton(onClick = onAudioCall) {
                        Icon(Icons.Rounded.Call, contentDescription = "Llamada", tint = Color.White)
                    }
                    Box {
                        IconButton(onClick = { showMore = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Más", tint = Color.White)
                        }
                        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                            DropdownMenuItem(
                                text = { Text("Buscar en el chat") },
                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                onClick = { showMore = false; searchMode = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Personalizar chat") },
                                leadingIcon = { Icon(Icons.Rounded.Palette, contentDescription = null) },
                                onClick = { showMore = false; showPreferences = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Reportar") },
                                leadingIcon = { Icon(Icons.Rounded.Report, contentDescription = null) },
                                onClick = { showMore = false; showReport = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Bloquear") },
                                leadingIcon = { Icon(Icons.Rounded.Block, contentDescription = null) },
                                onClick = { showMore = false; showBlockConfirm = true }
                            )
                        }
                    }
                }
            }

            if (searchMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(NexoNightSoft).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Buscar mensaje") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = nexoChatFieldColors()
                    )
                    IconButton(onClick = { searchMode = false; searchQuery = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cerrar búsqueda", tint = Color.White)
                    }
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Surface(color = Color(0xFF5B1D28).copy(alpha = 0.9f)) {
                    Text(errorMessage!!, color = Color.White, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(10.dp))
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(if (preferences.bubbleStyle == ChatBubbleStyle.Compact) 4.dp else 7.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
                        Surface(color = NexoNightSoft.copy(alpha = 0.72f), shape = RoundedCornerShape(12.dp)) {
                            Text("HOY", color = NexoMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                        }
                    }
                }
                if (loading) {
                    item { Text("Cargando conversación…", color = NexoMuted, modifier = Modifier.padding(12.dp)) }
                } else if (visibleMessages.isEmpty()) {
                    item {
                        Text(
                            if (searchMode) "No encontramos mensajes con ese texto." else "Todavía no hay mensajes. Empezá la conversación cuando quieras.",
                            color = NexoMuted,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(visibleMessages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            preferences = preferences,
                            onActions = { actionTarget = message }
                        )
                    }
                }
            }

            if (!loading && messages.isEmpty() && !searchMode && editingTarget == null && replyTarget == null) {
                ConversationStarters(person = person, onChoose = { draft = it })
            }

            (editingTarget ?: replyTarget)?.let { message ->
                Surface(color = NexoNightSoft.copy(alpha = 0.97f)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(3.dp).height(38.dp).background(if (editingTarget != null) NexoPink else NexoCyan))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (editingTarget != null) "Editando mensaje" else if (message.fromMe) "Respondiendo a vos" else "Respondiendo a ${person.name}",
                                color = if (editingTarget != null) NexoPink else NexoCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(message.text, color = NexoMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                        }
                        IconButton(onClick = {
                            if (editingTarget != null) draft = ""
                            editingTarget = null
                            replyTarget = null
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancelar", tint = NexoMuted)
                        }
                    }
                }
            }

            if (showEmojiRow) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(NexoNightSoft).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("😊", "😂", "❤️", "😍", "🔥", "👍", "🥰").forEach { emoji ->
                        Text(emoji, fontSize = 24.sp, modifier = Modifier.combinedClickable(onClick = { draft += emoji }, onLongClick = { draft += emoji }))
                    }
                }
            }

            Surface(color = NexoNight.copy(alpha = 0.98f)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Surface(modifier = Modifier.weight(1f), color = NexoNightSoft, shape = RoundedCornerShape(24.dp)) {
                        Row(modifier = Modifier.padding(start = 2.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showEmojiRow = !showEmojiRow }) {
                                Icon(Icons.Rounded.EmojiEmotions, contentDescription = "Emoji", tint = NexoMuted)
                            }
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { draft = it.take(4000) },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(if (editingTarget != null) "Editá el mensaje" else "Mensaje") },
                                maxLines = 5,
                                colors = nexoChatFieldColors(borderless = true),
                                shape = RoundedCornerShape(20.dp)
                            )
                            Box {
                                IconButton(onClick = { showAttachments = true }) {
                                    Icon(Icons.Rounded.AttachFile, contentDescription = "Adjuntar", tint = NexoMuted)
                                }
                                AttachmentMenu(
                                    expanded = showAttachments,
                                    onDismiss = { showAttachments = false },
                                    onPhoto = { showAttachments = false; imagePicker.launch("image/*") },
                                    onVideo = { showAttachments = false; videoPicker.launch("video/*") },
                                    onDocument = { showAttachments = false; documentPicker.launch("*/*") },
                                    onLocation = {
                                        showAttachments = false
                                        scope.launch {
                                            runCatching {
                                                repository.sendMessage(person.id, "Ubicación aproximada compartida", kind = MessageKind.Location)
                                            }.onFailure { errorMessage = userFacingError(it, "No pudimos compartir la ubicación.") }
                                        }
                                    },
                                    onContact = {
                                        showAttachments = false
                                        scope.launch {
                                            runCatching {
                                                repository.sendMessage(person.id, "Contacto compartido", kind = MessageKind.Contact)
                                            }.onFailure { errorMessage = userFacingError(it, "No pudimos compartir el contacto.") }
                                        }
                                    }
                                )
                            }
                            IconButton(onClick = { cameraLauncher.launch(null) }) {
                                Icon(Icons.Rounded.CameraAlt, contentDescription = "Cámara", tint = NexoMuted)
                            }
                        }
                    }
                    Spacer(Modifier.width(7.dp))
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        color = when {
                            recording -> NexoPink
                            draft.isNotBlank() -> NexoCyan
                            else -> NexoPurple
                        }
                    ) {
                        IconButton(onClick = { if (draft.isNotBlank()) sendCurrentDraft() else toggleVoiceRecording() }) {
                            Icon(
                                imageVector = if (draft.isNotBlank()) Icons.Rounded.Send else Icons.Rounded.Mic,
                                contentDescription = if (draft.isNotBlank()) "Enviar" else if (recording) "Detener nota de voz" else "Nota de voz",
                                tint = if (draft.isNotBlank()) Color(0xFF041119) else Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    actionTarget?.let { message ->
        MessageActionsDialog(
            message = message,
            onDismiss = { actionTarget = null },
            onReply = { replyTarget = message; actionTarget = null },
            onEdit = {
                editingTarget = message
                replyTarget = null
                draft = message.text
                actionTarget = null
            },
            onDelete = {
                actionTarget = null
                scope.launch {
                    runCatching { repository.deleteMessage(message.id) }
                        .onFailure { errorMessage = userFacingError(it, "No pudimos eliminar el mensaje.") }
                }
            },
            onReaction = { emoji ->
                actionTarget = null
                scope.launch {
                    runCatching { repository.reactToMessage(message.id, emoji) }
                        .onFailure { errorMessage = userFacingError(it, "No pudimos guardar la reacción.") }
                }
            }
        )
    }

    if (showPreferences) {
        ChatPreferencesDialog(
            preferences = preferences,
            onDismiss = { showPreferences = false },
            onSave = { savePreferences(it); showPreferences = false }
        )
    }

    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = { Text("Bloquear a ${person.name}") },
            text = { Text("Dejarán de aparecerse mutuamente y no podrán enviarse mensajes ni iniciar llamadas.") },
            confirmButton = {
                TextButton(onClick = {
                    showBlockConfirm = false
                    scope.launch {
                        runCatching { repository.blockUser(person.id) }
                            .onSuccess { onBlocked() }
                            .onFailure { errorMessage = userFacingError(it, "No pudimos bloquear este perfil.") }
                    }
                }) { Text("Bloquear") }
            },
            dismissButton = { TextButton(onClick = { showBlockConfirm = false }) { Text("Cancelar") } }
        )
    }

    if (showReport) {
        AlertDialog(
            onDismissRequest = { showReport = false },
            title = { Text("Reportar a ${person.name}") },
            text = {
                Column {
                    Text("El reporte es privado. Explicá brevemente el motivo.")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = reportReason,
                        onValueChange = { reportReason = it.take(500) },
                        placeholder = { Text("Spam, acoso, perfil falso…") },
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = reportReason.trim().length >= 2,
                    onClick = {
                        val reason = reportReason.trim()
                        showReport = false
                        reportReason = ""
                        scope.launch {
                            runCatching { repository.reportUser(person.id, reason) }
                                .onSuccess { toast("Reporte enviado") }
                                .onFailure { errorMessage = userFacingError(it, "No pudimos enviar el reporte.") }
                        }
                    }
                ) { Text("Enviar reporte") }
            },
            dismissButton = { TextButton(onClick = { showReport = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun ConversationStarters(
    person: PersonProfile,
    onChoose: (String) -> Unit
) {
    val interest = person.interests.firstOrNull()
    val suggestions = remember(person.id, interest) {
        listOfNotNull(
            interest?.let { "Vi que te gusta $it, ¿qué es lo que más disfrutás de eso?" },
            "¿Cuál sería tu plan ideal para un fin de semana?",
            "Contame algo sencillo que siempre te alegra el día."
        ).distinct()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NexoNight.copy(alpha = 0.96f))
            .padding(top = 8.dp, bottom = 4.dp)
    ) {
        Text(
            "Empezá con algo real",
            color = NexoCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item { Spacer(Modifier.width(6.dp)) }
            items(suggestions) { suggestion ->
                Surface(
                    color = NexoNightSoft,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.clickable { onChoose(suggestion) }
                ) {
                    Text(
                        suggestion,
                        color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.width(210.dp).padding(horizontal = 12.dp, vertical = 9.dp)
                    )
                }
            }
            item { Spacer(Modifier.width(6.dp)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    preferences: ChatPreferences,
    onActions: () -> Unit
) {
    val mine = message.fromMe
    val bubbleColor = when {
        preferences.bubbleStyle == ChatBubbleStyle.Glass && mine -> NexoPurple.copy(alpha = 0.76f)
        preferences.bubbleStyle == ChatBubbleStyle.Glass -> NexoNightSoft.copy(alpha = 0.78f)
        mine -> Color(0xFF6334D8)
        else -> Color(0xFF1A1D35)
    }
    val radius = when (preferences.bubbleStyle) {
        ChatBubbleStyle.Compact -> 12.dp
        ChatBubbleStyle.Glass -> 22.dp
        ChatBubbleStyle.Soft -> 20.dp
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (preferences.bubbleStyle == ChatBubbleStyle.Compact) 0.84f else 0.82f)
                .combinedClickable(onClick = { }, onLongClick = onActions),
            color = bubbleColor,
            shape = RoundedCornerShape(radius)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                message.replyToText?.takeIf { it.isNotBlank() }?.let { quoted ->
                    Surface(color = Color.Black.copy(alpha = 0.18f), shape = RoundedCornerShape(10.dp)) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp)) {
                            Text("Respuesta", color = NexoCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(quoted, color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                if (!message.deleted) {
                    MediaContent(message)
                }
                if (message.text.isNotBlank() && (message.kind == MessageKind.Text || message.deleted || message.mediaPath != null)) {
                    if (message.mediaPath != null && !message.deleted) Spacer(Modifier.height(6.dp))
                    Text(
                        text = message.text,
                        color = if (message.deleted) Color.White.copy(alpha = 0.55f) else Color.White,
                        fontSize = (15f * preferences.textScale).sp,
                        lineHeight = (20f * preferences.textScale).sp
                    )
                }

                if (message.reactions.isNotEmpty()) {
                    Spacer(Modifier.height(5.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        message.reactions.entries.take(5).forEach { (emoji, count) ->
                            Surface(color = Color.Black.copy(alpha = 0.18f), shape = RoundedCornerShape(50)) {
                                Text("$emoji $count", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    if (message.edited) {
                        Text("editado · ", color = Color.White.copy(alpha = 0.46f), fontSize = 8.sp)
                    }
                    Text(displayMessageTime(message.createdAt), color = Color.White.copy(alpha = 0.58f), fontSize = 9.sp)
                    if (mine) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            statusGlyph(message.status),
                            color = if (message.status == MessageStatus.Read) NexoCyan else Color.White.copy(alpha = 0.65f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaContent(message: ChatMessage) {
    when (message.kind) {
        MessageKind.Image -> {
            if (!message.mediaUrl.isNullOrBlank() && !message.mediaUrl.startsWith("demo://")) {
                AsyncImage(
                    model = message.mediaUrl,
                    contentDescription = "Foto",
                    modifier = Modifier.fillMaxWidth().height(210.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                MediaPlaceholder(Icons.Rounded.Image, "Foto")
            }
        }
        MessageKind.Video -> MediaPlaceholder(Icons.Rounded.Videocam, "Video")
        MessageKind.Audio -> MediaPlaceholder(Icons.Rounded.Mic, "Nota de voz · ${formatDuration(message.durationMs)}")
        MessageKind.Document -> MediaPlaceholder(Icons.Rounded.Description, message.mediaMime ?: "Documento")
        MessageKind.Location -> MediaPlaceholder(Icons.Rounded.LocationOn, "Ubicación aproximada")
        MessageKind.Contact -> MediaPlaceholder(Icons.Rounded.Person, "Contacto")
        else -> Unit
    }
}

@Composable
private fun MediaPlaceholder(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(color = Color.Black.copy(alpha = 0.16f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = NexoCyan)
            Spacer(Modifier.width(10.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MessageActionsDialog(
    message: ChatMessage,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReaction: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mensaje") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("❤️", "😂", "😮", "😢", "👍", "🔥").forEach { emoji ->
                        Text(emoji, fontSize = 25.sp, modifier = Modifier.combinedClickable(onClick = { onReaction(emoji) }, onLongClick = { onReaction(emoji) }))
                    }
                }
                Spacer(Modifier.height(14.dp))
                TextButton(onClick = onReply, modifier = Modifier.fillMaxWidth()) { Text("Responder") }
                if (message.fromMe && !message.deleted && message.kind == MessageKind.Text) {
                    TextButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Edit, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Editar")
                    }
                }
                if (message.fromMe && !message.deleted) {
                    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = NexoPink)
                        Spacer(Modifier.width(8.dp))
                        Text("Eliminar para todos", color = NexoPink)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
private fun AttachmentMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPhoto: () -> Unit,
    onVideo: () -> Unit,
    onDocument: () -> Unit,
    onLocation: () -> Unit,
    onContact: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Foto") }, leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) }, onClick = onPhoto)
        DropdownMenuItem(text = { Text("Video") }, leadingIcon = { Icon(Icons.Rounded.Videocam, contentDescription = null) }, onClick = onVideo)
        DropdownMenuItem(text = { Text("Documento") }, leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null) }, onClick = onDocument)
        DropdownMenuItem(text = { Text("Ubicación aproximada") }, leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null) }, onClick = onLocation)
        DropdownMenuItem(text = { Text("Contacto") }, leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) }, onClick = onContact)
    }
}

@Composable
private fun ChatPreferencesDialog(
    preferences: ChatPreferences,
    onDismiss: () -> Unit,
    onSave: (ChatPreferences) -> Unit
) {
    var draft by remember(preferences) { mutableStateOf(preferences) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Personalizar chat") },
        text = {
            Column {
                Text("Fondo", fontWeight = FontWeight.Bold)
                ChatWallpaper.entries.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = draft.wallpaper == option, onClick = { draft = draft.copy(wallpaper = option) })
                        Text(when (option) {
                            ChatWallpaper.Aurora -> "Aurora neón"
                            ChatWallpaper.Midnight -> "Medianoche"
                            ChatWallpaper.Carbon -> "Carbono"
                        })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Burbujas", fontWeight = FontWeight.Bold)
                ChatBubbleStyle.entries.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = draft.bubbleStyle == option, onClick = { draft = draft.copy(bubbleStyle = option) })
                        Text(when (option) {
                            ChatBubbleStyle.Soft -> "Suaves"
                            ChatBubbleStyle.Compact -> "Compactas"
                            ChatBubbleStyle.Glass -> "Glass"
                        })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Tamaño del texto", fontWeight = FontWeight.Bold)
                Slider(value = draft.textScale, onValueChange = { draft = draft.copy(textScale = it) }, valueRange = 0.9f..1.25f)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Enter para enviar", modifier = Modifier.weight(1f))
                    Switch(checked = draft.enterToSend, onCheckedChange = { draft = draft.copy(enterToSend = it) })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun nexoChatFieldColors(borderless: Boolean = false) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedBorderColor = if (borderless) Color.Transparent else NexoCyan,
    unfocusedBorderColor = if (borderless) Color.Transparent else Color.White.copy(alpha = 0.12f),
    focusedPlaceholderColor = NexoMuted,
    unfocusedPlaceholderColor = NexoMuted,
    cursorColor = NexoCyan
)

private fun chatWallpaper(wallpaper: ChatWallpaper): Brush = when (wallpaper) {
    ChatWallpaper.Aurora -> Brush.verticalGradient(listOf(Color(0xFF07101E), Color(0xFF111338), Color(0xFF180D2A), Color(0xFF080A16)))
    ChatWallpaper.Midnight -> Brush.verticalGradient(listOf(Color(0xFF030712), Color(0xFF08101C), Color(0xFF050712)))
    ChatWallpaper.Carbon -> Brush.verticalGradient(listOf(Color(0xFF121212), Color(0xFF1C1C1E), Color(0xFF101012)))
}

private fun statusGlyph(status: MessageStatus): String = when (status) {
    MessageStatus.Sending -> "◷"
    MessageStatus.Sent -> "✓"
    MessageStatus.Delivered -> "✓✓"
    MessageStatus.Read -> "✓✓"
    MessageStatus.Failed -> "!"
}

private fun displayMessageTime(value: String?): String {
    if (value.isNullOrBlank()) return ""
    if (value.length >= 16 && value.contains("T")) {
        val afterT = value.substringAfter('T')
        if (afterT.length >= 5) return afterT.take(5)
    }
    return value.take(5)
}

private fun formatDuration(durationMs: Long?): String {
    val totalSeconds = ((durationMs ?: 0L) / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
