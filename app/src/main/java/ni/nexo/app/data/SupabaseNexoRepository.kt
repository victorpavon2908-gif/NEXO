package ni.nexo.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Facebook
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperation
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.selectAsFlow
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class SupabaseNexoRepository(private val supabase: SupabaseClient) : NexoRepository {
    override val configured: Boolean = true
    override suspend fun hasSession(): Boolean = supabase.auth.currentSessionOrNull() != null

    override suspend fun signUp(email: String, password: String): AuthOutcome {
        supabase.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        val ready = supabase.auth.currentSessionOrNull() != null
        return if (ready) AuthOutcome(true, "Cuenta creada. Bienvenido a NEXO.")
        else AuthOutcome(false, "Cuenta creada. Revisá tu correo para confirmar y luego iniciá sesión.")
    }

    override suspend fun signIn(email: String, password: String): AuthOutcome {
        supabase.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        return AuthOutcome(true, "Sesión iniciada.")
    }

    override suspend fun signInWithGoogle(): AuthOutcome {
        supabase.auth.signInWith(Google)
        return AuthOutcome(false, "Completá el acceso con Google en el navegador.")
    }

    override suspend fun signInWithFacebook(): AuthOutcome {
        supabase.auth.signInWith(Facebook)
        return AuthOutcome(false, "Completá el acceso con Facebook en el navegador.")
    }

    override suspend fun signOut() { supabase.auth.signOut() }

    override suspend fun loadMyProfile(): LocalUserProfile? {
        val userId = currentUserId()
        return supabase.from("profiles").select { filter { eq("id", userId) } }
            .decodeList<ProfileRow>().firstOrNull()?.toLocalProfile()
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
                interests = profile.interests.map(String::trim).filter(String::isNotBlank),
                photoUrl = profile.photoUrl
            )
        ) { onConflict = "id" }
    }

    override suspend fun uploadProfilePhoto(bytes: ByteArray, mimeType: String): String {
        require(bytes.isNotEmpty()) { "La foto está vacía." }
        require(bytes.size <= MAX_PROFILE_PHOTO_BYTES) { "La foto debe pesar como máximo 5 MB." }
        val normalizedMime = mimeType.lowercase().substringBefore(';').trim()
        val extension = when (normalizedMime) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> error("Usá una foto JPG, PNG o WebP.")
        }
        val userId = currentUserId()
        val path = "$userId/profile-${System.currentTimeMillis()}.$extension"
        val bucket = supabase.storage.from(PROFILE_PHOTOS_BUCKET)
        bucket.upload(path, bytes) {
            upsert = false
            contentType = ContentType.parse(normalizedMime)
        }
        return bucket.publicUrl(path)
    }

    override suspend fun discoverProfiles(): List<PersonProfile> {
        val me = currentUserId()
        return supabase.from("profiles").select().decodeList<ProfileRow>().asSequence()
            .filter { it.id != me && it.isActive }.map(ProfileRow::toPersonProfile).toList()
    }

    override suspend fun like(targetUserId: String): Boolean {
        val me = currentUserId()
        require(me != targetUserId) { "No podés darte like a vos mismo" }
        supabase.from("likes").upsert(LikeRow(actorId = me, targetId = targetUserId)) {
            onConflict = "actor_id,target_id"
        }
        return findMatch(targetUserId) != null
    }

    override suspend fun loadMatches(): List<PersonProfile> {
        val me = currentUserId()
        val otherIds = supabase.from("matches").select().decodeList<MatchRow>().mapNotNull { row ->
            when (me) {
                row.userA -> row.userB
                row.userB -> row.userA
                else -> null
            }
        }.toSet()
        if (otherIds.isEmpty()) return emptyList()
        return supabase.from("profiles").select().decodeList<ProfileRow>()
            .filter { it.id in otherIds }.map(ProfileRow::toPersonProfile)
    }

    @OptIn(SupabaseExperimental::class)
    override suspend fun observeMessages(targetUserId: String): Flow<List<ChatMessage>> {
        val me = currentUserId()
        val match = findMatch(targetUserId) ?: error("Solo podés conversar con un match activo.")
        return supabase.from("messages")
            .selectAsFlow<MessageRow, String>(
                primaryKey = MessageRow::id,
                channelName = "nexo-messages-${match.id}",
                filter = FilterOperation("match_id", FilterOperator.EQ, match.id)
            )
            .map { rows ->
                rows.sortedBy { it.createdAt.orEmpty() }.map { row ->
                    ChatMessage(
                        id = row.id,
                        text = row.payload,
                        fromMe = row.senderId == me,
                        createdAt = row.createdAt,
                        encryptionVersion = row.encryptionVersion
                    )
                }
            }
    }

    override suspend fun sendMessage(targetUserId: String, text: String) {
        val clean = text.trim()
        require(clean.isNotBlank()) { "El mensaje está vacío." }
        require(clean.length <= 4000) { "El mensaje no puede superar 4000 caracteres." }
        val me = currentUserId()
        val match = findMatch(targetUserId) ?: error("Solo podés enviar mensajes a un match activo.")
        supabase.from("messages").insert(
            NewMessageRow(matchId = match.id, senderId = me, payload = clean, encryptionVersion = 0)
        )
    }

    private suspend fun findMatch(targetUserId: String): MatchRow? {
        val me = currentUserId()
        return supabase.from("matches").select().decodeList<MatchRow>().firstOrNull { row ->
            (row.userA == me && row.userB == targetUserId) || (row.userA == targetUserId && row.userB == me)
        }
    }

    private fun currentUserId(): String =
        supabase.auth.currentUserOrNull()?.id ?: error("La sesión expiró. Iniciá sesión nuevamente.")

    private companion object {
        const val PROFILE_PHOTOS_BUCKET = "profile-photos"
        const val MAX_PROFILE_PHOTO_BYTES = 5 * 1024 * 1024
    }
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
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("photo_url") val photoUrl: String? = null
) {
    fun toPersonProfile() = PersonProfile(id, name, age, city, bio, intention, interests, verified, photoUrl)
    fun toLocalProfile() = LocalUserProfile(name, age.toString(), city, bio, intention, interests, photoUrl)
}

@Serializable private data class LikeRow(
    @SerialName("actor_id") val actorId: String,
    @SerialName("target_id") val targetId: String
)

@Serializable private data class MatchRow(
    val id: String,
    @SerialName("user_a") val userA: String,
    @SerialName("user_b") val userB: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable private data class MessageRow(
    val id: String,
    @SerialName("match_id") val matchId: String,
    @SerialName("sender_id") val senderId: String,
    val payload: String,
    @SerialName("encryption_version") val encryptionVersion: Int = 0,
    val nonce: String? = null,
    @SerialName("sender_key_id") val senderKeyId: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable private data class NewMessageRow(
    @SerialName("match_id") val matchId: String,
    @SerialName("sender_id") val senderId: String,
    val payload: String,
    @SerialName("encryption_version") val encryptionVersion: Int = 0,
    val nonce: String? = null,
    @SerialName("sender_key_id") val senderKeyId: String? = null
)
