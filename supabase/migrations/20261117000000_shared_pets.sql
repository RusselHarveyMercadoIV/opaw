-- Households: an owner shares a pet with family and walkers through an invite link. Everyone
-- logs to the same pet, so meals never get doubled. The phone keeps its own copy of each pet and
-- syncs these tables both ways; ids are the phone's own (the pet's, each log's, each walk's).
--
-- Roles: the owner can do everything; family can log anything and edit the pet; a walker can log
-- walks, pee and poop only.

create table public.shared_pets (
    id uuid primary key,
    owner_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    -- The pet's profile as the app stores it: name, breed, birthday, meal times and so on.
    profile jsonb not null default '{}'::jsonb,
    -- Its pictures in the pet-photos bucket, "<pet id>/photo.jpg" and "<pet id>/cutout.png".
    photo_path text,
    cutout_path text,
    updated_at timestamptz not null default now()
);

create table public.pet_members (
    pet_id uuid not null references public.shared_pets (id) on delete cascade,
    user_id uuid not null references auth.users (id) on delete cascade,
    role text not null check (role in ('family', 'walker')),
    display_name text not null default '' check (char_length(display_name) <= 40),
    joined_at timestamptz not null default now(),
    primary key (pet_id, user_id)
);

create table public.pet_invites (
    code text primary key check (char_length(code) between 6 and 32),
    pet_id uuid not null references public.shared_pets (id) on delete cascade,
    role text not null check (role in ('family', 'walker')),
    invitee_name text not null default '' check (char_length(invitee_name) <= 40),
    created_by uuid not null default auth.uid() references auth.users (id) on delete cascade,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null default now() + interval '14 days',
    used_by uuid references auth.users (id) on delete set null,
    used_at timestamptz
);

create table public.pet_events (
    id uuid primary key,
    pet_id uuid not null references public.shared_pets (id) on delete cascade,
    kind text not null,
    data jsonb not null,
    author_id uuid default auth.uid() references auth.users (id) on delete set null,
    deleted boolean not null default false,
    updated_at timestamptz not null default now()
);
create index pet_events_pet_updated on public.pet_events (pet_id, updated_at);

create table public.pet_walks (
    id uuid primary key,
    pet_id uuid not null references public.shared_pets (id) on delete cascade,
    data jsonb not null,
    author_id uuid default auth.uid() references auth.users (id) on delete set null,
    deleted boolean not null default false,
    updated_at timestamptz not null default now()
);
create index pet_walks_pet_updated on public.pet_walks (pet_id, updated_at);

-- Every change moves updated_at on, which is how phones find what's new.
create function public.touch_updated_at() returns trigger
language plpgsql set search_path = '' as $$
begin
    new.updated_at := now();
    return new;
end;
$$;
create trigger shared_pets_touch before insert or update on public.shared_pets for each row execute function public.touch_updated_at();
create trigger pet_events_touch before insert or update on public.pet_events for each row execute function public.touch_updated_at();
create trigger pet_walks_touch before insert or update on public.pet_walks for each row execute function public.touch_updated_at();

-- The signed-in user's role for a pet: 'owner', 'family', 'walker', or null.
create function public.pet_role(pet uuid) returns text
language sql stable security definer set search_path = '' as $$
    select coalesce(
        (select 'owner' from public.shared_pets where id = pet and owner_id = (select auth.uid())),
        (select role from public.pet_members where pet_id = pet and user_id = (select auth.uid()))
    );
$$;
revoke all on function public.pet_role(uuid) from public;
grant execute on function public.pet_role(uuid) to authenticated;

alter table public.shared_pets enable row level security;
alter table public.pet_members enable row level security;
alter table public.pet_invites enable row level security;
alter table public.pet_events enable row level security;
alter table public.pet_walks enable row level security;

create policy "Members see the pet" on public.shared_pets for select to authenticated
    using (public.pet_role(id) is not null);
create policy "Owners share their pets" on public.shared_pets for insert to authenticated
    with check (owner_id = (select auth.uid()));
create policy "Owner and family edit the pet" on public.shared_pets for update to authenticated
    using (public.pet_role(id) in ('owner', 'family'))
    with check (public.pet_role(id) in ('owner', 'family'));
create policy "Owners stop sharing" on public.shared_pets for delete to authenticated
    using (owner_id = (select auth.uid()));

create policy "Members see each other" on public.pet_members for select to authenticated
    using (public.pet_role(pet_id) is not null);
