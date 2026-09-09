package ni.nexo.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface NexoRepository {
    val configured: Boolean
    val liveCallsAvailable: Boolean get() = false

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
    suspend fun sendMessage(
        targetUserId: String,
        text: String,
        replyToText: String? = null,
        replyToId: String? = null,
        kind: MessageKind = MessageKind.Text,
        mediaPath: String? = null,
        mediaMime: String? = null,
        mediaSizeBytes: Long? = null,
        durationMs: Long? = null
    )

    suspend fun uploadChatMedia(
        targetUserId: String,
        bytes: ByteArray,
        mimeType: String,
        fileName: String
    ): String

    suspend fun signedChatMediaUrl(path: String): String? = null
    suspend fun editMessage(messageId: String, newText: String) = Unit
    suspend fun deleteMessage(messageId: String) = Unit
    suspend fun reactToMessage(messageId: String, emoji: String) = Unit
    suspend fun markConversationRead(targetUserId: String) = Unit

    suspend fun blockUser(targetUserId: String) = Unit
    suspend fun reportUser(targetUserId: String, reason: String, details: String = "") = Unit

    suspend fun loadPrivacySettings(): PrivacySettings = PrivacySettings()
    suspend fun savePrivacySettings(settings: PrivacySettings) = Unit
    suspend fun registerPushToken(token: String) = Unit
    suspend fun setPresence(online: Boolean) = Unit
    suspend fun observePresence(userId: String): Flow<PresenceInfo> =
        flowOf(PresenceInfo(userId = userId))

    // Teléfono verificado y descubrimiento de contactos.
    suspend fun loadContactSettings(): ContactSettings = ContactSettings()
    suspend fun requestPhoneVerification(phoneE164: String): Unit {
        error("La verificación de teléfono requiere Supabase real.")
    }
    suspend fun verifyPhoneCode(phoneE164: String, code: String): Boolean = false
    suspend fun setContactDiscoveryEnabled(enabled: Boolean) = Unit
    suspend fun findContactsByHashes(phoneHashes: List<String>): List<ContactMatch> = emptyList()
    suspend fun startContactConversation(phoneHash: String) = Unit

    // Estados / novedades.
    suspend fun loadStatusUpdates(): List<StatusUpdate> = emptyList()
    suspend fun publishStatus(text: String) = Unit
    suspend fun publishStatus(
        text: String,
        kind: MessageKind,
        mediaPath: String?,
        mediaMime: String? = null
    ) {
        publishStatus(text)
    }
    suspend fun uploadStatusMedia(bytes: ByteArray, mimeType: String, fileName: String): String =
        error("La multimedia de estados requiere Supabase real.")

    // Grupos.
    suspend fun loadGroups(): List<GroupSummary> = emptyList()
    suspend fun createGroup(name: String, memberIds: List<String>): GroupSummary =
        error("Los grupos requieren Supabase real.")
    suspend fun observeGroupMessages(groupId: String): Flow<List<ChatMessage>> = flowOf(emptyList())
    suspend fun sendGroupMessage(
        groupId: String,
        text: String,
        kind: MessageKind = MessageKind.Text,
        mediaPath: String? = null,
        mediaMime: String? = null,
        mediaSizeBytes: Long? = null,
        durationMs: Long? = null,
        replyToText: String? = null,
        replyToId: String? = null
    ) = Unit
    suspend fun uploadGroupMedia(
        groupId: String,
        bytes: ByteArray,
        mimeType: String,
        fileName: String
    ): String = error("Los archivos de grupo requieren Supabase real.")

    suspend fun loadCalls(): List<CallRecord> = emptyList()
    suspend fun startCall(targetUserId: String, peerName: String, type: CallType): CallRecord =
        CallRecord(
            id = "not-configured",
            peerId = targetUserId,
            peerName = peerName,
            type = type,
            state = CallState.Failed,
            outgoing = true
        )
    suspend fun endCall(callId: String) = Unit
}
