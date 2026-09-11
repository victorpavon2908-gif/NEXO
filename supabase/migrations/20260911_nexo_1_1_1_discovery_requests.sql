-- NEXO 1.1.1 - búsqueda, solicitudes de mensajes y control de privacidad
-- Ejecutar DESPUÉS de 20260910_nexo_1_0_9_e2ee_webrtc.sql y
-- 20260910_nexo_1_0_10_message_integrity.sql.
-- Aditiva: conserva perfiles, likes, matches, mensajes E2EE, llamadas y grupos.

alter table public.user_preferences
    add column if not exists allow_message_requests boolean not null default true;

alter table public.matches
    add column if not exists relationship_kind text not null default 'match',
    add column if not exists conversation_state text not null default 'accepted',
    add column if not exists requested_by uuid references auth.users(id) on delete set null,
    add column if not exists accepted_at timestamptz;

do $$
begin
    if not exists (
        select 1 from pg_constraint
        where conname = 'matches_relationship_kind_check'
          and conrelid = 'public.matches'::regclass
    ) then
        alter table public.matches
            add constraint matches_relationship_kind_check
            check (relationship_kind in ('match','contact','request'));
    end if;

    if not exists (
        select 1 from pg_constraint
        where conname = 'matches_conversation_state_check'
          and conrelid = 'public.matches'::regclass
    ) then
        alter table public.matches
            add constraint matches_conversation_state_check
            check (conversation_state in ('pending','accepted','rejected'));
    end if;
end $$;

update public.matches
set accepted_at = coalesce(accepted_at, created_at),
    relationship_kind = coalesce(relationship_kind, 'match'),
    conversation_state = coalesce(conversation_state, 'accepted')
where conversation_state = 'accepted';

create index if not exists matches_conversation_state_idx
    on public.matches(conversation_state, created_at desc);
create index if not exists matches_requested_by_idx
    on public.matches(requested_by, created_at desc)
    where requested_by is not null;

-- Realtime para que A y B vean aceptación/match sin reiniciar la app.
do $$
begin
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'matches'
    ) then
        alter publication supabase_realtime add table public.matches;
    end if;
end
$$;

create or replace function public.nexo_order_pair(a uuid, b uuid)
returns table(first_user uuid, second_user uuid)
language sql
immutable
strict
set search_path = public
as $$
    select case when a::text < b::text then a else b end,
           case when a::text < b::text then b else a end;
$$;

create or replace function public.nexo_conversation_accepted(a uuid, b uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from public.matches m
        where ((m.user_a = a and m.user_b = b) or (m.user_a = b and m.user_b = a))
          and m.conversation_state = 'accepted'
          and not public.nexo_pair_blocked(a, b)
    );
$$;

-- Si había una solicitud pendiente y luego ambos dan like, se convierte en match real.
create or replace function public.create_match_after_like()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    first_user uuid;
    second_user uuid;
begin
    if exists (
        select 1 from public.likes
        where actor_id = new.target_id
          and target_id = new.actor_id
    ) then
        if new.actor_id::text < new.target_id::text then
            first_user := new.actor_id;
            second_user := new.target_id;
        else
            first_user := new.target_id;
            second_user := new.actor_id;
        end if;

        insert into public.matches(
            user_a, user_b, relationship_kind, conversation_state, requested_by, accepted_at
        ) values (
            first_user, second_user, 'match', 'accepted', null, now()
        )
        on conflict (user_a, user_b) do update
        set relationship_kind = 'match',
            conversation_state = 'accepted',
            requested_by = null,
            accepted_at = now();
    end if;

    return new;
end;
$$;

