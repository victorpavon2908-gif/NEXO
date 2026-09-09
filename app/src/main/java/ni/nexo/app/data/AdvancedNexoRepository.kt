package ni.nexo.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperation
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.selectAsFlow
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Capa RC3 que conserva el repositorio existente y agrega contactos verificados,
 * presencia visible, grupos y multimedia de estados sin duplicar la lógica madura
 * de matches/chat privado.
 */
class AdvancedNexoRepository(
    private val base: NexoRepository,
    private val supabase: SupabaseClient
) : NexoRepository by base {

    override suspend fun loadContactSettings(): ContactSettings {
        val row = supabase.postgrest
            .rpc("get_my_contact_settings")
            .decodeList<ContactSettingsRow>()
            .firstOrNull()
            ?: return ContactSettings()
        return ContactSettings(
            phoneE164 = row.phone,
            phoneVerified = row.phoneVerified,
            discoverable = row.discoverable
        )
    }

    override suspend fun requestPhoneVerification(phoneE164: String) {
        val clean = normalizeE164(phoneE164)
        require(clean.length in 8..16) { "Ingresá el número completo con código de país, por ejemplo +505XXXXXXXX." }
        supabase.auth.updateUser {
            phone = clean
        }
    }

    override suspend fun verifyPhoneCode(phoneE164: String, code: String): Boolean {
        val cleanPhone = normalizeE164(phoneE164)
        val cleanCode = code.filter(Char::isDigit)
        require(cleanCode.length == 6) { "El código debe tener 6 dígitos." }
        supabase.auth.verifyPhoneOtp(
            type = OtpType.Phone.PHONE_CHANGE,
            phone = cleanPhone,
            token = cleanCode
        )
        runCatching { setContactDiscoveryEnabled(true) }
        return true
    }

    override suspend fun setContactDiscoveryEnabled(enabled: Boolean) {
        supabase.postgrest.rpc(
            "set_contact_discoverable",
            buildJsonObject { put("enabled", enabled) }
        )
    }

    override suspend fun findContactsByHashes(phoneHashes: List<String>): List<ContactMatch> {
        val hashes = phoneHashes
            .asSequence()
            .map(String::trim)
            .filter { it.length == 64 }
            .distinct()
            .take(500)
            .toList()
        if (hashes.isEmpty()) return emptyList()

        val parameters = buildJsonObject {
            put("phone_hashes", JsonArray(hashes.map(::JsonPrimitive)))
        }
        return supabase.postgrest
            .rpc("find_nexo_contacts", parameters)
            .decodeList<ContactLookupRow>()
            .map { row ->
                ContactMatch(
                    phoneHash = row.phoneHash,
                    profile = PersonProfile(
                        id = row.id,
                        name = row.name,
                        age = row.age,
                        city = row.city,
                        bio = row.bio,
                        intention = row.intention,
                        interests = row.interests,
                        verified = row.verified,
                        photoUrl = row.photoUrl,
                        phoneVerified = row.phoneVerified,
                        isOnline = row.isOnline,
                        lastSeen = row.lastSeen
                    )
                )
            }
    }

    override suspend fun startContactConversation(phoneHash: String) {
        val cleanHash = phoneHash.trim().lowercase()
        require(cleanHash.matches(Regex("[0-9a-f]{64}"))) { "El contacto no es válido." }
        supabase.postgrest.rpc(
            "start_contact_conversation",
            buildJsonObject { put("contact_phone_hash", cleanHash) }
        )
    }

    @OptIn(SupabaseExperimental::class)
    override suspend fun observePresence(userId: String): Flow<PresenceInfo> {
        return supabase.from("presence")
            .selectAsFlow<PresenceDbRow, String>(
                primaryKey = PresenceDbRow::userId,
                channelName = "nexo-presence-$userId",
                filter = FilterOperation("user_id", FilterOperator.EQ, userId)
            )
            .map { rows ->
                rows.firstOrNull()?.let {
                    PresenceInfo(userId = it.userId, online = it.online, lastSeen = it.lastSeen)
                } ?: PresenceInfo(userId = userId)
            }
    }

    override suspend fun loadGroups(): List<GroupSummary> {
        val me = currentUserId()
        val groups = supabase.from("groups").select().decodeList<GroupRow>()
        if (groups.isEmpty()) return emptyList()
        val members = supabase.from("group_members").select().decodeList<GroupMemberRow>()
        val messages = runCatching {
            supabase.from("group_messages").select().decodeList<GroupMessageRow>()
        }.getOrDefault(emptyList())

        return groups.map { group ->
            val groupMembers = members.filter { it.groupId == group.id }
            val last = messages.filter { it.groupId == group.id }.maxByOrNull { it.createdAt.orEmpty() }
            GroupSummary(
                id = group.id,
                name = group.name,
                memberCount = groupMembers.size.coerceAtLeast(1),
                photoUrl = group.photoPath?.let { runCatching { signedGroupMediaUrl(it) }.getOrNull() },
                ownerId = group.ownerId,
                role = groupMembers.firstOrNull { it.userId == me }?.role
                    ?: if (group.ownerId == me) "owner" else "member",
                updatedAt = last?.createdAt ?: group.updatedAt ?: group.createdAt,
                lastMessage = last?.let { if (it.deletedAt != null) "Mensaje eliminado" else it.payload }
            )
        }.sortedByDescending { it.updatedAt.orEmpty() }
    }

    override suspend fun createGroup(name: String, memberIds: List<String>): GroupSummary {
        val cleanName = name.trim()
        require(cleanName.length in 1..80) { "El nombre del grupo debe tener entre 1 y 80 caracteres." }
        val me = currentUserId()
        val id = UUID.randomUUID().toString()
        val now = Instant.now().toString()

        supabase.from("groups").insert(
            GroupRow(
                id = id,
                name = cleanName,
                ownerId = me,
                createdAt = now,
                updatedAt = now
            )
        )
        supabase.from("group_members").insert(
            GroupMemberRow(groupId = id, userId = me, role = "owner", addedBy = me)
        )
        memberIds
            .asSequence()
            .filter { it.isNotBlank() && it != me }
            .distinct()
            .take(100)
            .forEach { memberId ->
                supabase.from("group_members").insert(
                    GroupMemberRow(groupId = id, userId = memberId, role = "member", addedBy = me)
                )
            }

        return GroupSummary(
            id = id,
            name = cleanName,
            memberCount = memberIds.filter { it != me }.distinct().size + 1,
            ownerId = me,
            role = "owner",
            updatedAt = now
        )
    }

    @OptIn(SupabaseExperimental::class)
    override suspend fun observeGroupMessages(groupId: String): Flow<List<ChatMessage>> {
        val me = currentUserId()
        return supabase.from("group_messages")
            .selectAsFlow<GroupMessageRow, String>(
                primaryKey = GroupMessageRow::id,
                channelName = "nexo-group-$groupId",
                filter = FilterOperation("group_id", FilterOperator.EQ, groupId)
            )
            .map { rows ->
                val names = runCatching {
                    supabase.from("profiles").select().decodeList<MiniProfileRow>().associateBy { it.id }
                }.getOrDefault(emptyMap())
                rows.sortedBy { it.createdAt.orEmpty() }.map { row ->
                    ChatMessage(
                        id = row.id,
                        text = if (row.deletedAt != null) "Mensaje eliminado" else row.payload,
                        fromMe = row.senderId == me,
                        createdAt = row.createdAt,
                        status = if (row.senderId == me) MessageStatus.Sent else MessageStatus.Delivered,
                        replyToText = row.replySnapshot,
                        replyToId = row.replyToId,
                        kind = messageKindRc3(row.kind),
                        mediaPath = row.mediaPath,
                        mediaUrl = row.mediaPath?.let { runCatching { signedGroupMediaUrl(it) }.getOrNull() },
                        mediaMime = row.mediaMime,
                        mediaSizeBytes = row.mediaSize,
                        durationMs = row.durationMs,
                        edited = row.editedAt != null,
                        deleted = row.deletedAt != null,
                        senderId = row.senderId,
                        senderName = if (row.senderId == me) "Vos" else names[row.senderId]?.name ?: "Miembro"
                    )
                }
            }
    }

    override suspend fun sendGroupMessage(
        groupId: String,
        text: String,
        kind: MessageKind,
        mediaPath: String?,
        mediaMime: String?,
        mediaSizeBytes: Long?,
        durationMs: Long?,
        replyToText: String?,
        replyToId: String?
    ) {
        val clean = text.trim()
        require(clean.isNotBlank() || mediaPath != null) { "El mensaje está vacío." }
        require(clean.length <= 4000) { "El mensaje no puede superar 4000 caracteres." }
        supabase.from("group_messages").insert(
            NewGroupMessageRow(
                groupId = groupId,
                senderId = currentUserId(),
                payload = clean.ifBlank { mediaLabelRc3(kind) },
                kind = kind.name.lowercase(),
                replyToId = replyToId,
                replySnapshot = replyToText?.take(500),
                mediaPath = mediaPath,
                mediaMime = mediaMime,
                mediaSize = mediaSizeBytes,
                durationMs = durationMs
            )
        )
    }

    override suspend fun uploadGroupMedia(
        groupId: String,
        bytes: ByteArray,
        mimeType: String,
        fileName: String
    ): String {
        require(bytes.isNotEmpty()) { "El archivo está vacío." }
        require(bytes.size <= MAX_GROUP_MEDIA_BYTES) { "El archivo supera el límite de 25 MB." }
        val me = currentUserId()
        val safe = safeFileName(fileName)
        val path = "$groupId/$me/${System.currentTimeMillis()}-$safe"
        val mime = normalizedMime(mimeType)
        supabase.storage.from(GROUP_MEDIA_BUCKET).upload(path, bytes) {
            upsert = false
            contentType = ContentType.parse(mime)
        }
        return path
    }

    override suspend fun uploadStatusMedia(bytes: ByteArray, mimeType: String, fileName: String): String {
        require(bytes.isNotEmpty()) { "El archivo está vacío." }
        require(bytes.size <= MAX_STATUS_MEDIA_BYTES) { "El estado supera el límite de 15 MB." }
        val mime = normalizedMime(mimeType)
        require(mime.startsWith("image/") || mime.startsWith("video/")) {
            "Los estados multimedia aceptan imágenes o videos."
        }
        val me = currentUserId()
        val path = "$me/${System.currentTimeMillis()}-${safeFileName(fileName)}"
        supabase.storage.from(STATUS_MEDIA_BUCKET).upload(path, bytes) {
            upsert = false
            contentType = ContentType.parse(mime)
        }
        return path
    }

    override suspend fun publishStatus(
        text: String,
        kind: MessageKind,
        mediaPath: String?,
        mediaMime: String?
    ) {
        val clean = text.trim().take(1500)
        require(clean.isNotBlank() || mediaPath != null) { "Escribí algo o elegí una foto/video para tu estado." }
        val dbKind = when (kind) {
            MessageKind.Image -> "image"
            MessageKind.Video -> "video"
            else -> "text"
        }
        supabase.from("status_updates").insert(
            RichStatusInsertRow(
                ownerId = currentUserId(),
                kind = dbKind,
                text = clean,
                mediaPath = mediaPath,
                mediaMime = mediaMime
            )
        )
    }

    override suspend fun loadStatusUpdates(): List<StatusUpdate> {
        val me = currentUserId()
        val rows = supabase.from("status_updates").select().decodeList<RichStatusRow>()
        val profiles = runCatching {
            supabase.from("profiles").select().decodeList<MiniProfileRow>().associateBy { it.id }
        }.getOrDefault(emptyMap())
        return rows.sortedByDescending { it.createdAt.orEmpty() }.map { row ->
            val profile = profiles[row.ownerId]
            StatusUpdate(
                id = row.id,
                ownerId = row.ownerId,
                ownerName = profile?.name ?: if (row.ownerId == me) "Vos" else "NEXO",
                text = row.text,
                kind = messageKindRc3(row.kind),
                mediaPath = row.mediaPath,
                mediaUrl = row.mediaPath?.let { runCatching { signedStatusMediaUrl(it) }.getOrNull() },
                mediaMime = row.mediaMime,
                ownerPhotoUrl = profile?.photoUrl,
                createdAt = row.createdAt,
                expiresAt = row.expiresAt,
                mine = row.ownerId == me
            )
        }
    }

    private suspend fun signedGroupMediaUrl(path: String): String =
        supabase.storage.from(GROUP_MEDIA_BUCKET).createSignedUrl(path, 1.hours)

    private suspend fun signedStatusMediaUrl(path: String): String =
        supabase.storage.from(STATUS_MEDIA_BUCKET).createSignedUrl(path, 1.hours)

    private fun currentUserId(): String =
        supabase.auth.currentUserOrNull()?.id ?: error("La sesión expiró. Iniciá sesión nuevamente.")

    private companion object {
        const val GROUP_MEDIA_BUCKET = "group-media"
        const val STATUS_MEDIA_BUCKET = "status-media"
        const val MAX_GROUP_MEDIA_BYTES = 25 * 1024 * 1024
        const val MAX_STATUS_MEDIA_BYTES = 15 * 1024 * 1024
    }
}

