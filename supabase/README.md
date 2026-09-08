# Supabase para NEXO 0.3

## 1. Crear el proyecto

Creá un proyecto en Supabase.

## 2. Ejecutar migraciones

Abrí SQL Editor y ejecutá, en este orden:

1. `migrations/20260908_nexo_0_2.sql`
2. `migrations/20260908_nexo_0_3.sql`

0.2 crea perfiles, likes, matches, RLS y el trigger de match recíproco.

0.3 agrega:

- `profiles.photo_url`.
- `messages`, ligados obligatoriamente a un match.
- RLS de chat: solo los dos participantes pueden leer y enviar.
- Publicación de `messages` en `supabase_realtime`.
- Bucket `profile-photos` de máximo 5 MB.
- Políticas de Storage para que cada usuario solo escriba en su propia carpeta.
- Campos reservados para cifrado E2E futuro: `encryption_version`, `nonce` y `sender_key_id`.

## 3. Configurar Android sin subir claves a GitHub

En `local.properties` agregá:

```properties
SUPABASE_URL=https://TU-PROYECTO.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_TU_CLAVE
```

`local.properties` ya está ignorado por Git.

La publishable key está diseñada para clientes móviles; la seguridad real depende de RLS. **Nunca** pongás una `service_role` o secret key dentro de Android.

## 4. Fotografías

El bucket `profile-photos` es público para lectura porque las fotos forman parte del perfil visible. Las escrituras siguen protegidas: un usuario autenticado solo puede subir, actualizar o borrar archivos dentro de `<su-user-id>/...`.

Formatos permitidos: JPEG, PNG y WebP. Tamaño máximo: 5 MB.

## 5. Chat Realtime

La app consulta el match real antes de abrir una conversación. Los mensajes se insertan en `messages` con `match_id` y `sender_id`. Supabase Realtime escucha cambios de esa conversación y actualiza la UI sin recargar la pantalla.

En 0.3 `encryption_version = 0` significa que `payload` contiene texto plano. La base ya admite una futura versión cifrada, pero la UI no afirma que exista E2E hasta implementarlo.

## 6. Modo demo

Si las variables de Supabase están vacías, NEXO arranca en modo demo. El proyecto sigue compilando en GitHub Actions sin secretos y permite probar el flujo general, pero no sube fotos reales ni usa el backend.
