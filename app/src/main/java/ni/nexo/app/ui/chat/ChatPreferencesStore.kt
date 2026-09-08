package ni.nexo.app.ui.chat

import android.content.Context

enum class ChatWallpaper {
    Aurora,
    Midnight,
    Carbon
}

enum class ChatBubbleStyle {
    Soft,
    Compact,
    Glass
}

data class ChatPreferences(
    val wallpaper: ChatWallpaper = ChatWallpaper.Aurora,
    val bubbleStyle: ChatBubbleStyle = ChatBubbleStyle.Soft,
    val textScale: Float = 1f,
    val enterToSend: Boolean = false
)

class ChatPreferencesStore(context: Context) {
    private val prefs = context.getSharedPreferences("nexo_chat_preferences", Context.MODE_PRIVATE)

    fun load(): ChatPreferences = ChatPreferences(
        wallpaper = runCatching {
            ChatWallpaper.valueOf(prefs.getString("wallpaper", ChatWallpaper.Aurora.name).orEmpty())
        }.getOrDefault(ChatWallpaper.Aurora),
        bubbleStyle = runCatching {
            ChatBubbleStyle.valueOf(prefs.getString("bubble_style", ChatBubbleStyle.Soft.name).orEmpty())
        }.getOrDefault(ChatBubbleStyle.Soft),
        textScale = prefs.getFloat("text_scale", 1f).coerceIn(0.9f, 1.25f),
        enterToSend = prefs.getBoolean("enter_to_send", false)
    )

    fun save(value: ChatPreferences) {
        prefs.edit()
            .putString("wallpaper", value.wallpaper.name)
            .putString("bubble_style", value.bubbleStyle.name)
            .putFloat("text_scale", value.textScale)
            .putBoolean("enter_to_send", value.enterToSend)
            .apply()
    }
}
