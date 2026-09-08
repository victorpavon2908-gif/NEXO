package ni.nexo.app.data

import kotlinx.coroutines.flow.Flow

interface NexoRepository {
    val configured: Boolean

    suspend fun hasSession(): Boolean
    suspend fun signUp(email: String, password: String): AuthOutcome
    suspend fun signIn(email: String, password: String): AuthOutcome
    suspend fun signInWithGoogle(): AuthOutcome =
        AuthOutcome(false, "Configurá Google en Supabase para activar este acceso.")
    suspend fun signInWithFacebook(): AuthOutcome =
        AuthOutcome(false, "Configurá Facebook en Supabase para activar este acceso.")
    suspend fun signOut()

    suspend fun loadMyProfile(): LocalUserProfile?
    suspend fun saveMyProfile(profile: LocalUserProfile)
    suspend fun uploadProfilePhoto(bytes: ByteArray, mimeType: String): String

    suspend fun discoverProfiles(): List<PersonProfile>
    suspend fun like(targetUserId: String): Boolean
    suspend fun loadMatches(): List<PersonProfile>

    suspend fun observeMessages(targetUserId: String): Flow<List<ChatMessage>>
    suspend fun sendMessage(targetUserId: String, text: String)
}
