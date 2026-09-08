package ni.nexo.app.data

object FakeNexoRepository {
    val people = listOf(
        PersonProfile(
            id = "valentina",
            name = "Valentina",
            age = 26,
            city = "Managua",
            bio = "Café, música y conversaciones que sí llevan a algo.",
            intention = "Relación seria",
            interests = listOf("Café", "Música", "Viajes"),
            verified = true
        ),
        PersonProfile(
            id = "sofia",
            name = "Sofía",
            age = 24,
            city = "Jinotepe",
            bio = "Me gustan los planes sencillos, reír bastante y conocer lugares nuevos.",
            intention = "Conocer y ver qué pasa",
            interests = listOf("Cine", "Comida", "Naturaleza"),
            verified = true
        ),
        PersonProfile(
            id = "daniela",
            name = "Daniela",
            age = 28,
            city = "Granada",
            bio = "Busco una conexión tranquila, auténtica y sin juegos.",
            intention = "Relación seria",
            interests = listOf("Fotografía", "Perros", "Viajes")
        )
    )

    fun starterMessages(person: PersonProfile): List<ChatMessage> = listOf(
        ChatMessage(1, "¡Hola! Qué bueno que hicimos match 😊", fromMe = false),
        ChatMessage(2, "Hola ${person.name}, también me dio gusto encontrarte por aquí.", fromMe = true),
        ChatMessage(3, "¿Qué te gustaría hacer para una primera salida?", fromMe = false)
    )
}
