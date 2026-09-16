# NEXO

NEXO es una app Android de conexiones, comunicación privada y experiencias sociales: perfiles, descubrimiento, chat, estados, llamadas preparadas, seguridad personal y una capa social con Feed, Retos, Versus, Círculos, Historias y NEXO AI.

## Estado actual — NEXO 1.0 RC12

La base Android y el núcleo de comunicación están integrados. La capa **NEXO Social 2.0** ya está incorporada a Discovery y cuenta con su esquema PostgreSQL/RLS versionado. El proyecto está preparado para configuración de producción y QA final.

### Ya implementado

- Registro e inicio de sesión por correo.
- Flujo OAuth preparado para Google y Facebook con deep link `nexo://auth-callback`.
- Perfil +18 persistente, foto, ciudad, bio, intención e intereses.
- Descubrimiento, likes, matches y pantalla de celebración.
- Chat persistente y Realtime cuando Supabase está conectado.
- Modo demo para pruebas físicas sin servidor.
- Bandeja de conversaciones con vista previa del último mensaje.
- Respuestas, edición, borrado para todos, reacciones y búsqueda.
- Fotos, videos, cámara, documentos y notas de voz.
- Multimedia privada mediante Supabase Storage y URLs firmadas.
- Estados/novedades de 24 horas.
- Historial e interfaz de llamadas de voz/video y señalización preparada.
- Bloqueos, reportes y preferencias de privacidad.
- Mensajes temporales, presencia y confirmaciones de lectura.
- Solicitud de eliminación de cuenta y cola de borrado.
- Notificaciones Android y canales para mensajes/llamadas.
- Agenda estilo WhatsApp: búsqueda, contactos, chat directo e invitación por SMS.
- Comparación de contactos mediante hashes; la agenda completa no se sube al servidor.
- Centro de Cita Segura y controles de privacidad asociados.
- NEXO Plus mensual/anual y Boost mediante Google Play Billing.
- Restauración y validación de compras mediante backend.
- Filtros avanzados y temas premium.
- Selector de más de 120 emojis, stickers NEXO, fondos y estilos de chat.
- Identidad visual neón, icono launcher y splash.
- Navegación Atrás y manejo global de errores.
- CI Android para lint y APK debug.

### NEXO Social 2.0 integrado

- **Para ti:** punto de entrada social unificado sobre Discovery.
- **Retos:** micro-retos, rachas, logros y recompensas virtuales.
- **Versus:** batallas sociales, clips y votaciones.
- **Círculos:** comunidades privadas para amigos, familia, trabajo, universidad o intereses.
- **Historias:** formato interactivo basado en elecciones de la comunidad.
- **NEXO AI:** creación asistida de publicaciones, encuestas, retos, ideas y respuestas.
- Esquema de datos y RLS en `supabase/migrations/20260915_nexo_2_0_social.sql`.

> La interfaz social está integrada como capa de producto. Antes de anunciar cada acción social como funcionalidad de producción, debe validarse su flujo contra Supabase con dos cuentas reales.

## Stack

- Android nativo
- Kotlin + Jetpack Compose + Material 3
- Android Gradle Plugin 9.2.0
- Gradle 9.4.1
- compileSdk 37.0 / targetSdk 36
- JDK 17
- Supabase Kotlin 3.7
- Supabase Auth + PostgREST + Storage + Realtime
- Ktor OkHttp
- Coil 3.6.2
- Firebase Cloud Messaging
- WebRTC
- Google Play Billing

## Configuración de producción

La app lee de `local.properties`, propiedades Gradle o variables de entorno:

```properties
SUPABASE_URL=https://TU-PROYECTO.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_TU_CLAVE
NEXO_TURN_URLS=...
NEXO_TURN_USERNAME=...
NEXO_TURN_CREDENTIAL=...
```

`local.properties` está ignorado por Git. Nunca incluir `service_role`, client secrets, credenciales TURN privadas ni claves de firma dentro del repositorio o del APK.

## Migraciones Supabase

Ejecutar todas las migraciones de `supabase/migrations/` en orden cronológico. La migración social principal es:

```text
supabase/migrations/20260915_nexo_2_0_social.sql
```

Después de migrar, verificar RLS, buckets privados, Edge Functions y datos con dos cuentas de prueba.

## CI

`.github/workflows/android-ci.yml` ejecuta lint y genera un APK debug. El CI puede compilar en modo conectado cuando existen los secretos `SUPABASE_URL` y `SUPABASE_PUBLISHABLE_KEY`; de lo contrario, mantiene el modo demo.

Una compilación debug correcta **no equivale** a una publicación de Google Play. Para producción se necesita una firma release protegida y un Android App Bundle (`.aab`).

## Publicación

Antes de subir NEXO a Google Play:

1. Configurar Supabase de producción y aplicar migraciones.
2. Configurar Firebase/FCM y OAuth.
3. Configurar STUN/TURN y probar WebRTC si se habilitan llamadas reales.
4. Configurar productos y validación de Google Play Billing.
5. Configurar firma de release fuera del repositorio.
6. Probar dos teléfonos con cuentas distintas.
7. Probar bloqueo, reportes, privacidad, multimedia y eliminación de cuenta.
8. Publicar Política de Privacidad, Términos y reglas de contenido.
9. Definir moderación operativa y tratamiento de reportes.
10. Generar el `.aab` firmado y completar la ficha de Google Play.

Checklist detallado: `docs/DEPLOYMENT_CHECKLIST.md`.

## Privacidad y seguridad

- NEXO no necesita publicar ubicación exacta del usuario.
- RLS protege perfiles, matches, mensajes, reacciones, recibos, bloqueos, reportes, estados y llamadas.
- Los archivos privados se sirven mediante autorización y URLs temporales.
- El tráfico HTTP sin TLS está desactivado.
- `android:allowBackup` está desactivado.
- El cifrado E2E no se anuncia como activo hasta integrar una implementación auditada.
- El borrado definitivo requiere procesar `account_deletion_requests` y eliminar también los objetos físicos de Storage.

## Estado para despliegue

**Código:** preparado para la fase de configuración/QA de producción.

**Pendiente fuera del código fuente:** credenciales externas, firma release, configuración de Google Play/Firebase/Supabase y prueba física final.

> Nunca agregues `service_role`, client secrets ni credenciales privadas al APK o al repositorio.
