package ni.nexo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ni.nexo.app.data.PersonProfile
import ni.nexo.app.ui.components.NexoBrandGradient
import ni.nexo.app.ui.components.NexoLogoMark
import ni.nexo.app.ui.components.NexoWordmark
import ni.nexo.app.ui.components.ProfilePhoto
import ni.nexo.app.ui.theme.NexoCyan
import ni.nexo.app.ui.theme.NexoMuted
import ni.nexo.app.ui.theme.NexoNight
import ni.nexo.app.ui.theme.NexoPink
import ni.nexo.app.ui.theme.NexoPurple
import ni.nexo.app.ui.theme.NexoSurface

@Composable
fun DiscoveryScreen(
    person: PersonProfile,
    onPass: () -> Unit,
    onLike: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexoNight)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NexoLogoMark(Modifier.size(35.dp))
            Spacer(Modifier.size(8.dp))
            NexoWordmark(compact = true)
            Spacer(Modifier.weight(1f))
            Text("Privacidad primero", fontSize = 11.sp, color = NexoMuted)
        }
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(NexoSurface)
                .border(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            NexoCyan.copy(alpha = 0.42f),
                            NexoPurple.copy(alpha = 0.30f),
                            NexoPink.copy(alpha = 0.42f)
                        )
                    ),
                    RoundedCornerShape(32.dp)
                )
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    ProfilePhoto(
                        photoUrl = person.photoUrl,
                        name = person.name,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                        backgroundColor = NexoPurple,
                        textColor = Color.White
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xE607091A))
                                )
                            )
                            .padding(start = 20.dp, end = 20.dp, top = 54.dp, bottom = 18.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${person.name}, ${person.age}",
                                    color = Color.White,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black
                                )
                                if (person.verified) {
                                    Spacer(Modifier.size(7.dp))
                                    Icon(
                                        Icons.Rounded.Verified,
                                        contentDescription = "Perfil verificado",
                                        tint = NexoCyan,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            }
                            Text(person.city, color = NexoMuted, fontSize = 13.sp)
                        }
                    }
                }

                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(
                        person.bio.ifBlank { "Conocé un poco más antes de decidir." },
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 21.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Busca: ${person.intention}",
                        color = NexoCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    if (person.interests.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            person.interests.joinToString("  •  "),
                            color = NexoMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onPass,
                modifier = Modifier.size(62.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF242942),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Pasar", modifier = Modifier.size(27.dp))
            }

            Spacer(Modifier.size(24.dp))

            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(NexoBrandGradient),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = onLike,
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = "Me gusta",
                        tint = Color.White,
                        modifier = Modifier.size(31.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}
