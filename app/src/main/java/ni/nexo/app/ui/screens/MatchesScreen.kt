package ni.nexo.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ni.nexo.app.data.PersonProfile
import ni.nexo.app.ui.components.NexoBackdrop
import ni.nexo.app.ui.components.NexoWordmark
import ni.nexo.app.ui.components.ProfilePhoto
import ni.nexo.app.ui.theme.NexoCyan
import ni.nexo.app.ui.theme.NexoMuted
import ni.nexo.app.ui.theme.NexoNightSoft
import ni.nexo.app.ui.theme.NexoPink
import ni.nexo.app.ui.theme.NexoPurple

@Composable
fun MatchesScreen(
    matches: List<PersonProfile>,
    onOpenChat: (PersonProfile) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val visible = remember(matches, query) {
        matches.filter { person ->
            query.isBlank() || person.name.contains(query, true) ||
                person.city.contains(query, true) || person.interests.any { it.contains(query, true) }
        }.sortedWith(compareByDescending<PersonProfile> { it.isOnline }.thenBy { it.name })
    }

    NexoBackdrop {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NexoWordmark(compact = true)
                Spacer(Modifier.weight(1f))
                Surface(color = NexoPurple.copy(alpha = 0.25f), shape = RoundedCornerShape(50)) {
                    Text(
                        "${matches.size} ${if (matches.size == 1) "conexión" else "conexiones"}",
                        color = NexoCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Conexiones", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("Interés mutuo, sin solicitudes incómodas.", color = NexoMuted, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))

            if (matches.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(60) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar conexión") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = NexoCyan,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
                        focusedPlaceholderColor = NexoMuted,
                        unfocusedPlaceholderColor = NexoMuted,
                        cursorColor = NexoCyan
                    )
                )
                Spacer(Modifier.height(10.dp))
            }

            when {
                matches.isEmpty() -> ConnectionsEmpty()
                visible.isEmpty() -> ConnectionsSearchEmpty()
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visible, key = { it.id }) { person ->
                        Surface(
                            color = NexoNightSoft.copy(alpha = 0.88f),
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.fillMaxWidth().clickable { onOpenChat(person) }
                        ) {
                            Row(Modifier.padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                ProfilePhoto(person.photoUrl, person.name, Modifier.size(56.dp), CircleShape, NexoPurple, Color.White)
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${person.name}, ${person.age}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        if (person.verified || person.phoneVerified) {
                                            Spacer(Modifier.size(5.dp))
                                            Icon(Icons.Rounded.Verified, "Verificado", tint = NexoCyan, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Text(
                                        if (person.isOnline) "en línea" else person.city,
                                        color = if (person.isOnline) NexoCyan else NexoMuted,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        person.interests.take(2).joinToString(" · ").ifBlank { person.intention },
                                        color = NexoMuted.copy(alpha = 0.82f),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onOpenChat(person) }) {
                                    Icon(Icons.Rounded.Chat, contentDescription = "Conversar", tint = NexoCyan)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionsEmpty() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(color = NexoPurple.copy(alpha = 0.20f), shape = CircleShape) {
            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = NexoPink, modifier = Modifier.padding(22.dp).size(36.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("Todavía no hay conexiones", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(4.dp))
        Text("Cuando el interés sea mutuo aparecerán aquí.", color = NexoMuted, fontSize = 12.sp)
    }
}

@Composable
private fun ConnectionsSearchEmpty() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No encontramos esa conexión", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Probá buscando por nombre, ciudad o interés.", color = NexoMuted, fontSize = 11.sp)
    }
}
