# NEXO

NEXO es una app Android de citas centrada en conexiones reales, privacidad y comunicación directa.

## Estado actual — MVP 0.3

La versión 0.3 añade comunicación y perfiles multimedia reales sobre la base segura de 0.2:

- Registro e inicio de sesión por correo con Supabase Auth.
- Perfil persistente +18 y fotografías reales.
- Fotos almacenadas en Supabase Storage con límite de 5 MB y escritura aislada por usuario.
- Descubrimiento y matches mostrando fotografías reales cuando existen.
- Chat persistente en PostgreSQL, disponible solo entre participantes de un match.
- Actualización de mensajes en tiempo real con Supabase Realtime.
- RLS para perfiles, likes, matches y mensajes.
- Esquema de mensajes preparado para cifrado E2E mediante `encryption_version`, `nonce` y `sender_key_id`.
- En 0.3 el contenido todavía se guarda en texto plano (`encryption_version = 0`); la interfaz lo indica explícitamente.
- Modo demo si Supabase no está configurado.

## Stack

- Android nativo
- Kotlin 2.4.x
- Jetpack Compose + Material 3
- Android Gradle Plugin 9.4
- compileSdk 37.0 / targetSdk 36
- JDK 17
- Supabase Kotlin 3.7
- Supabase Auth + PostgREST + Storage + Realtime
- Ktor OkHttp para soporte WebSocket
- Coil 3.6.2 para fotografías remotas

## Activar Supabase

1. Creá un proyecto en Supabase.
2. Ejecutá en orden:
   - `supabase/migrations/20260908_nexo_0_2.sql`
   - `supabase/migrations/20260908_nexo_0_3.sql`
3. En tu `local.properties` local agregá:

```properties
SUPABASE_URL=https://TU-PROYECTO.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_TU_CLAVE
```

`local.properties` está en `.gitignore` y no debe subirse.

Más detalles: `supabase/README.md`.

## Arquitectura

```text
Android / Compose
      |
      +---- Supabase Auth
      |
      +---- PostgreSQL + RLS
      |       ├── profiles
      |       ├── likes
      |       ├── matches
      |       └── messages <--- Realtime
      |
      +---- Supabase Storage
      |       └── profile-photos
      |
      +---- E2E real                [0.4]
      +---- WebRTC voz/video P2P    [posterior]
      +---- Notificaciones push     [posterior]
```

## Principio de privacidad

NEXO no guarda ubicación exacta. Un usuario solo puede escribir archivos dentro de su propia carpeta de Storage y solo los participantes de un match pueden leer o insertar mensajes de esa conversación. Las decisiones de autorización se protegen con RLS.

## Próximos hitos

1. Probar 0.3 con dos cuentas y dos teléfonos reales.
2. Activar cifrado E2E real y gestión de claves.
3. Bloqueos, reportes y moderación.
4. Estados de entrega/lectura y notificaciones push.
5. WebRTC P2P para voz/video.

> Nunca agregues una `service_role`, secret key ni credenciales privadas al APK o al repositorio.
