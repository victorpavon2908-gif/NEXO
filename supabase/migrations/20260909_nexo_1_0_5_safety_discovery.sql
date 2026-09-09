-- NEXO RC6: descubrimiento configurable, privacidad ampliada y Cita Segura.
-- Ejecutar después de 20260909_nexo_1_0_4_whatsapp_contacts.sql.

alter table public.user_preferences
    add column if not exists hide_from_phone_contacts boolean not null default false,
    add column if not exists approximate_location_only boolean not null default true,
    add column if not exists blur_private_media boolean not null default true,
    add column if not exists respectful_reminders boolean not null default true;

alter table public.profiles
    add column if not exists profile_paused boolean not null default false;

create table if not exists public.discovery_preferences (
    user_id uuid primary key references auth.users(id) on delete cascade,
    min_age integer not null default 18 check (min_age between 18 and 120),
    max_age integer not null default 60 check (max_age between 18 and 120 and max_age >= min_age),
    city text not null default '' check (char_length(city) <= 80),
    intention text not null default 'Todas' check (char_length(intention) <= 120),
    only_online boolean not null default false,
    profile_paused boolean not null default false,
    updated_at timestamptz not null default now()
);

alter table public.discovery_preferences enable row level security;

drop policy if exists "discovery_preferences_own_select" on public.discovery_preferences;
create policy "discovery_preferences_own_select" on public.discovery_preferences
for select using (auth.uid() = user_id);

drop policy if exists "discovery_preferences_own_insert" on public.discovery_preferences;
create policy "discovery_preferences_own_insert" on public.discovery_preferences
for insert with check (auth.uid() = user_id);

drop policy if exists "discovery_preferences_own_update" on public.discovery_preferences;
create policy "discovery_preferences_own_update" on public.discovery_preferences
for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

create table if not exists public.safe_date_plans (
    id uuid primary key,
    owner_id uuid not null references auth.users(id) on delete cascade,
    partner_id uuid references public.profiles(id) on delete set null,
    partner_name text not null check (char_length(partner_name) between 1 and 60),
    place text not null check (char_length(place) between 1 and 120),
    check_in_at text not null check (char_length(check_in_at) between 1 and 40),
    trusted_name text not null default '' check (char_length(trusted_name) <= 60),
    trusted_phone text not null check (char_length(trusted_phone) between 8 and 20),
    safety_code_hash text not null check (char_length(safety_code_hash) = 64),
    state text not null default 'planned' check (state in ('planned', 'confirmed_safe', 'cancelled')),
    created_at timestamptz not null default now()
);

create index if not exists safe_date_plans_owner_created_idx
    on public.safe_date_plans(owner_id, created_at desc);

alter table public.safe_date_plans enable row level security;

drop policy if exists "safe_dates_own_select" on public.safe_date_plans;
create policy "safe_dates_own_select" on public.safe_date_plans
for select using (auth.uid() = owner_id);

drop policy if exists "safe_dates_own_insert" on public.safe_date_plans;
create policy "safe_dates_own_insert" on public.safe_date_plans
for insert with check (auth.uid() = owner_id);

drop policy if exists "safe_dates_own_update" on public.safe_date_plans;
create policy "safe_dates_own_update" on public.safe_date_plans
for update using (auth.uid() = owner_id) with check (auth.uid() = owner_id);

drop policy if exists "safe_dates_own_delete" on public.safe_date_plans;
create policy "safe_dates_own_delete" on public.safe_date_plans
for delete using (auth.uid() = owner_id);

revoke all on public.discovery_preferences from anon;
revoke all on public.safe_date_plans from anon;
grant select, insert, update, delete on public.discovery_preferences to authenticated;
grant select, insert, update, delete on public.safe_date_plans to authenticated;

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
    where auth.uid() is not null
      and p.id <> auth.uid()
      and p.is_active
      and not p.profile_paused
      and not exists (
          select 1 from public.blocks b
          where (b.blocker_id = auth.uid() and b.blocked_id = p.id)
             or (b.blocker_id = p.id and b.blocked_id = auth.uid())
      )
    order by p.is_online desc, p.created_at desc;
$$;

revoke all on function public.discover_nexo_profiles() from public, anon;
grant execute on function public.discover_nexo_profiles() to authenticated;
