-- NEXO 1.0.0 RC10 - E2EE de mensajes + señalización WebRTC endurecida
-- Ejecutar después de 20260910_nexo_1_0_8_rc9_calls.sql.

-- ---------------------------------------------------------------------------
-- IDENTIDAD E2EE
-- La clave privada NUNCA se almacena en Supabase. Solo se publica la clave
-- pública RSA del dispositivo. El cliente cifra el contenido antes de enviarlo.
-- ---------------------------------------------------------------------------
create table if not exists public.e2ee_identity_keys (
    user_id uuid primary key references auth.users(id) on delete cascade,
    key_id text not null,
    public_key text not null,
    algorithm text not null default 'RSA-OAEP-256',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint e2ee_identity_key_id_len check (char_length(key_id) between 8 and 128),
    constraint e2ee_identity_public_key_len check (char_length(public_key) between 64 and 8192)
);

alter table public.e2ee_identity_keys enable row level security;

-- Una persona puede leer su propia clave o la de alguien con quien tiene match.
drop policy if exists e2ee_identity_keys_read_matches on public.e2ee_identity_keys;
create policy e2ee_identity_keys_read_matches
on public.e2ee_identity_keys for select
to authenticated
using (
    user_id = auth.uid()
    or exists (
        select 1
        from public.matches m
        where (m.user_a = auth.uid() and m.user_b = e2ee_identity_keys.user_id)
           or (m.user_b = auth.uid() and m.user_a = e2ee_identity_keys.user_id)
    )
);

drop policy if exists e2ee_identity_keys_insert_own on public.e2ee_identity_keys;
create policy e2ee_identity_keys_insert_own
on public.e2ee_identity_keys for insert
to authenticated
with check (user_id = auth.uid());

drop policy if exists e2ee_identity_keys_update_own on public.e2ee_identity_keys;
create policy e2ee_identity_keys_update_own
on public.e2ee_identity_keys for update
to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

grant select, insert, update on public.e2ee_identity_keys to authenticated;

-- ---------------------------------------------------------------------------
-- MENSAJES E2EE v1
-- payload pasa a contener ciphertext Base64. La clave AES por mensaje queda
-- envuelta dos veces: una para el emisor y otra para el receptor.
-- ---------------------------------------------------------------------------
alter table public.messages
    add column if not exists recipient_key_id text,
    add column if not exists wrapped_key_sender text,
    add column if not exists wrapped_key_recipient text,
    add column if not exists media_encryption_version smallint not null default 0,
    add column if not exists media_nonce text,
    add column if not exists media_wrapped_key_sender text,
    add column if not exists media_wrapped_key_recipient text;

-- Un texto de 4000 caracteres aumenta de tamaño al pasar a AES-GCM + Base64.
alter table public.messages drop constraint if exists messages_payload_check;
alter table public.messages
    add constraint messages_payload_check
    check (char_length(payload) between 1 and 8192);

alter table public.messages drop constraint if exists messages_crypto_shape;
alter table public.messages
    add constraint messages_crypto_shape
    check (
        encryption_version = 0
        or (
            encryption_version = 1
            and nonce is not null
            and sender_key_id is not null
            and recipient_key_id is not null
            and wrapped_key_sender is not null
            and wrapped_key_recipient is not null
        )
    );

alter table public.messages drop constraint if exists messages_media_crypto_shape;
alter table public.messages
    add constraint messages_media_crypto_shape
    check (
        media_encryption_version = 0
        or (
            media_encryption_version = 1
            and media_path is not null
            and media_nonce is not null
            and media_wrapped_key_sender is not null
            and media_wrapped_key_recipient is not null
        )
    ) not valid;

alter table public.messages validate constraint messages_media_crypto_shape;

-- ---------------------------------------------------------------------------
-- LLAMADAS: solo matches activos, preferencia allow_calls y sin bloqueo.
-- ---------------------------------------------------------------------------
drop policy if exists calls_insert_caller on public.calls;
create policy calls_insert_caller
on public.calls for insert
to authenticated
with check (
    caller_id = auth.uid()
    and caller_id <> callee_id
    and not public.nexo_pair_blocked(caller_id, callee_id)
    and exists (
        select 1 from public.matches m
        where (m.user_a = caller_id and m.user_b = callee_id)
           or (m.user_b = caller_id and m.user_a = callee_id)
    )
    and coalesce(
        (select up.allow_calls from public.user_preferences up where up.user_id = callee_id),
        true
    )
);

-- Señales WebRTC solamente para participantes de la llamada.
drop policy if exists call_signals_read_participants on public.call_signals;
create policy call_signals_read_participants
on public.call_signals for select
to authenticated
using (
    exists (
        select 1 from public.calls c
        where c.id = call_signals.call_id
          and (c.caller_id = auth.uid() or c.callee_id = auth.uid())
    )
);

drop policy if exists call_signals_insert_participants on public.call_signals;
create policy call_signals_insert_participants
on public.call_signals for insert
to authenticated
with check (
    sender_id = auth.uid()
    and exists (
        select 1 from public.calls c
        where c.id = call_signals.call_id
          and (c.caller_id = auth.uid() or c.callee_id = auth.uid())
          and c.state in ('ringing','connecting','connected')
    )
);

grant select, insert on public.call_signals to authenticated;

-- Limpia señales viejas al terminar una llamada sin hacerlas accesibles a terceros.
create or replace function public.nexo_cleanup_call_signals(target_call uuid)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    affected integer;
begin
    if not exists (
        select 1 from public.calls c
        where c.id = target_call
          and (c.caller_id = auth.uid() or c.callee_id = auth.uid())
          and c.state in ('ended','declined','missed','failed')
    ) then
        return 0;
    end if;

    delete from public.call_signals where call_id = target_call;
    get diagnostics affected = row_count;
    return affected;
end;
$$;

revoke all on function public.nexo_cleanup_call_signals(uuid) from public;
grant execute on function public.nexo_cleanup_call_signals(uuid) to authenticated;

-- Realtime para claves (rotación) y llamadas.
do $$
begin
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'e2ee_identity_keys'
    ) then
        alter publication supabase_realtime add table public.e2ee_identity_keys;
    end if;
end
$$;
