package ni.nexo.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ni.nexo.app.ui.NexoDestination
import ni.nexo.app.ui.theme.NexoCyan
import ni.nexo.app.ui.theme.NexoMuted
import ni.nexo.app.ui.theme.NexoNightSoft
import ni.nexo.app.ui.theme.NexoPurple

private data class NavItem(
    val destination: NexoDestination,
    val label: String,
    val icon: @Composable () -> Unit
)

@Composable
fun NexoBottomBar(
    selected: NexoDestination,
    onSelect: (NexoDestination) -> Unit
) {
    val items = listOf(
        NavItem(NexoDestination.Discover, "Explorar") {
            Icon(Icons.Rounded.Search, contentDescription = null)
        },
        NavItem(NexoDestination.PeopleSearch, "Buscar") {
            Icon(Icons.Rounded.PersonAdd, contentDescription = null)
        },
        NavItem(NexoDestination.Matches, "Conexiones") {
            Icon(Icons.Rounded.Favorite, contentDescription = null)
        },
        NavItem(NexoDestination.Chat, "Mensajes") {
            Icon(Icons.Rounded.Chat, contentDescription = null)
        },
        NavItem(NexoDestination.Profile, "Perfil") {
            Icon(Icons.Rounded.Person, contentDescription = null)
        }
    )

    NavigationBar(
        containerColor = NexoNightSoft,
        contentColor = Color.White
    ) {
        items.forEach { item ->
            NavigationBarItem(
                selected = selected == item.destination,
                onClick = { onSelect(item.destination) },
                icon = item.icon,
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = NexoCyan,
                    selectedTextColor = Color.White,
                    indicatorColor = NexoPurple.copy(alpha = 0.24f),
                    unselectedIconColor = NexoMuted,
                    unselectedTextColor = NexoMuted
                )
            )
        }
    }
}
