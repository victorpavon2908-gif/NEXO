# NEXO

NEXO es una app Android de citas centrada en conexiones reales, privacidad y comunicación directa.

## Estado actual — MVP 0.1

La primera base Android ya incluye un flujo local navegable:

- Pantalla de bienvenida
- Creación de perfil con validación +18
- Descubrimiento de perfiles
- Like / pasar
- Celebración de match
- Lista de matches
- Chat local de demostración
- Perfil y edición básica

Los datos actuales son simulados deliberadamente. La siguiente etapa conectará el flujo a Supabase sin acoplar la interfaz al backend.

## Stack

- Android nativo
- Kotlin 2.4.x
- Jetpack Compose 1.12 mediante BOM `2026.08.00`
- Material 3
- Android Gradle Plugin 9.4
- compileSdk / targetSdk 37
- JDK 17

## Arquitectura objetivo

```text
Android (UI + caché local)
        |
        +---- Supabase/PostgreSQL -> cuentas, perfiles, likes, matches, reportes
        |
        +---- Firebase Cloud Messaging -> notificaciones
        |
        +---- WebRTC -> voz/video P2P
        |
        +---- cifrado E2E -> contenido privado
```

## Principio de privacidad

NEXO no pretende almacenar en un servidor más información de la necesaria. La ubicación exacta no forma parte del perfil público y la arquitectura futura separará metadatos de conexión del contenido privado.

## Próximos hitos

1. Verificar build automático en GitHub Actions.
2. Conectar autenticación y perfiles con Supabase.
3. Persistencia local y sincronización.
4. Likes/matches reales.
5. Chat en tiempo real + cifrado E2E.
6. WebRTC para voz y video P2P.
7. Moderación, bloqueo, reportes y verificación.

> No agregues claves privadas ni secretos al repositorio. Se configurarán como variables locales/secretos de CI cuando integremos servicios externos.
