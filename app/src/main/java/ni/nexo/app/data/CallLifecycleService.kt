package ni.nexo.app.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import java.time.Instant

/**
 * RC9 call lifecycle helper.
 *
 * Keeps call-state mutations in one place so ringing, remote rejection,
 * missed-call timeout and normal hang-up use the same backend state machine.
 */
class CallLifecycleService {
    private val supabase get() = SupabaseClientProvider.client

    val available: Boolean
        get() = SupabaseClientProvider.isConfigured && supabase != null

    suspend fun accept(callId: String) {
        transition(callId, "connecting")
    }

    suspend fun connected(callId: String) {
        transition(callId, "connected")
    }

    suspend fun decline(callId: String) {
        transition(callId, "declined", terminal = true)
    }

    suspend fun markMissed(callId: String) {
        transition(callId, "missed", terminal = true)
    }

    suspend fun end(callId: String) {
        transition(callId, "ended", terminal = true)
    }

    private suspend fun transition(callId: String, nextState: String, terminal: Boolean = false) {
        require(callId.isNotBlank()) { "La llamada no tiene un identificador válido." }
        val client = supabase ?: error("Supabase no está configurado.")
        val me = client.auth.currentUserOrNull()?.id ?: error("La sesión expiró.")
        val now = Instant.now().toString()

        client.from("calls").update({
            set("state", nextState)
            if (nextState == "connected") set("answered_at", now)
            if (terminal) set("ended_at", now)
        }) {
            filter {
                eq("id", callId)
                or {
                    eq("caller_id", me)
                    eq("callee_id", me)
                }
            }
        }
    }
}
