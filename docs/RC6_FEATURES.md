# NEXO RC6 — Descubrimiento y Cita Segura

## Funciones incorporadas

- Filtros persistentes: edad, ciudad, intención y personas en línea.
- Pausa de perfil sin borrar la cuenta.
- Cita Segura con lugar público, hora de confirmación, contacto de confianza y código secreto.
- El código secreto se almacena como SHA-256 en Supabase; nunca se recupera en texto plano.
- Compartir el plan abre la aplicación de SMS con un borrador: NEXO no lo envía automáticamente.
- Botón “Estoy bien” y llamada manual al contacto de confianza.
- Ocultar el perfil de la sincronización de contactos.
- Ubicación aproximada como valor predeterminado.
- Fotografías recibidas protegidas hasta que la persona toca para mostrarlas.
- Plantilla para cerrar conversaciones con respeto.
- Pantalla informativa NEXO Plus sin compras ficticias.

## Activación en Supabase

Ejecutar, en orden, todas las migraciones anteriores y luego:

`supabase/migrations/20260909_nexo_1_0_5_safety_discovery.sql`

La migración crea `discovery_preferences`, `safe_date_plans`, políticas RLS y los nuevos controles de privacidad. No habilita SMS automático, pagos, WebRTC ni servicios de emergencia.

## Pruebas manuales mínimas

1. Guardar distintos filtros y confirmar que la lista se actualiza.
2. Pausar un perfil y confirmar desde una segunda cuenta que deja de aparecer.
3. Crear un plan seguro y revisar el borrador de SMS antes de enviarlo.
4. Confirmar “Estoy bien” y verificar el cambio de estado.
5. Activar “Ocultarme de mis contactos” y comprobar que se deshabilita el descubrimiento por agenda.
6. Recibir una fotografía y confirmar que aparece protegida hasta tocarla.
7. Abrir NEXO Plus y comprobar que no existe ningún cobro activo.

## Pendiente de infraestructura

- Recordatorios automáticos en segundo plano y escalamiento de una cita no confirmada.
- Firebase Cloud Messaging.
- WebRTC auditado para llamadas reales.
- Procesamiento de pagos para NEXO Plus.
- Moderación automática y revisión humana.
- Auditoría criptográfica antes de anunciar cifrado E2EE.
