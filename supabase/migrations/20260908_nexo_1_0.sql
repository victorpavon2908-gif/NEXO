-- NEXO 1.0 RC - backend integral de mensajería, seguridad, privacidad y comunicación
-- Ejecutar DESPUÉS de 20260908_nexo_0_2.sql y 20260908_nexo_0_3.sql.
-- Esta migración es aditiva: no borra perfiles, matches ni mensajes existentes.

create extension if not exists pgcrypto;

-- PERFIL / PRESENCIA ---------------------------------------------------------
alter table public.profiles
    add column if not exists username text,
    add column if not exists last_seen timestamptz,
    add column if not exists is_online boolean not null default false;

create unique index if not exists profiles_username_unique_idx
    on public.profiles(lower(username)) where username is not null;

create table if not exists public.user_preferences (
    user_id uuid primary key references auth.users(id) on delete cascade,
    theme text not null default 'aurora' check (theme in ('aurora','midnight','carbon','system')),
    bubble_style text not null default 'soft' check (bubble_style in ('soft','compact','glass')),
    text_scale real not null default 1.0 check (text_scale between 0.8 and 1.4),
    read_receipts boolean not null default true,
    show_online boolean not null default true,
    show_last_seen boolean not null default true,
    allow_calls boolean not null default true,
    allow_status_replies boolean not null default true,
    notifications_enabled boolean not null default true,
    notification_preview boolean not null default true,
    disappearing_seconds integer not null default 0 check (disappearing_seconds in (0,3600,86400,604800,7776000)),
    updated_at timestamptz not null default now()
);

create table if not exists public.presence (
    user_id uuid primary key references auth.users(id) on delete cascade,
    online boolean not null default false,
    last_seen timestamptz not null default now(),
    device_id text,
    updated_at timestamptz not null default now()
);

create table if not exists public.devices (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    push_token text not null,
    platform text not null default 'android',
    enabled boolean not null default true,
    last_seen timestamptz not null default now(),
    unique(user_id, push_token)
);

