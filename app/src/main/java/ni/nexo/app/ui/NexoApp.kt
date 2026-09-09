package ni.nexo.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ni.nexo.app.data.CallRecord
import ni.nexo.app.data.CallType
import ni.nexo.app.data.DiscoveryPreferences
import ni.nexo.app.data.LocalUserProfile
import ni.nexo.app.data.NexoRepositoryFactory
import ni.nexo.app.data.PersonProfile
import ni.nexo.app.data.PremiumEntitlements
import ni.nexo.app.data.SupabaseClientProvider
import ni.nexo.app.ui.components.NexoBottomBar
import ni.nexo.app.ui.screens.AuthScreen
import ni.nexo.app.ui.screens.CallScreen
import ni.nexo.app.ui.screens.ChatScreen
import ni.nexo.app.ui.screens.CommunicationHubScreen
import ni.nexo.app.ui.screens.DiscoveryScreen
import ni.nexo.app.ui.screens.DiscoveryFiltersScreen
import ni.nexo.app.ui.screens.EmptyStateScreen
import ni.nexo.app.ui.screens.MatchScreen
import ni.nexo.app.ui.screens.MatchesScreen
import ni.nexo.app.ui.screens.NexoPlusScreen
import ni.nexo.app.ui.screens.ProfileScreen
import ni.nexo.app.ui.screens.ProfileSetupScreen
import ni.nexo.app.ui.screens.SettingsScreen
import ni.nexo.app.ui.screens.SafetyCenterScreen
import ni.nexo.app.ui.screens.SplashScreen
import ni.nexo.app.ui.screens.WelcomeScreen
import ni.nexo.app.ui.theme.NexoNight

enum class NexoDestination {
    Splash,
    Welcome,
    Auth,
    ProfileSetup,
    Discover,
    Matches,
    Chat,
    Conversation,
    Call,
    Settings,
    Profile,
    MatchCelebration,
    DiscoveryFilters,
    SafetyCenter,
    NexoPlus
}

