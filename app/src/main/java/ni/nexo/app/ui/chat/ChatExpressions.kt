package ni.nexo.app.ui.chat

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ni.nexo.app.ui.theme.NexoCyan
import ni.nexo.app.ui.theme.NexoMuted
import ni.nexo.app.ui.theme.NexoNight
import ni.nexo.app.ui.theme.NexoNightSoft
import ni.nexo.app.ui.theme.NexoPurple

data class NexoSticker(
    val id: String,
    val emoji: String,
    val title: String,
    val caption: String,
    val colors: List<Color>,
    val premium: Boolean = false
)

private data class EmojiCategory(val icon: String, val label: String, val emojis: List<String>)

private val emojiCategories = listOf(
    EmojiCategory("😀", "Caritas", listOf("😀", "😃", "😄", "😁", "😆", "😂", "🤣", "😊", "🥰", "😍", "😘", "😎", "🤩", "🥳", "😏", "🥹", "😢", "😭", "😡", "🤯", "😱", "🫣", "🤔", "🫡", "🫶", "🙃")),
    EmojiCategory("🫶", "Gestos", listOf("👍", "👎", "👏", "🙌", "🤝", "🙏", "💪", "🤞", "✌️", "🤟", "🤙", "👌", "🫶", "🫰", "👀", "💅", "🕺", "💃", "🫂", "💋")),
    EmojiCategory("❤️", "Amor", listOf("❤️", "🩷", "🧡", "💛", "💚", "🩵", "💙", "💜", "🤎", "🖤", "🤍", "💔", "❤️‍🔥", "💖", "💘", "💝", "💞", "💕", "💌", "🌹")),
    EmojiCategory("🎉", "Momentos", listOf("🎉", "🎊", "🎂", "🎁", "🎈", "✨", "⭐", "🔥", "💯", "🏆", "🥇", "🎵", "🎶", "📸", "🎮", "⚽", "🏖️", "🌙", "☀️", "🌈")),
    EmojiCategory("🍕", "Comida", listOf("☕", "🍻", "🥂", "🍹", "🍕", "🍔", "🌮", "🍟", "🍗", "🍣", "🍰", "🍫", "🍓", "🍉", "🥑", "🌽", "🧀", "🍿", "🧋", "🧊")),
    EmojiCategory("🐶", "Naturaleza", listOf("🐶", "🐱", "🐻", "🐼", "🦁", "🐸", "🦋", "🌻", "🌸", "🌺", "🌴", "🌵", "🍀", "🌊", "⚡", "🌧️", "🌎", "🌙", "☁️", "❄️"))
)

val nexoStickers = listOf(
    NexoSticker("tuani", "😎", "TUANI", "Todo chill", listOf(Color(0xFF00D4FF), Color(0xFF7047EB))),
    NexoSticker("jajaja", "🤣", "JAJAJA", "Me hiciste el día", listOf(Color(0xFFFFB000), Color(0xFFFF4D8D))),
    NexoSticker("dale", "🤝", "¡DALE!", "De una", listOf(Color(0xFF00D68F), Color(0xFF00A8E8))),
    NexoSticker("amor", "🥰", "ME ENCANTA", "Demasiado lindo", listOf(Color(0xFFFF4D8D), Color(0xFF8B5CF6))),
    NexoSticker("buenos_dias", "☀️", "BUENOS DÍAS", "Que sea bonito", listOf(Color(0xFFFFC857), Color(0xFFFF7A00))),
    NexoSticker("descansa", "🌙", "DESCANSÁ", "Hablamos mañana", listOf(Color(0xFF3B3B98), Color(0xFF8B5CF6))),
    NexoSticker("match", "💘", "QUÉ MATCH", "Hay conexión", listOf(Color(0xFFFF2D95), Color(0xFF7C3AED)), premium = true),
    NexoSticker("fuego", "🔥", "ESTÁ BRUTAL", "Nivel fuego", listOf(Color(0xFFFF7A00), Color(0xFFFF1744)), premium = true),
    NexoSticker("plan", "🕺", "SALE PLAN", "¿Cuándo y dónde?", listOf(Color(0xFF00E5FF), Color(0xFFFF2D95)), premium = true),
    NexoSticker("pensando", "🫣", "A VER…", "Contame más", listOf(Color(0xFF6D5DFB), Color(0xFF00C2FF)), premium = true),
    NexoSticker("gracias", "🫶", "GRACIAS", "Sos lo máximo", listOf(Color(0xFFFF5EA8), Color(0xFF7B61FF)), premium = true),
    NexoSticker("nexo", "⚡", "HAY NEXO", "Esto promete", listOf(Color(0xFF00E5FF), Color(0xFFB026FF), Color(0xFFFF2D95)), premium = true)
)

