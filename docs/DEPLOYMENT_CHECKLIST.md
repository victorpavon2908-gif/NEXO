# NEXO — Checklist de despliegue

Esta lista separa lo que ya está versionado en el repositorio de lo que necesariamente debe configurarse fuera de GitHub antes de publicar en Google Play.

## 1. Backend Supabase

- [ ] Crear el proyecto de producción.
- [ ] Ejecutar **todas** las migraciones de `supabase/migrations/` en orden cronológico.
- [ ] Verificar que las migraciones sociales incluidas en `20260915_nexo_2_0_social.sql` se hayan aplicado correctamente.
- [ ] Crear/verificar buckets privados para fotos de perfil y multimedia de chat.
- [ ] Revisar RLS en producción y probar con dos cuentas distintas.
- [ ] Configurar las Edge Functions necesarias para compras y eliminación de cuentas.
- [ ] No colocar `service_role` ni secretos privados en el APK.

## 2. Android / Google Play

- [ ] Configurar `SUPABASE_URL` y `SUPABASE_PUBLISHABLE_KEY` como secretos del entorno de build.
- [ ] Generar una **clave de firma de producción** y guardarla fuera del repositorio.
- [ ] Configurar `signingConfig` de release usando secretos del CI.
- [ ] Incrementar `versionCode` para cada publicación.
- [ ] Usar una versión estable (por ejemplo `1.0.0`) únicamente cuando la prueba física de producción haya terminado.
- [ ] Generar y revisar el Android App Bundle (`.aab`).
- [ ] Probar instalación, actualización y desinstalación en Android físico.

## 3. Servicios externos

- [ ] Firebase: proyecto de producción y `google-services.json` correspondiente.
- [ ] FCM: probar notificaciones con la app en segundo plano y cerrada.
- [ ] OAuth Google/Facebook: registrar el deep link `nexo://auth-callback` y verificar retorno a la app.
- [ ] WebRTC: configurar STUN/TURN de producción y probar llamada entre dos teléfonos en redes diferentes.
- [ ] Pagos: configurar productos de Google Play Billing y validar compra/restauración en entorno de prueba.

## 4. Seguridad y privacidad

- [ ] Probar registro, inicio de sesión, recuperación y cierre de sesión.
- [ ] Probar bloqueo y reportes.
- [ ] Probar privacidad de mensajes y multimedia.
- [ ] Confirmar que los archivos privados no sean accesibles sin autorización.
- [ ] Probar solicitud y procesamiento de eliminación de cuenta.
- [ ] No anunciar cifrado E2E como activo hasta integrar y auditar el protocolo de cifrado correspondiente.
- [ ] Publicar Política de Privacidad, Términos de Servicio y reglas de contenido.
- [ ] Definir proceso operativo para reportes, moderación y solicitudes de usuarios.

## 5. NEXO Social 2.0

La capa social y su esquema de datos ya están versionados. Antes de presentarla como funcionalidad de producción, validar en dos cuentas:

- Feed y publicaciones.
- Retos y participación.
- Versus y prevención de votos duplicados.
- Círculos y membresías privadas.
- Historias y elecciones.
- Notificaciones.
- NEXO AI y límites Free/Plus.
- Reportar, bloquear y moderar contenido generado por usuarios.

La interfaz social actual es una **capa de producto integrada sobre Discovery**; no debe confundirse con una garantía de que cada acción social ya esté conectada a una API de producción.

## 6. Prueba final de dos teléfonos

1. Cuenta A registra e inicia sesión.
2. Cuenta B registra e inicia sesión.
3. A busca a B.
4. A abre el perfil y envía mensaje según la configuración de privacidad.
5. B recibe la notificación.
6. Ambos intercambian texto y multimedia.
7. Ambos prueban bloquear/reportar.
8. Probar una acción social con datos reales.
9. Probar llamada de audio/video si WebRTC está configurado.
10. Probar compra/restauración de Plus en entorno de pruebas.

## Resultado esperado

El repositorio debe llegar a publicación únicamente después de que los puntos externos anteriores estén configurados y la compilación de release haya sido firmada. El CI actual valida lint y APK debug; una compilación debug correcta no equivale por sí sola a una publicación de Google Play.
