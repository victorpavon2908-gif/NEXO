package ni.nexo.app.data

import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.selectAsFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Funciones de NEXO 1.1.1 que separan el concepto de match romántico de una
 * conversación aceptada. Los mensajes siguen pasando por NexoRepository para
 * conservar E2EE; este servicio solo administra descubrimiento y permisos.
 */
object DiscoveryMessagingService {

    val available: Boolean
        get() = SupabaseClientProvider.client != null

    suspend fun searchPeople(query: String): List<SearchPersonResult> {
        val clean = query.trim()
        if (clean.length < 2) return emptyList()
        val client = SupabaseClientProvider.client ?: return emptyList()
        return client.postgrest.rpc(
            "search_nexo_people",
            buildJsonObject { put("search_text", clean) }
        ).decodeList<SearchPersonRow>().map(SearchPersonRow::toModel)
    }

    suspend fun incomingRequests(): List<MessageRequestSummary> {
        val client = SupabaseClientProvider.client ?: return emptyList()
        return client.postgrest.rpc("list_nexo_message_requests")
            .decodeList<MessageRequestRow>()
            .map { MessageRequestSummary(it.toProfile(), it.requestedAt) }
    }

    suspend fun startRequest(targetUserId: String): ConversationAccess {
        val client = SupabaseClientProvider.client
            ?: error("Conectá Supabase para iniciar una conversación.")
        return client.postgrest.rpc(
            "start_nexo_message_request",
            buildJsonObject { put("target_user", targetUserId) }
        ).decodeList<ConversationAccessRow>()
            .firstOrNull()
            ?.toModel()
            ?: error("No pudimos preparar la conversación.")
    }

    suspend fun acceptRequest(targetUserId: String) {
        val client = SupabaseClientProvider.client ?: return
        client.postgrest.rpc(
            "accept_nexo_message_request",
            buildJsonObject { put("target_user", targetUserId) }
        )
    }

    suspend fun rejectRequest(targetUserId: String) {
        val client = SupabaseClientProvider.client ?: return
        client.postgrest.rpc(
            "reject_nexo_message_request",
            buildJsonObject { put("target_user", targetUserId) }
        )
    }

    suspend fun blockRequest(targetUserId: String) {
        val client = SupabaseClientProvider.client ?: return
        client.postgrest.rpc(
            "block_nexo_message_request",
            buildJsonObject { put("target_user", targetUserId) }
        )
    }

    suspend fun allowUnknownRequests(): Boolean {
        val client = SupabaseClientProvider.client ?: return true
        return client.postgrest.rpc("get_nexo_message_privacy")
            .decodeList<MessagePrivacyRow>()
            .firstOrNull()
            ?.allowMessageRequests
            ?: true
    }

    suspend fun setAllowUnknownRequests(enabled: Boolean) {
        val client = SupabaseClientProvider.client ?: return
        client.postgrest.rpc(
            "set_nexo_message_privacy",
            buildJsonObject { put("enabled", enabled) }
        )
    }

    suspend fun acceptedPeerIds(): Set<String> {
        val client = SupabaseClientProvider.client ?: return emptySet()
        return client.postgrest.rpc("list_nexo_accepted_peer_ids")
            .decodeList<PeerIdRow>()
            .mapTo(linkedSetOf()) { it.peerId }
    }

    suspend fun isMutualMatch(targetUserId: String): Boolean {
        val client = SupabaseClientProvider.client ?: return false
        return client.postgrest.rpc(
            "nexo_is_mutual_match",
            buildJsonObject { put("target_user", targetUserId) }
        ).decodeList<MutualMatchRow>()
            .firstOrNull()
            ?.isMatch
            ?: false
    }

    /**
     * Emite cuando cambia la tabla de conexiones visible para el usuario.
     * Supabase RLS limita las filas a aquellas en las que participa la sesión.
     */
    @OptIn(SupabaseExperimental::class)
    fun observeConnectionChanges(): Flow<Unit> {
        val client = SupabaseClientProvider.client ?: return flowOf(Unit)
        return client.from("matches")
            .selectAsFlow<ConversationPulseRow, String>(
                primaryKey = ConversationPulseRow::id,
                channelName = "nexo-connection-pulse"
            )
            .map { Unit }
    }

