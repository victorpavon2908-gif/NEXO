package ni.nexo.app.data

import java.time.Instant

data class PersonProfile(
    val id: String,
    val name: String,
    val age: Int,
    val city: String,
    val bio: String,
    val intention: String,
    val interests: List<String>,
    val verified: Boolean = false,
    val photoUrl: String? = null,
    val phoneVerified: Boolean = false,
    val isOnline: Boolean = false,
    val lastSeen: String? = null
)

data class LocalUserProfile(
    val name: String = "",
    val age: String = "",
    val city: String = "",
    val bio: String = "",
    val intention: String = "Conocer a alguien de verdad",
    val interests: List<String> = emptyList(),
    val photoUrl: String? = null
)

data class PresenceInfo(
    val userId: String,
    val online: Boolean = false,
    val lastSeen: String? = null
)

data class ContactSettings(
    val phoneE164: String? = null,
    val phoneVerified: Boolean = false,
    val discoverable: Boolean = false
)

data class ContactMatch(
    val phoneHash: String,
    val profile: PersonProfile
)

data class GroupSummary(
    val id: String,
    val name: String,
    val memberCount: Int = 1,
    val photoUrl: String? = null,
    val ownerId: String? = null,
    val role: String = "member",
    val updatedAt: String? = null,
    val lastMessage: String? = null
)

enum class MessageStatus {
    Sending,
    Sent,
    Delivered,
    Read,
    Failed
}

enum class MessageKind {
    Text,
    Image,
    Video,
    Audio,
    Document,
    Location,
    Contact,
    System
}

data class ChatMessage(
    val id: String,
    val text: String,
    val fromMe: Boolean,
    val createdAt: String? = null,
    val encryptionVersion: Int = 0,
    val status: MessageStatus = if (fromMe) MessageStatus.Read else MessageStatus.Delivered,
    val replyToText: String? = null,
    val replyToId: String? = null,
    val kind: MessageKind = MessageKind.Text,
    val mediaPath: String? = null,
    val mediaUrl: String? = null,
    val mediaMime: String? = null,
    val mediaSizeBytes: Long? = null,
    val durationMs: Long? = null,
    val edited: Boolean = false,
    val deleted: Boolean = false,
    val reactions: Map<String, Int> = emptyMap(),
    val senderId: String? = null,
    val senderName: String? = null
)

data class AuthOutcome(
    val sessionReady: Boolean,
    val message: String
)

data class PrivacySettings(
    val readReceipts: Boolean = true,
    val showOnline: Boolean = true,
    val showLastSeen: Boolean = true,
    val allowCalls: Boolean = true,
    val allowStatusReplies: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val notificationPreview: Boolean = true,
    val disappearingSeconds: Int = 0,
    val hideFromPhoneContacts: Boolean = false,
    val approximateLocationOnly: Boolean = true,
    val blurPrivateMedia: Boolean = true,
    val respectfulReminders: Boolean = true
)

data class DiscoveryPreferences(
    val minAge: Int = 18,
    val maxAge: Int = 60,
    val city: String = "",
    val intention: String = "Todas",
    val onlyOnline: Boolean = false,
    val profilePaused: Boolean = false
)

enum class SafeDateState {
    Planned,
    ConfirmedSafe,
    Cancelled
}

data class SafeDatePlan(
    val id: String,
    val partnerId: String? = null,
    val partnerName: String,
    val place: String,
    val checkInAt: String,
    val trustedName: String,
    val trustedPhone: String,
    val safetyCode: String,
    val state: SafeDateState = SafeDateState.Planned,
    val createdAt: String? = null
)

data class PremiumEntitlements(
    val plusActive: Boolean = false,
    val plusExpiresAt: String? = null,
    val boostExpiresAt: String? = null
) {
    val advancedFilters: Boolean get() = plusActive
    val premiumChatThemes: Boolean get() = plusActive
    val boostActive: Boolean
        get() = boostExpiresAt?.let { value ->
            runCatching { Instant.parse(value).isAfter(Instant.now()) }.getOrDefault(false)
        } ?: false
}

data class StatusUpdate(
    val id: String,
    val ownerId: String,
    val ownerName: String = "NEXO",
    val text: String,
    val kind: MessageKind = MessageKind.Text,
    val mediaPath: String? = null,
    val mediaUrl: String? = null,
    val mediaMime: String? = null,
    val ownerPhotoUrl: String? = null,
    val createdAt: String? = null,
    val expiresAt: String? = null,
    val mine: Boolean = false
)

enum class CallType {
    Audio,
    Video
}

enum class CallState {
    Ringing,
    Connecting,
    Connected,
    Declined,
    Missed,
    Ended,
    Failed
}

data class CallRecord(
    val id: String,
    val peerId: String,
    val peerName: String,
    val type: CallType,
    val state: CallState,
    val outgoing: Boolean,
    val startedAt: String? = null,
    val endedAt: String? = null
)
