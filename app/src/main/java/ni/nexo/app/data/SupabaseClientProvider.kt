package ni.nexo.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import ni.nexo.app.BuildConfig

object SupabaseClientProvider {
    private fun normalize(value: String): String = value
        .trim()
        .removeSurrounding("\"")
        .removeSurrounding("'")
        .trim()

    val supabaseUrl: String
        get() = normalize(BuildConfig.SUPABASE_URL).trimEnd('/')

    val publishableKey: String
        get() = normalize(BuildConfig.SUPABASE_PUBLISHABLE_KEY)

    val configurationIssue: String?
        get() = when {
            supabaseUrl.isBlank() -> "Falta SUPABASE_URL en esta compilación."
            !supabaseUrl.startsWith("https://") -> "SUPABASE_URL debe comenzar con https://"
            publishableKey.isBlank() -> "Falta SUPABASE_PUBLISHABLE_KEY o SUPABASE_ANON_KEY en esta compilación."
            else -> null
        }

    val isConfigured: Boolean
        get() = configurationIssue == null

    val client: SupabaseClient? by lazy {
        if (!isConfigured) return@lazy null

        createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = publishableKey
        ) {
            install(Auth) {
                scheme = "nexo"
                host = "auth-callback"
            }
            install(Postgrest)
            install(Storage)
            install(Realtime)
        }
    }
}