    fun signedIn(): Boolean = SupabaseClientProvider.client?.auth?.currentSessionOrNull() != null
}

data class SearchPersonResult(
    val profile: PersonProfile,
    val access: ConversationAccess
)

data class MessageRequestSummary(
    val profile: PersonProfile,
    val requestedAt: String? = null
)

data class ConversationAccess(
    val state: String,
    val relationshipKind: String? = null,
    val requestedByMe: Boolean = false,
    val canStartMessage: Boolean = false,
    val actionLabel: String = "Enviar mensaje"
) {
    val accepted: Boolean get() = state == "accepted"
    val pendingOutgoing: Boolean get() = state == "pending_outgoing"
    val pendingIncoming: Boolean get() = state == "pending_incoming"
    val matchOnly: Boolean get() = state == "match_only"
}

@Serializable
private data class ConversationAccessRow(
    val state: String,
    @SerialName("relationship_kind") val relationshipKind: String? = null,
    @SerialName("requested_by_me") val requestedByMe: Boolean = false,
    @SerialName("can_start_message") val canStartMessage: Boolean = false,
    @SerialName("action_label") val actionLabel: String = "Enviar mensaje"
) {
    fun toModel() = ConversationAccess(
        state = state,
        relationshipKind = relationshipKind,
        requestedByMe = requestedByMe,
        canStartMessage = canStartMessage,
        actionLabel = actionLabel
    )
}

@Serializable
private data class SearchPersonRow(
    val id: String,
    val name: String,
    val age: Int,
    val city: String,
    val bio: String = "",
    val intention: String = "",
    val interests: List<String> = emptyList(),
    val verified: Boolean = false,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("phone_verified") val phoneVerified: Boolean = false,
    @SerialName("is_online") val isOnline: Boolean = false,
    @SerialName("last_seen") val lastSeen: String? = null,
    @SerialName("access_state") val accessState: String,
    @SerialName("relationship_kind") val relationshipKind: String? = null,
    @SerialName("requested_by_me") val requestedByMe: Boolean = false,
    @SerialName("can_start_message") val canStartMessage: Boolean = false,
    @SerialName("action_label") val actionLabel: String = "Enviar mensaje"
) {
    fun toModel() = SearchPersonResult(
        profile = PersonProfile(
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
        ),
        access = ConversationAccess(
            state = accessState,
            relationshipKind = relationshipKind,
            requestedByMe = requestedByMe,
            canStartMessage = canStartMessage,
            actionLabel = actionLabel
        )
    )
}

@Serializable
private data class MessageRequestRow(
    val id: String,
    val name: String,
    val age: Int,
    val city: String,
    val bio: String = "",
    val intention: String = "",
    val interests: List<String> = emptyList(),
    val verified: Boolean = false,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("phone_verified") val phoneVerified: Boolean = false,
    @SerialName("is_online") val isOnline: Boolean = false,
    @SerialName("last_seen") val lastSeen: String? = null,
    @SerialName("requested_at") val requestedAt: String? = null
) {
    fun toProfile() = PersonProfile(
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
}

@Serializable
private data class MessagePrivacyRow(
    @SerialName("allow_message_requests") val allowMessageRequests: Boolean = true
)

@Serializable
private data class PeerIdRow(
    @SerialName("peer_id") val peerId: String
)

@Serializable
private data class MutualMatchRow(
    @SerialName("is_match") val isMatch: Boolean
)

@Serializable
private data class ConversationPulseRow(
    val id: String,
    @SerialName("user_a") val userA: String,
    @SerialName("user_b") val userB: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("relationship_kind") val relationshipKind: String = "match",
    @SerialName("conversation_state") val conversationState: String = "accepted",
    @SerialName("requested_by") val requestedBy: String? = null,
    @SerialName("accepted_at") val acceptedAt: String? = null
)
