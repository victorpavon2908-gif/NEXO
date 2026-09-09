package ni.nexo.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ni.nexo.app.ui.components.NexoBackdrop
import ni.nexo.app.ui.theme.NexoCyan
import ni.nexo.app.ui.theme.NexoMuted
import ni.nexo.app.ui.theme.NexoNightSoft
import ni.nexo.app.ui.theme.NexoPurple

@Composable
fun NexoPlusScreen(onBack: () -> Unit) {
    NexoBackdrop {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Volver", tint = Color.White) }
                Text("NEXO Plus", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(18.dp))
            Surface(color = NexoPurple.copy(alpha = 0.28f), shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Diamond, null, tint = NexoCyan)
                    Spacer(Modifier.height(8.dp))
                    Text("Más control, no más presión", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black)
                    Text("NEXO nunca cobrará por tu seguridad ni por responder un mensaje.", color = NexoMuted, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            listOf(
                "Filtros avanzados de descubrimiento",
                "Modo invisible para explorar con discreción",
                "Ver quién mostró interés",
                "Perfil destacado sin ocultar perfiles gratuitos",
                "Más opciones para personalizar chats"
            ).forEach { feature ->
                Surface(color = NexoNightSoft.copy(alpha = 0.84f), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = NexoCyan)
                        Spacer(Modifier.padding(5.dp))
                        Text(feature, color = Color.White, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Surface(color = NexoNightSoft, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Próximamente. El cobro se habilitará únicamente cuando exista una pasarela verificada y precios transparentes.", color = NexoMuted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(16.dp))
            }
        }
    }
}
