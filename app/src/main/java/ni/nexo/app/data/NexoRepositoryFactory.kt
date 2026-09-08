package ni.nexo.app.data

object NexoRepositoryFactory {
    fun create(): NexoRepository {
        val client = SupabaseClientProvider.client
        return if (client != null) {
            AdvancedNexoRepository(SupabaseNexoRepository(client), client)
        } else {
            DemoNexoRepository()
        }
    }
}