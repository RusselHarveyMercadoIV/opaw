-- 3D pet models made with Meshy ("Bring to life"). The pet-model Edge Function holds the Meshy
-- API key, drives each job through image-to-3D and quadruped rigging, and copies the finished
-- GLB files into the pet-models bucket. The app only reads its own rows and files.

create table public.pet_models (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users (id) on delete cascade,
    -- The dog's local id in the app, as in avatar_jobs.
    dog_id uuid not null,
    input_path text not null,
    -- modeling: Meshy image-to-3D is running. rigging: the quadruped skeleton is being added.
    stage text not null default 'modeling' check (stage in ('modeling', 'rigging', 'saving', 'done', 'failed')),
    progress int not null default 0,
    meshy_model_task text,
    meshy_rig_task text,
    -- Map of "rest", "walk" and "run" to storage paths in the pet-models bucket.
    outputs jsonb not null default '{}'::jsonb,
    error text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index pet_models_owner_idx on public.pet_models (owner_id, created_at desc);

alter table public.pet_models enable row level security;
-- Rows are written only by the Edge Function with the service role.
create policy "pet models: read own" on public.pet_models for select to authenticated using (owner_id = auth.uid());

insert into storage.buckets (id, name, public) values ('pet-models', 'pet-models', false)
on conflict (id) do nothing;

create policy "pet models: read own files" on storage.objects for select to authenticated
    using (bucket_id = 'pet-models' and (storage.foldername(name))[1] = auth.uid()::text);