-- SEGURIDAD -----------------------------------------------------------------
create table if not exists public.blocks (
    blocker_id uuid not null references auth.users(id) on delete cascade,
    blocked_id uuid not null references auth.users(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (blocker_id, blocked_id),
    constraint blocks_not_self check (blocker_id <> blocked_id)
);

create table if not exists public.reports (
    id uuid primary key default gen_random_uuid(),
    reporter_id uuid not null references auth.users(id) on delete cascade,
    reported_id uuid not null references auth.users(id) on delete cascade,
    reason text not null check (char_length(reason) between 2 and 80),
    details text not null default '' check (char_length(details) <= 1500),
    status text not null default 'open' check (status in ('open','reviewing','resolved','dismissed')),
    created_at timestamptz not null default now(),
    constraint reports_not_self check (reporter_id <> reported_id)
);

create index if not exists blocks_blocked_idx on public.blocks(blocked_id);
create index if not exists reports_reported_idx on public.reports(reported_id, created_at desc);

create or replace function public.nexo_pair_blocked(a uuid, b uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.blocks
        where (blocker_id = a and blocked_id = b)
           or (blocker_id = b and blocked_id = a)
    );
$$;

create or replace function public.nexo_match_participant(match_uuid uuid, user_uuid uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.matches m
        where m.id = match_uuid
          and (m.user_a = user_uuid or m.user_b = user_uuid)
    );
$$;

-- MENSAJERÍA COMPLETA --------------------------------------------------------
alter table public.messages
    add column if not exists kind text not null default 'text',
    add column if not exists reply_to_id uuid references public.messages(id) on delete set null,
    add column if not exists reply_snapshot text,
    add column if not exists media_path text,
    add column if not exists media_mime text,
    add column if not exists media_size bigint,
    add column if not exists duration_ms bigint,
    add column if not exists edited_at timestamptz,
    add column if not exists deleted_at timestamptz,
    add column if not exists delivered_at timestamptz,
    add column if not exists read_at timestamptz,
    add column if not exists expires_at timestamptz,
    add column if not exists client_id uuid;

create index if not exists messages_reply_idx on public.messages(reply_to_id);
create index if not exists messages_expires_idx on public.messages(expires_at) where expires_at is not null;
create unique index if not exists messages_client_id_unique_idx on public.messages(client_id) where client_id is not null;

create table if not exists public.message_reactions (
    message_id uuid not null references public.messages(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    emoji text not null check (char_length(emoji) between 1 and 16),
    created_at timestamptz not null default now(),
    primary key (message_id, user_id)
);

create index if not exists message_reactions_message_idx on public.message_reactions(message_id);

-- ESTADOS / NOVEDADES --------------------------------------------------------
create table if not exists public.status_updates (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    kind text not null default 'text' check (kind in ('text','image','video')),
    text text not null default '' check (char_length(text) <= 1500),
    media_path text,
    background text,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null default (now() + interval '24 hours')
);

create index if not exists status_updates_expiry_idx on public.status_updates(expires_at);
create index if not exists status_updates_owner_idx on public.status_updates(owner_id, created_at desc);

-- LLAMADAS / SEÑALIZACIÓN WEBRTC -------------------------------------------
create table if not exists public.calls (
    id uuid primary key default gen_random_uuid(),
    caller_id uuid not null references auth.users(id) on delete cascade,
    callee_id uuid not null references auth.users(id) on delete cascade,
    call_type text not null check (call_type in ('audio','video')),
    state text not null default 'ringing' check (state in ('ringing','connecting','connected','declined','missed','ended','failed')),
    started_at timestamptz not null default now(),
    answered_at timestamptz,
    ended_at timestamptz,
    constraint calls_not_self check (caller_id <> callee_id)
);

create table if not exists public.call_signals (
    id bigint generated by default as identity primary key,
    call_id uuid not null references public.calls(id) on delete cascade,
    sender_id uuid not null references auth.users(id) on delete cascade,
    signal_type text not null check (signal_type in ('offer','answer','ice','hangup')),
    payload jsonb not null,
    created_at timestamptz not null default now()
);

create index if not exists calls_caller_idx on public.calls(caller_id, started_at desc);
create index if not exists calls_callee_idx on public.calls(callee_id, started_at desc);
create index if not exists call_signals_call_idx on public.call_signals(call_id, id);

-- RLS -----------------------------------------------------------------------
alter table public.user_preferences enable row level security;
alter table public.presence enable row level security;
alter table public.devices enable row level security;
alter table public.blocks enable row level security;
alter table public.reports enable row level security;
alter table public.message_reactions enable row level security;
alter table public.status_updates enable row level security;
alter table public.calls enable row level security;
alter table public.call_signals enable row level security;

-- Perfil: no mostrar usuarios bloqueados en ninguna dirección.
drop policy if exists profiles_read_active on public.profiles;
create policy profiles_read_active
on public.profiles for select
to authenticated
using (
    id = auth.uid()
    or (
        is_active = true
        and not public.nexo_pair_blocked(id, auth.uid())
    )
);

-- Matches: ocultos si existe bloqueo.
drop policy if exists matches_read_participant on public.matches;
create policy matches_read_participant
on public.matches for select
to authenticated
using (
    (user_a = auth.uid() or user_b = auth.uid())
    and not public.nexo_pair_blocked(user_a, user_b)
);

-- Likes: no permitir likes hacia usuarios bloqueados.
drop policy if exists likes_insert_own on public.likes;
create policy likes_insert_own
on public.likes for insert
to authenticated
with check (
    actor_id = auth.uid()
    and target_id <> auth.uid()
    and not public.nexo_pair_blocked(actor_id, target_id)
);

drop policy if exists likes_update_own on public.likes;
create policy likes_update_own
on public.likes for update
to authenticated
using (actor_id = auth.uid())
with check (
    actor_id = auth.uid()
    and target_id <> auth.uid()
    and not public.nexo_pair_blocked(actor_id, target_id)
);

-- Mensajes: participantes del match y sin bloqueo activo.
drop policy if exists messages_read_match_participants on public.messages;
create policy messages_read_match_participants
on public.messages for select
to authenticated
using (
    exists (
        select 1 from public.matches m
        where m.id = messages.match_id
          and (m.user_a = auth.uid() or m.user_b = auth.uid())
          and not public.nexo_pair_blocked(m.user_a, m.user_b)
    )
    and (expires_at is null or expires_at > now())
);

drop policy if exists messages_insert_match_participants on public.messages;
create policy messages_insert_match_participants
on public.messages for insert
to authenticated
with check (
    sender_id = auth.uid()
    and exists (
        select 1 from public.matches m
        where m.id = messages.match_id
          and (m.user_a = auth.uid() or m.user_b = auth.uid())
          and not public.nexo_pair_blocked(m.user_a, m.user_b)
    )
);

drop policy if exists messages_update_sender on public.messages;
create policy messages_update_sender
on public.messages for update
to authenticated
using (
    sender_id = auth.uid()
    or exists (
        select 1 from public.matches m
        where m.id = messages.match_id
          and (m.user_a = auth.uid() or m.user_b = auth.uid())
    )
)
with check (
    exists (
        select 1 from public.matches m
        where m.id = messages.match_id
          and (m.user_a = auth.uid() or m.user_b = auth.uid())
    )
);

-- Reacciones.
drop policy if exists message_reactions_read_participants on public.message_reactions;
create policy message_reactions_read_participants
on public.message_reactions for select
to authenticated
using (
    exists (
        select 1 from public.messages msg
        join public.matches m on m.id = msg.match_id
        where msg.id = message_reactions.message_id
          and (m.user_a = auth.uid() or m.user_b = auth.uid())
    )
);

drop policy if exists message_reactions_write_own on public.message_reactions;
create policy message_reactions_write_own
on public.message_reactions for insert
to authenticated
with check (
    user_id = auth.uid()
    and exists (
        select 1 from public.messages msg
        join public.matches m on m.id = msg.match_id
        where msg.id = message_reactions.message_id
          and (m.user_a = auth.uid() or m.user_b = auth.uid())
    )
);

drop policy if exists message_reactions_update_own on public.message_reactions;
create policy message_reactions_update_own
on public.message_reactions for update
to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

drop policy if exists message_reactions_delete_own on public.message_reactions;
create policy message_reactions_delete_own
on public.message_reactions for delete
to authenticated
using (user_id = auth.uid());

-- Preferencias / presencia / dispositivos.
drop policy if exists user_preferences_own on public.user_preferences;
create policy user_preferences_own on public.user_preferences
for all to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

drop policy if exists presence_read_authenticated on public.presence;
create policy presence_read_authenticated on public.presence
for select to authenticated using (true);

drop policy if exists presence_write_own on public.presence;
create policy presence_write_own on public.presence
for all to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

drop policy if exists devices_own on public.devices;
create policy devices_own on public.devices
for all to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

-- Bloqueos / reportes.
drop policy if exists blocks_read_own on public.blocks;
create policy blocks_read_own on public.blocks
for select to authenticated
using (blocker_id = auth.uid());

drop policy if exists blocks_insert_own on public.blocks;
create policy blocks_insert_own on public.blocks
for insert to authenticated
with check (blocker_id = auth.uid() and blocked_id <> auth.uid());

drop policy if exists blocks_delete_own on public.blocks;
create policy blocks_delete_own on public.blocks
for delete to authenticated
using (blocker_id = auth.uid());

drop policy if exists reports_read_own on public.reports;
create policy reports_read_own on public.reports
for select to authenticated using (reporter_id = auth.uid());

drop policy if exists reports_insert_own on public.reports;
create policy reports_insert_own on public.reports
for insert to authenticated
with check (reporter_id = auth.uid() and reported_id <> auth.uid());

-- Estados.
drop policy if exists status_updates_read_active on public.status_updates;
create policy status_updates_read_active on public.status_updates
for select to authenticated
using (
    expires_at > now()
    and not public.nexo_pair_blocked(owner_id, auth.uid())
);

drop policy if exists status_updates_write_own on public.status_updates;
create policy status_updates_write_own on public.status_updates
for insert to authenticated
with check (owner_id = auth.uid());

drop policy if exists status_updates_delete_own on public.status_updates;
create policy status_updates_delete_own on public.status_updates
for delete to authenticated
using (owner_id = auth.uid());

-- Llamadas y señalización.
drop policy if exists calls_read_participants on public.calls;
create policy calls_read_participants on public.calls
for select to authenticated
using (caller_id = auth.uid() or callee_id = auth.uid());

drop policy if exists calls_insert_caller on public.calls;
create policy calls_insert_caller on public.calls
for insert to authenticated
with check (
    caller_id = auth.uid()
    and caller_id <> callee_id
    and not public.nexo_pair_blocked(caller_id, callee_id)
);

drop policy if exists calls_update_participants on public.calls;
create policy calls_update_participants on public.calls
for update to authenticated
using (caller_id = auth.uid() or callee_id = auth.uid())
with check (caller_id = auth.uid() or callee_id = auth.uid());

drop policy if exists call_signals_read_participants on public.call_signals;
create policy call_signals_read_participants on public.call_signals
for select to authenticated
using (
    exists (
        select 1 from public.calls c
        where c.id = call_signals.call_id
          and (c.caller_id = auth.uid() or c.callee_id = auth.uid())
    )
);

drop policy if exists call_signals_insert_participants on public.call_signals;
create policy call_signals_insert_participants on public.call_signals
for insert to authenticated
with check (
    sender_id = auth.uid()
    and exists (
        select 1 from public.calls c
        where c.id = call_signals.call_id
          and (c.caller_id = auth.uid() or c.callee_id = auth.uid())
    )
);

-- STORAGE -------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'chat-media',
    'chat-media',
    false,
    26214400,
    array[
        'image/jpeg','image/png','image/webp','image/gif',
        'video/mp4','video/webm',
        'audio/mpeg','audio/mp4','audio/aac','audio/ogg','audio/webm',
        'application/pdf','text/plain','application/zip'
    ]
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

-- Ruta obligatoria: <match_id>/<user_id>/<archivo>
drop policy if exists chat_media_read_match on storage.objects;
create policy chat_media_read_match
on storage.objects for select
to authenticated
using (
    bucket_id = 'chat-media'
    and exists (
        select 1 from public.matches m
        where m.id::text = (storage.foldername(name))[1]
          and (m.user_a = auth.uid() or m.user_b = auth.uid())
          and not public.nexo_pair_blocked(m.user_a, m.user_b)
    )
);

drop policy if exists chat_media_insert_match on storage.objects;
create policy chat_media_insert_match
on storage.objects for insert
to authenticated
with check (
    bucket_id = 'chat-media'
    and (storage.foldername(name))[2] = auth.uid()::text
    and exists (
        select 1 from public.matches m
        where m.id::text = (storage.foldername(name))[1]
          and (m.user_a = auth.uid() or m.user_b = auth.uid())
          and not public.nexo_pair_blocked(m.user_a, m.user_b)
    )
);

drop policy if exists chat_media_delete_own on storage.objects;
create policy chat_media_delete_own
on storage.objects for delete
to authenticated
using (
    bucket_id = 'chat-media'
    and (storage.foldername(name))[2] = auth.uid()::text
);

-- Realtime para experiencia tipo mensajería moderna.
do $$
declare
    table_name text;
begin
    foreach table_name in array array['messages','message_reactions','presence','calls','call_signals','status_updates']
    loop
        if not exists (
            select 1 from pg_publication_tables
            where pubname = 'supabase_realtime'
              and schemaname = 'public'
              and tablename = table_name
        ) then
            execute format('alter publication supabase_realtime add table public.%I', table_name);
        end if;
    end loop;
end
$$;

-- Permisos explícitos.
grant select, insert, update on public.messages to authenticated;
grant select, insert, update, delete on public.message_reactions to authenticated;
grant select, insert, update on public.user_preferences to authenticated;
grant select, insert, update on public.presence to authenticated;
grant select, insert, update, delete on public.devices to authenticated;
grant select, insert, delete on public.blocks to authenticated;
grant select, insert on public.reports to authenticated;
grant select, insert, delete on public.status_updates to authenticated;
grant select, insert, update on public.calls to authenticated;
grant select, insert on public.call_signals to authenticated;

revoke all on function public.nexo_pair_blocked(uuid, uuid) from public;
revoke all on function public.nexo_match_participant(uuid, uuid) from public;
grant execute on function public.nexo_pair_blocked(uuid, uuid) to authenticated;
grant execute on function public.nexo_match_participant(uuid, uuid) to authenticated;