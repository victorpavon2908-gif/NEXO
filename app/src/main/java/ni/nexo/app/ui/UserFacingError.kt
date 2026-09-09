package ni.nexo.app.ui

/**
 * Evita que respuestas de red, URLs, tokens o cabeceras del backend terminen
 * visibles en la interfaz. Los mensajes de validación escritos por NEXO sí se
 * conservan porque ayudan a la persona a corregir el dato.
 */
fun userFacingError(error: Throwable, fallback: String): String {
    val raw = error.message.orEmpty().trim()
    val normalized = raw.lowercase()

    return when {
        "sms provider" in normalized || "unexpected_failure" in normalized ->
            "El servicio de SMS no está disponible en este momento. Intentá nuevamente más tarde."
        "rate limit" in normalized || "too many requests" in normalized || "429" in normalized ->
            "Hiciste varios intentos. Esperá un momento y volvé a intentarlo."
        "invalid login" in normalized || "invalid credentials" in normalized ->
            "El correo o la contraseña no son correctos."
        "email not confirmed" in normalized ->
            "Confirmá tu correo antes de iniciar sesión."
        "invalid" in normalized && ("otp" in normalized || "token" in normalized) ->
            "El código no es válido o ya venció. Solicitá uno nuevo."
        "timeout" in normalized || "connect" in normalized || "network" in normalized ||
            "unable to resolve host" in normalized ->
            "No pudimos conectarnos. Revisá tu internet e intentá nuevamente."
        raw.isSafeValidationMessage() -> raw
        else -> fallback
    }
}

private fun String.isSafeValidationMessage(): Boolean {
    if (isBlank() || length > 220 || contains("http", ignoreCase = true) || contains("header", ignoreCase = true)) {
        return false
    }
    return startsWith("Ingresá") || startsWith("Elegí") || startsWith("Usá") ||
        startsWith("La ") || startsWith("El ") || startsWith("Los ") ||
        startsWith("Las ") || startsWith("NEXO ") || startsWith("Solo ") ||
        startsWith("Desbloqueá") || startsWith("Desactivá") || startsWith("Escribí") || startsWith("Indicá") ||
        startsWith("Configurá") || startsWith("Conectá")
}
