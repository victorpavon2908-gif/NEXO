-- NEXO 1.0 RC3 - contactos verificados, grupos y comunicación enriquecida
-- Ejecutar DESPUÉS de 20260908_nexo_1_0_2_polish.sql.
-- Aditiva: no borra usuarios, matches, mensajes ni estados existentes.

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------------------
-- TELÉFONO VERIFICADO + DIRECTORIO DE CONTACTOS PRIVADO
-- ---------------------------------------------------------------------------

alter table public.profiles
    add column if not exists phone_verified boolean not null default false;

-- El hash nunca se expone mediante profiles. Solo vive en este directorio con RLS.
create table if not exists public.contact_directory (
    user_id uuid primary key references auth.users(id) on delete cascade,
    phone_hash text not null unique,
    discoverable boolean not null default false,
    phone_verified_at timestamptz,
    updated_at timestamptz not null default now()
);

alter table public.contact_directory enable row level security;

-- El usuario solo puede leer su propia configuración. Las búsquedas se hacen por RPC.
drop policy if exists contact_directory_read_own on public.contact_directory;
create policy contact_directory_read_own
on public.contact_directory for select
to authenticated
using (user_id = auth.uid());

-- Sin insert/update directos desde Android: solo funciones SECURITY DEFINER.

create or replace function public.nexo_phone_hash(phone_value text)
returns text
language sql
immutable
strict
set search_path = public, extensions
as $$
    select encode(digest(regexp_replace(phone_value, '[^0-9]', '', 'g'), 'sha256'), 'hex');
$$;

create or replace function public.nexo_sync_auth_phone_directory()
returns trigger
language plpgsql
security definer
set search_path = public, auth, extensions
as $$
begin
    if new.phone is not null and new.phone <> '' and new.phone_confirmed_at is not null then
        insert into public.contact_directory(user_id, phone_hash, phone_verified_at, updated_at)
        values (new.id, public.nexo_phone_hash(new.phone), new.phone_confirmed_at, now())
        on conflict (user_id) do update
        set phone_hash = excluded.phone_hash,
            phone_verified_at = excluded.phone_verified_at,
            updated_at = now();

        update public.profiles
        set phone_verified = true
        where id = new.id;
    else
        delete from public.contact_directory where user_id = new.id;
        update public.profiles set phone_verified = false where id = new.id;
    end if;
    return new;
end;
$$;

drop trigger if exists nexo_auth_phone_sync on auth.users;
create trigger nexo_auth_phone_sync
after insert or update of phone, phone_confirmed_at on auth.users
for each row execute function public.nexo_sync_auth_phone_directory();

-- Evita que el cliente pueda falsificar phone_verified al guardar el perfil.
create or replace function public.nexo_force_profile_phone_verified()
returns trigger
language plpgsql
security definer
set search_path = public, auth
as $$
begin
    new.phone_verified := exists (
        select 1 from auth.users u
        where u.id = new.id
          and u.phone is not null
          and u.phone <> ''
          and u.phone_confirmed_at is not null
    );
    return new;
end;
$$;

drop trigger if exists profiles_force_phone_verified on public.profiles;
create trigger profiles_force_phone_verified
before insert or update on public.profiles
for each row execute function public.nexo_force_profile_phone_verified();

-- Sincroniza usuarios ya existentes que hayan verificado teléfono antes de esta migración.
insert into public.contact_directory(user_id, phone_hash, phone_verified_at, updated_at)
select id, public.nexo_phone_hash(phone), phone_confirmed_at, now()
from auth.users
where phone is not null and phone <> '' and phone_confirmed_at is not null
on conflict (user_id) do update
set phone_hash = excluded.phone_hash,
    phone_verified_at = excluded.phone_verified_at,
    updated_at = now();

update public.profiles p
set phone_verified = exists (
    select 1 from auth.users u
    where u.id = p.id
      and u.phone is not null
      and u.phone <> ''
      and u.phone_confirmed_at is not null
);

create or replace function public.get_my_contact_settings()
returns table(phone text, phone_verified boolean, discoverable boolean)
language plpgsql
stable
security definer
set search_path = public, auth
as $$
begin
    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;

    return query
    select
        u.phone,
        (u.phone is not null and u.phone <> '' and u.phone_confirmed_at is not null),
        coalesce(cd.discoverable, false)
    from auth.users u
    left join public.contact_directory cd on cd.user_id = u.id
    where u.id = auth.uid();
end;
$$;

create or replace function public.set_contact_discoverable(enabled boolean)
returns void
language plpgsql
security definer
set search_path = public, auth, extensions
as $$
declare
    current_phone text;
    confirmed_at timestamptz;