-- Contacto verificado: conversación directa aceptada sin requerir swipe/match.
create or replace function public.start_contact_conversation(contact_phone_hash text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    target_user uuid;
    first_user uuid;
    second_user uuid;
begin
    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;

    if contact_phone_hash is null or contact_phone_hash !~ '^[0-9a-f]{64}$' then
        raise exception 'Invalid contact hash';
    end if;

    select cd.user_id
      into target_user
    from public.contact_directory cd
    join public.profiles p on p.id = cd.user_id
    where cd.phone_hash = contact_phone_hash
      and cd.discoverable = true
      and cd.phone_verified_at is not null
      and p.is_active = true
      and not p.profile_paused
      and cd.user_id <> auth.uid()
      and not public.nexo_pair_blocked(cd.user_id, auth.uid())
    limit 1;

    if target_user is null then
        raise exception 'Contact is not available';
    end if;

    if auth.uid()::text < target_user::text then
        first_user := auth.uid();
        second_user := target_user;
    else
        first_user := target_user;
        second_user := auth.uid();
    end if;

    insert into public.matches(
        user_a, user_b, relationship_kind, conversation_state, requested_by, accepted_at
    ) values (
        first_user, second_user, 'contact', 'accepted', null, now()
    )
    on conflict (user_a, user_b) do update
    set conversation_state = 'accepted',
        relationship_kind = case
            when public.matches.relationship_kind = 'match' then 'match'
            else 'contact'
        end,
        requested_by = null,
        accepted_at = coalesce(public.matches.accepted_at, now());
end;
$$;

-- Preferencia de solicitudes de desconocidos.
create or replace function public.get_nexo_message_privacy()
returns table(allow_message_requests boolean)
language sql
stable
security definer
set search_path = public
as $$
    select coalesce(
        (select up.allow_message_requests from public.user_preferences up where up.user_id = auth.uid()),
        true
    )
    where auth.uid() is not null;
$$;

create or replace function public.set_nexo_message_privacy(enabled boolean)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;

    insert into public.user_preferences(user_id, allow_message_requests, updated_at)
    values (auth.uid(), enabled, now())
    on conflict (user_id) do update
    set allow_message_requests = excluded.allow_message_requests,
        updated_at = now();
end;
$$;

-- Devuelve el estado de acceso a un perfil desde la perspectiva del usuario actual.
create or replace function public.get_nexo_conversation_access(target_user uuid)
returns table(
    state text,
    relationship_kind text,
    requested_by_me boolean,
    can_start_message boolean,
    action_label text
)
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    rel public.matches%rowtype;
    target_allows boolean;
begin
    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;
    if target_user is null or target_user = auth.uid() then
        raise exception 'Invalid target';
    end if;
    if public.nexo_pair_blocked(auth.uid(), target_user) then
        return query select 'blocked'::text, null::text, false, false, 'No disponible'::text;
        return;
    end if;

    select * into rel
    from public.matches m
    where (m.user_a = auth.uid() and m.user_b = target_user)
       or (m.user_a = target_user and m.user_b = auth.uid())
    limit 1;

    if rel.id is not null then
        if rel.conversation_state = 'accepted' then
            return query select 'accepted'::text, rel.relationship_kind, false, true, 'Abrir chat'::text;
        elsif rel.conversation_state = 'pending' then
            if rel.requested_by = auth.uid() then
                return query select 'pending_outgoing'::text, rel.relationship_kind, true, true, 'Solicitud enviada'::text;
            else
                return query select 'pending_incoming'::text, rel.relationship_kind, false, false, 'Solicitud recibida'::text;
            end if;
        end if;
    end if;

    select coalesce(up.allow_message_requests, true)
      into target_allows
    from public.profiles p
    left join public.user_preferences up on up.user_id = p.id
    where p.id = target_user
      and p.is_active = true
      and not p.profile_paused;

    if coalesce(target_allows, false) then
        return query select 'none'::text, null::text, false, true, 'Enviar mensaje'::text;
    else
        return query select 'match_only'::text, null::text, false, false, 'Solo con match'::text;
    end if;
end;
$$;

-- Crear una solicitud desde búsqueda. La conversación queda pendiente hasta aceptar.
create or replace function public.start_nexo_message_request(target_user uuid)
returns table(
    state text,
    relationship_kind text,
    requested_by_me boolean,
    can_start_message boolean,
    action_label text
)
language plpgsql
security definer
set search_path = public
as $$
declare
    first_user uuid;
    second_user uuid;
    rel public.matches%rowtype;
    target_allows boolean;
begin
    if auth.uid() is null then
        raise exception 'Authentication required';
    end if;
    if target_user is null or target_user = auth.uid() then
        raise exception 'Invalid target';
    end if;
    if public.nexo_pair_blocked(auth.uid(), target_user) then
        raise exception 'No podés iniciar una conversación con esta persona.';
    end if;

    select * into rel
    from public.matches m
    where (m.user_a = auth.uid() and m.user_b = target_user)
       or (m.user_a = target_user and m.user_b = auth.uid())
    limit 1;

    if rel.id is not null then
        return query select * from public.get_nexo_conversation_access(target_user);
        return;
    end if;

    select coalesce(up.allow_message_requests, true)
      into target_allows
    from public.profiles p
    left join public.user_preferences up on up.user_id = p.id
    where p.id = target_user
      and p.is_active = true
      and not p.profile_paused;

    if not coalesce(target_allows, false) then
        raise exception 'Solo con match';
    end if;

    if auth.uid()::text < target_user::text then
        first_user := auth.uid();
        second_user := target_user;
    else
        first_user := target_user;
        second_user := auth.uid();
    end if;

    insert into public.matches(
        user_a, user_b, relationship_kind, conversation_state, requested_by, accepted_at
    ) values (
        first_user, second_user, 'request', 'pending', auth.uid(), null
    )
    on conflict (user_a, user_b) do nothing;

    return query select * from public.get_nexo_conversation_access(target_user);
end;
$$;

create or replace function public.accept_nexo_message_request(target_user uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if auth.uid() is null then raise exception 'Authentication required'; end if;

    update public.matches m
    set conversation_state = 'accepted',
        accepted_at = now()
    where ((m.user_a = auth.uid() and m.user_b = target_user)
        or (m.user_a = target_user and m.user_b = auth.uid()))
      and m.relationship_kind = 'request'
      and m.conversation_state = 'pending'
      and m.requested_by = target_user;

    if not found then
        raise exception 'La solicitud ya no está disponible.';
    end if;
end;
$$;

create or replace function public.reject_nexo_message_request(target_user uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if auth.uid() is null then raise exception 'Authentication required'; end if;

    delete from public.matches m
    where ((m.user_a = auth.uid() and m.user_b = target_user)
        or (m.user_a = target_user and m.user_b = auth.uid()))
      and m.relationship_kind = 'request'
      and m.conversation_state = 'pending'
      and m.requested_by = target_user;

    if not found then
        raise exception 'La solicitud ya no está disponible.';
    end if;
end;
$$;

create or replace function public.block_nexo_message_request(target_user uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if auth.uid() is null then raise exception 'Authentication required'; end if;
    if target_user is null or target_user = auth.uid() then raise exception 'Invalid target'; end if;

    insert into public.blocks(blocker_id, blocked_id)
    values (auth.uid(), target_user)
    on conflict (blocker_id, blocked_id) do nothing;

    delete from public.matches m
    where (m.user_a = auth.uid() and m.user_b = target_user)
       or (m.user_a = target_user and m.user_b = auth.uid());
end;
$$;

-- Solicitudes entrantes con información pública mínima del perfil.
create or replace function public.list_nexo_message_requests()
returns table(
    id uuid,
    name text,
    age integer,
    city text,
    bio text,
    intention text,
    interests text[],
    verified boolean,
    photo_url text,
    phone_verified boolean,
    is_online boolean,
    last_seen timestamptz,
    requested_at timestamptz
)
language sql
stable
security definer
set search_path = public
as $$
    select
        p.id, p.name, p.age::integer, p.city, p.bio, p.intention, p.interests,
        p.verified, p.photo_url, p.phone_verified,
        case when coalesce(up.show_online, true) then p.is_online else false end,
        case when coalesce(up.show_last_seen, true) then p.last_seen else null end,
        m.created_at
    from public.matches m
    join public.profiles p on p.id = m.requested_by
    left join public.user_preferences up on up.user_id = p.id
    where auth.uid() is not null
      and m.relationship_kind = 'request'
      and m.conversation_state = 'pending'
      and m.requested_by is not null
      and m.requested_by <> auth.uid()
      and (m.user_a = auth.uid() or m.user_b = auth.uid())
      and p.is_active = true
      and not public.nexo_pair_blocked(auth.uid(), p.id)
    order by m.created_at desc;
$$;

-- Búsqueda por nombre o ciudad, con permiso de mensajería calculado por servidor.
create or replace function public.search_nexo_people(search_text text)
returns table(
    id uuid,
    name text,
    age integer,
    city text,
    bio text,
    intention text,
    interests text[],
    verified boolean,
    photo_url text,
    phone_verified boolean,
    is_online boolean,
    last_seen timestamptz,
    access_state text,
    relationship_kind text,
    requested_by_me boolean,
    can_start_message boolean,
    action_label text
)
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    q text := trim(coalesce(search_text, ''));
begin
    if auth.uid() is null then raise exception 'Authentication required'; end if;
    if char_length(q) < 2 then return; end if;

    return query
    select
        p.id,
        p.name,
        p.age::integer,
        p.city,
        p.bio,
        p.intention,
        p.interests,
        p.verified,
        p.photo_url,
        p.phone_verified,
        case when coalesce(up.show_online, true) then p.is_online else false end,
        case when coalesce(up.show_last_seen, true) then p.last_seen else null end,
        access.state,
        access.relationship_kind,
        access.requested_by_me,
        access.can_start_message,
        access.action_label
    from public.profiles p
    left join public.user_preferences up on up.user_id = p.id
    cross join lateral public.get_nexo_conversation_access(p.id) access
    where p.id <> auth.uid()
      and p.is_active = true
      and not p.profile_paused
      and not public.nexo_pair_blocked(auth.uid(), p.id)
      and (p.name ilike '%' || q || '%' or p.city ilike '%' || q || '%')
    order by
      case when lower(p.name) = lower(q) then 0 else 1 end,
      p.is_online desc,
      p.name asc
    limit 50;
end;
$$;

create or replace function public.list_nexo_accepted_peer_ids()
returns table(peer_id uuid)
language sql
stable
security definer
set search_path = public
as $$
    select case when m.user_a = auth.uid() then m.user_b else m.user_a end
    from public.matches m
    where auth.uid() is not null
      and (m.user_a = auth.uid() or m.user_b = auth.uid())
      and m.conversation_state = 'accepted'
      and not public.nexo_pair_blocked(m.user_a, m.user_b);
$$;

create or replace function public.nexo_is_mutual_match(target_user uuid)
returns table(is_match boolean)
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.matches m
        where ((m.user_a = auth.uid() and m.user_b = target_user)
            or (m.user_a = target_user and m.user_b = auth.uid()))
          and m.relationship_kind = 'match'
          and m.conversation_state = 'accepted'
    )
    where auth.uid() is not null;
$$;

-- Solo un mensaje inicial del solicitante mientras la conversación esté pendiente.
create or replace function public.nexo_guard_pending_message_request()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    rel public.matches%rowtype;
begin
    select * into rel from public.matches m where m.id = new.match_id;
    if rel.id is null then
        raise exception 'La conversación ya no está disponible.';
    end if;

    if rel.conversation_state = 'rejected' then
        raise exception 'La conversación ya no está disponible.';
    end if;

    if rel.conversation_state = 'pending' then
        if rel.requested_by is distinct from auth.uid() or new.sender_id is distinct from auth.uid() then
            raise exception 'Aceptá la solicitud antes de responder.';
        end if;
        if exists (select 1 from public.messages msg where msg.match_id = new.match_id) then
            raise exception 'Ya enviaste el mensaje inicial. Esperá a que la otra persona acepte la solicitud.';
        end if;
    end if;

    return new;
end;
$$;

drop trigger if exists trg_nexo_guard_pending_message_request on public.messages;
create trigger trg_nexo_guard_pending_message_request
before insert on public.messages
for each row execute function public.nexo_guard_pending_message_request();

-- Llamadas solo en conversaciones aceptadas.
create or replace function public.nexo_guard_call_conversation()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if not public.nexo_conversation_accepted(new.caller_id, new.callee_id) then
        raise exception 'Aceptá la solicitud antes de iniciar llamadas.';
    end if;
    return new;
end;
$$;

drop trigger if exists trg_nexo_guard_call_conversation on public.calls;
create trigger trg_nexo_guard_call_conversation
before insert on public.calls
for each row execute function public.nexo_guard_call_conversation();

-- Agregar personas a grupos solo tras una conversación aceptada.
create or replace function public.nexo_guard_group_member_conversation()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    inviter uuid;
begin
    inviter := coalesce(new.added_by, auth.uid());
    if new.user_id = inviter then return new; end if;

    if not public.nexo_conversation_accepted(inviter, new.user_id) then
        raise exception 'Solo podés agregar a grupos personas con una conexión aceptada.';
    end if;
    return new;
end;
$$;

drop trigger if exists trg_nexo_guard_group_member_conversation on public.group_members;
create trigger trg_nexo_guard_group_member_conversation
before insert on public.group_members
for each row execute function public.nexo_guard_group_member_conversation();

-- Pausar/reanudar perfil en una única transacción de base de datos.
create or replace function public.nexo_sync_profile_pause_from_preferences()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    update public.profiles
    set profile_paused = new.profile_paused
    where id = new.user_id;
    return new;
end;
$$;

drop trigger if exists trg_nexo_sync_profile_pause on public.discovery_preferences;
create trigger trg_nexo_sync_profile_pause
after insert or update of profile_paused on public.discovery_preferences
for each row execute function public.nexo_sync_profile_pause_from_preferences();

-- Mantiene consistencia para cuentas existentes.
update public.profiles p
set profile_paused = dp.profile_paused
from public.discovery_preferences dp
where dp.user_id = p.id
  and p.profile_paused is distinct from dp.profile_paused;

revoke all on function public.get_nexo_message_privacy() from public, anon;
revoke all on function public.set_nexo_message_privacy(boolean) from public, anon;
revoke all on function public.get_nexo_conversation_access(uuid) from public, anon;
revoke all on function public.start_nexo_message_request(uuid) from public, anon;
revoke all on function public.accept_nexo_message_request(uuid) from public, anon;
revoke all on function public.reject_nexo_message_request(uuid) from public, anon;
revoke all on function public.block_nexo_message_request(uuid) from public, anon;
revoke all on function public.list_nexo_message_requests() from public, anon;
revoke all on function public.search_nexo_people(text) from public, anon;
revoke all on function public.list_nexo_accepted_peer_ids() from public, anon;
revoke all on function public.nexo_is_mutual_match(uuid) from public, anon;
revoke all on function public.start_contact_conversation(text) from public, anon;

grant execute on function public.get_nexo_message_privacy() to authenticated;
grant execute on function public.set_nexo_message_privacy(boolean) to authenticated;
grant execute on function public.get_nexo_conversation_access(uuid) to authenticated;
grant execute on function public.start_nexo_message_request(uuid) to authenticated;
grant execute on function public.accept_nexo_message_request(uuid) to authenticated;
grant execute on function public.reject_nexo_message_request(uuid) to authenticated;
grant execute on function public.block_nexo_message_request(uuid) to authenticated;
grant execute on function public.list_nexo_message_requests() to authenticated;
grant execute on function public.search_nexo_people(text) to authenticated;
grant execute on function public.list_nexo_accepted_peer_ids() to authenticated;
grant execute on function public.nexo_is_mutual_match(uuid) to authenticated;
grant execute on function public.start_contact_conversation(text) to authenticated;
