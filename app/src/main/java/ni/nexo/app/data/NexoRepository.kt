package ni.nexo.app.data

interface NexoRepository {
    val configured: Boolean

    suspend fun hasSession(): Boolean
    suspend fun signUp(email: String, password: String): AuthOutcome
    suspend fun signIn(email: String, password: String): AuthOutcome
    suspend fun signOut()

    suspend fun loadMyProfile(): LocalUserProfile?
    suspend fun saveMyProfile(profile: LocalUserProfile)
    suspend fun discoverProfiles(): List<PersonProfile>
    suspend fun like(targetUserId: String): Boolean
    suspend fun loadMatches(): List<PersonProfile>
}
