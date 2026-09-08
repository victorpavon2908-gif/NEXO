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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ni.nexo.app.data.CallRecord
import ni.nexo.app.data.CallState
import ni.nexo.app.data.CallType
import ni.nexo.app.data.ChatMessage
import ni.nexo.app.data.MessageKind
import ni.nexo.app.data.NexoRepository
import ni.nexo.app.data.PersonProfile
import ni.nexo.app.data.StatusUpdate
import ni.nexo.app.ui.components.NexoBackdrop
import ni.nexo.app.ui.components.NexoWordmark
import ni.nexo.app.ui.components.ProfilePhoto
import ni.nexo.app.ui.theme.NexoCyan
import ni.nexo.app.ui.theme.NexoMuted
import ni.nexo.app.ui.theme.NexoNightSoft
import ni.nexo.app.ui.theme.NexoPurple

enum class CommunicationTab { Chats, Updates, Calls }

@Composable
fun CommunicationHubScreen(
    matches: List<PersonProfile>,
    repository: NexoRepository,
    demoMode: Boolean,
    onOpenChat: (PersonProfile) -> Unit,
    onStartCall: (PersonProfile, CallType) -> Unit
) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(CommunicationTab.Chats) }
    var query by remember { mutableStateOf("") }
    var statuses by remember { mutableStateOf<List<StatusUpdate>>(emptyList()) }
    var calls by remember { mutableStateOf<List<CallRecord>>(emptyList()) }
    var previews by remember { mutableStateOf<Map<String, ChatMessage>>(emptyMap()) }
    var statusDraft by remember { mutableStateOf("") }
    var statusBusy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    suspend fun refreshCommunicationData() {
        statuses = runCatching { repository.loadStatusUpdates() }.getOrDefault(emptyList())
        calls = runCatching { repository.loadCalls() }.getOrDefault(emptyList())
    }

    LaunchedEffect(repository, tab) {
        refreshCommunicationData()
    }

    LaunchedEffect(repository, matches.map { it.id }) {
        val latest = linkedMapOf<String, ChatMessage>()
        matches.forEach { person ->
            runCatching { repository.observeMessages(person.id).first().lastOrNull() }
                .getOrNull()
                ?.let { latest[person.id] = it }
        }
        previews = latest
    }

    NexoBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NexoWordmark(compact = true)
                Spacer(Modifier.weight(1f))
                if (demoMode) {
                    Surface(color = NexoPurple.copy(alpha = 0.30f), shape = RoundedCornerShape(50)) {
                        Text(
                            "MODO PRUEBA",
                            color = NexoCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("Conexiones", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text(
                "Tus conversaciones, novedades y llamadas.",
                color = NexoMuted,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CommunicationTab.entries.forEach { item ->
                    val selected = tab == item
                    val label = when (item) {
                        CommunicationTab.Chats -> "Chats"
                        CommunicationTab.Updates -> "Novedades"
                        CommunicationTab.Calls -> "Llamadas"
                    }
                    val icon = when (item) {
                        CommunicationTab.Chats -> Icons.Rounded.Chat
                        CommunicationTab.Updates -> Icons.Rounded.History
                        CommunicationTab.Calls -> Icons.Rounded.Call
                    }
                    Surface(
                        color = if (selected) NexoPurple.copy(alpha = 0.38f) else NexoNightSoft.copy(alpha = 0.80f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f).clickable { tab = item }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, contentDescription = null, tint = if (selected) NexoCyan else NexoMuted, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(label, color = if (selected) Color.White else NexoMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            notice?.let {
                Surface(
                    color = NexoPurple.copy(alpha = 0.20f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(it, color = NexoCyan, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
                }
            }

            when (tab) {
                CommunicationTab.Chats -> ChatsPane(
                    matches = matches,
                    previews = previews,
                    query = query,
                    onQuery = { query = it },
                    onOpenChat = onOpenChat
                )
                CommunicationTab.Updates -> UpdatesPane(
                    statuses = statuses,
                    draft = statusDraft,
                    busy = statusBusy,
                    onDraft = { statusDraft = it.take(1500) },
                    onPublish = {
                        if (statusDraft.isNotBlank() && !statusBusy) {
                            statusBusy = true
                            scope.launch {
                                runCatching { repository.publishStatus(statusDraft) }
                                    .onSuccess {
                                        statusDraft = ""
                                        notice = "Tu novedad estará disponible durante 24 horas."
                                        refreshCommunicationData()
                                    }
                                    .onFailure { notice = it.message ?: "No pudimos publicar la novedad." }
                                statusBusy = false
                            }
                        }
                    }
                )
                CommunicationTab.Calls -> CallsPane(calls = calls, matches = matches, onStartCall = onStartCall)
            }
        }
    }
}

@Composable
private fun ChatsPane(
    matches: List<PersonProfile>,
    previews: Map<String, ChatMessage>,
    query: String,
    onQuery: (String) -> Unit,
    onOpenChat: (PersonProfile) -> Unit
) {
    val visible = remember(matches, query, previews) {
        matches
            .filter { query.isBlank() || it.name.contains(query, true) || it.city.contains(query, true) }
            .sortedByDescending { previews[it.id]?.createdAt.orEmpty() }
    }

    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Buscar conversación") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        colors = hubFieldColors()
    )
    Spacer(Modifier.height(10.dp))

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (visible.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (matches.isEmpty()) "Todavía no hay conversaciones" else "No encontramos ese chat",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (matches.isEmpty()) "Cuando hagas match, la conversación aparecerá aquí." else "Probá con otro nombre o ciudad.",
                        color = NexoMuted,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            items(visible, key = { it.id }) { person ->
                val preview = previews[person.id]
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenChat(person) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfilePhoto(
                        photoUrl = person.photoUrl,
                        name = person.name,
                        modifier = Modifier.size(58.dp),
                        shape = CircleShape,
                        backgroundColor = NexoPurple,
                        textColor = Color.White
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(person.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (person.verified) {
                                Spacer(Modifier.width(5.dp))
                                Text("✓", color = NexoCyan, fontWeight = FontWeight.Black)
                            }
                        }
                        Text(
                            text = preview?.let(::conversationPreviewText) ?: "Nuevo match · Decile hola 👋",
                            color = if (preview == null) NexoCyan.copy(alpha = 0.85f) else NexoMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatHubTime(preview?.createdAt),
                        color = NexoMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdatesPane(
    statuses: List<StatusUpdate>,
    draft: String,
    busy: Boolean,
    onDraft: (String) -> Unit,
    onPublish: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraft,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Compartí una novedad…") },
            maxLines = 3,
            shape = RoundedCornerShape(18.dp),
            colors = hubFieldColors()
        )
        Spacer(Modifier.width(6.dp))
        Surface(color = NexoCyan, shape = CircleShape) {
            IconButton(onClick = onPublish, enabled = draft.isNotBlank() && !busy) {
                Icon(Icons.Rounded.Send, contentDescription = "Publicar", tint = Color(0xFF051117))
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Text("Las novedades desaparecen después de 24 horas", color = NexoMuted, fontSize = 11.sp)
    Spacer(Modifier.height(8.dp))

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (statuses.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No hay novedades todavía", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Publicá algo breve para iniciar.", color = NexoMuted, fontSize = 12.sp)
                }
            }
        } else {
            items(statuses, key = { it.id }) { status ->
                Surface(color = NexoNightSoft.copy(alpha = 0.84f), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (status.mine) "Tu novedad" else status.ownerName,
                                color = if (status.mine) NexoCyan else Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(formatHubTime(status.createdAt), color = NexoMuted, fontSize = 10.sp)
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(status.text, color = Color.White.copy(alpha = 0.90f), fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CallsPane(
    calls: List<CallRecord>,
    matches: List<PersonProfile>,
    onStartCall: (PersonProfile, CallType) -> Unit
) {
    if (matches.isNotEmpty()) {
        Text("Llamar a un match", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(7.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth().height(132.dp)) {
            items(matches.take(3), key = { "quick-${it.id}" }) { person ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfilePhoto(person.photoUrl, person.name, Modifier.size(42.dp), CircleShape, NexoPurple, Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text(person.name, color = Color.White, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { onStartCall(person, CallType.Audio) }) {
                        Icon(Icons.Rounded.Call, contentDescription = "Llamar a ${person.name}", tint = NexoCyan)
                    }
                    IconButton(onClick = { onStartCall(person, CallType.Video) }) {
                        Icon(Icons.Rounded.Videocam, contentDescription = "Videollamar a ${person.name}", tint = NexoCyan)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    Text("Recientes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    Spacer(Modifier.height(6.dp))
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (calls.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No hay llamadas todavía", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Las llamadas recientes aparecerán aquí.", color = NexoMuted, fontSize = 12.sp)
                }
            }
        } else {
            items(calls, key = { it.id }) { call ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(color = NexoPurple.copy(alpha = 0.30f), shape = CircleShape) {
                        Icon(
                            if (call.type == CallType.Video) Icons.Rounded.Videocam else Icons.Rounded.Call,
                            contentDescription = null,
                            tint = NexoCyan,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(call.peerName, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "${if (call.outgoing) "Saliente" else "Entrante"} · ${callStateLabel(call.state)}",
                            color = NexoMuted,
                            fontSize = 11.sp
                        )
                    }
                    Text(formatHubTime(call.startedAt), color = NexoMuted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun hubFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = NexoCyan,
    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
    focusedContainerColor = NexoNightSoft.copy(alpha = 0.82f),
    unfocusedContainerColor = NexoNightSoft.copy(alpha = 0.72f),
    focusedPlaceholderColor = NexoMuted,
    unfocusedPlaceholderColor = NexoMuted,
    focusedLeadingIconColor = NexoCyan,
    unfocusedLeadingIconColor = NexoMuted,
    cursorColor = NexoCyan
)

private fun conversationPreviewText(message: ChatMessage): String {
    val prefix = if (message.fromMe) "Vos: " else ""
    if (message.deleted) return "${prefix}Mensaje eliminado"
    val body = when (message.kind) {
        MessageKind.Image -> "📷 ${message.text.ifBlank { "Foto" }}"
        MessageKind.Video -> "🎥 ${message.text.ifBlank { "Video" }}"
        MessageKind.Audio -> "🎙️ Nota de voz"
        MessageKind.Document -> "📎 ${message.text.ifBlank { "Documento" }}"
        MessageKind.Location -> "📍 Ubicación"
        MessageKind.Contact -> "👤 Contacto"
        MessageKind.System -> message.text
        MessageKind.Text -> message.text
    }
    return (prefix + body).take(90)
}

private fun callStateLabel(state: CallState): String = when (state) {
    CallState.Ringing -> "Llamando"
    CallState.Connecting -> "Conectando"
    CallState.Connected -> "Conectada"
    CallState.Declined -> "Rechazada"
    CallState.Missed -> "Perdida"
    CallState.Ended -> "Finalizada"
    CallState.Failed -> "Fallida"
}

private fun formatHubTime(value: String?): String {
    if (value.isNullOrBlank()) return ""
    if (Regex("^\\d{2}:\\d{2}$").matches(value)) return value
    return runCatching {
        val zone = ZoneId.systemDefault()
        val dateTime = Instant.parse(value).atZone(zone)
        val today = LocalDate.now(zone)
        when (dateTime.toLocalDate()) {
            today -> dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
            today.minusDays(1) -> "ayer"
            else -> dateTime.format(DateTimeFormatter.ofPattern("dd/MM"))
        }
    }.getOrElse {
        value.substringAfter('T', value).take(5)
    }
}
