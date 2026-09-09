-- NEXO RC7: derechos premium y auditoría segura de Google Play.
-- La app nunca escribe derechos premium directamente.

create table if not exists public.premium_entitlements (
    user_id uuid primary key references auth.users(id) on delete cascade,
    plus_active boolean not null default false,
    plus_expires_at timestamptz,
    boost_expires_at timestamptz,
    source text not null default 'google_play' check (source in ('google_play', 'admin')),
    updated_at timestamptz not null default now()
);

create table if not exists public.billing_purchase_audit (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    purchase_token_hash text not null unique check (char_length(purchase_token_hash) = 64),
    product_id text not null check (product_id in ('nexo_plus_monthly', 'nexo_plus_yearly', 'nexo_boost_24h')),
    purchase_type text not null check (purchase_type in ('subscription', 'one_time')),
    verified_at timestamptz not null default now(),
    expires_at timestamptz
);

create index if not exists billing_purchase_audit_user_idx
    on public.billing_purchase_audit(user_id, verified_at desc);

alter table public.premium_entitlements enable row level security;
alter table public.billing_purchase_audit enable row level security;

drop policy if exists "premium_own_select" on public.premium_entitlements;
create policy "premium_own_select" on public.premium_entitlements
for select using (auth.uid() = user_id);

drop policy if exists "billing_audit_own_select" on public.billing_purchase_audit;
create policy "billing_audit_own_select" on public.billing_purchase_audit
for select using (auth.uid() = user_id);

revoke all on public.premium_entitlements from anon, authenticated;
revoke all on public.billing_purchase_audit from anon, authenticated;
grant select on public.premium_entitlements to authenticated;
grant select on public.billing_purchase_audit to authenticated;

create or replace function public.grant_verified_google_play_purchase(
    target_user_id uuid,
    token_hash text,
    verified_product_id text,
    verified_purchase_type text,
    verified_expires_at timestamptz default null
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
    inserted_id uuid;
    existing_user_id uuid;
    existing_purchase_type text;
begin
    if verified_product_id not in ('nexo_plus_monthly', 'nexo_plus_yearly', 'nexo_boost_24h') then
        raise exception 'invalid_product';
    end if;
    if verified_purchase_type not in ('subscription', 'one_time') then
        raise exception 'invalid_purchase_type';
    end if;

    select audit.user_id, audit.purchase_type
      into existing_user_id, existing_purchase_type
      from public.billing_purchase_audit audit
     where audit.purchase_token_hash = grant_verified_google_play_purchase.token_hash;

    if existing_user_id is not null and existing_user_id <> target_user_id then
        raise exception 'purchase_owner_mismatch';
    end if;

    -- Un consumible restaurado jamás suma otras 24 horas.
    if existing_user_id is not null and existing_purchase_type = 'one_time' then
        return true;
    end if;

    if verified_purchase_type = 'subscription' then
        insert into public.billing_purchase_audit(
            user_id, purchase_token_hash, product_id, purchase_type, expires_at
        ) values (
            target_user_id, token_hash, verified_product_id, verified_purchase_type, verified_expires_at
        )
        on conflict (purchase_token_hash) do update set
            product_id = excluded.product_id,
            purchase_type = excluded.purchase_type,
            verified_at = now(),
            expires_at = excluded.expires_at
        returning id into inserted_id;
    else
        insert into public.billing_purchase_audit(
            user_id, purchase_token_hash, product_id, purchase_type, expires_at
        ) values (
            target_user_id, token_hash, verified_product_id, verified_purchase_type, verified_expires_at
        )
        on conflict (purchase_token_hash) do nothing
        returning id into inserted_id;

        -- También protege ante dos verificaciones simultáneas del mismo boost.
        if inserted_id is null then
            return true;
        end if;
    end if;

    if verified_purchase_type = 'subscription' then
        insert into public.premium_entitlements(user_id, plus_active, plus_expires_at, source, updated_at)
        values (target_user_id, true, verified_expires_at, 'google_play', now())
        on conflict (user_id) do update set
            plus_active = true,
            plus_expires_at = greatest(
                coalesce(public.premium_entitlements.plus_expires_at, excluded.plus_expires_at),
                excluded.plus_expires_at
            ),
            source = 'google_play',
            updated_at = now();
    else
        insert into public.premium_entitlements(user_id, boost_expires_at, source, updated_at)
        values (target_user_id, now() + interval '24 hours', 'google_play', now())
        on conflict (user_id) do update set
            boost_expires_at = greatest(coalesce(public.premium_entitlements.boost_expires_at, now()), now()) + interval '24 hours',
            source = 'google_play',
            updated_at = now();
    end if;
    return true;
end;
$$;

revoke all on function public.grant_verified_google_play_purchase(uuid, text, text, text, timestamptz) from public, anon, authenticated;
grant execute on function public.grant_verified_google_play_purchase(uuid, text, text, text, timestamptz) to service_role;

-- Los planes activos y boosts influyen en el orden, pero nunca ocultan perfiles gratuitos.
create or replace function public.discover_nexo_profiles()
returns table(
    id uuid,
    name text,
    age integer,
    city text,
    bio text,
    intention text,
    interests text[],
    verified boolean,
    is_active boolean,
    photo_url text,
    profile_paused boolean,
    phone_verified boolean,
    is_online boolean,
    last_seen timestamptz
)
language sql
security definer
set search_path = public
as $$
    select
        p.id, p.name, p.age::integer, p.city, p.bio, p.intention, p.interests,
        p.verified, p.is_active, p.photo_url, p.profile_paused, p.phone_verified,
        case when coalesce(pref.show_online, true) then p.is_online else false end,
        case when coalesce(pref.show_last_seen, true) then p.last_seen else null end
    from public.profiles p
    left join public.user_preferences pref on pref.user_id = p.id
    left join public.premium_entitlements premium on premium.user_id = p.id
    where auth.uid() is not null
      and p.id <> auth.uid()
      and p.is_active
      and not p.profile_paused
      and not exists (
          select 1 from public.blocks b
          where (b.blocker_id = auth.uid() and b.blocked_id = p.id)
             or (b.blocker_id = p.id and b.blocked_id = auth.uid())
      )
    order by
        (premium.boost_expires_at > now()) desc,
        (premium.plus_active and premium.plus_expires_at > now()) desc,
        p.is_online desc,
        p.created_at desc;
$$;

revoke all on function public.discover_nexo_profiles() from public, anon;
grant execute on function public.discover_nexo_profiles() to authenticated;