fun findNexoSticker(id: String): NexoSticker? = nexoStickers.firstOrNull { it.id == id }

class EmojiRecentsStore(context: Context) {
    private val prefs = context.getSharedPreferences("nexo_emoji_recents", Context.MODE_PRIVATE)

    fun load(): List<String> = prefs.getString("items", "")
        .orEmpty().split('|').filter(String::isNotBlank).take(24)

    fun remember(emoji: String): List<String> {
        val updated = (listOf(emoji) + load().filterNot { it == emoji }).take(24)
        prefs.edit().putString("items", updated.joinToString("|")).apply()
        return updated
    }
}

@Composable
fun ChatExpressionPanel(
    recentEmojis: List<String>,
    premiumEnabled: Boolean,
    onEmoji: (String) -> Unit,
    onSticker: (NexoSticker) -> Unit,
    onUpgrade: () -> Unit
) {
    var stickersSelected by remember { mutableStateOf(false) }
    var categoryIndex by remember { mutableStateOf(0) }

    Surface(color = NexoNight.copy(alpha = 0.99f), shadowElevation = 12.dp) {
        Column(Modifier.fillMaxWidth().height(310.dp).padding(top = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpressionTab("Emojis", !stickersSelected, Icons.Rounded.EmojiEmotions) { stickersSelected = false }
                ExpressionTab("Stickers", stickersSelected, Icons.Rounded.AutoAwesome) { stickersSelected = true }
            }
            Spacer(Modifier.height(8.dp))
            if (stickersSelected) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(nexoStickers, key = { it.id }) { sticker ->
                        Box {
                            NexoStickerCard(
                                sticker = sticker,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (sticker.premium && !premiumEnabled) onUpgrade() else onSticker(sticker)
                                }
                            )
                            if (sticker.premium && !premiumEnabled) {
                                Surface(color = Color.Black.copy(alpha = 0.7f), shape = CircleShape, modifier = Modifier.align(Alignment.TopEnd).padding(7.dp)) {
                                    Icon(Icons.Rounded.Lock, "Requiere Plus", tint = NexoCyan, modifier = Modifier.padding(5.dp).size(14.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().height(39.dp).padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (recentEmojis.isNotEmpty()) {
                        item {
                            CategoryButton("", "Recientes", categoryIndex == -1) { categoryIndex = -1 }
                        }
                    }
                    items(emojiCategories.indices.toList()) { index ->
                        CategoryButton(emojiCategories[index].icon, emojiCategories[index].label, categoryIndex == index) { categoryIndex = index }
                    }
                }
                Spacer(Modifier.height(8.dp))
                val shown = if (categoryIndex == -1) recentEmojis else emojiCategories[categoryIndex].emojis
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(shown) { emoji ->
                        Box(
                            modifier = Modifier.size(44.dp).clickable { onEmoji(emoji) },
                            contentAlignment = Alignment.Center
                        ) { Text(emoji, fontSize = 27.sp, textAlign = TextAlign.Center) }
                    }
                }
            }
        }
    }
}

@Composable
fun NexoStickerCard(sticker: NexoSticker, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Color.Transparent, shape = RoundedCornerShape(22.dp)) {
        Column(
            modifier = Modifier.background(Brush.linearGradient(sticker.colors)).padding(horizontal = 12.dp, vertical = 13.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(sticker.emoji, fontSize = 38.sp)
            Text(sticker.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp, textAlign = TextAlign.Center)
            Text(sticker.caption, color = Color.White.copy(alpha = 0.82f), fontSize = 9.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun RowScope.ExpressionTab(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) NexoPurple.copy(alpha = 0.55f) else NexoNightSoft,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.weight(1f).clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(vertical = 9.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (selected) NexoCyan else NexoMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(label, color = if (selected) Color.White else NexoMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CategoryButton(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) NexoPurple.copy(alpha = 0.5f) else NexoNightSoft,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon.isBlank()) Icon(Icons.Rounded.History, null, tint = NexoCyan, modifier = Modifier.size(17.dp))
            else Text(icon, fontSize = 16.sp)
            Spacer(Modifier.size(5.dp))
            Text(label, color = if (selected) Color.White else NexoMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