@Composable
fun NexoApp() {
    val repository = remember { NexoRepositoryFactory.create() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalContext.current as? LifecycleOwner

    var destination by rememberSaveable { mutableStateOf(NexoDestination.Splash) }
    var authRegisterMode by rememberSaveable { mutableStateOf(true) }
    var userProfile by remember { mutableStateOf(LocalUserProfile()) }
    var profileIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedPerson by remember { mutableStateOf<PersonProfile?>(null) }
    var activeCall by remember { mutableStateOf<CallRecord?>(null) }
    var activeCallPerson by remember { mutableStateOf<PersonProfile?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var discoveryPreferences by remember { mutableStateOf(DiscoveryPreferences()) }
    var premiumEntitlements by remember { mutableStateOf(PremiumEntitlements()) }
    var premiumReturnDestination by rememberSaveable { mutableStateOf(NexoDestination.Profile) }

    val people = remember { mutableStateListOf<PersonProfile>() }
    val matchedPeople = remember { mutableStateListOf<PersonProfile>() }

    suspend fun refreshDiscovery() {
        try {
            discoveryPreferences = runCatching { repository.loadDiscoveryPreferences() }
                .getOrDefault(discoveryPreferences)
            premiumEntitlements = runCatching { repository.loadPremiumEntitlements() }
                .getOrDefault(PremiumEntitlements())
            val fresh = repository.discoverProfiles().filter { person ->
                person.age in discoveryPreferences.minAge..discoveryPreferences.maxAge &&
                    (!premiumEntitlements.advancedFilters || discoveryPreferences.city.isBlank() || person.city.contains(discoveryPreferences.city, true)) &&
                    (!premiumEntitlements.advancedFilters || discoveryPreferences.intention == "Todas" || person.intention.equals(discoveryPreferences.intention, true)) &&
                    (!premiumEntitlements.advancedFilters || !discoveryPreferences.onlyOnline || person.isOnline)
            }
            people.clear()
            people.addAll(fresh)
            if (profileIndex >= people.size) profileIndex = 0
        } catch (error: Exception) {
            notice = userFacingError(error, "No pudimos cargar perfiles.")
        }
    }

    suspend fun refreshMatches() {
        try {
            val fresh = repository.loadMatches()
            matchedPeople.clear()
            matchedPeople.addAll(fresh)
        } catch (error: Exception) {
            notice = userFacingError(error, "No pudimos cargar tus conexiones.")
        }
    }

    suspend fun continueAfterAuth() {
        runCatching { repository.setPresence(true) }
        val saved = repository.loadMyProfile()
        notice = null
        if (saved == null) {
            destination = NexoDestination.ProfileSetup
        } else {
            userProfile = saved
            refreshDiscovery()
            refreshMatches()
            destination = NexoDestination.Discover
        }
    }

    fun launchCall(person: PersonProfile, type: CallType) {
        if (busy) return
        if (!repository.liveCallsAvailable) {
            notice = "Las llamadas reales se activarán cuando terminemos la conexión segura de audio y video."
            return
        }
        busy = true
        scope.launch {
            try {
                val call = repository.startCall(person.id, person.name, type)
                activeCall = call
                activeCallPerson = person
                destination = NexoDestination.Call
            } catch (error: Exception) {
                notice = userFacingError(error, "No pudimos iniciar la llamada.")
            } finally {
                busy = false
            }
        }
    }

    fun finishActiveCallAndGoBack() {
        val call = activeCall
        scope.launch {
            if (call != null) runCatching { repository.endCall(call.id) }
            activeCall = null
            activeCallPerson = null
            destination = NexoDestination.Chat
        }
    }

    LaunchedEffect(Unit) {
        delay(850)
        if (repository.hasSession()) {
            try {
                continueAfterAuth()
            } catch (error: Exception) {
                notice = userFacingError(error, "No pudimos recuperar tu sesión.")
                destination = NexoDestination.Welcome
            }
        } else {
            destination = NexoDestination.Welcome
        }
    }

    // Completa automáticamente el flujo al volver de Google/Facebook por deep link.
    LaunchedEffect(repository.configured) {
        if (repository.configured) {
            SupabaseClientProvider.client?.auth?.sessionStatus?.collectLatest { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        if (status.isNew && destination in setOf(NexoDestination.Welcome, NexoDestination.Auth)) {
                            runCatching { continueAfterAuth() }
                                .onFailure {
                                    notice = userFacingError(it, "La cuenta se autenticó, pero no pudimos abrir tu perfil.")
                                }
                        }
                    }
                    is SessionStatus.NotAuthenticated -> {
                        if (status.isSignOut && destination !in setOf(
                                NexoDestination.Splash,
                                NexoDestination.Welcome,
                                NexoDestination.Auth
                            )
                        ) {
                            people.clear()
                            matchedPeople.clear()
                            selectedPerson = null
                            userProfile = LocalUserProfile()
                            premiumEntitlements = PremiumEntitlements()
                            destination = NexoDestination.Welcome
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    // Presencia ligada al ciclo de vida: al ir al fondo NEXO deja de anunciarte online.
    DisposableEffect(lifecycleOwner, repository) {
        if (lifecycleOwner == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> scope.launch {
                        runCatching { if (repository.hasSession()) repository.setPresence(true) }
                    }
                    Lifecycle.Event.ON_STOP -> scope.launch {
                        runCatching { if (repository.hasSession()) repository.setPresence(false) }
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    LaunchedEffect(destination) {
        when (destination) {
            NexoDestination.Discover -> refreshDiscovery()
            NexoDestination.Matches, NexoDestination.Chat -> refreshMatches()
            else -> Unit
        }
    }

    // Los errores de las pantallas principales ya no se pierden silenciosamente.
    LaunchedEffect(notice, destination) {
        val current = notice ?: return@LaunchedEffect
        if (destination !in setOf(NexoDestination.Auth, NexoDestination.ProfileSetup)) {
            snackbarHostState.showSnackbar(current)
            if (notice == current) notice = null
        }
    }

    BackHandler(
        enabled = destination !in setOf(
            NexoDestination.Splash,
            NexoDestination.Welcome,
            NexoDestination.Discover
        )
    ) {
        when (destination) {
            NexoDestination.Auth -> {
                notice = null
                destination = NexoDestination.Welcome
            }
            NexoDestination.ProfileSetup -> {
                notice = null
                destination = if (userProfile.name.isBlank()) NexoDestination.Auth else NexoDestination.Profile
            }
            NexoDestination.Matches,
            NexoDestination.Chat,
            NexoDestination.Profile -> destination = NexoDestination.Discover
            NexoDestination.Conversation -> destination = NexoDestination.Chat
            NexoDestination.Call -> finishActiveCallAndGoBack()
            NexoDestination.Settings -> destination = NexoDestination.Profile
            NexoDestination.DiscoveryFilters -> destination = NexoDestination.Discover
            NexoDestination.SafetyCenter -> destination = if (selectedPerson == null) NexoDestination.Profile else NexoDestination.Conversation
            NexoDestination.NexoPlus -> destination = premiumReturnDestination
            NexoDestination.MatchCelebration -> destination = NexoDestination.Discover
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
        containerColor = NexoNight,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NexoBottomBar(selected = destination, onSelect = { destination = it })
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NexoNight)
                .padding(innerPadding)
        ) {
            when (destination) {
                NexoDestination.Splash -> SplashScreen()
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
                                val outcome = if (register) repository.signUp(email, password) else repository.signIn(email, password)
                                notice = outcome.message
                                if (outcome.sessionReady) continueAfterAuth()
                            } catch (error: Exception) {
                                notice = userFacingError(error, "No pudimos iniciar la sesión.")
                            } finally {
                                busy = false
                            }
                        }
                    },
                    onGoogle = {
                        if (!busy) {
                            busy = true
                            notice = null
                            scope.launch {
                                try {
                                    val outcome = repository.signInWithGoogle()
                                    notice = outcome.message
                                    if (outcome.sessionReady) continueAfterAuth()
                                } catch (error: Exception) {
                                    notice = userFacingError(error, "No pudimos abrir Google.")
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                    onFacebook = {
                        if (!busy) {
                            busy = true
                            notice = null
                            scope.launch {
                                try {
                                    val outcome = repository.signInWithFacebook()
                                    notice = outcome.message
                                    if (outcome.sessionReady) continueAfterAuth()
                                } catch (error: Exception) {
                                    notice = userFacingError(error, "No pudimos abrir Facebook.")
                                } finally {
                                    busy = false
                                }
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
                                notice = userFacingError(error, "No pudimos guardar tu perfil.")
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
                                "NEXO está conectado. Cuando haya perfiles compatibles aparecerán aquí."
                            } else {
                                "Probá ampliar tus filtros para encontrar más personas."
                            },
                            actionLabel = "Revisar filtros",
                            onAction = { destination = NexoDestination.DiscoveryFilters }
                        )
                    } else {
                        val person = people[profileIndex % people.size]
                        DiscoveryScreen(
                            person = person,
                            myInterests = userProfile.interests,
                            onFilters = { destination = NexoDestination.DiscoveryFilters },
                            onPass = { profileIndex = (profileIndex + 1) % people.size },
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
                                            notice = userFacingError(error, "No pudimos guardar tu interés.")
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
                            onMessage = { destination = NexoDestination.Conversation },
                            onKeepDiscovering = { destination = NexoDestination.Discover }
                        )
                    }
                }
                NexoDestination.Matches -> MatchesScreen(
                    matches = matchedPeople,
                    onOpenChat = {
                        selectedPerson = it
                        destination = NexoDestination.Conversation
                    }
                )
                NexoDestination.Chat -> CommunicationHubScreen(
                    matches = matchedPeople,
                    repository = repository,
                    demoMode = !repository.configured,
                    onOpenChat = {
                        selectedPerson = it
                        destination = NexoDestination.Conversation
                    },
                    onStartCall = { person, type -> launchCall(person, type) },
                    onUpgrade = {
                        premiumReturnDestination = NexoDestination.Chat
                        destination = NexoDestination.NexoPlus
                    }
                )
                NexoDestination.Conversation -> {
                    val person = selectedPerson
                    if (person == null) {
                        EmptyStateScreen("Elegí una conversación", "Volvé a Chats y elegí con quién querés hablar.")
                    } else {
                        ChatScreen(
                            person = person,
                            repository = repository,
                            onBack = { destination = NexoDestination.Chat },
                            onAudioCall = { launchCall(person, CallType.Audio) },
                            onVideoCall = { launchCall(person, CallType.Video) },
                            onSafeDate = { destination = NexoDestination.SafetyCenter },
                            onUpgrade = {
                                premiumReturnDestination = NexoDestination.Conversation
                                destination = NexoDestination.NexoPlus
                            },
                            onBlocked = {
                                scope.launch { refreshMatches(); refreshDiscovery() }
                                selectedPerson = null
                                destination = NexoDestination.Chat
                            }
                        )
                    }
                }
                NexoDestination.Call -> {
                    val person = activeCallPerson
                    val call = activeCall
                    if (person == null || call == null) {
                        EmptyStateScreen("Llamada no disponible", "Volvé a Chats para iniciar una llamada.")
                    } else {
                        CallScreen(
                            person = person,
                            initialCall = call,
                            repository = repository,
                            onFinished = {
                                activeCall = null
                                activeCallPerson = null
                                destination = NexoDestination.Chat
                            }
                        )
                    }
                }
                NexoDestination.Settings -> SettingsScreen(
                    repository = repository,
                    onBack = { destination = NexoDestination.Profile }
                )
                NexoDestination.DiscoveryFilters -> DiscoveryFiltersScreen(
                    repository = repository,
                    onBack = { destination = NexoDestination.Discover },
                    onUpgrade = {
                        premiumReturnDestination = NexoDestination.DiscoveryFilters
                        destination = NexoDestination.NexoPlus
                    },
                    onSaved = {
                        discoveryPreferences = it
                        destination = NexoDestination.Discover
                    }
                )
                NexoDestination.SafetyCenter -> SafetyCenterScreen(
                    repository = repository,
                    partnerId = selectedPerson?.id,
                    partnerNameInitial = selectedPerson?.name.orEmpty(),
                    onBack = {
                        destination = if (selectedPerson == null) NexoDestination.Profile else NexoDestination.Conversation
                    }
                )
                NexoDestination.NexoPlus -> NexoPlusScreen(
                    repository = repository,
                    onBack = { destination = premiumReturnDestination },
                    onEntitlementsChanged = { premiumEntitlements = it }
                )
                NexoDestination.Profile -> ProfileScreen(
                    profile = userProfile,
                    backendConfigured = repository.configured,
                    plusActive = premiumEntitlements.plusActive,
                    onEdit = {
                        notice = null
                        destination = NexoDestination.ProfileSetup
                    },
                    onSettings = { destination = NexoDestination.Settings },
                    onSafety = {
                        selectedPerson = null
                        destination = NexoDestination.SafetyCenter
                    },
                    onDiscoverySettings = { destination = NexoDestination.DiscoveryFilters },
                    onPlus = {
                        premiumReturnDestination = NexoDestination.Profile
                        destination = NexoDestination.NexoPlus
                    },
                    onLogout = {
                        if (!busy) {
                            busy = true
                            scope.launch {
                                try {
                                    runCatching { repository.setPresence(false) }
                                    repository.signOut()
                                    people.clear()
                                    matchedPeople.clear()
                                    selectedPerson = null
                                    activeCall = null
                                    activeCallPerson = null
                                    userProfile = LocalUserProfile()
                                    premiumEntitlements = PremiumEntitlements()
                                    notice = null
                                    destination = NexoDestination.Welcome
                                } catch (error: Exception) {
                                    notice = userFacingError(error, "No pudimos cerrar la sesión.")
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
