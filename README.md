# NEXO

NEXO es una app Android de citas y comunicación privada enfocada en conexiones reales, control de identidad y una experiencia de mensajería moderna.

## Estado actual — NEXO 1.0 RC5

La RC5 deja la aplicación prácticamente cerrada a nivel de producto y código Android. Lo pendiente está concentrado en servicios externos que requieren credenciales, infraestructura o un motor especializado.

### Ya implementado

- Registro e inicio de sesión por correo.
- Flujo OAuth preparado para Google y Facebook con deep link `nexo://auth-callback`.
- Perfil +18 persistente, foto, ciudad, bio, intención e intereses.
- Descubrimiento, likes, matches y pantalla de celebración.
- Chat persistente y Realtime cuando Supabase está conectado.
- Modo demo completo sin servidor para pruebas físicas.
- Bandeja de conversaciones con vista previa real del último mensaje.
- Respuestas, edición, borrado para todos, reacciones y búsqueda.
- Fotos, videos, cámara, documentos y notas de voz.
- Multimedia privada mediante Supabase Storage y URLs firmadas.
- Estados/novedades de 24 horas.
- Historial e interfaz de llamadas de voz/video.
- Señalización de llamadas preparada en PostgreSQL.
- Bloqueos y reportes.
- Preferencias de privacidad y mensajes temporales.
- Presencia online ligada al ciclo de vida de la app.
- Confirmaciones de lectura separadas del contenido del mensaje.
- Solicitud de eliminación de cuenta preparada y cola de borrado.
- Canales Android para mensajes y llamadas.
- Permiso de notificaciones en Android 13+.
- Agenda estilo WhatsApp: búsqueda, contactos en NEXO, chat directo e invitación por SMS.
- Los contactos se comparan mediante hashes; la agenda completa no se sube al servidor.
- Pantallas de conexiones y perfil rediseñadas con la identidad neón de NEXO.
- Sugerencias de conversación basadas en intereses, sin inventar porcentajes de compatibilidad.
- Señales visibles de identidad, teléfono y ciudad para decidir con más confianza.
- Errores técnicos, URLs y encabezados del backend nunca se muestran directamente en pantalla.
- Agendas grandes sincronizadas por lotes y vistas previas de chat cargadas en paralelo.
- Las llamadas reales permanecen bloqueadas hasta conectar WebRTC; NEXO no simula audio en producción.
- Icono launcher propio y splash coherente con la marca.
- Navegación Atrás consistente y mensajes globales de error.
- CI de Android con generación automática del APK debug.

## Stack

- Android nativo
- Kotlin + Jetpack Compose + Material 3
- Android Gradle Plugin 9.2.0
- Gradle 9.4.1
- compileSdk 37.0 / targetSdk 36
- JDK 17
- Supabase Kotlin 3.7
- Supabase Auth + PostgREST + Storage + Realtime
- Ktor OkHttp para WebSockets
- Coil 3.6.2 para imágenes

## Conectar Supabase

1. Crear un proyecto Supabase.
2. Ejecutar las migraciones en este orden:

```text
supabase/migrations/20260908_nexo_0_2.sql
supabase/migrations/20260908_nexo_0_3.sql
supabase/migrations/20260908_nexo_1_0.sql
supabase/migrations/20260908_nexo_1_0_1_security.sql
supabase/migrations/20260908_nexo_1_0_2_polish.sql
supabase/migrations/20260908_nexo_1_0_3_contacts_groups.sql
supabase/migrations/20260909_nexo_1_0_4_whatsapp_contacts.sql
```

3. Agregar localmente, sin subir a GitHub:

```properties
SUPABASE_URL=https://TU-PROYECTO.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_TU_CLAVE
```

`local.properties` está ignorado por Git y nunca debe contener una `service_role` dentro de la app.

## Arquitectura

```text
Android / Compose
      |
      +---- Auth
      |      ├── Email
      |      ├── Google [activar proveedor]
      |      └── Facebook [activar proveedor]
      |
      +---- PostgreSQL + RLS
      |      ├── profiles
      |      ├── likes / matches
      |      ├── messages / receipts / reactions
      |      ├── blocks / reports
      |      ├── status_updates
      |      ├── calls / call_signals
      |      ├── devices / presence / preferences
      |      └── account_deletion_requests
      |
      +---- Supabase Storage
      |      ├── profile-photos
      |      └── chat-media
      |
      +---- Firebase Cloud Messaging      [conexión externa]
      +---- WebRTC + STUN/TURN            [conexión externa]
      +---- Protocolo E2E auditado        [conexión externa]
      +---- Worker privado de borrado     [conexión externa]
```

## Privacidad y seguridad

- NEXO no necesita publicar ubicación exacta del usuario.
- RLS protege perfiles, matches, mensajes, reacciones, recibos, bloqueos, reportes, estados y llamadas.
- Los archivos de chat se almacenan en bucket privado y se entregan mediante URLs temporales.
- El receptor no obtiene permisos para editar el contenido del mensaje del remitente.
- `android:allowBackup` está desactivado para reducir copias automáticas de datos locales sensibles.
- El tráfico HTTP sin TLS está desactivado.
- El cifrado E2E no se anuncia como activo hasta integrar una implementación auditada.

## Lo único importante que falta conectar

1. **Supabase real:** URL, publishable key y migraciones.
2. **Google/Facebook:** credenciales y proveedores OAuth.
3. **Firebase Cloud Messaging:** proyecto Firebase, `google-services.json` y backend de envío.
4. **WebRTC:** motor Android, STUN/TURN y manejo de llamadas entrantes.
5. **E2EE:** biblioteca/protocolo auditado y gestión de claves.
6. **Eliminación definitiva:** worker/Edge Function privado que procese `account_deletion_requests` y borre Auth + objetos físicos de Storage.
7. **Publicación:** firma release, políticas legales, moderación operativa y pruebas físicas.

Ver también `docs/EXTERNAL_CONNECTIONS.md`.

> Nunca agregues `service_role`, client secrets ni credenciales privadas al APK o al repositorio.
