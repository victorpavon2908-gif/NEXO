-- NEXO 1.0.0 RC10 - integridad final de mensajes y llamadas
-- Ejecutar después de 20260910_nexo_1_0_9_e2ee_webrtc.sql.

-- Solo el autor puede modificar su fila de mensaje. Entregas/lecturas viven en
-- message_receipts, así que el receptor no necesita UPDATE sobre messages.
drop policy if exists messages_update_sender on public.messages;
create policy messages_update_sender
on public.messages for update
to authenticated
using (sender_id = auth.uid())
with check (sender_id = auth.uid());

-- Impide cambiar identidad/conversación/fecha de un mensaje durante una edición.
create or replace function public.nexo_guard_message_identity()
returns trigger
language plpgsql
set search_path = public
as $$
begin
    if new.id is distinct from old.id
       or new.match_id is distinct from old.match_id
       or new.sender_id is distinct from old.sender_id
       or new.created_at is distinct from old.created_at then
        raise exception 'message identity fields are immutable';
    end if;
    return new;
end;
$$;

drop trigger if exists trg_nexo_guard_message_identity on public.messages;
create trigger trg_nexo_guard_message_identity
before update on public.messages
for each row execute function public.nexo_guard_message_identity();

-- Participantes pueden cambiar el estado de una llamada, pero nunca sustituir
-- quién llama, quién recibe, el tipo o la fecha de inicio.
create or replace function public.nexo_guard_call_identity()
returns trigger
language plpgsql
set search_path = public
as $$
begin
    if new.id is distinct from old.id
       or new.caller_id is distinct from old.caller_id
       or new.callee_id is distinct from old.callee_id
       or new.call_type is distinct from old.call_type
       or new.started_at is distinct from old.started_at then
        raise exception 'call identity fields are immutable';
    end if;
    return new;
end;
$$;

drop trigger if exists trg_nexo_guard_call_identity on public.calls;
create trigger trg_nexo_guard_call_identity
before update on public.calls
for each row execute function public.nexo_guard_call_identity();

-- Las señales WebRTC son append-only. No se concede UPDATE/DELETE directo al
-- cliente; la limpieza se hace mediante nexo_cleanup_call_signals().
revoke update, delete on public.call_signals from authenticated;
