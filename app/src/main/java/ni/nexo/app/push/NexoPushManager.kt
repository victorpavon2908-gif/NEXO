package ni.nexo.app.push

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ni.nexo.app.data.SupabaseClientProvider
import ni.nexo.app.data.SupabaseNexoRepository

/**
 * Mantiene el token FCM ligado a la sesión real de Supabase.
 * El token se conserva localmente si Firebase lo entrega antes del inicio de sesión
 * y se publica en `devices` tan pronto como Supabase entra en estado autenticado.
 */
object NexoPushManager {
    private const val TAG = "NexoPush"
    private const val PREFS = "nexo_push"
    private const val KEY_FCM_TOKEN = "fcm_token"

    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context) {
        val appContext = context.applicationContext
        if (!started.compareAndSet(false, true)) return

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> onNewToken(appContext, token) }
            .addOnFailureListener { error ->
                Log.w(TAG, "FCM todavía no pudo entregar un token.", error)
            }

        SupabaseClientProvider.client?.let { client ->
            scope.launch {
                runCatching {
                    client.auth.sessionStatus.collectLatest { status ->
                        if (status is SessionStatus.Authenticated) {
                            syncStoredToken(appContext)
                        }
                    }
                }.onFailure { error ->
                    Log.w(TAG, "No pudimos observar la sesión para sincronizar FCM.", error)
                }
            }
        }

        scope.launch { syncStoredToken(appContext) }
    }

    fun onNewToken(context: Context, token: String) {
        val clean = token.trim()
        if (clean.isBlank()) return
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FCM_TOKEN, clean)
            .apply()
        scope.launch { syncStoredToken(context.applicationContext) }
    }

    private suspend fun syncStoredToken(context: Context) {
        val token = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FCM_TOKEN, null)
            ?.trim()
            .orEmpty()
        if (token.isBlank()) return

        val client = SupabaseClientProvider.client ?: return
        if (client.auth.currentSessionOrNull() == null) return

        runCatching {
            SupabaseNexoRepository(client).registerPushToken(token)
        }.onFailure { error ->
            Log.w(TAG, "No pudimos registrar el dispositivo para notificaciones.", error)
        }
    }
}
