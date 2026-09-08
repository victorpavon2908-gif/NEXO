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

data class ChatMessage(
    val id: String,
    val text: String,
    val fromMe: Boolean,
    val createdAt: String? = null,
    val encryptionVersion: Int = 0
)

data class AuthOutcome(
    val sessionReady: Boolean,
    val message: String
)
