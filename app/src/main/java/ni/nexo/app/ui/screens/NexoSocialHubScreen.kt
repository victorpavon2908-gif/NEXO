package ni.nexo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HowToVote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ni.nexo.app.data.PersonProfile
import ni.nexo.app.ui.theme.NexoCyan
import ni.nexo.app.ui.theme.NexoMuted
import ni.nexo.app.ui.theme.NexoNight
import ni.nexo.app.ui.theme.NexoSurface

private enum class SocialSection(val title: String) {
    Feed("Para ti"), Challenges("Retos"), Versus("Versus"), Circles("Círculos"), AI("NEXO AI"), Stories("Historias")
}

/** Unified NEXO social layer: feed, challenges, Versus, private circles, AI and interactive stories. */
@Composable
fun NexoSocialHubScreen(
    person: PersonProfile,
    myInterests: List<String>,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onFilters: () -> Unit,
    onPass: () -> Unit,
    onLike: () -> Unit,
    onUpgrade: () -> Unit = {}
) {
    var section by remember { mutableStateOf(SocialSection.Feed) }
    val stories = remember { listOf("Tu historia", "Ana", "Carlos", "María", "Equipo ⚽") }
    val circles = remember { listOf("Amigos", "Familia", "Trabajo", "Universidad", "Gaming") }
    val shared = person.interests.filter { mine -> myInterests.any { it.equals(mine, true) } }

    Column(Modifier.fillMaxSize().background(NexoNight).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("NEXO", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                Text("Conecta · participa · crea", color = NexoMuted)
            }
            AssistChip(onClick = onUpgrade, label = { Text("✦ PLUS") }, leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) })
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            AssistChip(onClick = onSearch, label = { Text("Buscar") }, leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) })
            AssistChip(onClick = onRefresh, label = { Text("Actualizar") }, leadingIcon = { Icon(Icons.Rounded.Bolt, null) })
            AssistChip(onClick = onFilters, label = { Text("Filtros") }, leadingIcon = { Icon(Icons.Rounded.Tune, null) })
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(stories) { story -> Surface(shape = MaterialTheme.shapes.large, color = NexoSurface, onClick = {}) { Text("◉ $story", color = Color.White, modifier = Modifier.padding(12.dp)) } }
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SocialSection.entries) { item ->
                AssistChip(onClick = { section = item }, label = { Text(item.title) }, leadingIcon = {
                    Icon(when (item) {
                        SocialSection.Feed -> Icons.Rounded.PlayArrow
                        SocialSection.Challenges -> Icons.Rounded.Bolt
                        SocialSection.Versus -> Icons.Rounded.HowToVote
                        SocialSection.Circles -> Icons.Rounded.Groups
                        SocialSection.AI -> Icons.Rounded.AutoAwesome
                        SocialSection.Stories -> Icons.Rounded.History
                    }, null)
                })
            }
        }
        Spacer(Modifier.height(12.dp))

        when (section) {
            SocialSection.Feed -> {
                SocialCard("🔥 Reto del día", "Mostrá algo que tengas cerca que casi nadie conozca.", "Participar")
                SocialCard("⚔️ Batalla activa", "¿Qué publicación recibe más votos? Participá y compartí.", "Votar")
                SocialCard("👥 Tu comunidad", "Hay actividad nueva en tus círculos privados.", "Abrir")
            }
            SocialSection.Challenges -> {
                SocialCard("🎯 Desafío diario", "Completá 3 micro-retos y desbloqueá una insignia.", "Comenzar")
                SocialCard("🔥 Racha", "Mantené tu actividad diaria y acumulá logros.", "Continuar")
                SocialCard("🎁 Recompensa", "Podés ver publicidad voluntaria a cambio de recompensas virtuales.", "Reclamar")
            }
            SocialSection.Versus -> {
                SocialCard("⚔️ VERSUS", "Personas, clips, outfits, gaming, música y deportes.", "Entrar")
                SocialCard("🏆 Temporada 01", "Victorias · votos · batallas y estadísticas de actividad.", "Ver estadísticas")
                SocialCard("🎬 ClipBattle", "Dos clips. Un voto. Una nueva ronda.", "Votar")
            }
            SocialSection.Circles -> {
                circles.forEach { circle -> SocialCard("👥 $circle", "Feed privado · chat · fotos · retos · eventos", "Abrir") }
                Button(onClick = {}) { Text("+ Crear círculo") }
            }
            SocialSection.AI -> {
                SocialCard("✨ Crear con NEXO AI", "Generá publicaciones, encuestas, retos, ideas y respuestas.", "Crear")
                SocialCard("🧠 Mi asistente", "Ideas para contenido y ayuda para responder, siempre bajo tu control.", "Probar")
                SocialCard("💎 NEXO AI Plus", "Más generaciones y herramientas para creadores.", "Ver Plus", onUpgrade)
            }
            SocialSection.Stories -> {
                SocialCard("📖 Historia interactiva", "La comunidad decide qué ocurre en el siguiente episodio.", "Continuar")
                SocialCard("🎭 Tu historia", "Creá una escena y dejá que otros decidan el siguiente paso.", "Crear")
            }
        }

        Spacer(Modifier.height(10.dp))
        SocialCard("🤝 Conexión sugerida", "${person.name}, ${person.age} · ${person.city.ifBlank { "NEXO" }}${if (shared.isNotEmpty()) " · En común: ${shared.joinToString(" · ")}" else ""}", "Me gusta", onLike)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Button(onClick = onPass) { Text("Pasar") }
        }
        Spacer(Modifier.height(8.dp))
        Surface(color = NexoSurface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Tu actividad NEXO", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("🔥 Racha 7 días · ⚔️ 12 victorias · 🎯 28 retos", color = NexoMuted)
            }
        }
    }
}

@Composable
private fun SocialCard(title: String, body: String, action: String, onClick: () -> Unit = {}) {
    Card(Modifier.fillMaxWidth().padding(bottom = 9.dp), colors = CardDefaults.cardColors(containerColor = NexoSurface)) {
        Column(Modifier.padding(15.dp)) {
            Text(title, color = if (title.contains("AI")) NexoCyan else Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(5.dp))
            Text(body, color = NexoMuted)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onClick) { Icon(Icons.Rounded.ThumbUp, null); Spacer(Modifier.padding(2.dp)); Text(action) }
        }
    }
}
