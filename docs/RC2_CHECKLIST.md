# NEXO 1.0 RC8 — checklist de prueba física

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
- [ ] Filtros de edad, ciudad, intención y disponibilidad cambian los perfiles visibles.
- [ ] Al quedar sin resultados se puede volver directamente a los filtros.
- [ ] Pausar perfil evita que una segunda cuenta lo descubra.

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
- [ ] La pregunta del día prepara una respuesta editable antes de publicarla.
- [ ] Crear una sala por interés desde una sugerencia.
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

## NEXO Plus y pagos

- [ ] Sin Edge Function configurada, todos los botones de compra permanecen bloqueados.
- [ ] Los precios se muestran desde Play Console en la moneda de la cuenta.
- [ ] Compra mensual/anual activa los filtros avanzados y los estilos Carbon/Glass.
- [ ] El impulso de 24 horas puede volver a comprarse después de consumirse.
- [ ] Cancelar Google Play no concede beneficios ni muestra un cargo exitoso.
- [ ] Restaurar compras recupera una suscripción válida.
- [ ] Un token repetido no vuelve a sumar horas de impulso.
- [ ] Cita Segura, contactos, mensajes, bloqueos y reportes siguen disponibles gratis.

## Emojis, stickers y personalización

- [ ] El panel cambia entre Emojis y Stickers sin tapar permanentemente el compositor.
- [ ] Las categorías y el historial recuerdan correctamente los últimos emojis usados.
- [ ] Los stickers gratuitos se envían y renderizan en chats privados y grupos.
- [ ] Los stickers bloqueados abren NEXO Plus y no se envían sin derecho activo.
- [ ] Los seis fondos, cinco burbujas y cinco acentos conservan buena legibilidad.
- [ ] Al vencer Plus, un estilo premium vuelve a Aurora, Cian y Suave.

## Cita Segura y privacidad

- [ ] Crear plan con lugar público, hora y contacto de confianza.
- [ ] El SMS se abre como borrador y nunca se envía automáticamente.
- [ ] “Estoy bien” cambia el estado del plan.
- [ ] Llamar al contacto abre el marcador sin iniciar la llamada.
- [ ] Cancelar un plan cambia su estado.
- [ ] Las fotos recibidas quedan protegidas hasta tocarlas.
- [ ] “Cerrar con respeto” prepara el texto sin enviarlo automáticamente.
- [ ] “Ocultarme de mis contactos” desactiva el descubrimiento por agenda.

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
