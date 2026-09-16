-- NEXO 2.0 social layer
-- Additive schema only: existing chat/discovery/billing tables are untouched.

create table if not exists public.social_posts (
    id uuid primary key default gen_random_uuid(),
    author_id uuid not null references auth.users(id) on delete cascade,
    kind text not null default 'post' check (kind in ('post','photo','video','poll','challenge_response','story')),
    body text not null default '',
    media_url text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.social_reactions (
    post_id uuid not null references public.social_posts(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    reaction text not null default 'like',
    created_at timestamptz not null default now(),
    primary key (post_id, user_id)
);

create table if not exists public.social_challenges (
    id uuid primary key default gen_random_uuid(),
    creator_id uuid not null references auth.users(id) on delete cascade,
    title text not null,
    description text not null default '',
    category text not null default 'general',
    daily boolean not null default false,
    active boolean not null default true,
    created_at timestamptz not null default now()
);

create table if not exists public.social_challenge_entries (
    challenge_id uuid not null references public.social_challenges(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    post_id uuid references public.social_posts(id) on delete set null,
    completed_at timestamptz not null default now(),
    primary key (challenge_id, user_id)
);

create table if not exists public.social_versus (
    id uuid primary key default gen_random_uuid(),
    creator_id uuid not null references auth.users(id) on delete cascade,
    left_post_id uuid references public.social_posts(id) on delete set null,
    right_post_id uuid references public.social_posts(id) on delete set null,
    category text not null default 'general',
    season text not null default '01',
    status text not null default 'open' check (status in ('open','closed')),
    created_at timestamptz not null default now()
);

create table if not exists public.social_versus_votes (
    versus_id uuid not null references public.social_versus(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    side text not null check (side in ('left','right')),
    created_at timestamptz not null default now(),
    primary key (versus_id, user_id)
);

create table if not exists public.social_circles (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    name text not null,
    description text not null default '',
    is_private boolean not null default true,
    created_at timestamptz not null default now()
);

create table if not exists public.social_circle_members (
    circle_id uuid not null references public.social_circles(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    role text not null default 'member' check (role in ('owner','admin','member')),
    joined_at timestamptz not null default now(),
    primary key (circle_id, user_id)
);

create table if not exists public.social_stories (
    id uuid primary key default gen_random_uuid(),
    author_id uuid not null references auth.users(id) on delete cascade,
    title text not null default '',
    body text not null,
    episode integer not null default 1,
    active boolean not null default true,
    created_at timestamptz not null default now()
);

create table if not exists public.social_story_choices (
    id uuid primary key default gen_random_uuid(),
    story_id uuid not null references public.social_stories(id) on delete cascade,
    label text not null,
    next_story_id uuid references public.social_stories(id) on delete set null,
    votes integer not null default 0
);

create table if not exists public.social_activity (
    user_id uuid primary key references auth.users(id) on delete cascade,
    streak_days integer not null default 0,
    challenge_wins integer not null default 0,
    versus_wins integer not null default 0,
    votes_received bigint not null default 0,
    updated_at timestamptz not null default now()
);

create index if not exists social_posts_author_created_idx on public.social_posts(author_id, created_at desc);
create index if not exists social_posts_created_idx on public.social_posts(created_at desc);
create index if not exists social_challenges_active_idx on public.social_challenges(active, created_at desc);
create index if not exists social_versus_open_idx on public.social_versus(status, created_at desc);
create index if not exists social_stories_active_idx on public.social_stories(active, created_at desc);

alter table public.social_posts enable row level security;
alter table public.social_reactions enable row level security;
alter table public.social_challenges enable row level security;
alter table public.social_challenge_entries enable row level security;
alter table public.social_versus enable row level security;
alter table public.social_versus_votes enable row level security;
alter table public.social_circles enable row level security;
alter table public.social_circle_members enable row level security;
alter table public.social_stories enable row level security;
alter table public.social_story_choices enable row level security;
alter table public.social_activity enable row level security;

-- Public social content is readable only to authenticated users.
create policy social_posts_read on public.social_posts for select to authenticated using (true);
create policy social_posts_insert_own on public.social_posts for insert to authenticated with check (auth.uid() = author_id);
create policy social_posts_update_own on public.social_posts for update to authenticated using (auth.uid() = author_id) with check (auth.uid() = author_id);
create policy social_posts_delete_own on public.social_posts for delete to authenticated using (auth.uid() = author_id);

create policy social_reactions_read on public.social_reactions for select to authenticated using (true);
create policy social_reactions_own on public.social_reactions for all to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy social_challenges_read on public.social_challenges for select to authenticated using (active = true or creator_id = auth.uid());
create policy social_challenges_own on public.social_challenges for all to authenticated using (auth.uid() = creator_id) with check (auth.uid() = creator_id);
create policy social_challenge_entries_read on public.social_challenge_entries for select to authenticated using (true);
create policy social_challenge_entries_own on public.social_challenge_entries for all to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy social_versus_read on public.social_versus for select to authenticated using (true);
create policy social_versus_own on public.social_versus for all to authenticated using (auth.uid() = creator_id) with check (auth.uid() = creator_id);
create policy social_versus_votes_read on public.social_versus_votes for select to authenticated using (true);
create policy social_versus_votes_own on public.social_versus_votes for all to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy social_circles_read on public.social_circles for select to authenticated using (owner_id = auth.uid() or exists (select 1 from public.social_circle_members m where m.circle_id = id and m.user_id = auth.uid()));
create policy social_circles_own on public.social_circles for all to authenticated using (owner_id = auth.uid()) with check (owner_id = auth.uid());
create policy social_circle_members_read on public.social_circle_members for select to authenticated using (user_id = auth.uid() or exists (select 1 from public.social_circles c where c.id = circle_id and c.owner_id = auth.uid()));
create policy social_circle_members_self on public.social_circle_members for insert to authenticated with check (user_id = auth.uid());
create policy social_circle_members_self_delete on public.social_circle_members for delete to authenticated using (user_id = auth.uid());

create policy social_stories_read on public.social_stories for select to authenticated using (active = true or author_id = auth.uid());
create policy social_stories_own on public.social_stories for all to authenticated using (auth.uid() = author_id) with check (auth.uid() = author_id);
create policy social_story_choices_read on public.social_story_choices for select to authenticated using (true);
create policy social_story_choices_author on public.social_story_choices for all to authenticated using (exists (select 1 from public.social_stories s where s.id = story_id and s.author_id = auth.uid())) with check (exists (select 1 from public.social_stories s where s.id = story_id and s.author_id = auth.uid()));

create policy social_activity_read on public.social_activity for select to authenticated using (user_id = auth.uid());
create policy social_activity_own on public.social_activity for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
