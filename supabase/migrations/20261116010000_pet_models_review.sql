-- Bring to life now first makes a cheap 2D reference sheet of the pet (standing, head straight,
-- even light) from the owner's photos, and waits for the owner to approve it before paying for
-- the 3D model and skeleton. cleaning: the sheet is being drawn. reviewing: waiting for the owner.

alter table public.pet_models drop constraint pet_models_stage_check;
alter table public.pet_models add constraint pet_models_stage_check
    check (stage in ('cleaning', 'reviewing', 'modeling', 'rigging', 'saving', 'done', 'failed'));

alter table public.pet_models add column sheet_task text;
