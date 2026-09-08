# NEXO

NEXO es una app Android de citas centrada en conexiones reales, privacidad y comunicación directa.

## Estado actual — MVP 0.2

La versión 0.2 convierte el prototipo local en una arquitectura preparada para usuarios reales:

- Registro e inicio de sesión por correo con Supabase Auth.
- Restauración de sesión.
- Perfil persistente +18 con nombre, edad, ciudad aproximada, bio, intención e intereses.
- Descubrimiento de perfiles reales desde PostgreSQL.
- Likes persistentes.
- Match creado en PostgreSQL únicamente cuando el like es recíproco.
- Lista de matches reales.
- Cierre de sesión.
- Row Level Security (RLS) incluida en la migración.
- Modo demo automático si Supabase todavía no está configurado.
- GitHub Actions sigue compilando sin almacenar secretos.

El chat de 0.2 continúa local de forma deliberada. El siguiente salto será chat en tiempo real y cifrado E2E.

## Stack

- Android nativo
- Kotlin 2.4.x
- Jetpack Compose + Material 3
- Android Gradle Plugin 9.4
- compileSdk 37.0 / targetSdk 36
- JDK 17
- Supabase Kotlin 3.7
- Supabase Auth + PostgREST/PostgreSQL

## Activar Supabase

1. Creá un proyecto gratuito en Supabase.
2. Ejecutá `supabase/migrations/20260908_nexo_0_2.sql` en SQL Editor.
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
      +---- Supabase Auth -> registro, login, sesión
      |
      +---- PostgreSQL + RLS
      |       ├── profiles
      |       ├── likes
      |       └── matches <- trigger por like recíproco
      |
      +---- Chat realtime + E2E      [0.3]
      +---- WebRTC voz/video P2P     [posterior]
      +---- Firebase notificaciones  [posterior]
```

## Principio de privacidad

NEXO no guarda ubicación exacta en el perfil. Las decisiones sensibles de autorización se protegen con RLS y la creación de matches sucede en la base de datos, no en lógica manipulable desde el teléfono.

## Próximos hitos

1. Probar 0.2 contra un proyecto Supabase real con dos cuentas.
2. Añadir fotografías con almacenamiento y reglas de visibilidad.
3. Chat en tiempo real.
4. Cifrado E2E del contenido privado.
5. Bloqueos, reportes y moderación.
6. WebRTC P2P para voz/video.
7. Notificaciones push.

> Nunca agregues una `service_role`, secret key ni credenciales privadas al APK o al repositorio.