create policy "Owners remove members, members leave" on public.pet_members for delete to authenticated
    using (public.pet_role(pet_id) = 'owner' or user_id = (select auth.uid()));

create policy "Owners see invites" on public.pet_invites for select to authenticated
    using (public.pet_role(pet_id) = 'owner');
create policy "Owners invite" on public.pet_invites for insert to authenticated
    with check (public.pet_role(pet_id) = 'owner' and created_by = (select auth.uid()));
create policy "Owners cancel invites" on public.pet_invites for delete to authenticated
    using (public.pet_role(pet_id) = 'owner');

-- Walkers log walks, pee and poop; everyone else logs anything.
create policy "Members see the logs" on public.pet_events for select to authenticated
    using (public.pet_role(pet_id) is not null);
create policy "Members add logs" on public.pet_events for insert to authenticated
    with check (public.pet_role(pet_id) in ('owner', 'family') or (public.pet_role(pet_id) = 'walker' and kind in ('pee', 'poop')));
create policy "Members change logs" on public.pet_events for update to authenticated
    using (public.pet_role(pet_id) in ('owner', 'family') or (public.pet_role(pet_id) = 'walker' and kind in ('pee', 'poop')))
    with check (public.pet_role(pet_id) in ('owner', 'family') or (public.pet_role(pet_id) = 'walker' and kind in ('pee', 'poop')));

create policy "Members see the walks" on public.pet_walks for select to authenticated
    using (public.pet_role(pet_id) is not null);
create policy "Members add walks" on public.pet_walks for insert to authenticated
    with check (public.pet_role(pet_id) is not null);
create policy "Members change walks" on public.pet_walks for update to authenticated
    using (public.pet_role(pet_id) is not null)
    with check (public.pet_role(pet_id) is not null);

-- Opening an invite link: joins the pet with the invite's role and returns the pet and role.
-- One invite is for one person; opening it again as the same person is fine.
create function public.accept_pet_invite(invite_code text, name text default '') returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
    invite public.pet_invites;
    me uuid := (select auth.uid());
begin
    if me is null then
        raise exception 'Sign in first.';
    end if;
    select * into invite from public.pet_invites where code = upper(trim(invite_code));
    if invite is null then
        raise exception 'That invite code doesn''t exist.';
    end if;
    if invite.used_by is not null and invite.used_by <> me then
        raise exception 'That invite has already been used.';
    end if;
    if invite.expires_at < now() then
        raise exception 'That invite has expired. Ask for a new one.';
    end if;
    if exists (select 1 from public.shared_pets where id = invite.pet_id and owner_id = me) then
        raise exception 'That''s your own pet.';
    end if;
    insert into public.pet_members (pet_id, user_id, role, display_name)
    values (invite.pet_id, me, invite.role, left(coalesce(nullif(trim(name), ''), invite.invitee_name), 40))
    on conflict (pet_id, user_id) do update set role = excluded.role, display_name = excluded.display_name;
    update public.pet_invites set used_by = me, used_at = now() where code = invite.code;
    return jsonb_build_object('pet_id', invite.pet_id, 'role', invite.role);
end;
$$;
revoke all on function public.accept_pet_invite(text, text) from public;
grant execute on function public.accept_pet_invite(text, text) to authenticated;

-- The pet's pictures, readable by its household, written by its owner and family.
insert into storage.buckets (id, name, public) values ('pet-photos', 'pet-photos', false)
on conflict (id) do nothing;

create function public.pet_folder_role(object_name text) returns text
language plpgsql stable security definer set search_path = '' as $$
begin
    return public.pet_role(((storage.foldername(object_name))[1])::uuid);
exception when invalid_text_representation then
    return null;
end;
$$;
revoke all on function public.pet_folder_role(text) from public;
grant execute on function public.pet_folder_role(text) to authenticated;

create policy "Household sees pet photos" on storage.objects for select to authenticated
    using (bucket_id = 'pet-photos' and public.pet_folder_role(name) is not null);
create policy "Owner and family add pet photos" on storage.objects for insert to authenticated
    with check (bucket_id = 'pet-photos' and public.pet_folder_role(name) in ('owner', 'family'));
create policy "Owner and family replace pet photos" on storage.objects for update to authenticated
    using (bucket_id = 'pet-photos' and public.pet_folder_role(name) in ('owner', 'family'));
