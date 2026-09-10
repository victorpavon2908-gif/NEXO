package ni.nexo.app.data

object NexoRepositoryFactory {
    fun create(): NexoRepository {
        val client = SupabaseClientProvider.client
        return if (client != null) {
            val advanced = AdvancedNexoRepository(SupabaseNexoRepository(client), client)
            SecureRealtimeNexoRepository(advanced, client)
        } else {
            DemoNexoRepository()
        }
    }
}
