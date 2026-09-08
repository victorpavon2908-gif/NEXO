package ni.nexo.app.data

class DemoNexoRepository : NexoRepository {
    override val configured: Boolean = false

    private var signedIn = false
    private var profile: LocalUserProfile? = null
    private val matchedIds = linkedSetOf<String>()

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
    }

    override suspend fun loadMyProfile(): LocalUserProfile? = profile

    override suspend fun saveMyProfile(profile: LocalUserProfile) {
        this.profile = profile
    }

    override suspend fun discoverProfiles(): List<PersonProfile> = FakeNexoRepository.people

    override suspend fun like(targetUserId: String): Boolean {
        matchedIds += targetUserId
        return true
    }

    override suspend fun loadMatches(): List<PersonProfile> =
        FakeNexoRepository.people.filter { it.id in matchedIds }
}