private fun normalizeE164(value: String): String {
    val digits = value.trim().filter(Char::isDigit)
    return "+$digits"
}

private fun normalizedMime(value: String): String =
    value.substringBefore(';').trim().ifBlank { "application/octet-stream" }

private fun safeFileName(value: String): String = value
    .ifBlank { "archivo" }
    .replace(Regex("[^A-Za-z0-9._-]"), "_")
    .takeLast(100)

private fun messageKindRc3(value: String): MessageKind = when (value.lowercase()) {
    "image" -> MessageKind.Image
    "video" -> MessageKind.Video
    "audio" -> MessageKind.Audio
    "document" -> MessageKind.Document
    "location" -> MessageKind.Location
    "contact" -> MessageKind.Contact
    "system" -> MessageKind.System
    else -> MessageKind.Text
}

private fun mediaLabelRc3(kind: MessageKind): String = when (kind) {
    MessageKind.Image -> "Foto"
    MessageKind.Video -> "Video"
    MessageKind.Audio -> "Nota de voz"
    MessageKind.Document -> "Archivo"
    MessageKind.Location -> "Ubicación"
    MessageKind.Contact -> "Contacto"
    MessageKind.System -> "Aviso"
    MessageKind.Text -> "Mensaje"
}

@Serializable
private data class ContactSettingsRow(
    val phone: String? = null,
    @SerialName("phone_verified") val phoneVerified: Boolean = false,
    val discoverable: Boolean = false
)