begin
    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;

    select phone, phone_confirmed_at
      into current_phone, confirmed_at
    from auth.users
    where id = auth.uid();

    if current_phone is null or current_phone = '' or confirmed_at is null then
        raise exception 'Verify your phone before enabling contact discovery';
    end if;

    insert into public.contact_directory(user_id, phone_hash, discoverable, phone_verified_at, updated_at)
    values (auth.uid(), public.nexo_phone_hash(current_phone), enabled, confirmed_at, now())
    on conflict (user_id) do update
    set phone_hash = excluded.phone_hash,
        discoverable = excluded.discoverable,
        phone_verified_at = excluded.phone_verified_at,
        updated_at = now();
end;
$$;

-- Recibe solamente hashes SHA-256 de teléfonos normalizados por el dispositivo.
-- Límite de 500 por llamada para evitar barridos masivos accidentales.
create or replace function public.find_nexo_contacts(phone_hashes text[])
returns table(
    phone_hash text,
    id uuid,
    name text,
    age smallint,
    city text,
    bio text,
    intention text,
    interests text[],
    verified boolean,
    photo_url text,
    is_online boolean,
    last_seen timestamptz,
    phone_verified boolean
)
language plpgsql
stable
security definer
set search_path = public
as $$
begin
    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;

    if coalesce(array_length(phone_hashes, 1), 0) > 500 then
        raise exception 'Too many contacts in one lookup';
    end if;

    return query
    select
        cd.phone_hash,
        p.id,
        p.name,
        p.age,
        p.city,
        p.bio,
        p.intention,
        p.interests,
        p.verified,
        p.photo_url,
        case when coalesce(pref.show_online, true) then p.is_online else false end,
        case when coalesce(pref.show_last_seen, true) then p.last_seen else null end,
        p.phone_verified
    from public.contact_directory cd
    join public.profiles p on p.id = cd.user_id
    left join public.user_preferences pref on pref.user_id = p.id
    where cd.discoverable = true
      and cd.phone_hash = any(phone_hashes)
      and p.is_active = true
      and p.id <> auth.uid()
      and not public.nexo_pair_blocked(p.id, auth.uid());
end;
$$;

-- ---------------------------------------------------------------------------
-- GRUPOS
-- ---------------------------------------------------------------------------

