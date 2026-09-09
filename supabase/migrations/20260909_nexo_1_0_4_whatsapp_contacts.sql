-- NEXO 1.0 RC4 - conversación directa con contactos verificados
-- Ejecutar DESPUÉS de 20260908_nexo_1_0_3_contacts_groups.sql.
-- Aditiva: conserva usuarios, likes, matches y mensajes existentes.

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

    if contact_phone_hash is null
       or contact_phone_hash !~ '^[0-9a-f]{64}$' then
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

    insert into public.matches(user_a, user_b)
    values (first_user, second_user)
    on conflict (user_a, user_b) do nothing;
end;
$$;

revoke all on function public.start_contact_conversation(text) from public;
grant execute on function public.start_contact_conversation(text) to authenticated;
