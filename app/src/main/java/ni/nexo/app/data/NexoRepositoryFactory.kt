package ni.nexo.app.data

object NexoRepositoryFactory {
    fun create(): NexoRepository {
        val client = SupabaseClientProvider.client
        return if (client != null) SupabaseNexoRepository(client) else DemoNexoRepository()
    }
}
