package ni.nexo.app.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc

/**
 * Starts the self-service account deletion flow.
 *
 * In production the RPC immediately hides the profile, removes active social
 * relationships and queues the final deletion. The privileged cleanup worker
 * is responsible for deleting Auth + Storage objects after the external
 * Supabase service is connected.
 */
object AccountDeletionService {
    suspend fun request(repository: NexoRepository): Result<Unit> = runCatching {
        if (repository.configured) {
            val client = SupabaseClientProvider.client
                ?: error("Supabase no está disponible en esta instalación.")
            client.postgrest.rpc("request_my_account_deletion")
            runCatching { client.auth.signOut() }
        } else {
            repository.signOut()
        }
    }
}