@Serializable
private data class ContactLookupRow(
    @SerialName("phone_hash") val phoneHash: String,
    val id: String,
    val name: String,
    val age: Int,
    val city: String,
    val bio: String = "",
    val intention: String = "",
    val interests: List<String> = emptyList(),
    val verified: Boolean = false,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("is_online") val isOnline: Boolean = false,
    @SerialName("last_seen") val lastSeen: String? = null,
    @SerialName("phone_verified") val phoneVerified: Boolean = true
)

@Serializable
private data class PresenceDbRow(
    @SerialName("user_id") val userId: String,
    val online: Boolean = false,
    @SerialName("last_seen") val lastSeen: String? = null
)

@Serializable
private data class GroupRow(
    val id: String,
    val name: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("photo_path") val photoPath: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
private data class GroupMemberRow(
    @SerialName("group_id") val groupId: String,
    @SerialName("user_id") val userId: String,
    val role: String = "member",
    @SerialName("added_by") val addedBy: String? = null,
    @SerialName("joined_at") val joinedAt: String? = null
)

@Serializable
private data class GroupMessageRow(
    val id: String,
    @SerialName("group_id") val groupId: String,
    @SerialName("sender_id") val senderId: String,
    val payload: String = "",
    val kind: String = "text",
    @SerialName("reply_to_id") val replyToId: String? = null,
    @SerialName("reply_snapshot") val replySnapshot: String? = null,
    @SerialName("media_path") val mediaPath: String? = null,
    @SerialName("media_mime") val mediaMime: String? = null,
    @SerialName("media_size") val mediaSize: Long? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("edited_at") val editedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
private data class NewGroupMessageRow(
    @SerialName("group_id") val groupId: String,
    @SerialName("sender_id") val senderId: String,
    val payload: String,
    val kind: String = "text",
    @SerialName("reply_to_id") val replyToId: String? = null,
    @SerialName("reply_snapshot") val replySnapshot: String? = null,
    @SerialName("media_path") val mediaPath: String? = null,
    @SerialName("media_mime") val mediaMime: String? = null,
    @SerialName("media_size") val mediaSize: Long? = null,
    @SerialName("duration_ms") val durationMs: Long? = null
)

@Serializable
private data class MiniProfileRow(
    val id: String,
    val name: String,
    @SerialName("photo_url") val photoUrl: String? = null
)

@Serializable
private data class RichStatusRow(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val kind: String = "text",
    val text: String = "",
    @SerialName("media_path") val mediaPath: String? = null,
    @SerialName("media_mime") val mediaMime: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null
)

@Serializable
private data class RichStatusInsertRow(
    @SerialName("owner_id") val ownerId: String,
    val kind: String,
    val text: String,
    @SerialName("media_path") val mediaPath: String? = null,
    @SerialName("media_mime") val mediaMime: String? = null
)
