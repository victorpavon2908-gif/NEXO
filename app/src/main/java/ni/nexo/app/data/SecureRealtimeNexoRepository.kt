package ni.nexo.app.data

import android.util.Base64
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperation
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.selectAsFlow
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Capa RC10 para comunicación privada real.
 *
 * Conserva el repositorio existente y reemplaza solamente los caminos donde
 * necesitamos E2EE y señalización WebRTC. De esta forma no se duplica auth,
 * perfiles, contactos, estados, grupos ni monetización.
 */
class SecureRealtimeNexoRepository(
    private val base: NexoRepository,
    private val supabase: SupabaseClient
) : NexoRepository by base {

    override val liveCallsAvailable: Boolean = true

    private val json = Json { ignoreUnknownKeys = true }
    private val pendingMedia = ConcurrentHashMap<String, PendingMediaCrypto>()
    private val inlineImageCache = ConcurrentHashMap<String, String>()

    override suspend fun ensureMessagingIdentity(): Boolean {
        val me = currentUserId()
        val identity = E2eeCrypto.ensureIdentity(me)
        val now = Instant.now().toString()
        supabase.from("e2ee_identity_keys").upsert(
            IdentityKeyRow(
                userId = me,
                keyId = identity.keyId,
                publicKey = identity.publicKeyBase64,
                updatedAt = now
            )
        ) { onConflict = "user_id" }
        return true
    }

    override suspend fun setPresence(online: Boolean) {
        base.setPresence(online)
        if (online) runCatching { ensureMessagingIdentity() }
    }

    @OptIn(SupabaseExperimental::class)
    override suspend fun observeMessages(targetUserId: String): Flow<List<ChatMessage>> {
        val me = currentUserId()
        val match = findMatch(targetUserId)
            ?: error("Solo podés conversar con un match activo.")

        return supabase.from("messages")
            .selectAsFlow<SecureMessageRow, String>(
                primaryKey = SecureMessageRow::id,
                channelName = "nexo-secure-messages-${match.id}",
                filter = FilterOperation("match_id", FilterOperator.EQ, match.id)
            )
            .map { rows ->
                val reactionRows = runCatching {
                    supabase.from("message_reactions").select().decodeList<SecureReactionRow>()
                }.getOrDefault(emptyList())
                val reactionsByMessage = reactionRows
                    .groupBy { it.messageId }
                    .mapValues { (_, values) -> values.groupingBy { it.emoji }.eachCount() }

                val receiptRows = runCatching {
                    supabase.from("message_receipts").select().decodeList<SecureReceiptRow>()
                }.getOrDefault(emptyList())
                val peerReceiptByMessage = receiptRows
                    .asSequence()
                    .filter { it.userId != me }
                    .associateBy { it.messageId }

                rows.sortedBy { it.createdAt.orEmpty() }.map { row ->
                    val deleted = row.deletedAt != null
                    val content = when {
                        deleted -> E2eeMessageContent(text = "Mensaje eliminado")
                        row.encryptionVersion == 1 -> decryptMessageContent(me, row)
                        else -> E2eeMessageContent(row.payload, row.replySnapshot)
                    }
                    val mediaUrl = resolveMediaUrl(me, row)
                    val peerReceipt = peerReceiptByMessage[row.id]

                    ChatMessage(
                        id = row.id,
                        text = content.text,
                        fromMe = row.senderId == me,
                        createdAt = row.createdAt,
                        encryptionVersion = row.encryptionVersion,
                        status = when {
                            row.senderId != me -> MessageStatus.Delivered
                            peerReceipt?.readAt != null -> MessageStatus.Read
                            peerReceipt?.deliveredAt != null -> MessageStatus.Delivered
                            else -> MessageStatus.Sent
                        },
                        replyToText = content.replySnapshot,
                        replyToId = row.replyToId,
                        kind = secureMessageKind(row.kind),
                        mediaPath = row.mediaPath,
                        mediaUrl = mediaUrl,
                        mediaMime = row.mediaMime,
                        mediaSizeBytes = row.mediaSize,
                        durationMs = row.durationMs,
                        edited = row.editedAt != null,
                        deleted = deleted,
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
        val match = findMatch(targetUserId)
            ?: error("Solo podés enviar mensajes a un match activo.")
        val senderIdentity = ensureAndLoadOwnIdentity(me)
        val recipientIdentity = loadIdentity(targetUserId)
            ?: error("La otra persona todavía no activó su identidad segura de NEXO. Pedile que abra la versión actualizada de la app.")

        val content = E2eeMessageContent(
            text = clean.ifBlank { secureMediaLabel(kind) },
            replySnapshot = replyToText?.take(500)
        )
        val encrypted = E2eeCrypto.encryptText(
            json.encodeToString(content),
            senderIdentity.publicKey,
            recipientIdentity.publicKey
        )
        val settings = runCatching { base.loadPrivacySettings() }.getOrDefault(PrivacySettings())
        val expiresAt = if (settings.disappearingSeconds > 0) {
            Instant.now().plusSeconds(settings.disappearingSeconds.toLong()).toString()
        } else null
        val mediaCrypto = mediaPath?.let { pendingMedia[it] }
        val messageId = UUID.randomUUID().toString()

        supabase.from("messages").insert(
            NewSecureMessageRow(
                id = messageId,
                matchId = match.id,
                senderId = me,
                payload = encrypted.ciphertextBase64,
                kind = secureDbKind(kind),
                replyToId = replyToId,
                replySnapshot = null,
                mediaPath = mediaPath,
                mediaMime = mediaMime,
                mediaSize = mediaSizeBytes,
                durationMs = durationMs,
                expiresAt = expiresAt,
                clientId = UUID.randomUUID().toString(),
                encryptionVersion = 1,
                nonce = encrypted.nonceBase64,
                senderKeyId = senderIdentity.keyId,
                recipientKeyId = recipientIdentity.keyId,
                wrappedKeySender = encrypted.wrappedKeySenderBase64,
                wrappedKeyRecipient = encrypted.wrappedKeyRecipientBase64,
                mediaEncryptionVersion = if (mediaCrypto != null) 1 else 0,
                mediaNonce = mediaCrypto?.nonce,
                mediaWrappedKeySender = mediaCrypto?.wrappedSender,
                mediaWrappedKeyRecipient = mediaCrypto?.wrappedRecipient
            )
        )
        if (mediaPath != null) pendingMedia.remove(mediaPath)
        sendPushEvent("message", messageId)
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
        val match = findMatch(targetUserId)
            ?: error("Solo podés compartir archivos con un match activo.")
        val senderIdentity = ensureAndLoadOwnIdentity(me)
        val recipientIdentity = loadIdentity(targetUserId)
            ?: error("La otra persona todavía no activó su identidad segura de NEXO.")

        val encrypted = E2eeCrypto.encryptBytes(
            bytes,
            senderIdentity.publicKey,
            recipientIdentity.publicKey
        )
        val safeName = fileName
            .ifBlank { "archivo" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .takeLast(100)
        val path = "${match.id}/$me/${System.currentTimeMillis()}-$safeName.e2ee"

        supabase.storage.from(CHAT_MEDIA_BUCKET).upload(path, encrypted.ciphertext) {
            upsert = false
            contentType = ContentType.Application.OctetStream
        }
        pendingMedia[path] = PendingMediaCrypto(
            nonce = encrypted.nonceBase64,
            wrappedSender = encrypted.wrappedKeySenderBase64,
            wrappedRecipient = encrypted.wrappedKeyRecipientBase64
        )
        return path
    }

    override suspend fun editMessage(messageId: String, newText: String) {
        val clean = newText.trim()
        require(clean.isNotBlank()) { "El mensaje no puede quedar vacío." }
        require(clean.length <= 4000) { "El mensaje no puede superar 4000 caracteres." }

        val me = currentUserId()
        val row = supabase.from("messages")
            .select { filter { eq("id", messageId) } }
            .decodeList<SecureMessageRow>()
            .firstOrNull() ?: error("El mensaje ya no existe.")
        require(row.senderId == me) { "Solo podés editar tus propios mensajes." }

        if (row.encryptionVersion != 1) {
            base.editMessage(messageId, clean)
            return
        }

        val match = findMatchById(row.matchId) ?: error("La conversación ya no está disponible.")
        val peerId = if (match.userA == me) match.userB else match.userA
        val senderIdentity = ensureAndLoadOwnIdentity(me)
        val recipientIdentity = loadIdentity(peerId)
            ?: error("No encontramos la clave segura del receptor.")
        val oldContent = decryptMessageContent(me, row)
        val encrypted = E2eeCrypto.encryptText(
            json.encodeToString(oldContent.copy(text = clean)),
            senderIdentity.publicKey,
            recipientIdentity.publicKey
        )

        supabase.from("messages").update({
            set("payload", encrypted.ciphertextBase64)
            set("nonce", encrypted.nonceBase64)
            set("sender_key_id", senderIdentity.keyId)
            set("recipient_key_id", recipientIdentity.keyId)
            set("wrapped_key_sender", encrypted.wrappedKeySenderBase64)
            set("wrapped_key_recipient", encrypted.wrappedKeyRecipientBase64)
            set("edited_at", Instant.now().toString())
        }) { filter { eq("id", messageId) } }
    }

    override suspend fun signedChatMediaUrl(path: String): String? = base.signedChatMediaUrl(path)

    override suspend fun startCall(targetUserId: String, peerName: String, type: CallType): CallRecord {
        findMatch(targetUserId) ?: error("Solo podés llamar a un match activo.")
        val prefs = runCatching {
            supabase.from("user_preferences")
                .select { filter { eq("user_id", targetUserId) } }
                .decodeList<CallPreferenceRow>()
                .firstOrNull()
        }.getOrNull()
        if (prefs?.allowCalls == false) error("Esta persona no está aceptando llamadas.")
        val call = base.startCall(targetUserId, peerName, type)
        sendPushEvent("call", call.id)
        return call
    }

    override suspend fun acceptCall(callId: String) {
        val me = currentUserId()
        val row = loadCallRow(callId) ?: error("La llamada ya no está disponible.")
        require(row.calleeId == me) { "Solo el receptor puede aceptar esta llamada." }
        require(row.state == "ringing") { "La llamada ya cambió de estado." }
        supabase.from("calls").update({
            set("state", "connecting")
            set("answered_at", Instant.now().toString())
        }) { filter { eq("id", callId) } }
    }

    override suspend fun declineCall(callId: String) {
        val me = currentUserId()
        val row = loadCallRow(callId) ?: return
        require(row.calleeId == me) { "Solo el receptor puede rechazar esta llamada." }
        if (row.state !in setOf("ringing", "connecting")) return
        runCatching { sendCallSignal(callId, CallSignalType.Hangup, "{\"reason\":\"declined\"}") }
        supabase.from("calls").update({
            set("state", "declined")
            set("ended_at", Instant.now().toString())
        }) { filter { eq("id", callId) } }
    }

    override suspend fun markCallConnected(callId: String) {
        val row = loadCallRow(callId) ?: return
        if (row.state in setOf("ended", "declined", "missed", "failed")) return
        supabase.from("calls").update({
            set("state", "connected")
            if (row.answeredAt == null) set("answered_at", Instant.now().toString())
        }) { filter { eq("id", callId) } }
    }

    override suspend fun endCall(callId: String) {
        val row = loadCallRow(callId)
        if (row == null || row.state in setOf("ended", "declined", "missed", "failed")) return
        runCatching { sendCallSignal(callId, CallSignalType.Hangup, "{\"reason\":\"hangup\"}") }
        base.endCall(callId)
        runCatching {
            supabase.postgrest.rpc(
                "nexo_cleanup_call_signals",
                kotlinx.serialization.json.buildJsonObject {
                    put("target_call", callId)
                }
            )
        }
    }

    override suspend fun sendCallSignal(callId: String, type: CallSignalType, payload: String) {
        val me = currentUserId()
        val parsed = json.parseToJsonElement(payload) as? JsonObject
            ?: error("La señal WebRTC no tiene un JSON válido.")
        supabase.from("call_signals").insert(
            NewCallSignalRow(
                callId = callId,
                senderId = me,
                signalType = type.dbValue(),
                payload = parsed
            )
        )
    }

    override suspend fun loadCallSignals(callId: String, afterId: Long): List<CallSignal> {
        val me = currentUserId()
        return supabase.from("call_signals")
            .select { filter { eq("call_id", callId) } }
            .decodeList<CallSignalRow>()
            .asSequence()
            .filter { it.id > afterId && it.senderId != me }
            .sortedBy { it.id }
            .map { row ->
                CallSignal(
                    id = row.id,
                    type = callSignalType(row.signalType),
                    payload = row.payload.toString(),
                    createdAt = row.createdAt
                )
            }
            .toList()
    }

    private suspend fun resolveMediaUrl(me: String, row: SecureMessageRow): String? {
        val path = row.mediaPath ?: return null
        if (row.mediaEncryptionVersion != 1) {
            return runCatching { base.signedChatMediaUrl(path) }.getOrNull()
        }
        if (secureMessageKind(row.kind) != MessageKind.Image) return null
        inlineImageCache[path]?.let { return it }

        val wrapped = if (row.senderId == me) row.mediaWrappedKeySender else row.mediaWrappedKeyRecipient
        if (row.mediaNonce.isNullOrBlank() || wrapped.isNullOrBlank()) return null
        return runCatching {
            val encrypted = supabase.storage.from(CHAT_MEDIA_BUCKET).downloadAuthenticated(path)
            val clear = E2eeCrypto.decryptBytes(me, encrypted, row.mediaNonce, wrapped)
            val mime = row.mediaMime?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
            "data:$mime;base64,${Base64.encodeToString(clear, Base64.NO_WRAP)}"
        }.getOrNull()?.also { inlineImageCache[path] = it }
    }

    private fun decryptMessageContent(me: String, row: SecureMessageRow): E2eeMessageContent {
        val wrapped = if (row.senderId == me) row.wrappedKeySender else row.wrappedKeyRecipient
        if (row.nonce.isNullOrBlank() || wrapped.isNullOrBlank()) {
            return E2eeMessageContent("🔒 Mensaje cifrado no disponible en este dispositivo")
        }
        return runCatching {
            val plaintext = E2eeCrypto.decryptText(me, row.payload, row.nonce, wrapped)
            json.decodeFromString<E2eeMessageContent>(plaintext)
        }.getOrElse {
            E2eeMessageContent("🔒 No pudimos descifrar este mensaje en este dispositivo")
        }
    }

    private suspend fun ensureAndLoadOwnIdentity(me: String): IdentityKeyRow {
        val local = E2eeCrypto.ensureIdentity(me)
        val now = Instant.now().toString()
        val row = IdentityKeyRow(me, local.keyId, local.publicKeyBase64, updatedAt = now)
        supabase.from("e2ee_identity_keys").upsert(row) { onConflict = "user_id" }
        return row
    }

    private suspend fun loadIdentity(userId: String): IdentityKeyRow? =
        supabase.from("e2ee_identity_keys")
            .select { filter { eq("user_id", userId) } }
            .decodeList<IdentityKeyRow>()
            .firstOrNull()

    private suspend fun findMatch(targetUserId: String): SecureMatchRow? {
        val me = currentUserId()
        return supabase.from("matches").select().decodeList<SecureMatchRow>().firstOrNull { row ->
            (row.userA == me && row.userB == targetUserId) ||
                (row.userA == targetUserId && row.userB == me)
        }
    }

    private suspend fun findMatchById(matchId: String): SecureMatchRow? =
        supabase.from("matches")
            .select { filter { eq("id", matchId) } }
            .decodeList<SecureMatchRow>()
            .firstOrNull()

    private suspend fun loadCallRow(callId: String): SecureCallRow? =
        supabase.from("calls")
            .select { filter { eq("id", callId) } }
            .decodeList<SecureCallRow>()
            .firstOrNull()

    private suspend fun sendPushEvent(eventType: String, eventId: String) {
        runCatching {
            supabase.functions.invoke(
                function = "nexo-push",
                body = kotlinx.serialization.json.buildJsonObject {
                    put("event_type", eventType)
                    put(if (eventType == "message") "message_id" else "call_id", eventId)
                }
            )
        }
    }

    private fun currentUserId(): String =
        supabase.auth.currentUserOrNull()?.id ?: error("La sesión expiró. Iniciá sesión nuevamente.")

    private companion object {
        const val CHAT_MEDIA_BUCKET = "chat-media"
        const val MAX_CHAT_MEDIA_BYTES = 25 * 1024 * 1024
    }
}

@Serializable
private data class E2eeMessageContent(
    val text: String,
    val replySnapshot: String? = null
)

@Serializable
private data class IdentityKeyRow(
    @SerialName("user_id") val userId: String,
    @SerialName("key_id") val keyId: String,
    @SerialName("public_key") val publicKey: String,
    val algorithm: String = "RSA-OAEP-256",
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
private data class SecureMatchRow(
    val id: String,
    @SerialName("user_a") val userA: String,
    @SerialName("user_b") val userB: String
)

@Serializable
private data class SecureMessageRow(
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
    @SerialName("encryption_version") val encryptionVersion: Int = 0,
    val nonce: String? = null,
    @SerialName("sender_key_id") val senderKeyId: String? = null,
    @SerialName("recipient_key_id") val recipientKeyId: String? = null,
    @SerialName("wrapped_key_sender") val wrappedKeySender: String? = null,
    @SerialName("wrapped_key_recipient") val wrappedKeyRecipient: String? = null,
    @SerialName("media_encryption_version") val mediaEncryptionVersion: Int = 0,
    @SerialName("media_nonce") val mediaNonce: String? = null,
    @SerialName("media_wrapped_key_sender") val mediaWrappedKeySender: String? = null,
    @SerialName("media_wrapped_key_recipient") val mediaWrappedKeyRecipient: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
private data class NewSecureMessageRow(
    val id: String,
    @SerialName("match_id") val matchId: String,
    @SerialName("sender_id") val senderId: String,
    val payload: String,
    val kind: String,
    @SerialName("reply_to_id") val replyToId: String? = null,
    @SerialName("reply_snapshot") val replySnapshot: String? = null,
    @SerialName("media_path") val mediaPath: String? = null,
    @SerialName("media_mime") val mediaMime: String? = null,
    @SerialName("media_size") val mediaSize: Long? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("client_id") val clientId: String,
    @SerialName("encryption_version") val encryptionVersion: Int,
    val nonce: String,
    @SerialName("sender_key_id") val senderKeyId: String,
    @SerialName("recipient_key_id") val recipientKeyId: String,
    @SerialName("wrapped_key_sender") val wrappedKeySender: String,
    @SerialName("wrapped_key_recipient") val wrappedKeyRecipient: String,
    @SerialName("media_encryption_version") val mediaEncryptionVersion: Int = 0,
    @SerialName("media_nonce") val mediaNonce: String? = null,
    @SerialName("media_wrapped_key_sender") val mediaWrappedKeySender: String? = null,
    @SerialName("media_wrapped_key_recipient") val mediaWrappedKeyRecipient: String? = null
)

@Serializable
private data class SecureReactionRow(
    @SerialName("message_id") val messageId: String,
    @SerialName("user_id") val userId: String,
    val emoji: String
)

@Serializable
private data class SecureReceiptRow(
    @SerialName("message_id") val messageId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("delivered_at") val deliveredAt: String? = null,
    @SerialName("read_at") val readAt: String? = null
)

@Serializable
private data class CallPreferenceRow(
    @SerialName("allow_calls") val allowCalls: Boolean = true
)

@Serializable
private data class SecureCallRow(
    val id: String,
    @SerialName("caller_id") val callerId: String,
    @SerialName("callee_id") val calleeId: String,
    @SerialName("call_type") val callType: String,
    val state: String,
    @SerialName("answered_at") val answeredAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null
)

@Serializable
private data class CallSignalRow(
    val id: Long,
    @SerialName("call_id") val callId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("signal_type") val signalType: String,
    val payload: JsonObject,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
private data class NewCallSignalRow(
    @SerialName("call_id") val callId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("signal_type") val signalType: String,
    val payload: JsonObject
)

private data class PendingMediaCrypto(
    val nonce: String,
    val wrappedSender: String,
    val wrappedRecipient: String
)

private fun secureDbKind(kind: MessageKind): String = kind.name.lowercase()

private fun secureMessageKind(value: String): MessageKind = when (value.lowercase()) {
    "image" -> MessageKind.Image
    "video" -> MessageKind.Video
    "audio" -> MessageKind.Audio
    "document" -> MessageKind.Document
    "location" -> MessageKind.Location
    "contact" -> MessageKind.Contact
    "sticker" -> MessageKind.Sticker
    "system" -> MessageKind.System
    else -> MessageKind.Text
}

private fun secureMediaLabel(kind: MessageKind): String = when (kind) {
    MessageKind.Image -> "Foto"
    MessageKind.Video -> "Video"
    MessageKind.Audio -> "Nota de voz"
    MessageKind.Document -> "Documento"
    MessageKind.Location -> "Ubicación"
    MessageKind.Contact -> "Contacto"
    MessageKind.Sticker -> "Sticker"
    MessageKind.System -> "Aviso"
    MessageKind.Text -> "Mensaje"
}

private fun CallSignalType.dbValue(): String = when (this) {
    CallSignalType.Offer -> "offer"
    CallSignalType.Answer -> "answer"
    CallSignalType.Ice -> "ice"
    CallSignalType.Hangup -> "hangup"
}

private fun callSignalType(value: String): CallSignalType = when (value.lowercase()) {
    "offer" -> CallSignalType.Offer
    "answer" -> CallSignalType.Answer
    "ice" -> CallSignalType.Ice
    else -> CallSignalType.Hangup
}
