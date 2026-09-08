-- NEXO 0.2 - perfiles, likes y matches
-- Ejecutar en un proyecto Supabase nuevo o mediante Supabase CLI.

create extension if not exists pgcrypto;

create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    name text not null check (char_length(name) between 1 and 60),
    age smallint not null check (age between 18 and 120),
    city text not null check (char_length(city) between 1 and 100),
    bio text not null default '' check (char_length(bio) <= 500),
    intention text not null default 'Conocer a alguien de verdad' check (char_length(intention) <= 120),
    interests text[] not null default '{}',
    verified boolean not null default false,
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.likes (
    actor_id uuid not null references auth.users(id) on delete cascade,
    target_id uuid not null references auth.users(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (actor_id, target_id),
    constraint likes_not_self check (actor_id <> target_id)
);

create table if not exists public.matches (
    id uuid primary key default gen_random_uuid(),
    user_a uuid not null references auth.users(id) on delete cascade,
    user_b uuid not null references auth.users(id) on delete cascade,
    created_at timestamptz not null default now(),
    constraint matches_not_self check (user_a <> user_b),
    constraint matches_unique_pair unique (user_a, user_b)
);

create index if not exists likes_target_idx on public.likes(target_id);
create index if not exists matches_user_a_idx on public.matches(user_a);
create index if not exists matches_user_b_idx on public.matches(user_b);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists profiles_set_updated_at on public.profiles;
create trigger profiles_set_updated_at
before update on public.profiles
for each row execute function public.set_updated_at();

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
        select 1
        from public.likes
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

        insert into public.matches(user_a, user_b)
        values (first_user, second_user)
        on conflict (user_a, user_b) do nothing;
    end if;

    return new;
end;
$$;

drop trigger if exists likes_create_match on public.likes;
create trigger likes_create_match
after insert or update on public.likes
for each row execute function public.create_match_after_like();

alter table public.profiles enable row level security;
alter table public.likes enable row level security;
alter table public.matches enable row level security;

drop policy if exists profiles_read_active on public.profiles;
create policy profiles_read_active
on public.profiles for select
to authenticated
using (is_active = true or id = auth.uid());

drop policy if exists profiles_insert_own on public.profiles;
create policy profiles_insert_own
on public.profiles for insert
to authenticated
with check (id = auth.uid() and age >= 18);

drop policy if exists profiles_update_own on public.profiles;
create policy profiles_update_own
on public.profiles for update
to authenticated
using (id = auth.uid())
with check (id = auth.uid() and age >= 18);

drop policy if exists likes_read_own on public.likes;
create policy likes_read_own
on public.likes for select
to authenticated
using (actor_id = auth.uid());

drop policy if exists likes_insert_own on public.likes;
create policy likes_insert_own
on public.likes for insert
to authenticated
with check (actor_id = auth.uid() and target_id <> auth.uid());

drop policy if exists likes_update_own on public.likes;
create policy likes_update_own
on public.likes for update
to authenticated
using (actor_id = auth.uid())
with check (actor_id = auth.uid() and target_id <> auth.uid());

drop policy if exists likes_delete_own on public.likes;
create policy likes_delete_own
on public.likes for delete
to authenticated
using (actor_id = auth.uid());

drop policy if exists matches_read_participant on public.matches;
create policy matches_read_participant
on public.matches for select
to authenticated
using (user_a = auth.uid() or user_b = auth.uid());

grant select, insert, update on public.profiles to authenticated;
grant select, insert, update, delete on public.likes to authenticated;
grant select on public.matches to authenticated;

revoke all on function public.create_match_after_like() from public;
revoke all on function public.set_updated_at() from public;
