-- Bring to life moves to Tripo, which can rig four-legged pets; Meshy stays as a fallback.
-- Each job remembers which service it started on, and the task columns lose their Meshy names.

alter table public.pet_models rename column meshy_model_task to model_task;
alter table public.pet_models rename column meshy_rig_task to rig_task;

alter table public.pet_models
    -- Jobs from before this change were all Meshy's.
    add column provider text not null default 'meshy' check (provider in ('tripo', 'meshy')),
    -- Tripo only: the free riggability check, then the walk clip.
    add column check_task text,
    add column anim_task text;
