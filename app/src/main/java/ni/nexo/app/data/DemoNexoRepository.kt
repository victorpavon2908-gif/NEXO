package ni.nexo.app.data

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class DemoNexoRepository : NexoRepository {
    override val configured: Boolean = false

    private var signedIn = false
    private var profile: LocalUserProfile? = null
    private val matchedIds = linkedSetOf<String>()
    private val messagesByPerson = mutableMapOf<String, MutableStateFlow<List<ChatMessage>>>()

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
        messagesByPerson.clear()
    }

    override suspend fun loadMyProfile(): LocalUserProfile? = profile

    override suspend fun saveMyProfile(profile: LocalUserProfile) {
        this.profile = profile
    }

    override suspend fun uploadProfilePhoto(bytes: ByteArray, mimeType: String): String {
        error("Conectá Supabase para subir fotografías reales.")
    }

    override suspend fun discoverProfiles(): List<PersonProfile> = FakeNexoRepository.people

    override suspend fun like(targetUserId: String): Boolean {
        matchedIds += targetUserId
        return true
    }

    override suspend fun loadMatches(): List<PersonProfile> {
        val seeded = FakeNexoRepository.people.firstOrNull()
        val ids = buildSet {
            seeded?.let { add(it.id) }
            addAll(matchedIds)
        }
        return FakeNexoRepository.people.filter { it.id in ids }
    }

    override suspend fun observeMessages(targetUserId: String): Flow<List<ChatMessage>> =
        messagesByPerson.getOrPut(targetUserId) {
            val person = FakeNexoRepository.people.firstOrNull { it.id == targetUserId }
            MutableStateFlow(person?.let(FakeNexoRepository::starterMessages).orEmpty())
        }

    override suspend fun sendMessage(targetUserId: String, text: String, replyToText: String?) {
        val clean = text.trim()
        if (clean.isBlank()) return

        val flow = messagesByPerson.getOrPut(targetUserId) { MutableStateFlow(emptyList()) }
        val id = "demo-${System.nanoTime()}"
        val createdAt = nowLabel()

        flow.value = flow.value + ChatMessage(
            id = id,
            text = clean.take(4000),
            fromMe = true,
            createdAt = createdAt,
            status = MessageStatus.Sending,
            replyToText = replyToText?.take(160)
        )

        delay(180)
        flow.value = flow.value.map { message ->
            if (message.id == id) message.copy(status = MessageStatus.Sent) else message
        }

        delay(220)
        flow.value = flow.value.map { message ->
            if (message.id == id) message.copy(status = MessageStatus.Delivered) else message
        }

        delay(260)
        flow.value = flow.value.map { message ->
            if (message.id == id) message.copy(status = MessageStatus.Read) else message
        }

        delay(650)
        val person = FakeNexoRepository.people.firstOrNull { it.id == targetUserId }
        val reply = demoReply(clean, person?.name ?: "NEXO")
        flow.value = flow.value + ChatMessage(
            id = "demo-reply-${System.nanoTime()}",
            text = reply,
            fromMe = false,
            createdAt = nowLabel(),
            status = MessageStatus.Delivered
        )
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
