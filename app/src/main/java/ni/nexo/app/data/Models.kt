package ni.nexo.app.data

data class PersonProfile(
    val id: String,
    val name: String,
    val age: Int,
    val city: String,
    val bio: String,
    val intention: String,
    val interests: List<String>,
    val verified: Boolean = false
)

data class LocalUserProfile(
    val name: String = "",
    val age: String = "",
    val city: String = "",
    val bio: String = "",
    val intention: String = "Conocer a alguien de verdad",
    val interests: List<String> = emptyList()
)

data class ChatMessage(
    val id: Int,
    val text: String,
    val fromMe: Boolean
)

data class AuthOutcome(
    val sessionReady: Boolean,
    val message: String
)
