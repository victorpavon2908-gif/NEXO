# NEXO 1.0 RC5 — checklist de prueba física

Usar esta lista antes de conectar servicios externos o publicar.

## Inicio y cuenta

- [ ] Splash oscuro y logo NEXO correcto.
- [ ] Registro por correo en modo demo.
- [ ] Login por correo.
- [ ] Botones Google/Facebook abren el flujo cuando el proveedor esté configurado.
- [ ] Atrás del sistema vuelve a la pantalla correcta.
- [ ] Editar perfil y cambiar foto.
- [ ] Solicitar eliminación de cuenta y confirmar salida.

## Discovery y matches

- [ ] Pasar perfiles sin bloqueos visuales.
- [ ] Like y celebración de match.
- [ ] Match aparece en Matches y Chats.
- [ ] Bloquear elimina a la persona de discovery/matches.
- [ ] Reportar muestra confirmación y no rompe el chat.

## Mensajes

- [ ] Enviar texto y recibir respuesta demo.
- [ ] Estados enviando/enviado/entregado/leído.
- [ ] Responder a un mensaje.
- [ ] Editar mensaje propio.
- [ ] Eliminar mensaje propio.
- [ ] Reaccionar con emoji.
- [ ] Buscar dentro del chat.
- [ ] Galería, video y documento.
- [ ] Cámara.
- [ ] Nota de voz y permiso de micrófono.
- [ ] Vista previa correcta del último mensaje en la bandeja.
- [ ] Fondo/burbujas/tamaño de texto persistentes.
- [ ] Mensajes temporales configurables.
- [ ] Un chat nuevo muestra sugerencias naturales y permite editarlas antes de enviar.

## Contactos

- [ ] El permiso de contactos se solicita solo después de tocar la acción correspondiente.
- [ ] Contactos con NEXO aparecen separados de quienes pueden ser invitados.
- [ ] Buscar filtra por nombre, ciudad o número sin bloquear la pantalla.
- [ ] Tocar un contacto de NEXO abre el chat directamente.
- [ ] Una agenda de más de 500 números se procesa por lotes.
- [ ] Un error del proveedor SMS nunca muestra URL, token ni encabezados técnicos.

## Novedades y llamadas

- [ ] Publicar novedad demo.
- [ ] Hora/fecha amigable, no ISO crudo.
- [ ] Historial de llamadas con estados en español.
- [ ] Llamada de voz simulada.
- [ ] Videollamada simulada.
- [ ] Botón Atrás finaliza correctamente la llamada simulada.

## Android / calidad

- [ ] Icono launcher NEXO visible.
- [ ] Splash nativo Android 12+ coherente.
- [ ] Barra de navegación oscura.
- [ ] Permiso de notificaciones Android 13+.
- [ ] Canales de notificación creados.
- [ ] Teclado no tapa el campo de mensaje.
- [ ] Rotación/reapertura no produce crash.
- [ ] Background/foreground actualiza presencia cuando Supabase esté conectado.
- [ ] Probar al menos 30 minutos para observar batería, memoria y temperatura.
- [ ] Una llamada real no se inicia hasta que WebRTC esté conectado.

## Con dos cuentas reales después de conectar Supabase

- [ ] Registro y confirmación de ambos usuarios.
- [ ] Like recíproco crea un único match.
- [ ] Mensajes Realtime en ambos teléfonos.
- [ ] Fotos/documentos accesibles solo entre participantes.
- [ ] Recibos de lectura correctos.
- [ ] Bloqueo corta lectura/escritura según RLS.
- [ ] Reporte queda registrado.
- [ ] Solicitud de eliminación oculta inmediatamente el perfil.

No marcar WebRTC real, FCM ni E2EE como aprobados hasta conectar y probar sus servicios externos correspondientes.
