# Monetización segura de NEXO

## Qué se cobra

### NEXO Plus mensual y anual

- Filtros por ciudad, intención y disponibilidad en línea.
- Tema Carbon y burbujas Glass.
- Prioridad moderada en descubrimiento sin ocultar perfiles gratuitos.
- Restauración mediante la cuenta de Google Play.

### Impulso de perfil por 24 horas

- Producto consumible de una sola compra.
- Sube temporalmente la prioridad del perfil.
- Cada token puede otorgar el beneficio una sola vez.

## Qué siempre permanece gratis

- Contactos y mensajes.
- Bloquear y reportar.
- Cita Segura y controles de privacidad.
- Filtro de edad y pausa de perfil.
- Verificación de identidad o teléfono.

Nunca se vende una insignia de identidad y nunca se cobra por salir de una situación insegura.

## Productos en Play Console

| ID | Tipo | Precio inicial sugerido |
|---|---|---:|
| `nexo_plus_monthly` | Suscripción | USD 2.49/mes |
| `nexo_plus_yearly` | Suscripción | USD 19.99/año |
| `nexo_boost_24h` | Producto consumible | USD 0.99 |

Los precios son una hipótesis inicial. Configurarlos en Play Console y permitir que Google Play aplique moneda, impuestos y métodos disponibles por país. No escribir precios dentro del APK.

## Activación

1. Ejecutar `20260909_nexo_1_0_6_monetization.sql`.
2. Crear los tres productos en Play Console con exactamente los IDs anteriores.
3. Vincular Play Console con un proyecto Google Cloud y habilitar Google Play Android Developer API.
4. Crear una cuenta de servicio con acceso mínimo a pedidos y suscripciones.
5. Desplegar `supabase/functions/verify-google-play-purchase`.
6. Guardar únicamente como secretos de la función:
   - `GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL`
   - `GOOGLE_PLAY_PRIVATE_KEY`
7. Probar con una pista de prueba cerrada y cuentas de prueba de licencia.

La clave privada y `SUPABASE_SERVICE_ROLE_KEY` nunca deben incluirse en Android, GitHub ni Play Console como texto público.

## Flujo de seguridad

1. Google Play procesa el pago.
2. Android recibe un `purchaseToken`.
3. La Edge Function valida el token directamente con Google.
4. Una función SQL atómica registra el hash del token y concede el derecho.
5. Android confirma o consume la compra solo después de la verificación.
6. La app vuelve a leer los derechos; nunca confía en una preferencia local.

Antes de producción se debe conectar Real-time Developer Notifications para renovaciones, cancelaciones, reembolsos y periodos de gracia aunque la persona no abra la pantalla Plus.
