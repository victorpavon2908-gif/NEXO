# Supabase para NEXO 0.2

## 1. Crear el proyecto

Creá un proyecto gratuito en Supabase.

## 2. Crear el esquema

Abrí SQL Editor y ejecutá `migrations/20260908_nexo_0_2.sql` completo.

El script crea:

- `profiles`: perfil público mínimo de cada usuario.
- `likes`: intereses unidireccionales.
- `matches`: solo se llena automáticamente cuando existen likes recíprocos.
- RLS para que cada usuario solo pueda modificar sus propios datos.
- Un trigger de base de datos que decide el match; el teléfono no puede falsificarlo.

## 3. Configurar Android sin subir claves a GitHub

En `local.properties` agregá:

```properties
SUPABASE_URL=https://TU-PROYECTO.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_TU_CLAVE
```

`local.properties` ya está ignorado por Git.

La publishable key está diseñada para clientes móviles; la seguridad real depende de RLS. **Nunca** pongás una `service_role` o secret key dentro de Android.

## 4. Confirmación de correo

Supabase normalmente puede exigir confirmación de correo. Para pruebas internas podés desactivar temporalmente `Confirm email`; para producción configuraremos deep links/OTP correctamente.

## 5. Modo demo

Si las variables anteriores están vacías, NEXO arranca en modo demo y usa perfiles locales. Esto permite que GitHub Actions compile el proyecto sin secretos y que el diseño siga siendo testeable.
