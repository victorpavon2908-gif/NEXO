# NEXO 1.0 RC6 — conexiones externas pendientes

El código Android, navegación, modo demo, base de datos, seguridad, multimedia, privacidad, contactos, descubrimiento, Cita Segura, estados y flujos de llamada están preparados. Para pasar de RC6 a producción faltan integraciones que requieren credenciales, infraestructura o un motor externo.

## 1. Supabase

Crear un proyecto Supabase y ejecutar, en orden:

```text
supabase/migrations/20260908_nexo_0_2.sql
supabase/migrations/20260908_nexo_0_3.sql
supabase/migrations/20260908_nexo_1_0.sql
supabase/migrations/20260908_nexo_1_0_1_security.sql
supabase/migrations/20260908_nexo_1_0_2_polish.sql
supabase/migrations/20260908_nexo_1_0_3_contacts_groups.sql
supabase/migrations/20260909_nexo_1_0_4_whatsapp_contacts.sql
supabase/migrations/20260909_nexo_1_0_5_safety_discovery.sql
```

En `local.properties` local, nunca en GitHub:

```properties
SUPABASE_URL=https://TU-PROYECTO.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_xxxxx
```

La base queda preparada para perfiles, likes, matches, mensajes, multimedia, reacciones, recibos de lectura, mensajes temporales, bloqueos, reportes, preferencias, presencia, dispositivos, novedades, llamadas, señalización y solicitudes de eliminación de cuenta.

## 2. Google y Facebook

Habilitar Google y Facebook en Supabase Auth y configurar sus credenciales. El callback Android ya está preparado como:

```text
nexo://auth-callback
```

La app observa el estado de sesión, por lo que al regresar del navegador puede completar el acceso y abrir el perfil sin volver a pulsar login.

Nunca guardar client secrets en el repositorio o APK.

## 3. Firebase Cloud Messaging

La aplicación ya crea canales Android separados para:

- mensajes/matches;
- llamadas.

También solicita `POST_NOTIFICATIONS` en Android 13+ y la tabla `devices` ya existe para tokens.

Falta:

- crear/seleccionar proyecto Firebase para `ni.nexo.app`;
- agregar `google-services.json` localmente;
- registrar el token FCM;
- implementar backend/Edge Function que envíe push para mensajes, matches y llamadas.

## 4. WebRTC / llamadas reales

La UI de llamada, historial, permisos Android y tablas `calls` + `call_signals` ya están preparadas.

Falta conectar:

- motor WebRTC Android;
- servidores STUN;
- servidor TURN/Coturn para conexiones que no logren P2P;
- intercambio SDP/ICE usando `call_signals`;
- audio focus, Bluetooth/altavoz y cámara;
- llamada entrante mediante push de alta prioridad.

Hasta entonces, el modo demo permite probar visualmente el ciclo de llamada.

## 5. Cifrado E2E

El esquema conserva campos para versión de cifrado, nonce e identificador de clave. No debe implementarse criptografía casera.

Para producción se debe integrar una implementación madura/auditada de un protocolo E2E apropiado y resolver:

- identidad criptográfica por dispositivo;
- prekeys / rotación;
- verificación de dispositivos;
- cifrado de texto y adjuntos;
- recuperación/cambio de dispositivo;
- migración desde mensajes `encryption_version = 0`.

NEXO no debe mostrar “cifrado E2E activo” hasta completar esta integración.

## 6. Eliminación definitiva de cuenta

La base de NEXO agrega `account_deletion_requests` y la RPC `request_my_account_deletion()`.

Al solicitar eliminación desde la app:

1. el perfil se desactiva inmediatamente;
2. se eliminan likes y matches activos;
3. se limpian estados/presencia y se deshabilitan dispositivos;
4. la solicitud queda en cola;
5. se cierra la sesión.

Falta un worker privado o Supabase Edge Function con `service_role` que procese la cola y elimine definitivamente:

- usuario de `auth.users`;
- objetos físicos de `profile-photos`;
- objetos físicos de `chat-media`;
- cualquier dato residual requerido por la política de retención.

La `service_role` solo debe existir en el backend privado, nunca en Android.

## 7. Publicación y operación

Antes de Play Store:

- cambiar el repositorio a privado para trabajo sensible;
- configurar firma release y Play App Signing;
- preparar política de privacidad, términos y reglas de comunidad;
- definir proceso real de moderación de reportes;
- documentar retención y eliminación de datos;
- probar registro, match, bloqueo, reporte y eliminación con dos cuentas reales;
- probar chat/multimedia en dos o más teléfonos físicos;
- probar background/foreground y consumo de batería;
- completar requisitos de testing de Google Play.

## Prueba sin conexiones

El modo demo permite probar ya:

- registro e inicio;
- creación/edición de perfil;
- discovery y match;
- chats y vista previa del último mensaje;
- envío/recepción simulada;
- estados enviado/entregado/leído;
- respuesta, edición, borrado y reacciones;
- fotos, videos, documentos, cámara y notas de voz;
- personalización del chat;
- novedades de 24 horas;
- historial y llamada simulada;
- privacidad, notificaciones, bloqueo y reporte;
- navegación Atrás;
- solicitud de eliminación de cuenta en modo demo.
