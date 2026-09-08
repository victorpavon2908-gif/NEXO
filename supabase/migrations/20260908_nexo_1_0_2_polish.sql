-- NEXO 1.0 RC2 - polish, account deletion queue and production cleanup contract
-- Ejecutar DESPUÉS de 20260908_nexo_1_0_1_security.sql.
-- Aditiva: no elimina cuentas existentes al aplicar la migración.

create table if not exists public.account_deletion_requests (
    user_id uuid primary key references auth.users(id) on delete cascade,
    requested_at timestamptz not null default now(),
    status text not null default 'pending' check (status in ('pending','processing','completed','cancelled')),
    completed_at timestamptz
);

alter table public.account_deletion_requests enable row level security;

drop policy if exists account_deletion_requests_read_own on public.account_deletion_requests;
create policy account_deletion_requests_read_own
on public.account_deletion_requests for select
to authenticated
using (user_id = auth.uid());

drop policy if exists account_deletion_requests_insert_own on public.account_deletion_requests;
create policy account_deletion_requests_insert_own
on public.account_deletion_requests for insert
to authenticated
with check (user_id = auth.uid());

-- El usuario puede solicitar borrado sin exponer service_role al cliente.
-- El perfil desaparece del descubrimiento inmediatamente y se cierran sus
-- relaciones activas. Un worker/Edge Function con service_role debe terminar
-- el borrado de Auth y de objetos físicos de Storage.
create or replace function public.request_my_account_deletion()
returns void
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    uid uuid := auth.uid();
begin
    if uid is null then
        raise exception 'Authentication required';
    end if;

    insert into public.account_deletion_requests(user_id, requested_at, status)
    values (uid, now(), 'pending')
    on conflict (user_id) do update
      set requested_at = excluded.requested_at,
          status = 'pending',
          completed_at = null;

    update public.profiles
       set is_active = false,
           is_online = false,
           last_seen = now(),
           updated_at = now()
     where id = uid;

    delete from public.likes
     where actor_id = uid or target_id = uid;

    -- Los mensajes dependen de matches y se limpian por ON DELETE CASCADE.
    delete from public.matches
     where user_a = uid or user_b = uid;

    delete from public.status_updates
     where owner_id = uid;

    delete from public.presence
     where user_id = uid;

    update public.devices
       set enabled = false,
           last_seen = now()
     where user_id = uid;

    update public.calls
       set state = case when state in ('ended','declined','missed','failed') then state else 'ended' end,
           ended_at = coalesce(ended_at, now())
     where caller_id = uid or callee_id = uid;
end;
$$;

revoke all on function public.request_my_account_deletion() from public;
revoke all on function public.request_my_account_deletion() from anon;
grant execute on function public.request_my_account_deletion() to authenticated;

grant select, insert on public.account_deletion_requests to authenticated;

-- Vista mínima para que un worker privado pueda encontrar solicitudes
-- pendientes sin depender de la aplicación Android.
create index if not exists account_deletion_requests_pending_idx
    on public.account_deletion_requests(status, requested_at)
    where status in ('pending','processing');
