-- NEXO 0.3 - fotografías, chat persistente y Supabase Realtime
-- Ejecutar DESPUÉS de 20260908_nexo_0_2.sql.

alter table public.profiles
    add column if not exists photo_url text;

create table if not exists public.messages (
    id uuid primary key default gen_random_uuid(),
    match_id uuid not null references public.matches(id) on delete cascade,
    sender_id uuid not null references auth.users(id) on delete cascade,
    payload text not null check (char_length(payload) between 1 and 4000),
    encryption_version smallint not null default 0 check (encryption_version >= 0),
    nonce text,
    sender_key_id text,
    created_at timestamptz not null default now(),
    constraint messages_crypto_shape check (
        encryption_version = 0 or (nonce is not null and sender_key_id is not null)
    )
);

create index if not exists messages_match_created_idx
    on public.messages(match_id, created_at);

create index if not exists messages_sender_idx
    on public.messages(sender_id);

alter table public.messages enable row level security;

drop policy if exists messages_read_match_participants on public.messages;
create policy messages_read_match_participants
on public.messages for select
to authenticated
using (
    exists (
        select 1
        from public.matches m
        where m.id = messages.match_id
          and (m.user_a = auth.uid() or m.user_b = auth.uid())
    )
);

drop policy if exists messages_insert_match_participants on public.messages;
create policy messages_insert_match_participants
on public.messages for insert
to authenticated
with check (
    sender_id = auth.uid()
    and exists (
        select 1
        from public.matches m
        where m.id = messages.match_id
          and (m.user_a = auth.uid() or m.user_b = auth.uid())
    )
);

grant select, insert on public.messages to authenticated;

-- Realtime necesita que la tabla pertenezca a la publicación supabase_realtime.
do $$
begin
    if not exists (
        select 1
        from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'messages'
    ) then
        alter publication supabase_realtime add table public.messages;
    end if;
end
$$;

-- Bucket público: la lectura de la foto de perfil es pública, pero cada usuario
-- solo puede escribir dentro de su propia carpeta <auth.uid()>/...
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'profile-photos',
    'profile-photos',
    true,
    5242880,
    array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists profile_photos_insert_own on storage.objects;
create policy profile_photos_insert_own
on storage.objects for insert
to authenticated
with check (
    bucket_id = 'profile-photos'
    and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists profile_photos_update_own on storage.objects;
create policy profile_photos_update_own
on storage.objects for update
to authenticated
using (
    bucket_id = 'profile-photos'
    and (storage.foldername(name))[1] = auth.uid()::text
)
with check (
    bucket_id = 'profile-photos'
    and (storage.foldername(name))[1] = auth.uid()::text
);

drop policy if exists profile_photos_delete_own on storage.objects;
create policy profile_photos_delete_own
on storage.objects for delete
to authenticated
using (
    bucket_id = 'profile-photos'
    and (storage.foldername(name))[1] = auth.uid()::text
);
