package ni.nexo.app.ui.screens

import androidx.compose.runtime.Composable
import ni.nexo.app.data.PersonProfile

/**
 * NEXO's main discovery surface now hosts the social layer as well as people discovery.
 * Keeping the original API avoids changing the app navigation contract while the social
 * backend is introduced incrementally.
 */
@Composable
fun DiscoveryScreen(
    person: PersonProfile,
    myInterests: List<String> = emptyList(),
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onFilters: () -> Unit,
    onPass: () -> Unit,
    onLike: () -> Unit
) {
    NexoSocialHubScreen(
        person = person,
        myInterests = myInterests,
        onSearch = onSearch,
        onRefresh = onRefresh,
        onFilters = onFilters,
        onPass = onPass,
        onLike = onLike
    )
}
