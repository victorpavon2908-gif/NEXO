package ni.nexo.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import ni.nexo.app.data.CallRecord
import ni.nexo.app.data.CallState
import ni.nexo.app.data.CallType
import ni.nexo.app.data.ChatMessage
import ni.nexo.app.data.ContactMatch
import ni.nexo.app.data.ContactSettings
import ni.nexo.app.data.DeviceContact
import ni.nexo.app.data.DeviceContacts
import ni.nexo.app.data.GroupSummary
import ni.nexo.app.data.MessageKind
import ni.nexo.app.data.NexoRepository
import ni.nexo.app.data.PersonProfile
import ni.nexo.app.data.StatusUpdate
import ni.nexo.app.ui.components.NexoBackdrop
import ni.nexo.app.ui.components.NexoWordmark
import ni.nexo.app.ui.components.ProfilePhoto
import ni.nexo.app.ui.userFacingError
import ni.nexo.app.ui.theme.NexoCyan
import ni.nexo.app.ui.theme.NexoMuted
import ni.nexo.app.ui.theme.NexoNightSoft
import ni.nexo.app.ui.theme.NexoPink
import ni.nexo.app.ui.theme.NexoPurple

enum class CommunicationTab { Chats, Contacts, Groups, Updates, Calls }

@Composable
fun CommunicationHubScreen(
    matches: List<PersonProfile>,
    repository: NexoRepository,
    demoMode: Boolean,
    onOpenChat: (PersonProfile) -> Unit,
    onStartCall: (PersonProfile, CallType) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(CommunicationTab.Chats) }
    var query by remember { mutableStateOf("") }
    var contactQuery by remember { mutableStateOf("") }
    var statuses by remember { mutableStateOf<List<StatusUpdate>>(emptyList()) }
    var calls by remember { mutableStateOf<List<CallRecord>>(emptyList()) }
    var previews by remember { mutableStateOf<Map<String, ChatMessage>>(emptyMap()) }
    var statusDraft by remember { mutableStateOf("") }
    var statusBusy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    var contactSettings by remember { mutableStateOf(ContactSettings()) }
    var deviceContacts by remember { mutableStateOf<List<DeviceContact>>(emptyList()) }
    var contactMatches by remember { mutableStateOf<List<ContactMatch>>(emptyList()) }
    var contactBusy by remember { mutableStateOf(false) }
    var manualPhone by remember { mutableStateOf("") }
    var verificationPhone by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var waitingForCode by remember { mutableStateOf(false) }

    var groups by remember { mutableStateOf<List<GroupSummary>>(emptyList()) }
    var selectedGroup by remember { mutableStateOf<GroupSummary?>(null) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    val selectedGroupMembers = remember { mutableStateListOf<String>() }

    suspend fun refreshCommunicationData() {
        statuses = runCatching { repository.loadStatusUpdates() }.getOrDefault(emptyList())
        calls = runCatching { repository.loadCalls() }.getOrDefault(emptyList())
        groups = runCatching { repository.loadGroups() }.getOrDefault(emptyList())
        if (!demoMode) {
            contactSettings = runCatching { repository.loadContactSettings() }.getOrDefault(ContactSettings())
        }
    }

    suspend fun syncContacts() {
        contactBusy = true
        try {
            val local = DeviceContacts.read(context)
            deviceContacts = local
            contactMatches = local
                .map { it.phoneHash }
                .chunked(500)
                .flatMap { hashes -> repository.findContactsByHashes(hashes) }
                .distinctBy { it.profile.id }
            notice = if (contactMatches.isEmpty()) {
                "No encontramos todavía contactos de tu agenda que estén visibles en NEXO."
            } else {
                "Encontramos ${contactMatches.size} contacto(s) en NEXO."
            }
        } catch (t: Throwable) {
            notice = userFacingError(t, "No pudimos sincronizar tus contactos.")
        } finally {
            contactBusy = false
        }
    }

    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch { syncContacts() }
        else notice = "El permiso de contactos es opcional. También podés buscar un número manualmente."
    }

    val statusImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        statusBusy = true
        scope.launch {
            runCatching {
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("No pudimos leer la imagen.")
                val path = repository.uploadStatusMedia(bytes, mime, uri.lastPathSegment ?: "estado.jpg")
                repository.publishStatus(statusDraft, MessageKind.Image, path, mime)
            }.onSuccess {
                statusDraft = ""
                notice = "Estado publicado por 24 horas."
                refreshCommunicationData()
            }.onFailure { notice = userFacingError(it, "No pudimos publicar la foto.") }
            statusBusy = false
        }
    }

    val statusVideoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        statusBusy = true
        scope.launch {
            runCatching {
                val mime = context.contentResolver.getType(uri) ?: "video/mp4"
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("No pudimos leer el video.")
                val path = repository.uploadStatusMedia(bytes, mime, uri.lastPathSegment ?: "estado.mp4")
                repository.publishStatus(statusDraft, MessageKind.Video, path, mime)
            }.onSuccess {
                statusDraft = ""
                notice = "Estado publicado por 24 horas."
                refreshCommunicationData()
            }.onFailure { notice = userFacingError(it, "No pudimos publicar el video.") }
            statusBusy = false
        }
    }

    LaunchedEffect(repository, tab) { refreshCommunicationData() }

    LaunchedEffect(tab, contactSettings.phoneVerified) {
        if (
            tab == CommunicationTab.Contacts &&
            contactSettings.phoneVerified &&
            deviceContacts.isEmpty() &&
            context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        ) {
            syncContacts()
        }
    }

    LaunchedEffect(repository, matches.map { it.id }) {
        previews = supervisorScope {
            matches.map { person ->
                async {
                    runCatching { repository.observeMessages(person.id).first().lastOrNull() }
                        .getOrNull()
                        ?.let { person.id to it }
                }
            }.awaitAll().filterNotNull().toMap()
        }
    }

    selectedGroup?.let { group ->
        GroupChatPane(
            group = group,
            repository = repository,
            onBack = {
                selectedGroup = null
                scope.launch { groups = runCatching { repository.loadGroups() }.getOrDefault(groups) }
            }
        )
        return
    }

    val knownPeople = remember(matches, contactMatches) {
        (matches + contactMatches.map { it.profile }).distinctBy { it.id }
    }

    NexoBackdrop {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NexoWordmark(compact = true)
                Spacer(Modifier.weight(1f))
                if (demoMode) {
                    Surface(color = NexoPurple.copy(alpha = 0.30f), shape = RoundedCornerShape(50)) {
                        Text("MODO PRUEBA", color = NexoCyan, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Conexiones", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("Chats, contactos, grupos, estados y llamadas.", color = NexoMuted, fontSize = 13.sp)

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CommunicationTab.entries.forEach { item ->
                    val selected = tab == item
                    val label = when (item) {
                        CommunicationTab.Chats -> "Chats"
                        CommunicationTab.Contacts -> "Contactos"
                        CommunicationTab.Groups -> "Grupos"
                        CommunicationTab.Updates -> "Estados"
                        CommunicationTab.Calls -> "Llamadas"
                    }
                    val icon = when (item) {
                        CommunicationTab.Chats -> Icons.Rounded.Chat
                        CommunicationTab.Contacts -> Icons.Rounded.Contacts
                        CommunicationTab.Groups -> Icons.Rounded.Group
                        CommunicationTab.Updates -> Icons.Rounded.History
                        CommunicationTab.Calls -> Icons.Rounded.Call
                    }
                    Surface(
                        color = if (selected) NexoPurple.copy(alpha = 0.38f) else NexoNightSoft.copy(alpha = 0.80f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.clickable { tab = item }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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

            Spacer(Modifier.height(10.dp))
            notice?.let {
                Surface(color = NexoPurple.copy(alpha = 0.20f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text(it, color = NexoCyan, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
                }
            }

            when (tab) {
                CommunicationTab.Chats -> ChatsPane(matches, previews, query, { query = it }, onOpenChat)
                CommunicationTab.Contacts -> ContactsPane(
                    settings = contactSettings,
                    localContacts = deviceContacts,
                    found = contactMatches,
                    busy = contactBusy,
                    query = contactQuery,
                    onQuery = { contactQuery = it },
                    manualPhone = manualPhone,
                    onManualPhone = { manualPhone = it },
                    onRequestSync = {
                        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                            scope.launch { syncContacts() }
                        } else contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                    },
                    onStartPhoneVerification = { phone ->
                        verificationPhone = DeviceContacts.normalizeToE164(context, phone) ?: phone.trim()
                        contactBusy = true
                        scope.launch {
                            runCatching { repository.requestPhoneVerification(verificationPhone) }
                                .onSuccess {
                                    waitingForCode = true
                                    notice = "Te enviamos un código SMS de 6 dígitos."
                                }
                                .onFailure {
                                    notice = userFacingError(it, "No pudimos enviar el código SMS. Intentá nuevamente.")
                                }
                            contactBusy = false
                        }
                    },
                    verificationPhone = verificationPhone,
                    verificationCode = verificationCode,
                    waitingForCode = waitingForCode,
                    onVerificationCode = { verificationCode = it.filter(Char::isDigit).take(6) },
                    onVerifyCode = {
                        contactBusy = true
                        scope.launch {
                            runCatching { repository.verifyPhoneCode(verificationPhone, verificationCode) }
                                .onSuccess {
                                    waitingForCode = false
                                    verificationCode = ""
                                    contactSettings = repository.loadContactSettings()
                                    notice = "Número verificado. Tus contactos ya pueden encontrarte si tienen tu número."
                                }
                                .onFailure {
                                    notice = userFacingError(it, "No pudimos verificar el código. Revisalo e intentá nuevamente.")
                                }
                            contactBusy = false
                        }
                    },
                    onDiscoveryChanged = { enabled ->
                        scope.launch {
                            runCatching { repository.setContactDiscoveryEnabled(enabled) }
                                .onSuccess { contactSettings = contactSettings.copy(discoverable = enabled) }
                                .onFailure { notice = userFacingError(it, "No pudimos guardar esta preferencia.") }
                        }
                    },
                    onOpenContact = { contact ->
                        contactBusy = true
                        scope.launch {
                            runCatching { repository.startContactConversation(contact.phoneHash) }
                                .onSuccess { onOpenChat(contact.profile) }
                                .onFailure {
                                    notice = userFacingError(it, "No pudimos abrir la conversación.")
                                }
                            contactBusy = false
                        }
                    },
                    onNewGroup = { showCreateGroup = true },
                    onNewContact = {
                        runCatching {
                            context.startActivity(
                                Intent(ContactsContract.Intents.Insert.ACTION).apply {
                                    type = ContactsContract.RawContacts.CONTENT_TYPE
                                }
                            )
                        }.onFailure { notice = "No encontramos una aplicación para guardar el contacto." }
                    },
                    onInvite = { contact ->
                        runCatching {
                            val sms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(contact.phoneE164)}"))
                            sms.putExtra("sms_body", "Hola ${contact.localName}, hablemos por NEXO. Descargá la app para conectar conmigo.")
                            context.startActivity(sms)
                        }.onFailure { notice = "No encontramos una aplicación para enviar la invitación." }
                    }
                )
                CommunicationTab.Groups -> GroupsPane(
                    groups = groups,
                    people = knownPeople,
                    onOpen = { selectedGroup = it },
                    onCreate = { showCreateGroup = true }
                )
                CommunicationTab.Updates -> UpdatesPane(
                    statuses = statuses,
                    draft = statusDraft,
                    busy = statusBusy,
                    onDraft = { statusDraft = it.take(1500) },
                    onPublishText = {
                        if (statusDraft.isNotBlank() && !statusBusy) {
                            statusBusy = true
                            scope.launch {
                                runCatching { repository.publishStatus(statusDraft) }
                                    .onSuccess {
                                        statusDraft = ""
                                        notice = "Tu estado estará disponible durante 24 horas."
                                        refreshCommunicationData()
                                    }
                                    .onFailure { notice = userFacingError(it, "No pudimos publicar el estado.") }
                                statusBusy = false
                            }
                        }
                    },
                    onPhoto = { statusImagePicker.launch("image/*") },
                    onVideo = { statusVideoPicker.launch("video/*") }
                )
                CommunicationTab.Calls -> CallsPane(calls, matches, onStartCall)
            }
        }
    }

    if (showCreateGroup) {
        AlertDialog(
            onDismissRequest = { showCreateGroup = false },
            title = { Text("Nuevo grupo") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it.take(80) },
                        label = { Text("Nombre del grupo") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Agregar personas", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    LazyColumn(modifier = Modifier.height(260.dp)) {
                        items(knownPeople, key = { it.id }) { person ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    if (person.id in selectedGroupMembers) selectedGroupMembers.remove(person.id)
                                    else selectedGroupMembers.add(person.id)
                                }.padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = person.id in selectedGroupMembers,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedGroupMembers.add(person.id) else selectedGroupMembers.remove(person.id)
                                    }
                                )
                                ProfilePhoto(person.photoUrl, person.name, Modifier.size(36.dp), CircleShape, NexoPurple, Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text(person.name, modifier = Modifier.weight(1f))
                                if (person.phoneVerified) Text("✓ tel", color = NexoCyan, fontSize = 10.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newGroupName.isNotBlank(),
                    onClick = {
                        val name = newGroupName.trim()
                        val memberIds = selectedGroupMembers.toList()
                        showCreateGroup = false
                        scope.launch {
                            runCatching { repository.createGroup(name, memberIds) }
                                .onSuccess {
                                    groups = (listOf(it) + groups).distinctBy(GroupSummary::id)
                                    selectedGroup = it
                                    newGroupName = ""
                                    selectedGroupMembers.clear()
                                }
                                .onFailure { notice = userFacingError(it, "No pudimos crear el grupo.") }
                        }
                    }
                ) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { showCreateGroup = false }) { Text("Cancelar") } }
        )
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
        matches.filter { query.isBlank() || it.name.contains(query, true) || it.city.contains(query, true) }
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
    Spacer(Modifier.height(8.dp))

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (visible.isEmpty()) {
            item { HubEmpty("Todavía no hay conversaciones", "Cuando hagas match, la conversación aparecerá aquí.") }
        } else {
            items(visible, key = { it.id }) { person ->
                val preview = previews[person.id]
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenChat(person) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfilePhoto(person.photoUrl, person.name, Modifier.size(58.dp), CircleShape, NexoPurple, Color.White)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(person.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (person.verified || person.phoneVerified) {
                                Spacer(Modifier.width(5.dp))
                                Text("✓", color = NexoCyan, fontWeight = FontWeight.Black)
                            }
                        }
                        Text(
                            preview?.let(::conversationPreviewText) ?: "Nuevo match · Decile hola 👋",
                            color = if (preview == null) NexoCyan.copy(alpha = 0.85f) else NexoMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(formatHubTime(preview?.createdAt), color = NexoMuted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun ContactsPane(
    settings: ContactSettings,
    localContacts: List<DeviceContact>,
    found: List<ContactMatch>,
    busy: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    manualPhone: String,
    onManualPhone: (String) -> Unit,
    onRequestSync: () -> Unit,
    onStartPhoneVerification: (String) -> Unit,
    verificationPhone: String,
    verificationCode: String,
    waitingForCode: Boolean,
    onVerificationCode: (String) -> Unit,
    onVerifyCode: () -> Unit,
    onDiscoveryChanged: (Boolean) -> Unit,
    onOpenContact: (ContactMatch) -> Unit,
    onNewGroup: () -> Unit,
    onNewContact: () -> Unit,
    onInvite: (DeviceContact) -> Unit
) {
    val normalizedQuery = query.trim()
    val visibleFound = remember(found, localContacts, normalizedQuery) {
        found.filter { contact ->
            val localName = localContacts.firstOrNull { it.phoneHash == contact.phoneHash }?.localName.orEmpty()
            normalizedQuery.isBlank() ||
                localName.contains(normalizedQuery, ignoreCase = true) ||
                contact.profile.name.contains(normalizedQuery, ignoreCase = true) ||
                contact.profile.city.contains(normalizedQuery, ignoreCase = true)
        }.sortedBy { contact ->
            localContacts.firstOrNull { it.phoneHash == contact.phoneHash }?.localName
                ?: contact.profile.name
        }
    }
    val foundHashes = remember(found) { found.mapTo(hashSetOf()) { it.phoneHash } }
    val invitations = remember(localContacts, foundHashes, normalizedQuery) {
        localContacts.filter { contact ->
            contact.phoneHash !in foundHashes &&
                (normalizedQuery.isBlank() ||
                    contact.localName.contains(normalizedQuery, ignoreCase = true) ||
                    contact.phoneE164.contains(normalizedQuery))
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Contactos", color = Color.White, fontWeight = FontWeight.Black, fontSize = 21.sp)
                    Text(
                        if (settings.phoneVerified) "${found.size} contactos en NEXO" else "Encontrá a las personas de tu agenda",
                        color = NexoMuted,
                        fontSize = 11.sp
                    )
                }
                if (busy) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = NexoCyan, strokeWidth = 2.dp)
                else IconButton(onClick = onRequestSync, enabled = settings.phoneVerified) {
                    Icon(Icons.Rounded.Contacts, contentDescription = "Actualizar contactos", tint = NexoCyan)
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar nombre o número") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = hubFieldColors()
            )
        }

        item { ContactActionRow(Icons.Rounded.Group, "Nuevo grupo", "Creá un grupo con tus contactos", onNewGroup) }
        item { ContactActionRow(Icons.Rounded.PersonAdd, "Nuevo contacto", "Guardalo en la agenda del teléfono", onNewContact) }

        if (!settings.phoneVerified) {
            item {
                Surface(color = NexoNightSoft.copy(alpha = 0.86f), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("Verificá tu número", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        Text(
                            "Así NEXO puede reconocer de forma privada quién de tu agenda ya usa la app.",
                            color = NexoMuted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        if (!waitingForCode) {
                            OutlinedTextField(
                                value = manualPhone,
                                onValueChange = onManualPhone,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Tu número con código de país") },
                                placeholder = { Text("+50588888888") },
                                singleLine = true,
                                colors = hubFieldColors()
                            )
                            Spacer(Modifier.height(7.dp))
                            Button(
                                onClick = { onStartPhoneVerification(manualPhone) },
                                enabled = manualPhone.isNotBlank() && !busy
                            ) { Text("Enviar código SMS") }
                        } else {
                            Text("Código enviado a $verificationPhone", color = NexoCyan, fontSize = 12.sp)
                            Spacer(Modifier.height(5.dp))
                            OutlinedTextField(
                                value = verificationCode,
                                onValueChange = onVerificationCode,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Código de 6 dígitos") },
                                singleLine = true,
                                colors = hubFieldColors()
                            )
                            Spacer(Modifier.height(7.dp))
                            Button(onClick = onVerifyCode, enabled = verificationCode.length == 6 && !busy) {
                                Text("Verificar")
                            }
                        }
                    }
                }
            }
        }

        if (settings.phoneVerified && localContacts.isEmpty()) {
            item {
                OutlinedButton(onClick = onRequestSync, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Contacts, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Mostrar contactos del teléfono")
                }
            }
        }

        if (visibleFound.isNotEmpty()) {
            item { ContactSectionLabel("Contactos en NEXO") }
            items(visibleFound, key = { "nexo-${it.profile.id}" }) { match ->
                val person = match.profile
                val localName = localContacts.firstOrNull { it.phoneHash == match.phoneHash }?.localName
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { onOpenContact(match) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfilePhoto(person.photoUrl, person.name, Modifier.size(52.dp), CircleShape, NexoPurple, Color.White)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(localName ?: person.name, color = Color.White, fontWeight = FontWeight.Bold)
                        if (!localName.isNullOrBlank() && localName != person.name) Text("En NEXO: ${person.name}", color = NexoMuted, fontSize = 11.sp)
                        Text(
                            if (person.isOnline) "en línea" else person.lastSeen?.let { "últ. vez ${formatHubTime(it)}" } ?: "Disponible en NEXO",
                            color = if (person.isOnline) NexoCyan else NexoMuted,
                            fontSize = 10.sp
                        )
                    }
                    IconButton(onClick = { onOpenContact(match) }, enabled = !busy) {
                        Icon(Icons.Rounded.Chat, contentDescription = "Iniciar chat", tint = NexoCyan)
                    }
                }
            }
        } else if (settings.phoneVerified && localContacts.isNotEmpty()) {
            item { HubEmpty("Nadie de tu agenda aparece todavía", "Podés invitar a tus contactos a descargar NEXO.") }
        }

        if (invitations.isNotEmpty()) {
            item { ContactSectionLabel("Invitar a NEXO") }
            items(invitations, key = { "invite-${it.phoneHash}" }) { contact ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onInvite(contact) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfilePhoto(null, contact.localName, Modifier.size(48.dp), CircleShape, NexoPurple, Color.White)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(contact.localName, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(maskPhone(contact.phoneE164), color = NexoMuted, fontSize = 10.sp)
                    }
                    Text("Invitar", color = NexoCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (settings.phoneVerified) {
            item {
                HorizontalDivider(color = NexoMuted.copy(alpha = 0.18f))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Permitir que me encuentren", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Solo personas que ya tienen tu número", color = NexoMuted, fontSize = 10.sp)
                    }
                    Switch(checked = settings.discoverable, onCheckedChange = onDiscoveryChanged)
                }
            }
        }
    }
}

@Composable
private fun ContactActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(color = NexoPurple.copy(alpha = 0.36f), shape = CircleShape) {
            Icon(icon, contentDescription = null, tint = NexoCyan, modifier = Modifier.padding(12.dp).size(22.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(subtitle, color = NexoMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ContactSectionLabel(label: String) {
    Text(label, color = NexoCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
}

@Composable
private fun GroupsPane(
    groups: List<GroupSummary>,
    people: List<PersonProfile>,
    onOpen: (GroupSummary) -> Unit,
    onCreate: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Grupos", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text("Creá grupos con tus conexiones y contactos verificados.", color = NexoMuted, fontSize = 11.sp)
        }
        IconButton(onClick = onCreate, enabled = people.isNotEmpty()) {
            Icon(Icons.Rounded.PersonAdd, contentDescription = "Nuevo grupo", tint = NexoCyan)
        }
    }
    Spacer(Modifier.height(8.dp))
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (groups.isEmpty()) {
            item { HubEmpty("Todavía no tenés grupos", if (people.isEmpty()) "Primero conectá con alguien o encontralo en Contactos." else "Tocá + para crear tu primer grupo.") }
        } else {
            items(groups, key = { it.id }) { group ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(group) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = CircleShape, color = NexoPurple, modifier = Modifier.size(54.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(group.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        }
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(group.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(group.lastMessage ?: "${group.memberCount} miembros", color = NexoMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(formatHubTime(group.updatedAt), color = NexoMuted, fontSize = 9.sp)
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
    onPublishText: () -> Unit,
    onPhoto: () -> Unit,
    onVideo: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraft,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Publicá un estado…") },
            maxLines = 3,
            shape = RoundedCornerShape(18.dp),
            colors = hubFieldColors()
        )
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onPhoto, enabled = !busy) { Icon(Icons.Rounded.Image, "Foto", tint = NexoCyan) }
        IconButton(onClick = onVideo, enabled = !busy) { Icon(Icons.Rounded.Videocam, "Video", tint = NexoCyan) }
        Surface(color = NexoCyan, shape = CircleShape) {
            IconButton(onClick = onPublishText, enabled = draft.isNotBlank() && !busy) {
                Icon(Icons.Rounded.Send, contentDescription = "Publicar", tint = Color(0xFF051117))
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text("Fotos, videos y texto desaparecen después de 24 horas", color = NexoMuted, fontSize = 10.sp)
    Spacer(Modifier.height(7.dp))

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (statuses.isEmpty()) {
            item { HubEmpty("No hay estados todavía", "Publicá texto, una foto o un video para iniciar.") }
        } else {
            items(statuses, key = { it.id }) { status ->
                Surface(color = NexoNightSoft.copy(alpha = 0.84f), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProfilePhoto(status.ownerPhotoUrl, status.ownerName, Modifier.size(38.dp), CircleShape, NexoPurple, Color.White)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (status.mine) "Tu estado" else status.ownerName, color = if (status.mine) NexoCyan else Color.White, fontWeight = FontWeight.Bold)
                                Text(formatHubTime(status.createdAt), color = NexoMuted, fontSize = 9.sp)
                            }
                        }
                        if (status.kind == MessageKind.Image && !status.mediaUrl.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            AsyncImage(model = status.mediaUrl, contentDescription = "Estado", modifier = Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Crop)
                        } else if (status.kind == MessageKind.Video) {
                            Spacer(Modifier.height(8.dp))
                            Surface(color = NexoPurple.copy(alpha = 0.24f), shape = RoundedCornerShape(14.dp)) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Videocam, null, tint = NexoCyan)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Video de estado", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        if (status.text.isNotBlank()) {
                            Spacer(Modifier.height(7.dp))
                            Text(status.text, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, lineHeight = 20.sp)
                        }
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
        Text("Llamar a una conexión", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth().height(132.dp)) {
            items(matches.take(3), key = { "quick-${it.id}" }) { person ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProfilePhoto(person.photoUrl, person.name, Modifier.size(42.dp), CircleShape, NexoPurple, Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text(person.name, color = Color.White, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { onStartCall(person, CallType.Audio) }) { Icon(Icons.Rounded.Call, "Llamar", tint = NexoCyan) }
                    IconButton(onClick = { onStartCall(person, CallType.Video) }) { Icon(Icons.Rounded.Videocam, "Videollamar", tint = NexoCyan) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    Text("Recientes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    Spacer(Modifier.height(6.dp))
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (calls.isEmpty()) {
            item { HubEmpty("No hay llamadas todavía", "Las llamadas recientes aparecerán aquí.") }
        } else {
            items(calls, key = { it.id }) { call ->
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = NexoPurple.copy(alpha = 0.30f), shape = CircleShape) {
                        Icon(if (call.type == CallType.Video) Icons.Rounded.Videocam else Icons.Rounded.Call, null, tint = NexoCyan, modifier = Modifier.padding(10.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(call.peerName, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(callStateLabel(call.state), color = NexoMuted, fontSize = 11.sp)
                    }
                    Text(formatHubTime(call.startedAt), color = NexoMuted, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun HubEmpty(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(body, color = NexoMuted, fontSize = 11.sp)
    }
}

@Composable
private fun hubFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = NexoCyan,
    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
    focusedPlaceholderColor = NexoMuted,
    unfocusedPlaceholderColor = NexoMuted,
    cursorColor = NexoCyan
)

private fun conversationPreviewText(message: ChatMessage): String {
    if (message.deleted) return "Mensaje eliminado"
    val label = when (message.kind) {
        MessageKind.Image -> "📷 Foto"
        MessageKind.Video -> "🎬 Video"
        MessageKind.Audio -> "🎤 Nota de voz"
        MessageKind.Document -> "📎 Archivo"
        MessageKind.Location -> "📍 Ubicación"
        MessageKind.Contact -> "👤 Contacto"
        MessageKind.System -> message.text
        MessageKind.Text -> message.text
    }
    return if (message.fromMe) "Vos: $label" else label
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

private fun maskPhone(phone: String): String {
    val prefixLength = (phone.length - 8).coerceAtLeast(1).coerceAtMost(phone.length)
    val prefix = phone.take(prefixLength)
    val suffix = phone.takeLast(4)
    return "$prefix •••• $suffix"
}

private fun formatHubTime(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return runCatching {
        val instant = Instant.parse(value)
        val zone = ZoneId.systemDefault()
        val dateTime = instant.atZone(zone)
        val today = LocalDate.now(zone)
        when (dateTime.toLocalDate()) {
            today -> dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
            today.minusDays(1) -> "ayer"
            else -> dateTime.format(DateTimeFormatter.ofPattern("dd/MM"))
        }
    }.getOrDefault("")
}
