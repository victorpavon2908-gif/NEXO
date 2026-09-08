package ni.nexo.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ni.nexo.app.data.ChatMessage
import ni.nexo.app.data.NexoRepository
import ni.nexo.app.data.PersonProfile
import ni.nexo.app.ui.theme.NexoPurple

@Composable
fun ChatScreen(
    person: PersonProfile,
    repository: NexoRepository
) {
    val scope = rememberCoroutineScope()
    var messages by remember(person.id) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var draft by remember(person.id) { mutableStateOf("") }
    var loading by remember(person.id) { mutableStateOf(true) }
    var sending by remember(person.id) { mutableStateOf(false) }
    var errorMessage by remember(person.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(person.id, repository) {
        loading = true
        errorMessage = null
        try {
            repository.observeMessages(person.id).collectLatest { fresh ->
                messages = fresh
                loading = false
            }
        } catch (error: Exception) {
            loading = false
            errorMessage = error.message ?: "No pudimos cargar la conversación."
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text(person.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("● Mensajes persistentes · tiempo real", fontSize = 12.sp, color = Color(0xFF16835A))
            Text("Cifrado E2E preparado, todavía no activado en 0.3", fontSize = 11.sp, color = Color.Gray)
            if (!errorMessage.isNullOrBlank()) {
                Text(errorMessage!!, fontSize = 12.sp, color = Color(0xFFB42318))
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (loading) {
                item { Text("Cargando conversación…", color = Color.Gray) }
            } else if (messages.isEmpty()) {
                item {
                    Text(
                        "Todavía no hay mensajes. Podés empezar la conversación cuando quieras.",
                        color = Color.Gray
                    )
                }
            } else {
                items(messages, key = { it.id }) { message ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            color = if (message.fromMe) NexoPurple else Color(0xFFF0EEF7),
                            contentColor = if (message.fromMe) Color.White else Color(0xFF17152A),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth(0.78f)
                        ) {
                            Text(message.text, modifier = Modifier.padding(13.dp), lineHeight = 20.sp)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(4000) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Escribe un mensaje…") },
                shape = RoundedCornerShape(18.dp),
                maxLines = 3
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val text = draft.trim()
                    if (text.isNotEmpty() && !sending) {
                        sending = true
                        errorMessage = null
                        scope.launch {
                            try {
                                repository.sendMessage(person.id, text)
                                draft = ""
                            } catch (error: Exception) {
                                errorMessage = error.message ?: "No pudimos enviar el mensaje."
                            } finally {
                                sending = false
                            }
                        }
                    }
                },
                enabled = draft.isNotBlank() && !sending
            ) {
                Text(if (sending) "…" else "Enviar")
            }
        }
    }
}
