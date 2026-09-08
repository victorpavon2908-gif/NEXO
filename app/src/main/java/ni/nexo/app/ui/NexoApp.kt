package ni.nexo.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import ni.nexo.app.data.LocalUserProfile
import ni.nexo.app.data.NexoRepositoryFactory
import ni.nexo.app.data.PersonProfile
import ni.nexo.app.ui.components.NexoBottomBar
import ni.nexo.app.ui.screens.AuthScreen
import ni.nexo.app.ui.screens.ChatScreen
import ni.nexo.app.ui.screens.DiscoveryScreen
import ni.nexo.app.ui.screens.EmptyStateScreen
import ni.nexo.app.ui.screens.MatchScreen
import ni.nexo.app.ui.screens.MatchesScreen
import ni.nexo.app.ui.screens.ProfileScreen
import ni.nexo.app.ui.screens.ProfileSetupScreen
import ni.nexo.app.ui.screens.WelcomeScreen

enum class NexoDestination {
    Welcome,
    Auth,
    ProfileSetup,
    Discover,
    Matches,
    Chat,
    Profile,
    MatchCelebration
}

@Composable
fun NexoApp() {
    val repository = remember { NexoRepositoryFactory.create() }
    val scope = rememberCoroutineScope()

    var destination by rememberSaveable { mutableStateOf(NexoDestination.Welcome) }
    var authRegisterMode by rememberSaveable { mutableStateOf(true) }
    var userProfile by remember { mutableStateOf(LocalUserProfile()) }
    var profileIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedPerson by remember { mutableStateOf<PersonProfile?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    val people = remember { mutableStateListOf<PersonProfile>() }
    val matchedPeople = remember { mutableStateListOf<PersonProfile>() }

    suspend fun refreshDiscovery() {
        try {
            val fresh = repository.discoverProfiles()
            people.clear()
            people.addAll(fresh)
            if (profileIndex >= people.size) profileIndex = 0
        } catch (error: Exception) {
            notice = error.message ?: "No pudimos cargar perfiles."
        }
    }

    suspend fun refreshMatches() {
        try {
            val fresh = repository.loadMatches()
            matchedPeople.clear()
            matchedPeople.addAll(fresh)
        } catch (error: Exception) {
            notice = error.message ?: "No pudimos cargar tus matches."
        }
    }

    LaunchedEffect(Unit) {
        if (repository.hasSession()) {
            try {
                val saved = repository.loadMyProfile()
                if (saved == null) {
                    destination = NexoDestination.ProfileSetup
                } else {
                    userProfile = saved
                    refreshDiscovery()
                    refreshMatches()
                    destination = NexoDestination.Discover
                }
            } catch (error: Exception) {
                notice = error.message
                destination = NexoDestination.Welcome
            }
        }
    }

    LaunchedEffect(destination) {
        when (destination) {
            NexoDestination.Discover -> refreshDiscovery()
            NexoDestination.Matches -> refreshMatches()
            else -> Unit
        }
    }

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
                    onStart = {
                        authRegisterMode = true
                        notice = null
                        destination = NexoDestination.Auth
                    },
                    onLogin = {
                        authRegisterMode = false
                        notice = null
                        destination = NexoDestination.Auth
                    }
                )

                NexoDestination.Auth -> AuthScreen(
                    startInRegisterMode = authRegisterMode,
                    backendConfigured = repository.configured,
                    busy = busy,
                    message = notice,
                    onBack = {
                        notice = null
                        destination = NexoDestination.Welcome
                    },
                    onSubmit = { register, email, password ->
                        busy = true
                        notice = null
                        scope.launch {
                            try {
                                val outcome = if (register) {
                                    repository.signUp(email, password)
                                } else {
                                    repository.signIn(email, password)
                                }
                                notice = outcome.message
                                if (outcome.sessionReady) {
                                    val saved = repository.loadMyProfile()
                                    if (saved == null) {
                                        destination = NexoDestination.ProfileSetup
                                    } else {
                                        userProfile = saved
                                        refreshDiscovery()
                                        refreshMatches()
                                        destination = NexoDestination.Discover
                                    }
                                }
                            } catch (error: Exception) {
                                notice = error.message ?: "No pudimos iniciar la sesión."
                            } finally {
                                busy = false
                            }
                        }
                    }
                )

                NexoDestination.ProfileSetup -> ProfileSetupScreen(
                    initial = userProfile,
                    backendConfigured = repository.configured,
                    busy = busy,
                    message = notice,
                    onBack = {
                        notice = null
                        destination = if (userProfile.name.isBlank()) NexoDestination.Auth else NexoDestination.Profile
                    },
                    onUploadPhoto = repository::uploadProfilePhoto,
                    onContinue = { candidate ->
                        busy = true
                        notice = null
                        scope.launch {
                            try {
                                repository.saveMyProfile(candidate)
                                userProfile = candidate
                                refreshDiscovery()
                                refreshMatches()
                                destination = NexoDestination.Discover
                            } catch (error: Exception) {
                                notice = error.message ?: "No pudimos guardar tu perfil."
                            } finally {
                                busy = false
                            }
                        }
                    }
                )

                NexoDestination.Discover -> {
                    if (people.isEmpty()) {
                        EmptyStateScreen(
                            title = "Aún no hay perfiles para mostrar",
                            body = if (repository.configured) {
                                "NEXO ya está conectado. Cuando entren más usuarios aparecerán aquí."
                            } else {
                                "Configurá Supabase para empezar a probar usuarios reales."
                            }
                        )
                    } else {
                        val person = people[profileIndex % people.size]
                        DiscoveryScreen(
                            person = person,
                            onPass = {
                                profileIndex = (profileIndex + 1) % people.size
                            },
                            onLike = {
                                if (!busy) {
                                    busy = true
                                    scope.launch {
                                        try {
                                            val isMatch = repository.like(person.id)
                                            profileIndex = (profileIndex + 1) % people.size
                                            if (isMatch) {
                                                selectedPerson = person
                                                if (matchedPeople.none { it.id == person.id }) matchedPeople.add(person)
                                                destination = NexoDestination.MatchCelebration
                                            }
                                        } catch (error: Exception) {
                                            notice = error.message ?: "No pudimos enviar el like."
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                NexoDestination.MatchCelebration -> {
                    val person = selectedPerson
                    if (person == null) {
                        EmptyStateScreen("Match no disponible", "Volvé a Descubrir para seguir conectando.")
                    } else {
                        MatchScreen(
                            person = person,
                            onMessage = { destination = NexoDestination.Chat },
                            onKeepDiscovering = { destination = NexoDestination.Discover }
                        )
                    }
                }

                NexoDestination.Matches -> MatchesScreen(
                    matches = matchedPeople,
                    onOpenChat = {
                        selectedPerson = it
                        destination = NexoDestination.Chat
                    }
                )

                NexoDestination.Chat -> {
                    val person = selectedPerson
                    if (person == null) {
                        EmptyStateScreen(
                            title = "Elegí un match primero",
                            body = "Abrí la pestaña Matches y seleccioná con quién querés conversar."
                        )
                    } else {
                        ChatScreen(person = person, repository = repository)
                    }
                }

                NexoDestination.Profile -> ProfileScreen(
                    profile = userProfile,
                    backendConfigured = repository.configured,
                    onEdit = {
                        notice = null
                        destination = NexoDestination.ProfileSetup
                    },
                    onLogout = {
                        if (!busy) {
                            busy = true
                            scope.launch {
                                try {
                                    repository.signOut()
                                    people.clear()
                                    matchedPeople.clear()
                                    selectedPerson = null
                                    userProfile = LocalUserProfile()
                                    notice = null
                                    destination = NexoDestination.Welcome
                                } catch (error: Exception) {
                                    notice = error.message ?: "No pudimos cerrar la sesión."
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}
