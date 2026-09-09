package ni.nexo.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Facebook
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperation
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.selectAsFlow
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import io.ktor.client.call.body
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import java.time.Instant
import java.security.MessageDigest
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.put

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

    override suspend fun signOut() {
        runCatching { setPresence(false) }
        supabase.auth.signOut()
    }

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
        currentUserId()
        return supabase.postgrest
            .rpc("discover_nexo_profiles")
            .decodeList<ProfileRow>()
            .asSequence()
            .filter { it.isActive && !it.profilePaused }
            .map(ProfileRow::toPersonProfile)
            .toList()
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
                val reactionRows = runCatching {
                    supabase.from("message_reactions").select().decodeList<ReactionRow>()
                }.getOrDefault(emptyList())
                val reactionsByMessage = reactionRows
                    .groupBy { it.messageId }
                    .mapValues { (_, values) -> values.groupingBy { it.emoji }.eachCount() }

                val receiptRows = runCatching {
                    supabase.from("message_receipts").select().decodeList<ReceiptRow>()
                }.getOrDefault(emptyList())
                val peerReceiptByMessage = receiptRows
                    .asSequence()
                    .filter { it.userId != me }
                    .associateBy { it.messageId }

                rows.sortedBy { it.createdAt.orEmpty() }.map { row ->
                    val mediaUrl = row.mediaPath?.let { path ->
                        runCatching { signedChatMediaUrl(path) }.getOrNull()
                    }
                    val peerReceipt = peerReceiptByMessage[row.id]
                    ChatMessage(
                        id = row.id,
                        text = if (row.deletedAt != null) "Mensaje eliminado" else row.payload,
                        fromMe = row.senderId == me,
                        createdAt = row.createdAt,
                        encryptionVersion = row.encryptionVersion,
                        status = when {
                            row.senderId != me -> MessageStatus.Delivered
                            peerReceipt?.readAt != null -> MessageStatus.Read
                            peerReceipt?.deliveredAt != null -> MessageStatus.Delivered
                            else -> MessageStatus.Sent
                        },
                        replyToText = row.replySnapshot,
                        replyToId = row.replyToId,
                        kind = messageKind(row.kind),
                        mediaPath = row.mediaPath,
                        mediaUrl = mediaUrl,
                        mediaMime = row.mediaMime,
                        mediaSizeBytes = row.mediaSize,
                        durationMs = row.durationMs,
                        edited = row.editedAt != null,
                        deleted = row.deletedAt != null,
                        reactions = reactionsByMessage[row.id].orEmpty()
                    )
                }
            }
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
        val clean = text.trim()
        require(clean.isNotBlank() || mediaPath != null) { "El mensaje está vacío." }
        require(clean.length <= 4000) { "El mensaje no puede superar 4000 caracteres." }

        val me = currentUserId()
        val match = findMatch(targetUserId) ?: error("Solo podés enviar mensajes a un match activo.")
        val settings = runCatching { loadPrivacySettings() }.getOrDefault(PrivacySettings())
        val expiresAt = if (settings.disappearingSeconds > 0) {
            Instant.now().plusSeconds(settings.disappearingSeconds.toLong()).toString()
        } else null

        supabase.from("messages").insert(
            NewMessageRow(
                matchId = match.id,
                senderId = me,
                payload = clean.ifBlank { defaultMediaLabel(kind) },
                kind = kind.dbValue(),
                replyToId = replyToId,
                replySnapshot = replyToText?.take(500),
                mediaPath = mediaPath,
                mediaMime = mediaMime,
                mediaSize = mediaSizeBytes,
                durationMs = durationMs,
                expiresAt = expiresAt,
                clientId = UUID.randomUUID().toString(),
                encryptionVersion = 0
            )
        )
    }

    override suspend fun uploadChatMedia(
        targetUserId: String,
        bytes: ByteArray,
        mimeType: String,
        fileName: String
    ): String {
        require(bytes.isNotEmpty()) { "El archivo está vacío." }
        require(bytes.size <= MAX_CHAT_MEDIA_BYTES) { "El archivo supera el límite actual de 25 MB." }

        val me = currentUserId()
        val match = findMatch(targetUserId) ?: error("Solo podés compartir archivos con un match activo.")
        val safeName = fileName
            .ifBlank { "archivo" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .takeLast(100)
        val path = "${match.id}/$me/${System.currentTimeMillis()}-$safeName"
        val normalizedMime = mimeType.substringBefore(';').trim().ifBlank { "application/octet-stream" }
        supabase.storage.from(CHAT_MEDIA_BUCKET).upload(path, bytes) {
            upsert = false
            contentType = ContentType.parse(normalizedMime)
        }
        return path
    }

    override suspend fun signedChatMediaUrl(path: String): String? {
        if (path.isBlank()) return null
        return supabase.storage.from(CHAT_MEDIA_BUCKET).createSignedUrl(path, 1.hours)
    }

    override suspend fun editMessage(messageId: String, newText: String) {
        val clean = newText.trim()
        require(clean.isNotBlank()) { "El mensaje no puede quedar vacío." }
        require(clean.length <= 4000) { "El mensaje no puede superar 4000 caracteres." }
        supabase.from("messages").update({
            set("payload", clean)
            set("edited_at", Instant.now().toString())
        }) {
            filter { eq("id", messageId) }
        }
    }

    override suspend fun deleteMessage(messageId: String) {
        supabase.from("messages").update({
            set("payload", "Mensaje eliminado")
            set("deleted_at", Instant.now().toString())
            set("media_path", null as String?)
            set("media_mime", null as String?)
        }) {
            filter { eq("id", messageId) }
        }
    }

    override suspend fun reactToMessage(messageId: String, emoji: String) {
        val clean = emoji.trim()
        require(clean.isNotBlank()) { "Elegí una reacción." }
        supabase.from("message_reactions").upsert(
            ReactionRow(messageId = messageId, userId = currentUserId(), emoji = clean.take(16))
        ) { onConflict = "message_id,user_id" }
    }

    override suspend fun markConversationRead(targetUserId: String) {
        val me = currentUserId()
        val match = findMatch(targetUserId) ?: return
        val incoming = supabase.from("messages")
            .select { filter { eq("match_id", match.id) } }
            .decodeList<MessageRow>()
            .filter { it.senderId != me && it.deletedAt == null }
        if (incoming.isEmpty()) return
        val now = Instant.now().toString()
        incoming.forEach { row ->
            supabase.from("message_receipts").upsert(
                ReceiptRow(
                    messageId = row.id,
                    userId = me,
                    deliveredAt = now,
                    readAt = now
                )
            ) { onConflict = "message_id,user_id" }
        }
    }

    override suspend fun blockUser(targetUserId: String) {
        val me = currentUserId()
        supabase.from("blocks").upsert(BlockRow(blockerId = me, blockedId = targetUserId)) {
            onConflict = "blocker_id,blocked_id"
        }
        runCatching {
            supabase.from("likes").delete {
                filter {
                    eq("actor_id", me)
                    eq("target_id", targetUserId)
                }
            }
        }
    }

    override suspend fun reportUser(targetUserId: String, reason: String, details: String) {
        val cleanReason = reason.trim()
        require(cleanReason.length >= 2) { "Indicá un motivo para el reporte." }
        supabase.from("reports").insert(
            ReportRow(
                reporterId = currentUserId(),
                reportedId = targetUserId,
                reason = cleanReason.take(80),
                details = details.trim().take(1500)
            )
        )
    }

    override suspend fun loadPrivacySettings(): PrivacySettings {
        val me = currentUserId()
        return supabase.from("user_preferences")
            .select { filter { eq("user_id", me) } }
            .decodeList<UserPreferencesRow>()
            .firstOrNull()
            ?.toPrivacySettings()
            ?: PrivacySettings()
    }

    override suspend fun savePrivacySettings(settings: PrivacySettings) {
        supabase.from("user_preferences").upsert(
            UserPreferencesRow.from(currentUserId(), settings)
        ) { onConflict = "user_id" }
        if (settings.hideFromPhoneContacts) {
            supabase.postgrest.rpc(
                "set_contact_discoverable",
                kotlinx.serialization.json.buildJsonObject { put("enabled", false) }
            )
        }
    }

    override suspend fun loadDiscoveryPreferences(): DiscoveryPreferences {
        return supabase.from("discovery_preferences")
            .select { filter { eq("user_id", currentUserId()) } }
            .decodeList<DiscoveryPreferencesRow>()
            .firstOrNull()
            ?.toModel()
            ?: DiscoveryPreferences()
    }

    override suspend fun saveDiscoveryPreferences(settings: DiscoveryPreferences) {
        require(settings.minAge in 18..120 && settings.maxAge in settings.minAge..120) {
            "Elegí un rango de edad válido."
        }
        supabase.from("discovery_preferences").upsert(
            DiscoveryPreferencesRow.from(currentUserId(), settings)
        ) { onConflict = "user_id" }
        supabase.from("profiles").update({ set("profile_paused", settings.profilePaused) }) {
            filter { eq("id", currentUserId()) }
        }
    }

    override suspend fun loadSafeDatePlans(): List<SafeDatePlan> {
        return supabase.from("safe_date_plans")
            .select { filter { eq("owner_id", currentUserId()) } }
            .decodeList<SafeDateRow>()
            .sortedByDescending { it.createdAt.orEmpty() }
            .map(SafeDateRow::toModel)
    }

    override suspend fun createSafeDatePlan(plan: SafeDatePlan): SafeDatePlan {
        require(plan.partnerName.isNotBlank()) { "Indicá con quién será la cita." }
        require(plan.place.isNotBlank()) { "Indicá un lugar público para la cita." }
        require(plan.trustedPhone.filter(Char::isDigit).length >= 8) { "Ingresá un teléfono de confianza válido." }
        require(plan.safetyCode.length >= 4) { "Usá un código secreto de al menos 4 caracteres." }
        val saved = plan.copy(id = plan.id.ifBlank { UUID.randomUUID().toString() })
        supabase.from("safe_date_plans").insert(SafeDateRow.from(currentUserId(), saved))
        return saved
    }

    override suspend fun updateSafeDateState(planId: String, state: SafeDateState) {
        supabase.from("safe_date_plans").update({ set("state", state.dbValue()) }) {
            filter {
                eq("id", planId)
                eq("owner_id", currentUserId())
            }
        }
    }

    override suspend fun loadPremiumEntitlements(): PremiumEntitlements {
        return supabase.from("premium_entitlements")
            .select { filter { eq("user_id", currentUserId()) } }
            .decodeList<PremiumEntitlementRow>()
            .firstOrNull()
            ?.toModel()
            ?: PremiumEntitlements()
    }

    override suspend fun isSecureBillingReady(): Boolean {
        return runCatching {
            supabase.functions(
                function = "verify-google-play-purchase",
                body = BillingHealthRequest(),
                headers = Headers.build {
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
            ).body<PurchaseVerificationResponse>().ready
        }.getOrDefault(false)
    }

    override suspend fun verifyGooglePlayPurchase(purchaseToken: String, productIds: List<String>): Boolean {
        require(purchaseToken.isNotBlank()) { "La compra no contiene un comprobante válido." }
        val allowedProducts = productIds.filter { it in NEXO_BILLING_PRODUCTS }.distinct()
        require(allowedProducts.isNotEmpty()) { "El producto de Google Play no pertenece a NEXO." }
        val response = supabase.functions(
            function = "verify-google-play-purchase",
            body = PurchaseVerificationRequest(purchaseToken, allowedProducts),
            headers = Headers.build {
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }
        ).body<PurchaseVerificationResponse>()
        return response.verified
    }

    override suspend fun registerPushToken(token: String) {
        val clean = token.trim()
        if (clean.isBlank()) return
        supabase.from("devices").upsert(
            DeviceRow(userId = currentUserId(), pushToken = clean)
        ) { onConflict = "user_id,push_token" }
    }

    override suspend fun setPresence(online: Boolean) {
        val me = currentUserId()
        val now = Instant.now().toString()
        supabase.from("presence").upsert(
            PresenceRow(userId = me, online = online, lastSeen = now, updatedAt = now)
        ) { onConflict = "user_id" }
        runCatching {
            supabase.from("profiles").update({
                set("is_online", online)
                set("last_seen", now)
            }) { filter { eq("id", me) } }
        }
    }

    override suspend fun loadStatusUpdates(): List<StatusUpdate> {
        val me = currentUserId()
        val rows = supabase.from("status_updates").select().decodeList<StatusRow>()
        val profiles = runCatching {
            supabase.from("profiles").select().decodeList<ProfileRow>().associateBy { it.id }
        }.getOrDefault(emptyMap())
        return rows.sortedByDescending { it.createdAt.orEmpty() }.map { row ->
            StatusUpdate(
                id = row.id,
                ownerId = row.ownerId,
                ownerName = profiles[row.ownerId]?.name ?: if (row.ownerId == me) "Vos" else "NEXO",
                text = row.text,
                kind = messageKind(row.kind),
                mediaPath = row.mediaPath,
                createdAt = row.createdAt,
                expiresAt = row.expiresAt,
                mine = row.ownerId == me
            )
        }
    }

    override suspend fun publishStatus(text: String) {
        val clean = text.trim()
        require(clean.isNotBlank()) { "Escribí algo para publicar tu estado." }
        supabase.from("status_updates").insert(
            NewStatusRow(
                ownerId = currentUserId(),
                text = clean.take(1500),
                kind = "text"
            )
        )
    }

    override suspend fun loadCalls(): List<CallRecord> {
        val me = currentUserId()
        val rows = supabase.from("calls").select().decodeList<CallDbRow>()
        val profiles = runCatching {
            supabase.from("profiles").select().decodeList<ProfileRow>().associateBy { it.id }
        }.getOrDefault(emptyMap())
        return rows.sortedByDescending { it.startedAt.orEmpty() }.map { row ->
            val peerId = if (row.callerId == me) row.calleeId else row.callerId
            CallRecord(
                id = row.id,
                peerId = peerId,
                peerName = profiles[peerId]?.name ?: "NEXO",
                type = if (row.callType == "video") CallType.Video else CallType.Audio,
                state = callState(row.state),
                outgoing = row.callerId == me,
                startedAt = row.startedAt,
                endedAt = row.endedAt
            )
        }
    }

    override suspend fun startCall(targetUserId: String, peerName: String, type: CallType): CallRecord {
        val me = currentUserId()
        val id = UUID.randomUUID().toString()
        val startedAt = Instant.now().toString()
        val row = CallDbRow(
            id = id,
            callerId = me,
            calleeId = targetUserId,
            callType = if (type == CallType.Video) "video" else "audio",
            state = "ringing",
            startedAt = startedAt
        )
        supabase.from("calls").insert(row)
        return CallRecord(
            id = id,
            peerId = targetUserId,
            peerName = peerName,
            type = type,
            state = CallState.Ringing,
            outgoing = true,
            startedAt = startedAt
        )
    }

    override suspend fun endCall(callId: String) {
        supabase.from("calls").update({
            set("state", "ended")
            set("ended_at", Instant.now().toString())
        }) { filter { eq("id", callId) } }
    }

    private suspend fun findMatch(targetUserId: String): MatchRow? {
        val me = currentUserId()
        return supabase.from("matches")
            .select()
            .decodeList<MatchRow>()
            .firstOrNull { row ->
                (row.userA == me && row.userB == targetUserId) ||
                    (row.userA == targetUserId && row.userB == me)
            }
    }

    private fun currentUserId(): String =
        supabase.auth.currentUserOrNull()?.id ?: error("La sesión expiró. Iniciá sesión nuevamente.")

    private companion object {
        const val PROFILE_PHOTOS_BUCKET = "profile-photos"
        const val CHAT_MEDIA_BUCKET = "chat-media"
        const val MAX_PROFILE_PHOTO_BYTES = 5 * 1024 * 1024
        const val MAX_CHAT_MEDIA_BYTES = 25 * 1024 * 1024
        val NEXO_BILLING_PRODUCTS = setOf("nexo_plus_monthly", "nexo_plus_yearly", "nexo_boost_24h")
    }
}

private fun MessageKind.dbValue(): String = name.lowercase()

private fun messageKind(value: String): MessageKind = when (value.lowercase()) {
    "image" -> MessageKind.Image
    "video" -> MessageKind.Video
    "audio" -> MessageKind.Audio
    "document" -> MessageKind.Document
    "location" -> MessageKind.Location
    "contact" -> MessageKind.Contact
    "system" -> MessageKind.System
    else -> MessageKind.Text
}

private fun defaultMediaLabel(kind: MessageKind): String = when (kind) {
    MessageKind.Image -> "Foto"
    MessageKind.Video -> "Video"
    MessageKind.Audio -> "Nota de voz"
    MessageKind.Document -> "Documento"
    MessageKind.Location -> "Ubicación"
    MessageKind.Contact -> "Contacto"
    MessageKind.System -> "Aviso"
    MessageKind.Text -> "Mensaje"
}

private fun callState(value: String): CallState = when (value.lowercase()) {
    "ringing" -> CallState.Ringing
    "connecting" -> CallState.Connecting
    "connected" -> CallState.Connected
    "declined" -> CallState.Declined
    "missed" -> CallState.Missed
    "ended" -> CallState.Ended
    else -> CallState.Failed
}

private fun SafeDateState.dbValue(): String = when (this) {
    SafeDateState.Planned -> "planned"
    SafeDateState.ConfirmedSafe -> "confirmed_safe"
    SafeDateState.Cancelled -> "cancelled"
}

private fun safeDateState(value: String): SafeDateState = when (value.lowercase()) {
    "confirmed_safe" -> SafeDateState.ConfirmedSafe
    "cancelled" -> SafeDateState.Cancelled
    else -> SafeDateState.Planned
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
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("profile_paused") val profilePaused: Boolean = false,
    @SerialName("phone_verified") val phoneVerified: Boolean = false,
    @SerialName("is_online") val isOnline: Boolean = false,
    @SerialName("last_seen") val lastSeen: String? = null
) {
    fun toPersonProfile() = PersonProfile(
        id = id,
        name = name,
        age = age,
        city = city,
        bio = bio,
        intention = intention,
        interests = interests,
        verified = verified,
        photoUrl = photoUrl,
        phoneVerified = phoneVerified,
        isOnline = isOnline,
        lastSeen = lastSeen
    )

    fun toLocalProfile() = LocalUserProfile(
        name = name,
        age = age.toString(),
        city = city,
        bio = bio,
        intention = intention,
        interests = interests,
        photoUrl = photoUrl
    )
}

@Serializable
private data class LikeRow(
    @SerialName("actor_id") val actorId: String,
    @SerialName("target_id") val targetId: String
)

@Serializable
private data class MatchRow(
    val id: String,
    @SerialName("user_a") val userA: String,
    @SerialName("user_b") val userB: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
private data class MessageRow(
    val id: String,
    @SerialName("match_id") val matchId: String,
    @SerialName("sender_id") val senderId: String,
    val payload: String,
    val kind: String = "text",
    @SerialName("reply_to_id") val replyToId: String? = null,
    @SerialName("reply_snapshot") val replySnapshot: String? = null,
    @SerialName("media_path") val mediaPath: String? = null,
    @SerialName("media_mime") val mediaMime: String? = null,
    @SerialName("media_size") val mediaSize: Long? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("edited_at") val editedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("encryption_version") val encryptionVersion: Int = 0,
    val nonce: String? = null,
    @SerialName("sender_key_id") val senderKeyId: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
private data class NewMessageRow(
    @SerialName("match_id") val matchId: String,
    @SerialName("sender_id") val senderId: String,
    val payload: String,
    val kind: String = "text",
    @SerialName("reply_to_id") val replyToId: String? = null,
    @SerialName("reply_snapshot") val replySnapshot: String? = null,
    @SerialName("media_path") val mediaPath: String? = null,
    @SerialName("media_mime") val mediaMime: String? = null,
    @SerialName("media_size") val mediaSize: Long? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("client_id") val clientId: String? = null,
    @SerialName("encryption_version") val encryptionVersion: Int = 0,
    val nonce: String? = null,
    @SerialName("sender_key_id") val senderKeyId: String? = null
)

@Serializable
private data class ReactionRow(
    @SerialName("message_id") val messageId: String,
    @SerialName("user_id") val userId: String,
    val emoji: String
)

@Serializable
private data class ReceiptRow(
    @SerialName("message_id") val messageId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("delivered_at") val deliveredAt: String? = null,
    @SerialName("read_at") val readAt: String? = null
)

@Serializable
private data class BlockRow(
    @SerialName("blocker_id") val blockerId: String,
    @SerialName("blocked_id") val blockedId: String
)

@Serializable
private data class ReportRow(
    @SerialName("reporter_id") val reporterId: String,
    @SerialName("reported_id") val reportedId: String,
    val reason: String,
    val details: String = ""
)

@Serializable
private data class UserPreferencesRow(
    @SerialName("user_id") val userId: String,
    @SerialName("read_receipts") val readReceipts: Boolean = true,
    @SerialName("show_online") val showOnline: Boolean = true,
    @SerialName("show_last_seen") val showLastSeen: Boolean = true,
    @SerialName("allow_calls") val allowCalls: Boolean = true,
    @SerialName("allow_status_replies") val allowStatusReplies: Boolean = true,
    @SerialName("notifications_enabled") val notificationsEnabled: Boolean = true,
    @SerialName("notification_preview") val notificationPreview: Boolean = true,
    @SerialName("disappearing_seconds") val disappearingSeconds: Int = 0,
    @SerialName("hide_from_phone_contacts") val hideFromPhoneContacts: Boolean = false,
    @SerialName("approximate_location_only") val approximateLocationOnly: Boolean = true,
    @SerialName("blur_private_media") val blurPrivateMedia: Boolean = true,
    @SerialName("respectful_reminders") val respectfulReminders: Boolean = true
) {
    fun toPrivacySettings() = PrivacySettings(
        readReceipts = readReceipts,
        showOnline = showOnline,
        showLastSeen = showLastSeen,
        allowCalls = allowCalls,
        allowStatusReplies = allowStatusReplies,
        notificationsEnabled = notificationsEnabled,
        notificationPreview = notificationPreview,
        disappearingSeconds = disappearingSeconds,
        hideFromPhoneContacts = hideFromPhoneContacts,
        approximateLocationOnly = approximateLocationOnly,
        blurPrivateMedia = blurPrivateMedia,
        respectfulReminders = respectfulReminders
    )

    companion object {
        fun from(userId: String, settings: PrivacySettings) = UserPreferencesRow(
            userId = userId,
            readReceipts = settings.readReceipts,
            showOnline = settings.showOnline,
            showLastSeen = settings.showLastSeen,
            allowCalls = settings.allowCalls,
            allowStatusReplies = settings.allowStatusReplies,
            notificationsEnabled = settings.notificationsEnabled,
            notificationPreview = settings.notificationPreview,
            disappearingSeconds = settings.disappearingSeconds,
            hideFromPhoneContacts = settings.hideFromPhoneContacts,
            approximateLocationOnly = settings.approximateLocationOnly,
            blurPrivateMedia = settings.blurPrivateMedia,
            respectfulReminders = settings.respectfulReminders
        )
    }
}

@Serializable
private data class DiscoveryPreferencesRow(
    @SerialName("user_id") val userId: String,
    @SerialName("min_age") val minAge: Int = 18,
    @SerialName("max_age") val maxAge: Int = 60,
    val city: String = "",
    val intention: String = "Todas",
    @SerialName("only_online") val onlyOnline: Boolean = false,
    @SerialName("profile_paused") val profilePaused: Boolean = false
) {
    fun toModel() = DiscoveryPreferences(minAge, maxAge, city, intention, onlyOnline, profilePaused)

    companion object {
        fun from(userId: String, settings: DiscoveryPreferences) = DiscoveryPreferencesRow(
            userId, settings.minAge, settings.maxAge, settings.city.trim(), settings.intention,
            settings.onlyOnline, settings.profilePaused
        )
    }
}

@Serializable
private data class SafeDateRow(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("partner_id") val partnerId: String? = null,
    @SerialName("partner_name") val partnerName: String,
    val place: String,
    @SerialName("check_in_at") val checkInAt: String,
    @SerialName("trusted_name") val trustedName: String,
    @SerialName("trusted_phone") val trustedPhone: String,
    @SerialName("safety_code_hash") val safetyCodeHash: String,
    val state: String = "planned",
    @SerialName("created_at") val createdAt: String? = null
) {
    fun toModel() = SafeDatePlan(
        id, partnerId, partnerName, place, checkInAt, trustedName, trustedPhone,
        "••••", safeDateState(state), createdAt
    )

    companion object {
        fun from(ownerId: String, plan: SafeDatePlan) = SafeDateRow(
            plan.id, ownerId, plan.partnerId, plan.partnerName.trim(), plan.place.trim(),
            plan.checkInAt.trim(), plan.trustedName.trim(), plan.trustedPhone.trim(),
            sha256(plan.safetyCode.trim()), plan.state.dbValue()
        )
    }
}

@Serializable
private data class PremiumEntitlementRow(
    @SerialName("user_id") val userId: String,
    @SerialName("plus_active") val plusActive: Boolean = false,
    @SerialName("plus_expires_at") val plusExpiresAt: String? = null,
    @SerialName("boost_expires_at") val boostExpiresAt: String? = null
) {
    fun toModel(): PremiumEntitlements {
        val notExpired = plusExpiresAt?.let { value ->
            runCatching { Instant.parse(value).isAfter(Instant.now()) }.getOrDefault(false)
        } ?: false
        return PremiumEntitlements(plusActive && notExpired, plusExpiresAt, boostExpiresAt)
    }
}

@Serializable
private data class PurchaseVerificationRequest(
    @SerialName("purchase_token") val purchaseToken: String,
    @SerialName("product_ids") val productIds: List<String>
)

@Serializable
private data class BillingHealthRequest(
    @SerialName("health_check") val healthCheck: Boolean = true
)

@Serializable
private data class PurchaseVerificationResponse(
    val verified: Boolean = false,
    val ready: Boolean = false
)

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }

@Serializable
private data class DeviceRow(
    @SerialName("user_id") val userId: String,
    @SerialName("push_token") val pushToken: String,
    val platform: String = "android",
    val enabled: Boolean = true
)

@Serializable
private data class PresenceRow(
    @SerialName("user_id") val userId: String,
    val online: Boolean,
    @SerialName("last_seen") val lastSeen: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
private data class StatusRow(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val kind: String = "text",
    val text: String = "",
    @SerialName("media_path") val mediaPath: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null
)

@Serializable
private data class NewStatusRow(
    @SerialName("owner_id") val ownerId: String,
    val kind: String = "text",
    val text: String
)

@Serializable
private data class CallDbRow(
    val id: String,
    @SerialName("caller_id") val callerId: String,
    @SerialName("callee_id") val calleeId: String,
    @SerialName("call_type") val callType: String,
    val state: String,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null
)
