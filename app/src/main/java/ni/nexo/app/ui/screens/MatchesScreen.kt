package ni.nexo.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ni.nexo.app.data.PersonProfile
import ni.nexo.app.ui.theme.NexoPurple

@Composable
fun MatchesScreen(
    matches: List<PersonProfile>,
    onOpenChat: (PersonProfile) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Tus matches", fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("Solo aparecen las conexiones donde hubo interés mutuo.", color = Color.Gray)
        Spacer(Modifier.size(18.dp))

        if (matches.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("♡", fontSize = 58.sp, color = NexoPurple)
                Text("Aún no tenés matches", fontWeight = FontWeight.Bold)
                Text("Dale like a alguien que realmente te interese.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(matches, key = { it.id }) { person ->
                    Surface(
                        tonalElevation = 2.dp,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onOpenChat(person) }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(52.dp),
                                shape = CircleShape,
                                color = NexoPurple.copy(alpha = 0.12f)
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) { Text(person.name.take(1), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = NexoPurple) }
                            }
                            Spacer(Modifier.size(14.dp))
                            Column {
                                Text("${person.name}, ${person.age}", fontWeight = FontWeight.Bold)
                                Text(person.city, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}