create table if not exists public.groups (
    id uuid primary key default gen_random_uuid(),
    name text not null check (char_length(name) between 1 and 80),
    owner_id uuid not null references auth.users(id) on delete cascade,
    photo_path text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.group_members (
    group_id uuid not null references public.groups(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    role text not null default 'member' check (role in ('owner','admin','member')),
    added_by uuid references auth.users(id) on delete set null,
    joined_at timestamptz not null default now(),
    primary key (group_id, user_id)
);

create table if not exists public.group_messages (
    id uuid primary key default gen_random_uuid(),
    group_id uuid not null references public.groups(id) on delete cascade,
    sender_id uuid not null references auth.users(id) on delete cascade,
    payload text not null default '' check (char_length(payload) <= 4000),
    kind text not null default 'text',
    reply_to_id uuid references public.group_messages(id) on delete set null,
    reply_snapshot text,
    media_path text,
    media_mime text,
    media_size bigint,
    duration_ms bigint,
    edited_at timestamptz,
    deleted_at timestamptz,
    created_at timestamptz not null default now()
);

create index if not exists group_members_user_idx on public.group_members(user_id, joined_at desc);
create index if not exists group_messages_group_idx on public.group_messages(group_id, created_at);

create or replace function public.nexo_group_member(group_uuid uuid, user_uuid uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.group_members gm
        where gm.group_id = group_uuid and gm.user_id = user_uuid
    );
$$;

create or replace function public.nexo_group_admin(group_uuid uuid, user_uuid uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.group_members gm
        where gm.group_id = group_uuid
          and gm.user_id = user_uuid
          and gm.role in ('owner','admin')
    ) or exists (
        select 1 from public.groups g
        where g.id = group_uuid and g.owner_id = user_uuid
    );
$$;

alter table public.groups enable row level security;
alter table public.group_members enable row level security;
alter table public.group_messages enable row level security;

drop policy if exists groups_read_member on public.groups;
create policy groups_read_member
on public.groups for select
to authenticated
using (public.nexo_group_member(id, auth.uid()) or owner_id = auth.uid());

drop policy if exists groups_insert_owner on public.groups;
create policy groups_insert_owner
on public.groups for insert
to authenticated
with check (owner_id = auth.uid());

drop policy if exists groups_update_admin on public.groups;
create policy groups_update_admin
on public.groups for update
to authenticated
using (public.nexo_group_admin(id, auth.uid()))
with check (public.nexo_group_admin(id, auth.uid()));

drop policy if exists groups_delete_owner on public.groups;
create policy groups_delete_owner
on public.groups for delete
to authenticated
using (owner_id = auth.uid());

drop policy if exists group_members_read_member on public.group_members;
create policy group_members_read_member
on public.group_members for select
to authenticated
using (public.nexo_group_member(group_id, auth.uid()) or exists (
    select 1 from public.groups g where g.id = group_id and g.owner_id = auth.uid()
));

drop policy if exists group_members_insert_admin on public.group_members;
create policy group_members_insert_admin
on public.group_members for insert
to authenticated
with check (
    public.nexo_group_admin(group_id, auth.uid())
    or exists (select 1 from public.groups g where g.id = group_id and g.owner_id = auth.uid())
);

drop policy if exists group_members_update_admin on public.group_members;
create policy group_members_update_admin
on public.group_members for update
to authenticated
using (public.nexo_group_admin(group_id, auth.uid()))
with check (public.nexo_group_admin(group_id, auth.uid()));

drop policy if exists group_members_delete_admin_or_self on public.group_members;
create policy group_members_delete_admin_or_self
on public.group_members for delete
to authenticated
using (
    user_id = auth.uid()
    or public.nexo_group_admin(group_id, auth.uid())
);

drop policy if exists group_messages_read_member on public.group_messages;
create policy group_messages_read_member
on public.group_messages for select
to authenticated
using (public.nexo_group_member(group_id, auth.uid()));

drop policy if exists group_messages_insert_member on public.group_messages;
create policy group_messages_insert_member
on public.group_messages for insert
to authenticated
with check (
    sender_id = auth.uid()
    and public.nexo_group_member(group_id, auth.uid())
);

drop policy if exists group_messages_update_sender on public.group_messages;
create policy group_messages_update_sender
on public.group_messages for update
to authenticated
using (sender_id = auth.uid())
with check (sender_id = auth.uid());

drop policy if exists group_messages_delete_sender on public.group_messages;
create policy group_messages_delete_sender
on public.group_messages for delete
to authenticated
using (sender_id = auth.uid());

-- ---------------------------------------------------------------------------
-- STORAGE: cualquier archivo en chat/grupos + multimedia de estados
-- ---------------------------------------------------------------------------

-- El selector Android ya acepta */*. Se elimina la whitelist MIME del bucket,
-- manteniendo 25 MB y todas las políticas de seguridad por match.
update storage.buckets
set allowed_mime_types = null,
    file_size_limit = 26214400
where id = 'chat-media';

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('group-media', 'group-media', false, 26214400, null)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

-- Ruta group-media: <group_id>/<user_id>/<archivo>
drop policy if exists group_media_read_member on storage.objects;
create policy group_media_read_member
on storage.objects for select
to authenticated
using (
    bucket_id = 'group-media'
    and public.nexo_group_member(((storage.foldername(name))[1])::uuid, auth.uid())
);

drop policy if exists group_media_insert_member on storage.objects;
create policy group_media_insert_member
on storage.objects for insert
to authenticated
with check (
    bucket_id = 'group-media'
    and (storage.foldername(name))[2] = auth.uid()::text
    and public.nexo_group_member(((storage.foldername(name))[1])::uuid, auth.uid())
);

drop policy if exists group_media_delete_own on storage.objects;
create policy group_media_delete_own
on storage.objects for delete
to authenticated
using (
    bucket_id = 'group-media'
    and (storage.foldername(name))[2] = auth.uid()::text
);

alter table public.status_updates
    add column if not exists media_mime text;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'status-media',
    'status-media',
    false,
    15728640,
    array['image/jpeg','image/png','image/webp','image/gif','video/mp4','video/webm']
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

-- Ruta status-media: <owner_id>/<archivo>
drop policy if exists status_media_read_authenticated on storage.objects;
create policy status_media_read_authenticated
on storage.objects for select
to authenticated
using (
    bucket_id = 'status-media'
    and not public.nexo_pair_blocked(((storage.foldername(name))[1])::uuid, auth.uid())
);

drop policy if exists status_media_insert_own on storage.objects;
create policy status_media_insert_own
on storage.objects for insert
to authenticated
with check (
    bucket_id = 'status-media'
    and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists status_media_delete_own on storage.objects;
create policy status_media_delete_own
on storage.objects for delete
to authenticated
using (
    bucket_id = 'status-media'
    and (storage.foldername(name))[1] = auth.uid()::text
);

-- ---------------------------------------------------------------------------
-- REALTIME + PERMISOS
-- ---------------------------------------------------------------------------

do $$
declare
    table_name text;
begin
    foreach table_name in array array['groups','group_members','group_messages','contact_directory']
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

grant select on public.contact_directory to authenticated;
grant select, insert, update, delete on public.groups to authenticated;
grant select, insert, update, delete on public.group_members to authenticated;
grant select, insert, update, delete on public.group_messages to authenticated;

grant execute on function public.get_my_contact_settings() to authenticated;
grant execute on function public.set_contact_discoverable(boolean) to authenticated;
grant execute on function public.find_nexo_contacts(text[]) to authenticated;
grant execute on function public.nexo_group_member(uuid, uuid) to authenticated;
grant execute on function public.nexo_group_admin(uuid, uuid) to authenticated;

revoke all on function public.nexo_phone_hash(text) from public;
revoke all on function public.nexo_sync_auth_phone_directory() from public;
revoke all on function public.nexo_force_profile_phone_verified() from public;
