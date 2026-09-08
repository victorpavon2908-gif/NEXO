package ni.nexo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import ni.nexo.app.data.FakeNexoRepository
import ni.nexo.app.data.LocalUserProfile
import ni.nexo.app.data.PersonProfile
import ni.nexo.app.ui.components.NexoBottomBar
import ni.nexo.app.ui.screens.ChatScreen
import ni.nexo.app.ui.screens.DiscoveryScreen
import ni.nexo.app.ui.screens.MatchScreen
import ni.nexo.app.ui.screens.MatchesScreen
import ni.nexo.app.ui.screens.ProfileScreen
import ni.nexo.app.ui.screens.ProfileSetupScreen
import ni.nexo.app.ui.screens.WelcomeScreen

enum class NexoDestination {
    Welcome,
    ProfileSetup,
    Discover,
    Matches,
    Chat,
    Profile,
    MatchCelebration
}

@Composable
fun NexoApp() {
    var destination by rememberSaveable { mutableStateOf(NexoDestination.Welcome) }
    var userProfile by remember { mutableStateOf(LocalUserProfile()) }
    var profileIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedPerson by remember { mutableStateOf(FakeNexoRepository.people.first()) }
    val matchedPeople = remember { mutableStateListOf<PersonProfile>() }

    val showBottomBar = destination in setOf(
        NexoDestination.Discover,
        NexoDestination.Matches,
        NexoDestination.Chat,
        NexoDestination.Profile
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.White,
        bottomBar = {
            if (showBottomBar) {
                NexoBottomBar(
                    selected = destination,
                    onSelect = { destination = it }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(innerPadding)
        ) {
            when (destination) {
                NexoDestination.Welcome -> WelcomeScreen(
                    onStart = { destination = NexoDestination.ProfileSetup },
                    onLogin = { destination = NexoDestination.Discover }
                )

                NexoDestination.ProfileSetup -> ProfileSetupScreen(
                    initial = userProfile,
                    onBack = { destination = NexoDestination.Welcome },
                    onContinue = {
                        userProfile = it
                        destination = NexoDestination.Discover
                    }
                )

                NexoDestination.Discover -> {
                    val person = FakeNexoRepository.people[profileIndex % FakeNexoRepository.people.size]
                    DiscoveryScreen(
                        person = person,
                        onPass = {
                            profileIndex = (profileIndex + 1) % FakeNexoRepository.people.size
                        },
                        onLike = {
                            selectedPerson = person
                            if (matchedPeople.none { it.id == person.id }) matchedPeople.add(person)
                            profileIndex = (profileIndex + 1) % FakeNexoRepository.people.size
                            destination = NexoDestination.MatchCelebration
                        }
                    )
                }

                NexoDestination.MatchCelebration -> MatchScreen(
                    person = selectedPerson,
                    onMessage = { destination = NexoDestination.Chat },
                    onKeepDiscovering = { destination = NexoDestination.Discover }
                )

                NexoDestination.Matches -> MatchesScreen(
                    matches = matchedPeople,
                    onOpenChat = {
                        selectedPerson = it
                        destination = NexoDestination.Chat
                    }
                )

                NexoDestination.Chat -> ChatScreen(person = selectedPerson)

                NexoDestination.Profile -> ProfileScreen(
                    profile = userProfile,
                    onEdit = { destination = NexoDestination.ProfileSetup }
                )
            }
        }
    }
}
