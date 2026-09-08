package ni.nexo.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ni.nexo.app.data.ChatMessage
import ni.nexo.app.data.MessageStatus
import ni.nexo.app.data.NexoRepository
import ni.nexo.app.data.PersonProfile
import ni.nexo.app.ui.chat.ChatBubbleStyle
import ni.nexo.app.ui.chat.ChatPreferences
import ni.nexo.app.ui.chat.ChatPreferencesStore
import ni.nexo.app.ui.chat.ChatWallpaper
import ni.nexo.app.ui.components.ProfilePhoto
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
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val preferencesStore = remember { ChatPreferencesStore(context) }

    var preferences by remember { mutableStateOf(preferencesStore.load()) }
    var messages by remember(person.id) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var draft by remember(person.id) { mutableStateOf("") }
    var loading by remember(person.id) { mutableStateOf(true) }
    var sending by remember(person.id) { mutableStateOf(false) }
    var errorMessage by remember(person.id) { mutableStateOf<String?>(null) }
    var replyTarget by remember(person.id) { mutableStateOf<ChatMessage?>(null) }
    var showEmojiRow by remember { mutableStateOf(false) }
    var showAttachments by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showPreferences by remember { mutableStateOf(false) }
    var searchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(person.id, repository) {
        loading = true
        errorMessage = null
        try {
            repository.observeMessages(person.id).collectLatest { fresh ->
                messages = fresh
                loading = false
            }
        } catch (error: Exception) {
            loading = false
            errorMessage = error.message ?: "No pudimos cargar la conversación."
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun savePreferences(updated: ChatPreferences) {
        preferences = updated
        preferencesStore.save(updated)
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun sendCurrentDraft() {
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        sending = true
        errorMessage = null
        val quoted = replyTarget?.text
        scope.launch {
            try {
                repository.sendMessage(person.id, text, quoted)
                draft = ""
                replyTarget = null
                showEmojiRow = false
            } catch (error: Exception) {
                errorMessage = error.message ?: "No pudimos enviar el mensaje."
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
            Surface(
                color = NexoNightSoft.copy(alpha = 0.96f),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 7.dp),
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
                            Text(
                                person.name,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (person.verified) {
                                Spacer(Modifier.width(4.dp))
                                Text("✓", color = NexoCyan, fontWeight = FontWeight.Black)
                            }
                        }
                        Text(
                            text = if (repository.configured) "activo en NEXO" else "en línea · modo prueba",
                            color = NexoCyan,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = { toast("Videollamadas P2P: módulo siguiente") }) {
                        Icon(Icons.Rounded.Videocam, contentDescription = "Videollamada", tint = Color.White)
                    }
                    IconButton(onClick = { toast("Llamadas P2P: módulo siguiente") }) {
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
                                onClick = {
                                    showMore = false
                                    searchMode = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Personalizar chat") },
                                leadingIcon = { Icon(Icons.Rounded.Palette, contentDescription = null) },
                                onClick = {
                                    showMore = false
                                    showPreferences = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Mensajes temporales") },
                                onClick = {
                                    showMore = false
                                    toast("Mensajes temporales se activarán en la siguiente fase")
                                }
                            )
                        }
                    }
                }
            }

            if (searchMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NexoNightSoft)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
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
                    IconButton(onClick = {
                        searchMode = false
                        searchQuery = ""
                    }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cerrar búsqueda", tint = Color.White)
                    }
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Surface(color = Color(0xFF5B1D28).copy(alpha = 0.9f)) {
                    Text(
                        text = errorMessage!!,
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(10.dp)
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(
                    if (preferences.bubbleStyle == ChatBubbleStyle.Compact) 4.dp else 7.dp
                )
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            color = NexoNightSoft.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "HOY",
                                color = NexoMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                if (loading) {
                    item {
                        Text("Cargando conversación…", color = NexoMuted, modifier = Modifier.padding(12.dp))
                    }
                } else if (visibleMessages.isEmpty()) {
                    item {
                        Text(
                            if (searchMode) "No encontramos mensajes con ese texto."
                            else "Todavía no hay mensajes. Empezá la conversación cuando quieras.",
                            color = NexoMuted,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(visibleMessages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            preferences = preferences,
                            onReply = { replyTarget = message }
                        )
                    }
                }
            }

            replyTarget?.let { message ->
                Surface(color = NexoNightSoft.copy(alpha = 0.97f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(38.dp)
                                .background(NexoCyan)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (message.fromMe) "Vos" else person.name,
                                color = NexoCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                message.text,
                                color = NexoMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(onClick = { replyTarget = null }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancelar respuesta", tint = NexoMuted)
                        }
                    }
                }
            }

            if (showEmojiRow) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NexoNightSoft)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("😊", "😂", "❤️", "😍", "🔥", "👍", "🥰").forEach { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 24.sp,
                            modifier = Modifier.combinedClickable(
                                onClick = { draft += emoji },
                                onLongClick = { draft += emoji }
                            )
                        )
                    }
                }
            }

            Surface(color = NexoNight.copy(alpha = 0.98f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = NexoNightSoft,
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 2.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { showEmojiRow = !showEmojiRow }) {
                                Icon(Icons.Rounded.EmojiEmotions, contentDescription = "Emoji", tint = NexoMuted)
                            }
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { draft = it.take(4000) },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Mensaje") },
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
                                    onAction = {
                                        showAttachments = false
                                        toast("$it: integración de archivos en preparación")
                                    }
                                )
                            }
                            IconButton(onClick = { toast("Cámara dentro del chat: siguiente módulo") }) {
                                Icon(Icons.Rounded.CameraAlt, contentDescription = "Cámara", tint = NexoMuted)
                            }
                        }
                    }
                    Spacer(Modifier.width(7.dp))
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        color = if (draft.isNotBlank()) NexoCyan else NexoPurple
                    ) {
                        IconButton(
                            onClick = {
                                if (draft.isNotBlank()) sendCurrentDraft()
                                else toast("Notas de voz: siguiente módulo")
                            }
                        ) {
                            Icon(
                                imageVector = if (draft.isNotBlank()) Icons.Rounded.Send else Icons.Rounded.Mic,
                                contentDescription = if (draft.isNotBlank()) "Enviar" else "Nota de voz",
                                tint = if (draft.isNotBlank()) Color(0xFF041119) else Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPreferences) {
        ChatPreferencesDialog(
            preferences = preferences,
            onDismiss = { showPreferences = false },
            onSave = {
                savePreferences(it)
                showPreferences = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    preferences: ChatPreferences,
    onReply: () -> Unit
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (preferences.bubbleStyle == ChatBubbleStyle.Compact) 0.84f else 0.82f)
                .combinedClickable(onClick = { }, onLongClick = onReply),
            color = bubbleColor,
            shape = RoundedCornerShape(radius)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                message.replyToText?.takeIf { it.isNotBlank() }?.let { quoted ->
                    Surface(
                        color = Color.Black.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp)) {
                            Text(
                                "Respuesta",
                                color = NexoCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                quoted,
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    text = message.text,
                    color = Color.White,
                    fontSize = (15f * preferences.textScale).sp,
                    lineHeight = (20f * preferences.textScale).sp
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        displayMessageTime(message.createdAt),
                        color = Color.White.copy(alpha = 0.58f),
                        fontSize = 9.sp
                    )
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
private fun AttachmentMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (String) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Fotos y videos") },
            leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) },
            onClick = { onAction("Fotos y videos") }
        )
        DropdownMenuItem(
            text = { Text("Documento") },
            leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null) },
            onClick = { onAction("Documento") }
        )
        DropdownMenuItem(
            text = { Text("Ubicación") },
            leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null) },
            onClick = { onAction("Ubicación") }
        )
        DropdownMenuItem(
            text = { Text("Contacto") },
            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
            onClick = { onAction("Contacto") }
        )
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
                        RadioButton(
                            selected = draft.wallpaper == option,
                            onClick = { draft = draft.copy(wallpaper = option) }
                        )
                        Text(
                            when (option) {
                                ChatWallpaper.Aurora -> "Aurora neón"
                                ChatWallpaper.Midnight -> "Medianoche"
                                ChatWallpaper.Carbon -> "Carbono"
                            }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Burbujas", fontWeight = FontWeight.Bold)
                ChatBubbleStyle.entries.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = draft.bubbleStyle == option,
                            onClick = { draft = draft.copy(bubbleStyle = option) }
                        )
                        Text(
                            when (option) {
                                ChatBubbleStyle.Soft -> "Suaves"
                                ChatBubbleStyle.Compact -> "Compactas"
                                ChatBubbleStyle.Glass -> "Glass"
                            }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Tamaño del texto", fontWeight = FontWeight.Bold)
                Slider(
                    value = draft.textScale,
                    onValueChange = { draft = draft.copy(textScale = it) },
                    valueRange = 0.9f..1.25f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enter para enviar", modifier = Modifier.weight(1f))
                    Switch(
                        checked = draft.enterToSend,
                        onCheckedChange = { draft = draft.copy(enterToSend = it) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
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
    ChatWallpaper.Aurora -> Brush.verticalGradient(
        listOf(Color(0xFF07101E), Color(0xFF111338), Color(0xFF180D2A), Color(0xFF080A16))
    )
    ChatWallpaper.Midnight -> Brush.verticalGradient(
        listOf(Color(0xFF030712), Color(0xFF08101C), Color(0xFF050712))
    )
    ChatWallpaper.Carbon -> Brush.verticalGradient(
        listOf(Color(0xFF121212), Color(0xFF1C1C1E), Color(0xFF101012))
    )
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
