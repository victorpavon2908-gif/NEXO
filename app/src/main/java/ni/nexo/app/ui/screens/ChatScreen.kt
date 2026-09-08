package ni.nexo.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ni.nexo.app.data.ChatMessage
import ni.nexo.app.data.FakeNexoRepository
import ni.nexo.app.data.PersonProfile
import ni.nexo.app.ui.theme.NexoPurple

@Composable
fun ChatScreen(person: PersonProfile) {
    val messages = remember(person.id) {
        mutableStateListOf<ChatMessage>().apply { addAll(FakeNexoRepository.starterMessages(person)) }
    }
    var draft by remember(person.id) { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text(person.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("🔒 Chat preparado para cifrado E2E", fontSize = 12.sp, color = Color(0xFF16835A))
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
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

        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Escribe un mensaje…") },
                shape = RoundedCornerShape(18.dp),
                maxLines = 3
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        messages.add(ChatMessage(messages.size + 100, text, fromMe = true))
                        draft = ""
                    }
                },
                enabled = draft.isNotBlank()
            ) {
                Text("Enviar")
            }
        }
    }
}
