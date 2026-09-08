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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import ni.nexo.app.data.PersonProfile
import ni.nexo.app.ui.components.ProfilePhoto
import ni.nexo.app.ui.theme.NexoPurple

@Composable
fun DiscoveryScreen(
    person: PersonProfile,
    onPass: () -> Unit,
    onLike: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("NEXO", fontSize = 24.sp, fontWeight = FontWeight.Black, color = NexoPurple)
            Text("Privacidad primero", fontSize = 12.sp, color = Color.Gray)
        }
        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                ProfilePhoto(
                    photoUrl = person.photoUrl,
                    name = person.name,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                    backgroundColor = NexoPurple,
                    textColor = Color.White
                )

                Column(Modifier.padding(22.dp)) {
                    Text(
                        text = "${person.name}, ${person.age}${if (person.verified) "  ✓" else ""}",
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text("📍 ${person.city}", color = Color.Gray)
                    Spacer(Modifier.height(10.dp))
                    Text(person.bio, fontSize = 16.sp, lineHeight = 22.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Busca: ${person.intention}", fontWeight = FontWeight.SemiBold, color = NexoPurple)
                    Spacer(Modifier.height(10.dp))
                    Text(person.interests.joinToString("  •  "), color = Color.DarkGray)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onPass,
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFE8EE), contentColor = Color(0xFFCC315F)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) { Text("✕", fontSize = 24.sp) }

            Button(
                onClick = onLike,
                modifier = Modifier.size(74.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = NexoPurple),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) { Text("♥", fontSize = 28.sp) }
        }
        Spacer(Modifier.height(8.dp))
    }
}
