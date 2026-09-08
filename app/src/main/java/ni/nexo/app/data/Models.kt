package ni.nexo.app.data

data class PersonProfile(
    val id: String,
    val name: String,
    val age: Int,
    val city: String,
    val bio: String,
    val intention: String,
    val interests: List<String>,
    val verified: Boolean = false,
    val photoUrl: String? = null
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
    val reactions: Map<String, Int> = emptyMap()
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
    val disappearingSeconds: Int = 0
)

data class StatusUpdate(
    val id: String,
    val ownerId: String,
    val ownerName: String = "NEXO",
    val text: String,
    val kind: MessageKind = MessageKind.Text,
    val mediaPath: String? = null,
    val mediaUrl: String? = null,
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