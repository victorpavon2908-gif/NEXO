-- NEXO 1.0.0 RC9 - lifecycle de llamadas
-- Ejecutar despues de 20260909_nexo_1_0_7_chat_expression.sql

alter table public.calls
    add column if not exists answered_at timestamptz,
    add column if not exists ring_expires_at timestamptz;

update public.calls
set ring_expires_at = coalesce(ring_expires_at, started_at + interval '30 seconds')
where state = 'ringing';

alter table public.calls
    alter column ring_expires_at set default (now() + interval '30 seconds');

-- Estados aceptados por el cliente RC9.
do $$
begin
    if exists (
        select 1 from pg_constraint
        where conrelid = 'public.calls'::regclass
          and conname = 'calls_state_check'
    ) then
        alter table public.calls drop constraint calls_state_check;
    end if;
end $$;

alter table public.calls
    add constraint calls_state_check
    check (state in ('ringing','connecting','connected','declined','missed','ended','failed'));

create index if not exists calls_callee_ringing_idx
    on public.calls (callee_id, started_at desc)
    where state = 'ringing';

create index if not exists calls_caller_active_idx
    on public.calls (caller_id, started_at desc)
    where state in ('ringing','connecting','connected');

-- Solo los participantes pueden mutar la llamada. Mantiene las politicas
-- anteriores, pero evita que una actualizacion cambie caller/callee.
drop policy if exists calls_update_participants on public.calls;
create policy calls_update_participants
on public.calls for update
to authenticated
using (caller_id = auth.uid() or callee_id = auth.uid())
with check (caller_id = auth.uid() or callee_id = auth.uid());

-- Funcion idempotente para limpiar llamadas que quedaron timbrando porque
-- el dispositivo remoto no respondio. El cliente RC9 tambien aplica timeout
-- local a los 30 s para que la interfaz no quede bloqueada.
create or replace function public.nexo_expire_stale_calls()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    affected integer;
begin
    update public.calls
       set state = 'missed',
           ended_at = coalesce(ended_at, now())
     where state = 'ringing'
       and coalesce(ring_expires_at, started_at + interval '30 seconds') <= now()
       and (caller_id = auth.uid() or callee_id = auth.uid());

    get diagnostics affected = row_count;
    return affected;
end;
$$;

revoke all on function public.nexo_expire_stale_calls() from public;
grant execute on function public.nexo_expire_stale_calls() to authenticated;

-- Realtime ya incluye public.calls desde 1.0; este bloque lo deja seguro si
-- una instalacion parcial no habia agregado la tabla a la publicacion.
do $$
begin
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'calls'
    ) then
        alter publication supabase_realtime add table public.calls;
    end if;
end $$;
