-- Opaw initial schema: social "Pack" features and the avatar clip job queue.
-- Apply with the Supabase CLI (`supabase db push`) or paste into the SQL editor.

create extension if not exists postgis with schema extensions;

-- ---------------------------------------------------------------------------
-- Profiles
-- ---------------------------------------------------------------------------
create table public.profiles (
    id uuid primary key references auth.users (id) on delete cascade,
    display_name text not null default '' check (char_length(display_name) <= 40),
    accepted_rules_at timestamptz,
    created_at timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- Blocks (needed by the visibility rules below)
-- ---------------------------------------------------------------------------
create table public.blocks (
    blocker_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    blocked_id uuid not null references auth.users (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (blocker_id, blocked_id),
    check (blocker_id <> blocked_id)
);

-- True when either user has blocked the other.
create or replace function public.is_blocked(a uuid, b uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.blocks
        where (blocker_id = a and blocked_id = b)
           or (blocker_id = b and blocked_id = a)
    );
$$;

-- ---------------------------------------------------------------------------
-- Dogs shared with the Pack
-- ---------------------------------------------------------------------------
create table public.dogs (
    id uuid primary key,
    owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    name text not null check (char_length(name) between 1 and 40),
    breed text not null default '' check (char_length(breed) <= 60),
    size text not null default 'medium' check (size in ('small', 'medium', 'large', 'giant')),
    avatar_path text,
    -- Coarse position only (the app snaps to a ~500 m grid; the trigger below rounds again).
    lat double precision,
    lon double precision,
    location extensions.geography(point, 4326)
        generated always as (
            case when lat is null or lon is null then null
                 else extensions.st_setsrid(extensions.st_makepoint(lon, lat), 4326)::extensions.geography
            end
        ) stored,
    is_discoverable boolean not null default true,
    updated_at timestamptz not null default now()
);

create index dogs_location_idx on public.dogs using gist (location);
create index dogs_owner_idx on public.dogs (owner_id);

-- Defense in depth: never store more than ~1 km precision, whatever the client sends.
create or replace function public.coarsen_dog_location()
returns trigger
language plpgsql
as $$
begin
    if new.lat is not null then
        new.lat := round(new.lat::numeric, 2)::double precision;
    end if;
    if new.lon is not null then
        new.lon := round(new.lon::numeric, 2)::double precision;
    end if;
    new.updated_at := now();
    return new;
end;
$$;

create trigger dogs_coarsen_location
before insert or update on public.dogs
for each row execute function public.coarsen_dog_location();

-- ---------------------------------------------------------------------------
-- Posts: shared walks, memories and badges
-- ---------------------------------------------------------------------------
create table public.posts (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    dog_id uuid not null references public.dogs (id) on delete cascade,
    kind text not null check (kind in ('walk', 'memory', 'badge')),
    caption text not null default '' check (char_length(caption) <= 500),
    distance_m double precision,
    duration_s double precision,
    -- Simplified route with the start and end trimmed off by the app.
    route jsonb,
    image_path text,
    badge text,
    created_at timestamptz not null default now()
);

create index posts_created_idx on public.posts (created_at desc);
create index posts_dog_idx on public.posts (dog_id);

create table public.likes (
    post_id uuid not null references public.posts (id) on delete cascade,
    user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (post_id, user_id)
);

create table public.follows (
    follower_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    dog_id uuid not null references public.dogs (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (follower_id, dog_id)
);

-- ---------------------------------------------------------------------------
-- Reports (reviewed by the team; required for App Store user-generated content)
-- ---------------------------------------------------------------------------
create table public.reports (
    id uuid primary key default gen_random_uuid(),
    reporter_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    post_id uuid references public.posts (id) on delete set null,
    dog_id uuid references public.dogs (id) on delete set null,
    reason text not null check (char_length(reason) between 1 and 500),
    status text not null default 'open' check (status in ('open', 'actioned', 'dismissed')),
    created_at timestamptz not null default now(),
    check (post_id is not null or dog_id is not null)
);

-- ---------------------------------------------------------------------------
-- Avatar clip jobs, processed by avatar-worker with the service role key
-- ---------------------------------------------------------------------------
create table public.avatar_jobs (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    -- The dog's local id in the app. Not a foreign key: the dog may not be shared with the Pack.
    dog_id uuid not null,
    breed text not null default '',
    input_path text not null,
    clips text[] not null default array['idle', 'wag', 'excited', 'hungry', 'sleepy', 'shake', 'celebrate', 'walk'],
    status text not null default 'queued' check (status in ('queued', 'processing', 'done', 'failed')),
    -- Map of clip name to storage path in the avatar-clips bucket.
    outputs jsonb not null default '{}'::jsonb,
    error text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index avatar_jobs_status_idx on public.avatar_jobs (status, created_at);

-- Atomically claims the oldest queued job. Called by the worker.
create or replace function public.claim_avatar_job()
returns setof public.avatar_jobs
language sql
security definer
set search_path = public
as $$
    update public.avatar_jobs
    set status = 'processing', updated_at = now()
    where id = (
        select id from public.avatar_jobs
        where status = 'queued'
        order by created_at
        for update skip locked
        limit 1
    )
    returning *;
$$;

revoke execute on function public.claim_avatar_job() from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- Nearby dogs
-- ---------------------------------------------------------------------------
create or replace function public.nearby_dogs(p_lat double precision, p_lon double precision, p_radius_m double precision default 5000)
returns table (
    id uuid,
    owner_id uuid,
    name text,
    breed text,
    size text,
    avatar_path text,
    lat double precision,
    lon double precision,
    distance_m double precision
)
language sql
stable
security invoker
set search_path = public, extensions
as $$
    select d.id, d.owner_id, d.name, d.breed, d.size, d.avatar_path, d.lat, d.lon,
           -- Rounded so exact distances can't be used to triangulate.
           round(st_distance(d.location, st_setsrid(st_makepoint(p_lon, p_lat), 4326)::geography) / 250) * 250 as distance_m
    from public.dogs d
    where d.is_discoverable
      and d.location is not null
      and d.owner_id <> auth.uid()
      and st_dwithin(d.location, st_setsrid(st_makepoint(p_lon, p_lat), 4326)::geography, least(p_radius_m, 50000))
    order by distance_m
    limit 100;
$$;

-- ---------------------------------------------------------------------------
-- Row level security
-- ---------------------------------------------------------------------------
alter table public.profiles enable row level security;
alter table public.blocks enable row level security;
alter table public.dogs enable row level security;
alter table public.posts enable row level security;
alter table public.likes enable row level security;
alter table public.follows enable row level security;
alter table public.reports enable row level security;
alter table public.avatar_jobs enable row level security;

create policy "profiles: read all" on public.profiles for select to authenticated using (true);
create policy "profiles: write own" on public.profiles for insert to authenticated with check (id = auth.uid());
create policy "profiles: update own" on public.profiles for update to authenticated using (id = auth.uid());

create policy "blocks: read own" on public.blocks for select to authenticated using (blocker_id = auth.uid());
create policy "blocks: add own" on public.blocks for insert to authenticated with check (blocker_id = auth.uid());
create policy "blocks: remove own" on public.blocks for delete to authenticated using (blocker_id = auth.uid());

create policy "dogs: read visible" on public.dogs for select to authenticated
    using (owner_id = auth.uid() or (is_discoverable and not public.is_blocked(auth.uid(), owner_id)));
create policy "dogs: add own" on public.dogs for insert to authenticated with check (owner_id = auth.uid());
create policy "dogs: update own" on public.dogs for update to authenticated using (owner_id = auth.uid()) with check (owner_id = auth.uid());
create policy "dogs: delete own" on public.dogs for delete to authenticated using (owner_id = auth.uid());

create policy "posts: read unblocked" on public.posts for select to authenticated
    using (owner_id = auth.uid() or not public.is_blocked(auth.uid(), owner_id));
create policy "posts: add own" on public.posts for insert to authenticated
    with check (owner_id = auth.uid() and exists (select 1 from public.dogs d where d.id = dog_id and d.owner_id = auth.uid()));
create policy "posts: delete own" on public.posts for delete to authenticated using (owner_id = auth.uid());

create policy "likes: read all" on public.likes for select to authenticated using (true);
create policy "likes: add own" on public.likes for insert to authenticated with check (user_id = auth.uid());
create policy "likes: remove own" on public.likes for delete to authenticated using (user_id = auth.uid());

create policy "follows: read all" on public.follows for select to authenticated using (true);
create policy "follows: add own" on public.follows for insert to authenticated with check (follower_id = auth.uid());
create policy "follows: remove own" on public.follows for delete to authenticated using (follower_id = auth.uid());

create policy "reports: add own" on public.reports for insert to authenticated with check (reporter_id = auth.uid());

create policy "avatar jobs: read own" on public.avatar_jobs for select to authenticated using (owner_id = auth.uid());
create policy "avatar jobs: add own" on public.avatar_jobs for insert to authenticated
    with check (owner_id = auth.uid() and status = 'queued');

-- ---------------------------------------------------------------------------
-- Storage
-- ---------------------------------------------------------------------------
insert into storage.buckets (id, name, public) values
    ('avatar-inputs', 'avatar-inputs', false),
    ('avatar-clips', 'avatar-clips', false),
    ('post-images', 'post-images', true)
on conflict (id) do nothing;

-- Files live under a folder named after the owner's user id, e.g. "<uid>/<dog-id>.png".
create policy "avatar inputs: upload own" on storage.objects for insert to authenticated
    with check (bucket_id = 'avatar-inputs' and (storage.foldername(name))[1] = auth.uid()::text);
create policy "avatar inputs: replace own" on storage.objects for update to authenticated
    using (bucket_id = 'avatar-inputs' and (storage.foldername(name))[1] = auth.uid()::text);
create policy "avatar inputs: read own" on storage.objects for select to authenticated
    using (bucket_id = 'avatar-inputs' and (storage.foldername(name))[1] = auth.uid()::text);

create policy "avatar clips: read own" on storage.objects for select to authenticated
    using (bucket_id = 'avatar-clips' and (storage.foldername(name))[1] = auth.uid()::text);

create policy "post images: upload own" on storage.objects for insert to authenticated
    with check (bucket_id = 'post-images' and (storage.foldername(name))[1] = auth.uid()::text);
create policy "post images: delete own" on storage.objects for delete to authenticated
    using (bucket_id = 'post-images' and (storage.foldername(name))[1] = auth.uid()::text);
