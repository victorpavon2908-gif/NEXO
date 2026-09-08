package ni.nexo.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable

class SupabaseNexoRepository(
    private val supabase: SupabaseClient
) : NexoRepository {
    override val configured: Boolean = true

    override suspend fun hasSession(): Boolean = supabase.auth.currentSessionOrNull() != null

    override suspend fun signUp(email: String, password: String): AuthOutcome {
        supabase.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        val ready = supabase.auth.currentSessionOrNull() != null
        return if (ready) {
            AuthOutcome(true, "Cuenta creada. Bienvenido a NEXO.")
        } else {
            AuthOutcome(false, "Cuenta creada. Revisá tu correo para confirmar y luego iniciá sesión.")
        }
    }

    override suspend fun signIn(email: String, password: String): AuthOutcome {
        supabase.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        return AuthOutcome(true, "Sesión iniciada.")
    }

    override suspend fun signOut() {
        supabase.auth.signOut()
    }

    override suspend fun loadMyProfile(): LocalUserProfile? {
        val userId = currentUserId()
        return supabase.from("profiles")
            .select {
                filter { eq("id", userId) }
            }
            .decodeList<ProfileRow>()
            .firstOrNull()
            ?.toLocalProfile()
    }

    override suspend fun saveMyProfile(profile: LocalUserProfile) {
        val userId = currentUserId()
        val age = profile.age.toIntOrNull() ?: error("La edad no es válida")
        require(age >= 18) { "NEXO es solo para mayores de 18 años" }

        supabase.from("profiles").upsert(
            ProfileRow(
                id = userId,
                name = profile.name.trim(),
                age = age,
                city = profile.city.trim(),
                bio = profile.bio.trim(),
                intention = profile.intention.trim(),
                interests = profile.interests.map(String::trim).filter(String::isNotBlank)
            ),
            onConflict = "id"
        )
    }

    override suspend fun discoverProfiles(): List<PersonProfile> {
        val me = currentUserId()
        return supabase.from("profiles")
            .select()
            .decodeList<ProfileRow>()
            .asSequence()
            .filter { it.id != me && it.isActive }
            .map(ProfileRow::toPersonProfile)
            .toList()
    }

    override suspend fun like(targetUserId: String): Boolean {
        val me = currentUserId()
        require(me != targetUserId) { "No podés darte like a vos mismo" }

        supabase.from("likes").upsert(
            LikeRow(actorId = me, targetId = targetUserId),
            onConflict = "actor_id,target_id"
        )

        return supabase.from("matches")
            .select()
            .decodeList<MatchRow>()
            .any { row ->
                (row.userA == me && row.userB == targetUserId) ||
                    (row.userA == targetUserId && row.userB == me)
            }
    }

    override suspend fun loadMatches(): List<PersonProfile> {
        val me = currentUserId()
        val otherIds = supabase.from("matches")
            .select()
            .decodeList<MatchRow>()
            .mapNotNull { row ->
                when (me) {
                    row.userA -> row.userB
                    row.userB -> row.userA
                    else -> null
                }
            }
            .toSet()

        if (otherIds.isEmpty()) return emptyList()

        return supabase.from("profiles")
            .select()
            .decodeList<ProfileRow>()
            .filter { it.id in otherIds }
            .map(ProfileRow::toPersonProfile)
    }

    private fun currentUserId(): String =
        supabase.auth.currentUserOrNull()?.id ?: error("La sesión expiró. Iniciá sesión nuevamente.")
}

@Serializable
private data class ProfileRow(
    val id: String,
    val name: String,
    val age: Int,
    val city: String,
    val bio: String = "",
    val intention: String = "Conocer a alguien de verdad",
    val interests: List<String> = emptyList(),
    val verified: Boolean = false,
    val isActive: Boolean = true
) {
    fun toPersonProfile() = PersonProfile(
        id = id,
        name = name,
        age = age,
        city = city,
        bio = bio,
        intention = intention,
        interests = interests,
        verified = verified
    )

    fun toLocalProfile() = LocalUserProfile(
        name = name,
        age = age.toString(),
        city = city,
        bio = bio,
        intention = intention,
        interests = interests
    )
}

@Serializable
private data class LikeRow(
    val actorId: String,
    val targetId: String
)

@Serializable
private data class MatchRow(
    val id: String,
    val userA: String,
    val userB: String,
    val createdAt: String? = null
)
