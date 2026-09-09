-- NEXO RC8: control de stickers propios y protección del paquete Plus.
-- Ejecutar después de 20260909_nexo_1_0_6_monetization.sql.

create or replace function public.enforce_nexo_sticker_entitlement()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    free_stickers constant text[] := array[
        'tuani', 'jajaja', 'dale', 'amor', 'buenos_dias', 'descansa'
    ];
    plus_stickers constant text[] := array[
        'match', 'fuego', 'plan', 'pensando', 'gracias', 'nexo'
    ];
begin
    if new.kind <> 'sticker' then
        return new;
    end if;

    if new.payload = any(free_stickers) then
        return new;
    end if;

    if new.payload = any(plus_stickers) and exists (
        select 1
          from public.premium_entitlements premium
         where premium.user_id = new.sender_id
           and premium.plus_active
           and premium.plus_expires_at > now()
    ) then
        return new;
    end if;

    if new.payload = any(plus_stickers) then
        raise exception 'NEXO Plus es necesario para enviar este sticker.' using errcode = '42501';
    end if;

    raise exception 'El sticker NEXO no es válido.' using errcode = '22023';
end;
$$;

revoke all on function public.enforce_nexo_sticker_entitlement() from public, anon, authenticated;

drop trigger if exists messages_sticker_entitlement on public.messages;
create trigger messages_sticker_entitlement
before insert or update of kind, payload, sender_id on public.messages
for each row execute function public.enforce_nexo_sticker_entitlement();

drop trigger if exists group_messages_sticker_entitlement on public.group_messages;
create trigger group_messages_sticker_entitlement
before insert or update of kind, payload, sender_id on public.group_messages
for each row execute function public.enforce_nexo_sticker_entitlement();
