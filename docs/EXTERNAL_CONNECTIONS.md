# NEXO 1.0 RC — conexiones externas pendientes

El código de aplicación, navegación, modo demo, esquema de base de datos y flujos de comunicación están preparados. Para pasar de RC a producción deben conectarse servicios que requieren credenciales del propietario del proyecto.

## 1. Supabase

1. Crear un proyecto Supabase.
2. Ejecutar en orden:
   - `supabase/migrations/20260908_nexo_0_2.sql`
   - `supabase/migrations/20260908_nexo_0_3.sql`
   - `supabase/migrations/20260908_nexo_1_0.sql`
3. En `local.properties` (NO subir a GitHub):

```properties
SUPABASE_URL=https://TU-PROYECTO.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_xxxxx
```

La migración 1.0 deja listos perfiles, likes, matches, mensajes, multimedia privada, reacciones, edición/borrado, confirmaciones de lectura, mensajes temporales, bloqueos, reportes, preferencias, presencia, dispositivos, novedades/estados, llamadas y señalización WebRTC con RLS.

## 2. Google y Facebook

Habilitar ambos proveedores en Supabase Auth. Configurar el callback `nexo://auth-callback` en la app y las URLs autorizadas correspondientes en cada proveedor. Nunca guardar client secrets en el repositorio.

## 3. Firebase Cloud Messaging

Crear/seleccionar un proyecto Firebase para el applicationId `ni.nexo.app`, descargar `google-services.json` localmente y configurar FCM. La tabla `devices` ya está creada para registrar tokens y preferencias de notificación.

## 4. WebRTC / llamadas reales

La base de datos y la UI de llamada ya existen. Para transportar audio/video se necesita conectar un motor WebRTC Android y configurar:

- servidores STUN;
- servidor TURN (Coturn recomendado para producción);
- intercambio de SDP/ICE usando `calls` y `call_signals`;
- permisos de cámara/micrófono y audio focus;
- manejo de llamadas entrantes con notificaciones de alta prioridad.

Hasta conectar el motor WebRTC, el modo demo simula el ciclo de la llamada y Supabase puede registrar/señalizar la sesión.

## 5. Cifrado E2E

El esquema conserva `encryption_version`, `nonce` y `sender_key_id`, pero el protocolo criptográfico de producción debe implementarse con una biblioteca/protocolo auditado (por ejemplo una implementación madura del protocolo Signal), nunca criptografía casera. No se debe declarar E2EE activo antes de completar esta integración.

## 6. Publicación

Antes de Play Store:

- cambiar el repositorio a privado durante el desarrollo sensible;
- preparar política de privacidad, términos y reglas de comunidad;
- completar flujo de eliminación de cuenta/datos;
- probar bloqueo/reporte y moderación;
- probar en al menos dos dispositivos físicos;
- configurar firma de release;
- completar requisitos de testing de Google Play.

## Estado funcional sin conexiones

El modo demo permite probar registro, perfil, discovery, match, bandeja de chats, envío/recepción simulada, estados de mensaje, respuesta, edición, borrado, reacciones, fotos/documentos/cámara, notas de voz, personalización, novedades, llamadas simuladas, privacidad, bloqueo y reportes.