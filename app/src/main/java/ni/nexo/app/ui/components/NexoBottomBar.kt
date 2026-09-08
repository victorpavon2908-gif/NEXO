package ni.nexo.app.ui.components

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import ni.nexo.app.ui.NexoDestination

private data class NavItem(
    val destination: NexoDestination,
    val symbol: String,
    val label: String
)

@Composable
fun NexoBottomBar(
    selected: NexoDestination,
    onSelect: (NexoDestination) -> Unit
) {
    val items = listOf(
        NavItem(NexoDestination.Discover, "◇", "Descubrir"),
        NavItem(NexoDestination.Matches, "♡", "Matches"),
        NavItem(NexoDestination.Chat, "◌", "Chat"),
        NavItem(NexoDestination.Profile, "●", "Perfil")
    )

    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                selected = selected == item.destination,
                onClick = { onSelect(item.destination) },
                icon = { Text(item.symbol, fontWeight = FontWeight.Bold) },
                label = { Text(item.label) }
            )
        }
    }
}
