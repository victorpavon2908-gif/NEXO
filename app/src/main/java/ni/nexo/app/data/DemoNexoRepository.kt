package ni.nexo.app.data

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class DemoNexoRepository : NexoRepository {
    override val configured: Boolean = false
    override val liveCallsAvailable: Boolean = true

    private var signedIn = false
    private var profile: LocalUserProfile? = null
    private val matchedIds = linkedSetOf<String>()
    private val blockedIds = linkedSetOf<String>()
    private val messagesByPerson = mutableMapOf<String, MutableStateFlow<List<ChatMessage>>>()
    private var privacySettings = PrivacySettings()
    private var discoveryPreferences = DiscoveryPreferences()
    private val safeDatePlans = mutableListOf<SafeDatePlan>()
    private val statusUpdates = mutableListOf(
        StatusUpdate(
            id = "status-valentina",
            ownerId = "valentina",
            ownerName = "Valentina",
            text = "Café, música y una tarde tranquila ☕✨",
            createdAt = "11:42"
        ),
        StatusUpdate(
            id = "status-sofia",
            ownerId = "sofia",
            ownerName = "Sofía",
            text = "Buscando un lugar nuevo para conocer este finde 🌿",
            createdAt = "10:18"
        )
    )
    private val calls = mutableListOf<CallRecord>()

    override suspend fun hasSession(): Boolean = signedIn

    override suspend fun signUp(email: String, password: String): AuthOutcome {
        signedIn = true
        return AuthOutcome(true, "Modo demo activo: cuenta simulada creada.")
    }

    override suspend fun signIn(email: String, password: String): AuthOutcome {
        signedIn = true
        return AuthOutcome(true, "Modo demo activo: sesión simulada iniciada.")
    }

    override suspend fun signOut() {
        signedIn = false
        profile = null
        matchedIds.clear()
        blockedIds.clear()
        messagesByPerson.clear()
        calls.clear()
    }

    override suspend fun loadMyProfile(): LocalUserProfile? = profile

    override suspend fun saveMyProfile(profile: LocalUserProfile) {
        this.profile = profile
    }

    override suspend fun uploadProfilePhoto(bytes: ByteArray, mimeType: String): String {
        error("Conectá Supabase para subir fotografías reales.")
    }

    override suspend fun discoverProfiles(): List<PersonProfile> =
        FakeNexoRepository.people.filterNot { it.id in blockedIds }

    override suspend fun like(targetUserId: String): Boolean {
        if (targetUserId in blockedIds) return false
        matchedIds += targetUserId
        return true
    }

    override suspend fun loadMatches(): List<PersonProfile> {
        val seeded = FakeNexoRepository.people.firstOrNull()
        val ids = buildSet {
            seeded?.let { add(it.id) }
            addAll(matchedIds)
        }
        return FakeNexoRepository.people.filter { it.id in ids && it.id !in blockedIds }
    }

    override suspend fun startContactConversation(phoneHash: String) {
        val person = FakeNexoRepository.people.firstOrNull() ?: return
        matchedIds += person.id
    }

    override suspend fun observeMessages(targetUserId: String): Flow<List<ChatMessage>> =
        messagesByPerson.getOrPut(targetUserId) {
            val person = FakeNexoRepository.people.firstOrNull { it.id == targetUserId }
            MutableStateFlow(person?.let(FakeNexoRepository::starterMessages).orEmpty())
        }

    override suspend fun sendMessage(
        targetUserId: String,
        text: String,
        replyToText: String?,
        replyToId: String?,
        kind: MessageKind,
        mediaPath: String?,
        mediaMime: String?,
        mediaSizeBytes: Long?,
        durationMs: Long?
    ) {
        require(targetUserId !in blockedIds) { "Desbloqueá a esta persona antes de enviar mensajes." }
        val clean = text.trim()
        require(clean.isNotBlank() || mediaPath != null) { "El mensaje está vacío." }

        val flow = messagesByPerson.getOrPut(targetUserId) { MutableStateFlow(emptyList()) }
        val id = "demo-${System.nanoTime()}"
        val createdAt = nowLabel()
        val displayText = clean.ifBlank {
            when (kind) {
                MessageKind.Image -> "Foto"
                MessageKind.Video -> "Video"
                MessageKind.Audio -> "Nota de voz"
                MessageKind.Document -> "Documento"
                MessageKind.Location -> "Ubicación"
                MessageKind.Contact -> "Contacto"
                else -> "Mensaje"
            }
        }

        flow.value = flow.value + ChatMessage(
            id = id,
            text = displayText.take(4000),
            fromMe = true,
            createdAt = createdAt,
            status = MessageStatus.Sending,
            replyToText = replyToText?.take(160),
            replyToId = replyToId,
            kind = kind,
            mediaPath = mediaPath,
            mediaUrl = mediaPath,
            mediaMime = mediaMime,
            mediaSizeBytes = mediaSizeBytes,
            durationMs = durationMs
        )

        delay(160)
        mutateMessage(id) { it.copy(status = MessageStatus.Sent) }
        delay(200)
        mutateMessage(id) { it.copy(status = MessageStatus.Delivered) }
        delay(240)
        mutateMessage(id) { it.copy(status = MessageStatus.Read) }

        if (kind == MessageKind.Text) {
            delay(600)
            val person = FakeNexoRepository.people.firstOrNull { it.id == targetUserId }
            flow.value = flow.value + ChatMessage(
                id = "demo-reply-${System.nanoTime()}",
                text = demoReply(clean, person?.name ?: "NEXO"),
                fromMe = false,
                createdAt = nowLabel(),
                status = MessageStatus.Delivered
            )
        }
    }

    override suspend fun uploadChatMedia(
        targetUserId: String,
        bytes: ByteArray,
        mimeType: String,
        fileName: String
    ): String {
        require(bytes.isNotEmpty()) { "El archivo está vacío." }
        require(bytes.size <= 25 * 1024 * 1024) { "El archivo supera el límite de 25 MB del modo de prueba." }
        return "demo://media/$targetUserId/${System.nanoTime()}-${fileName.ifBlank { "archivo" }}"
    }

    override suspend fun signedChatMediaUrl(path: String): String? = path

    override suspend fun editMessage(messageId: String, newText: String) {
        val clean = newText.trim()
        require(clean.isNotBlank()) { "El mensaje no puede quedar vacío." }
        mutateMessage(messageId) {
            if (it.fromMe && !it.deleted) it.copy(text = clean.take(4000), edited = true) else it
        }
    }

    override suspend fun deleteMessage(messageId: String) {
        mutateMessage(messageId) {
            if (it.fromMe) it.copy(
                text = "Mensaje eliminado",
                deleted = true,
                mediaPath = null,
                mediaUrl = null,
                reactions = emptyMap()
            ) else it
        }
    }

    override suspend fun reactToMessage(messageId: String, emoji: String) {
        if (emoji.isBlank()) return
        mutateMessage(messageId) {
            val next = it.reactions.toMutableMap()
            next[emoji] = (next[emoji] ?: 0) + 1
            it.copy(reactions = next)
        }
    }

    override suspend fun markConversationRead(targetUserId: String) {
        val flow = messagesByPerson[targetUserId] ?: return
        flow.value = flow.value.map { message ->
            if (!message.fromMe) message.copy(status = MessageStatus.Read) else message
        }
    }

    override suspend fun blockUser(targetUserId: String) {
        blockedIds += targetUserId
        matchedIds -= targetUserId
    }

    override suspend fun reportUser(targetUserId: String, reason: String, details: String) {
        require(reason.isNotBlank()) { "Indicá un motivo para el reporte." }
    }

    override suspend fun loadPrivacySettings(): PrivacySettings = privacySettings

    override suspend fun savePrivacySettings(settings: PrivacySettings) {
        privacySettings = settings
    }

    override suspend fun loadDiscoveryPreferences(): DiscoveryPreferences = discoveryPreferences

    override suspend fun saveDiscoveryPreferences(settings: DiscoveryPreferences) {
        require(settings.minAge in 18..120 && settings.maxAge in settings.minAge..120) {
            "Elegí un rango de edad válido."
        }
        discoveryPreferences = settings
    }

    override suspend fun loadSafeDatePlans(): List<SafeDatePlan> = safeDatePlans.toList()

    override suspend fun createSafeDatePlan(plan: SafeDatePlan): SafeDatePlan {
        require(plan.partnerName.isNotBlank()) { "Indicá con quién será la cita." }
        require(plan.place.isNotBlank()) { "Indicá un lugar público para la cita." }
        require(plan.trustedPhone.filter(Char::isDigit).length >= 8) { "Ingresá un teléfono de confianza válido." }
        require(plan.safetyCode.length >= 4) { "Usá un código secreto de al menos 4 caracteres." }
        val saved = plan.copy(id = plan.id.ifBlank { "safe-${System.nanoTime()}" }, createdAt = nowLabel())
        safeDatePlans.add(0, saved)
        return saved
    }

    override suspend fun updateSafeDateState(planId: String, state: SafeDateState) {
        val index = safeDatePlans.indexOfFirst { it.id == planId }
        if (index >= 0) safeDatePlans[index] = safeDatePlans[index].copy(state = state)
    }

    override suspend fun loadPremiumEntitlements(): PremiumEntitlements = PremiumEntitlements()

    override suspend fun isSecureBillingReady(): Boolean = false

    override suspend fun verifyGooglePlayPurchase(purchaseToken: String, productIds: List<String>): Boolean = false

    override suspend fun loadStatusUpdates(): List<StatusUpdate> = statusUpdates.toList()

    override suspend fun publishStatus(text: String) {
        val clean = text.trim()
        require(clean.isNotBlank()) { "Escribí algo para publicar tu estado." }
        statusUpdates.add(
            0,
            StatusUpdate(
                id = "status-${System.nanoTime()}",
                ownerId = "me",
                ownerName = profile?.name?.ifBlank { "Vos" } ?: "Vos",
                text = clean.take(1500),
                createdAt = nowLabel(),
                mine = true
            )
        )
    }

    override suspend fun loadCalls(): List<CallRecord> = calls.toList()

    override suspend fun startCall(targetUserId: String, peerName: String, type: CallType): CallRecord {
        val call = CallRecord(
            id = UUID.randomUUID().toString(),
            peerId = targetUserId,
            peerName = peerName,
            type = type,
            state = CallState.Ringing,
            outgoing = true,
            startedAt = nowLabel()
        )
        calls.add(0, call)
        return call
    }

    override suspend fun endCall(callId: String) {
        val index = calls.indexOfFirst { it.id == callId }
        if (index >= 0) calls[index] = calls[index].copy(state = CallState.Ended, endedAt = nowLabel())
    }

    private fun mutateMessage(id: String, transform: (ChatMessage) -> ChatMessage) {
        messagesByPerson.values.forEach { flow ->
            flow.value = flow.value.map { message -> if (message.id == id) transform(message) else message }
        }
    }

    private fun demoReply(message: String, personName: String): String {
        val lower = message.lowercase()
        return when {
            "hola" in lower || "buenas" in lower -> "¡Holaa! 😊 ¿Cómo va tu día?"
            "café" in lower || "cafe" in lower -> "Me encanta la idea ☕. Un café tranquilo sería un buen primer plan."
            "cine" in lower -> "Sí, totalmente 🎬. ¿Qué tipo de películas te gustan?"
            "viaj" in lower -> "Viajar siempre suma ✈️. ¿Qué lugar tenés pendiente por conocer?"
            "como estas" in lower || "cómo estás" in lower -> "Muy bien 😊 y mejor ahora que estamos hablando por NEXO."
            "salir" in lower || "vernos" in lower -> "Podría ser 😊. Primero sigamos conociéndonos un poquito por aquí."
            else -> "Me gustó eso 😊. Contame un poco más, quiero conocerte mejor."
        }
    }

    private fun nowLabel(): String =
        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
}
