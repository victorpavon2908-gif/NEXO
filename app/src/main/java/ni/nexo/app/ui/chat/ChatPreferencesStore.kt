package ni.nexo.app.ui.chat

import android.content.Context

enum class ChatWallpaper {
    Aurora,
    Midnight,
    Carbon,
    Sunset,
    Ocean,
    Sakura
}

enum class ChatBubbleStyle {
    Soft,
    Compact,
    Glass,
    Neon,
    Minimal
}

enum class ChatAccent {
    Cyan,
    Purple,
    Pink,
    Lime,
    Sunset
}

data class ChatPreferences(
    val wallpaper: ChatWallpaper = ChatWallpaper.Aurora,
    val bubbleStyle: ChatBubbleStyle = ChatBubbleStyle.Soft,
    val accent: ChatAccent = ChatAccent.Cyan,
    val textScale: Float = 1f,
    val enterToSend: Boolean = false
)

class ChatPreferencesStore(context: Context) {
    private val prefs = context.getSharedPreferences("nexo_chat_preferences", Context.MODE_PRIVATE)

    fun load(conversationId: String? = null): ChatPreferences {
        val prefix = keyPrefix(conversationId)
        fun stringValue(key: String, fallback: String): String =
            prefs.getString("${prefix}_$key", prefs.getString(key, fallback)).orEmpty()

        return ChatPreferences(
            wallpaper = runCatching {
                ChatWallpaper.valueOf(stringValue("wallpaper", ChatWallpaper.Aurora.name))
            }.getOrDefault(ChatWallpaper.Aurora),
            bubbleStyle = runCatching {
                ChatBubbleStyle.valueOf(stringValue("bubble_style", ChatBubbleStyle.Soft.name))
            }.getOrDefault(ChatBubbleStyle.Soft),
            accent = runCatching {
                ChatAccent.valueOf(stringValue("accent", ChatAccent.Cyan.name))
            }.getOrDefault(ChatAccent.Cyan),
            textScale = prefs.getFloat("${prefix}_text_scale", prefs.getFloat("text_scale", 1f)).coerceIn(0.9f, 1.25f),
            enterToSend = prefs.getBoolean("${prefix}_enter_to_send", prefs.getBoolean("enter_to_send", false))
        )
    }

    fun save(value: ChatPreferences, conversationId: String? = null) {
        val prefix = keyPrefix(conversationId)
        prefs.edit()
            .putString("${prefix}_wallpaper", value.wallpaper.name)
            .putString("${prefix}_bubble_style", value.bubbleStyle.name)
            .putString("${prefix}_accent", value.accent.name)
            .putFloat("${prefix}_text_scale", value.textScale)
            .putBoolean("${prefix}_enter_to_send", value.enterToSend)
            .apply()
    }

    private fun keyPrefix(conversationId: String?): String = conversationId
        ?.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        ?.take(80)
        ?.takeIf(String::isNotBlank)
        ?: "global"
}
