-- NEXO 1.0 RC security hardening
-- Ejecutar DESPUÉS de 20260908_nexo_1_0.sql.
-- Separa los recibos de entrega/lectura del contenido del mensaje para que
-- el receptor nunca necesite permiso UPDATE sobre el mensaje del remitente.

create table if not exists public.message_receipts (
    message_id uuid not null references public.messages(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    delivered_at timestamptz not null default now(),
    read_at timestamptz,
    primary key (message_id, user_id)
);

create index if not exists message_receipts_user_idx
    on public.message_receipts(user_id, delivered_at desc);

alter table public.message_receipts enable row level security;

-- El contenido del mensaje solo lo puede actualizar quien lo envió.
drop policy if exists messages_update_sender on public.messages;
create policy messages_update_sender
on public.messages for update
to authenticated
using (sender_id = auth.uid())
with check (sender_id = auth.uid());

-- Los participantes pueden ver recibos de sus mensajes/conversación.
drop policy if exists message_receipts_read_participants on public.message_receipts;
create policy message_receipts_read_participants
on public.message_receipts for select
to authenticated
using (
    exists (
        select 1
        from public.messages msg
        join public.matches m on m.id = msg.match_id
        where msg.id = message_receipts.message_id
          and (m.user_a = auth.uid() or m.user_b = auth.uid())
    )
);

-- Cada usuario crea/actualiza únicamente su propio recibo y solo si participa.
drop policy if exists message_receipts_insert_own on public.message_receipts;
create policy message_receipts_insert_own
on public.message_receipts for insert
to authenticated
with check (
    user_id = auth.uid()
    and exists (
        select 1
        from public.messages msg
        join public.matches m on m.id = msg.match_id
        where msg.id = message_receipts.message_id
          and msg.sender_id <> auth.uid()
          and (m.user_a = auth.uid() or m.user_b = auth.uid())
          and not public.nexo_pair_blocked(m.user_a, m.user_b)
    )
);

drop policy if exists message_receipts_update_own on public.message_receipts;
create policy message_receipts_update_own
on public.message_receipts for update
to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

grant select, insert, update on public.message_receipts to authenticated;

do $$
begin
    if not exists (
        select 1
        from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'message_receipts'
    ) then
        alter publication supabase_realtime add table public.message_receipts;
    end if;
end
$$;