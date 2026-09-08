package ni.nexo.app.data

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

    override suspend fun loadMatches(): List<PersonProfile> =
        FakeNexoRepository.people.filter { it.id in matchedIds }

    override suspend fun observeMessages(targetUserId: String): Flow<List<ChatMessage>> =
        messagesByPerson.getOrPut(targetUserId) {
            val person = FakeNexoRepository.people.firstOrNull { it.id == targetUserId }
            MutableStateFlow(person?.let(FakeNexoRepository::starterMessages).orEmpty())
        }

    override suspend fun sendMessage(targetUserId: String, text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val flow = messagesByPerson.getOrPut(targetUserId) { MutableStateFlow(emptyList()) }
        flow.value = flow.value + ChatMessage(
            id = "demo-${System.nanoTime()}",
            text = clean.take(4000),
            fromMe = true
        )
    }
}
