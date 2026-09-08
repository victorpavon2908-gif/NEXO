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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
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
import ni.nexo.app.ui.theme.NexoPurple

@Composable
fun ChatListScreen(
    matches: List<PersonProfile>,
    demoMode: Boolean,
    onOpenChat: (PersonProfile) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Todos") }

    val visible = remember(matches, query, filter) {
        matches.filter { person ->
            val matchesQuery = query.isBlank() ||
                person.name.contains(query, ignoreCase = true) ||
                person.city.contains(query, ignoreCase = true)
            val matchesFilter = when (filter) {
                "Verificados" -> person.verified
                else -> true
            }
            matchesQuery && matchesFilter
        }
    }

    NexoBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NexoWordmark(compact = true)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { }) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = "Cámara", tint = Color.White)
                }
                IconButton(onClick = { }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Más", tint = Color.White)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Mensajes",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Conversaciones privadas, rápidas y a tu estilo.",
                color = NexoMuted,
                fontSize = 13.sp
            )

            if (demoMode) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = NexoPurple.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "MODO PRUEBA · Valentina responderá automáticamente para que pruebes el chat.",
                        color = NexoCyan,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar chats") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = NexoCyan,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                    focusedContainerColor = NexoNightSoft.copy(alpha = 0.8f),
                    unfocusedContainerColor = NexoNightSoft.copy(alpha = 0.68f),
                    focusedPlaceholderColor = NexoMuted,
                    unfocusedPlaceholderColor = NexoMuted,
                    focusedLeadingIconColor = NexoCyan,
                    unfocusedLeadingIconColor = NexoMuted
                )
            )

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Todos", "No leídos", "Verificados").forEach { label ->
                    Surface(
                        color = if (filter == label) NexoPurple.copy(alpha = 0.35f) else NexoNightSoft.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.clickable { filter = label }
                    ) {
                        Text(
                            text = label,
                            color = if (filter == label) Color.White else NexoMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Surface(
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Archive, contentDescription = null, tint = NexoCyan)
                    Spacer(Modifier.width(14.dp))
                    Text("Archivados", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("0", color = NexoMuted, fontSize = 12.sp)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (visible.isEmpty()) {
                    item {
                        Text(
                            text = if (matches.isEmpty())
                                "Todavía no tenés conversaciones. Cuando hagas match aparecerán aquí."
                            else
                                "No encontramos chats con ese filtro.",
                            color = NexoMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 28.dp)
                        )
                    }
                } else {
                    items(visible, key = { it.id }) { person ->
                        ChatListItem(person = person, onClick = { onOpenChat(person) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatListItem(person: PersonProfile, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfilePhoto(
            photoUrl = person.photoUrl,
            name = person.name,
            modifier = Modifier.size(58.dp),
            shape = CircleShape,
            backgroundColor = NexoPurple,
            textColor = Color.White
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = person.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (person.verified) {
                    Spacer(Modifier.width(5.dp))
                    Text("✓", color = NexoCyan, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${person.city} · Tocá para abrir la conversación",
                color = NexoMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("ahora", color = NexoCyan, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Surface(color = NexoCyan, shape = CircleShape) {
                Text(
                    text = "1",
                    color = Color(0xFF05101A),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }
    }
}
