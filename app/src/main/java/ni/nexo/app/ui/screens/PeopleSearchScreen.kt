package ni.nexo.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ni.nexo.app.data.DiscoveryMessagingService
import ni.nexo.app.data.MessageRequestSummary
import ni.nexo.app.data.PersonProfile
import ni.nexo.app.data.SearchPersonResult
import ni.nexo.app.ui.components.NexoBackdrop
import ni.nexo.app.ui.components.ProfilePhoto
import ni.nexo.app.ui.theme.NexoCyan
import ni.nexo.app.ui.theme.NexoMuted
import ni.nexo.app.ui.theme.NexoNightSoft
import ni.nexo.app.ui.theme.NexoPurple
import ni.nexo.app.ui.userFacingError

@Composable
fun PeopleSearchScreen(
    onBack: () -> Unit,
    onOpenChat: (PersonProfile) -> Unit,
    onConnectionsChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchPersonResult>>(emptyList()) }
    var requests by remember { mutableStateOf<List<MessageRequestSummary>>(emptyList()) }
    var allowUnknown by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var actionUserId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun refreshRequests() {
        requests = runCatching { DiscoveryMessagingService.incomingRequests() }
            .getOrDefault(emptyList())
    }

    suspend fun refreshSearch() {
        if (query.trim().length < 2) {
            results = emptyList()
            return
        }
        loading = true
        runCatching { DiscoveryMessagingService.searchPeople(query) }
            .onSuccess { results = it }
            .onFailure { message = userFacingError(it, "No pudimos buscar personas.") }
        loading = false
    }

    LaunchedEffect(Unit) {
        allowUnknown = runCatching { DiscoveryMessagingService.allowUnknownRequests() }.getOrDefault(true)
        refreshRequests()
    }

    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(280)
        refreshSearch()
    }

    NexoBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text("Buscar personas", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
                    Text("Por nombre o ciudad · con control de privacidad", color = NexoMuted, fontSize = 11.sp)
                }
                if (loading) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = NexoCyan)
            }

            Spacer(Modifier.height(10.dp))
            Surface(
                color = NexoNightSoft.copy(alpha = 0.88f),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Aceptar mensajes de desconocidos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            if (allowUnknown) "Pueden enviarte una solicitud con un único mensaje inicial cifrado."
                            else "Solo matches y contactos verificados pueden iniciar chat.",
                            color = NexoMuted,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                    Switch(
                        checked = allowUnknown,
                        onCheckedChange = { enabled ->
                            allowUnknown = enabled
                            scope.launch {
                                runCatching { DiscoveryMessagingService.setAllowUnknownRequests(enabled) }
                                    .onSuccess { message = "Preferencia guardada." }
                                    .onFailure {
                                        allowUnknown = !enabled
                                        message = userFacingError(it, "No pudimos guardar la preferencia.")
                                    }
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Nombre o ciudad") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp)
            )

            message?.let {
                Spacer(Modifier.height(8.dp))
                Surface(color = NexoPurple.copy(alpha = 0.22f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(it, color = NexoCyan, fontSize = 11.sp, modifier = Modifier.padding(10.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (requests.isNotEmpty()) {
                    item {
                        Text("Solicitudes", color = NexoCyan, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    }
                    items(requests, key = { "request-${it.profile.id}" }) { request ->
                        RequestCard(
                            request = request,
                            busy = actionUserId == request.profile.id,
                            onAccept = {
                                actionUserId = request.profile.id
                                scope.launch {
                                    runCatching { DiscoveryMessagingService.acceptRequest(request.profile.id) }
                                        .onSuccess {
                                            refreshRequests()
                                            onConnectionsChanged()
                                            onOpenChat(request.profile)
                                        }
                                        .onFailure { message = userFacingError(it, "No pudimos aceptar la solicitud.") }
                                    actionUserId = null
                                }
                            },
                            onReject = {
                                actionUserId = request.profile.id
                                scope.launch {
                                    runCatching { DiscoveryMessagingService.rejectRequest(request.profile.id) }
                                        .onSuccess {
                                            refreshRequests()
                                            refreshSearch()
                                            onConnectionsChanged()
                                        }
                                        .onFailure { message = userFacingError(it, "No pudimos rechazar la solicitud.") }
                                    actionUserId = null
                                }
                            },
                            onBlock = {
                                actionUserId = request.profile.id
                                scope.launch {
                                    runCatching { DiscoveryMessagingService.blockRequest(request.profile.id) }
                                        .onSuccess {
                                            refreshRequests()
                                            refreshSearch()
                                            onConnectionsChanged()
                                        }
                                        .onFailure { message = userFacingError(it, "No pudimos bloquear a esta persona.") }
                                    actionUserId = null
                                }
                            }
                        )
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }

                if (query.trim().length < 2) {
                    item {
                        SearchHint(
                            "Buscá a alguien que ya conocés",
                            "Escribí al menos 2 letras del nombre o de la ciudad. Los contactos verificados pueden hablar sin hacer match."
                        )
                    }
                } else if (!loading && results.isEmpty()) {
                    item { SearchHint("Sin resultados", "No encontramos perfiles activos con ese nombre o ciudad.") }
                } else {
                    items(results, key = { "search-${it.profile.id}" }) { result ->
                        PersonSearchCard(
                            result = result,
                            busy = actionUserId == result.profile.id,
                            onMessage = {
                                when {
                                    result.access.accepted -> onOpenChat(result.profile)
                                    result.access.pendingIncoming -> message = "Primero aceptá la solicitud para responder."
                                    result.access.pendingOutgoing -> onOpenChat(result.profile)
                                    result.access.canStartMessage -> {
                                        actionUserId = result.profile.id
                                        scope.launch {
                                            runCatching { DiscoveryMessagingService.startRequest(result.profile.id) }
                                                .onSuccess {
                                                    onConnectionsChanged()
                                                    onOpenChat(result.profile)
                                                }
                                                .onFailure { message = userFacingError(it, "No pudimos iniciar la solicitud.") }
                                            actionUserId = null
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestCard(
    request: MessageRequestSummary,
    busy: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onBlock: () -> Unit
) {
    val person = request.profile
    Surface(color = NexoNightSoft.copy(alpha = 0.88f), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfilePhoto(person.photoUrl, person.name, Modifier.size(48.dp), CircleShape, NexoPurple, Color.White)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(person.name, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("${person.age} · ${person.city}", color = NexoMuted, fontSize = 10.sp)
                    Text("Quiere iniciar una conversación", color = NexoCyan, fontSize = 10.sp)
                }
                if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = NexoCyan)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Button(onClick = onAccept, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Aceptar")
                }
                OutlinedButton(onClick = onReject, enabled = !busy) {
                    Icon(Icons.Rounded.Close, contentDescription = "Rechazar", modifier = Modifier.size(17.dp))
                }
                OutlinedButton(onClick = onBlock, enabled = !busy) {
                    Icon(Icons.Rounded.Block, contentDescription = "Bloquear", modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun PersonSearchCard(
    result: SearchPersonResult,
    busy: Boolean,
    onMessage: () -> Unit
) {
    val person = result.profile
    val access = result.access
    val enabled = !busy && !access.matchOnly && !access.pendingIncoming && access.canStartMessage
    val label = when {
        access.accepted -> "Abrir chat"
        access.pendingOutgoing -> "Abrir solicitud"
        access.pendingIncoming -> "Solicitud recibida"
        access.matchOnly -> "Solo con match"
        else -> access.actionLabel
    }

    Surface(color = NexoNightSoft.copy(alpha = 0.84f), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfilePhoto(person.photoUrl, person.name, Modifier.size(52.dp), CircleShape, NexoPurple, Color.White)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(person.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text("${person.age} · ${person.city}", color = NexoMuted, fontSize = 10.sp)
                    Text(
                        person.bio.ifBlank { person.intention },
                        color = NexoMuted,
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onMessage,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Chat, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.size(5.dp))
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun SearchHint(title: String, body: String) {
    Surface(color = NexoNightSoft.copy(alpha = 0.72f), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(body, color = NexoMuted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
